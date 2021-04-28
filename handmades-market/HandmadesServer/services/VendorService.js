const MongooseService = require('./MongooseService');
const vendorModel = require('../models/vendor');
/**
 * @description Controlls Customer model
 */
class VendorService extends MongooseService {
  constructor(mongoose) {
    super(vendorModel(mongoose));
  }

  /**
   * Saves passed instance of vendor to the DB
   * @param {Object} User, owning this model
   */
  async save(user) {
    /* eslint-disable no-underscore-dangle */
    const vendor = {
      _id: user.modelId,
      userId: user._id,
      orders: [],
      markets: [],
    };
    /* eslint-enable no-underscore-dangle */

    await this.trySave(vendor);
  }

  async addMarket(vendorId, marketId) {
    await this.model.updateOne(
      { _id: vendorId },
      { $push: { markets: marketId } },
    );
  }

  async addOrder(vendorId, orderId) {
    await this.model.updateOne(
      { _id: vendorId },
      { $push: { orders: orderId } },
    );
  }

  /**
   * @description Provides current state of the model to client
   * @param {ObjectId} id Id of the customer in the DB
   * @return {ChangeStream | null, Vendor | null} change stream for events
   * on customer or null if no soch vendor, and the vendor to send current
   * state
   */
  async watch(id) {
    const vendor = await this.model.findOne({ _id: id });

    if (vendor === null) return { changeStream: null, vendor };

    // filter out requested customer
    const pipeline = [
      {
        $match: {
          'documentKey._id': vendor._id,
          'updateDescription.updatedFields.orders': undefined,
          'updateDescription.updatedFields.markets': undefined,
        },
      }];
    const options = { fullDocument: 'updateLookup' };
    const changeStream = this.model.watch(pipeline, options);
    return { changeStream, vendor };
  }
}

module.exports = VendorService;
