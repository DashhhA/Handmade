const MongooseService = require('./MongooseService');
const marketModel = require('../models/market');
const statuses = require('../utils/MarketStatus');
/**
 * @description Controlls Customer model
 */
class MarketService extends MongooseService {
  constructor(mongoose) {
    super(marketModel(mongoose));
  }

  async addProduct(marketId, productId) {
    await this.model.updateOne(
      { _id: marketId },
      { $push: { products: productId } },
    );
  }

  /**
   * Creates instance of market in the DB
   * @param {ObjectId} vendorId id of the vendor
   * @param {string} city Market city
   * @param {string} name Name of the market
   * @param {string} description Description of the market
   * @param {string | undefined} imageUrl Location og the image
   * @param {Array<string>} tags Tags for the market products
   */
  async save(vendorId, name, city, description, imageUrl, tags) {
    const descr = {
      vendorId,
      products: [],
      name,
      city,
      description,
      status: statuses.validating,
      tags,
    };
    if (imageUrl !== undefined) descr.imageUrl = imageUrl;
    const saved = await this.trySave(descr);

    return saved;
  }

  /**
   * @description Provides current state of the model to client
   * @param {ObjectId} id Id of the market in the DB
   * @return {ChangeStream | null, Market | null} change stream for events
   * on market or null if no soch market, and tehe market to send current
   * state
   */
  async watch(id) {
    const market = await this.model.findOne({ _id: id });

    if (market === null) return { changeStream: null, market };

    // filter out requested customer
    const pipeline = [
      {
        $match: {
          'documentKey._id': market._id,
          'updateDescription.updatedFields.products': undefined,
        },
      }];
    const options = { fullDocument: 'updateLookup' };
    const changeStream = this.model.watch(pipeline, options);
    return { changeStream, market };
  }
}

module.exports = MarketService;
