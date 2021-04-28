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
   * @param {ObjectId} vandorId id of the vendor
   * @param {string} name Name of the market
   * @param {string} description Description of the market
   * @param {string | undefined} imageUrl Location og the image
   */
  async save(vendorId, name, description, imageUrl) {
    const descr = {
      vendorId,
      products: [],
      name,
      description,
      status: statuses.validating,
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
