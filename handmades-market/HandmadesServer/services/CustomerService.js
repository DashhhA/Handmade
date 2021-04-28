const MongooseService = require('./MongooseService');
const customerModel = require('../models/customer');
/**
 * @description Controlls Customer model
 */
class CustomerService extends MongooseService {
  constructor(mongoose) {
    super(customerModel(mongoose));
  }

  /**
   * Saves passed instance of customer to the DB
   * @param {Object} User, owning this model
   */
  async save(user) {
    /* eslint-disable no-underscore-dangle */
    const customer = {
      _id: user.modelId,
      userId: user._id,
      orders: [],
    };
    /* eslint-enable no-underscore-dangle */

    await this.trySave(customer);
  }

  async addOrder(customerId, orderId) {
    await this.model.updateOne(
      { _id: customerId },
      { $push: { orders: orderId } },
    );
  }

  /**
   * @description Provides current state of the model to client
   * @param {ObjectId} id Id of the customer in the DB
   * @return {ChangeStream | null, Customer | null} change stream for events
   * on customer or null if no soch customer, and tehe customer to send current
   * state
   */
  async watch(id) {
    const customer = await this.model.findOne({ _id: id });

    if (customer === null) return { changeStream: null, customer };

    // filter out requested customer
    const pipeline = [
      {
        $match: {
          'documentKey._id': customer._id,
          'updateDescription.updatedFields.orders': undefined,
        },
      }];
    const options = { fullDocument: 'updateLookup' };
    const changeStream = this.model.watch(pipeline, options);
    return { changeStream, customer };
  }
}

module.exports = CustomerService;
