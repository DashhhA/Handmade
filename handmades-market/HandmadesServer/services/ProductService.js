const MongooseService = require('./MongooseService');
const productModel = require('../models/product');
const utils = require('../utils/util');
const { ProductException } = require('../utils/errors');
/**
 * @description Controlls Customer model
 */
class ProductService extends MongooseService {
  constructor(mongoose) {
    super(productModel(mongoose));
  }

  /**
   * Saves instance of product to the DB
   */
  async save(descr) {
    if (descr.quantity < 0) {
      throw new ProductException('quantity must not be negative');
    }
    /* eslint-disable no-underscore-dangle */
    async function saveWithCode(code, model) {
      const product = {
        code,
        ...descr,
      };
      try {
        const saved = await model.trySave(product);
        return saved;
      } catch (e) {
        if (e.code === 11000 && e.keyValue.code !== undefined) {
          const newCode = await utils.asyncId();
          return saveWithCode(newCode, model);
        }
        throw e;
      }
    }
    /* eslint-enable no-underscore-dangle */
    const code = await utils.asyncId();
    const saved = await saveWithCode(code, this);

    return saved;
  }

  /**
   * Returns products with their vendor by product codes
   * @param {Array[string]} codes Unique codes of products
   */
  async getWithVendor(codes) {
    // for each matched product get market from "markets" collection, then get
    // vendor from "vendors" collection by vendorId from market
    const products = await this.model.aggregate([
      { $match: { code: { $in: codes } } },
      { $lookup: { from: 'markets', as: 'markets', localField: 'marketId', foreignField: '_id' } },
      { $project: { quantity: 1, code: 1, market: { $arrayElemAt: ['$markets', 0] } } },
      { $lookup: { from: 'vendors', as: 'vendors', localField: 'market.vendorId', foreignField: '_id' } },
      { $project: { quantity: 1, code: 1, marketId: '$market._id', vendor: { $arrayElemAt: ['$vendors', 0] } } },
    ]);

    return products;
  }

  /**
   * @description Provides current state of the model to client
   * @param {ObjectId} id Id of the customer in the DB
   * @return {ChangeStream | null, Vendor | null} change stream for events
   * on customer or null if no soch vendor, and the vendor to send current
   * state
   */
  async watch(id) {
    const product = await this.model.findOne({ _id: id });

    if (product === null) return { changeStream: null, product };

    // filter out requested customer
    const pipeline = [
      {
        $match: {
          'documentKey._id': product._id,
        },
      }];
    const options = { fullDocument: 'updateLookup' };
    const changeStream = this.model.watch(pipeline, options);
    return { changeStream, product };
  }
}

module.exports = ProductService;
