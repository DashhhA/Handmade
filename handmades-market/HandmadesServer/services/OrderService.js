const MongooseService = require('./MongooseService');
const orderModel = require('../models/order');
const orderStatus = require('../utils/OrderStatus');
/**
 * @description Controlls Customer model
 */
class OrderService extends MongooseService {
  constructor(mongoose) {
    super(orderModel(mongoose));
  }

  /**
   * Saves passed instance of order to the DB
   * @param {Customer} customer, made this order
   * @param {Vendor} vendor,owns products
   * @param {Chat} chat, Chat for this order
   * @param {Array[Object]} products List of product id's and quantities
   */
  async save(customer, vendor, chat, products) {
    /* eslint-disable no-underscore-dangle */
    const order = {
      customerId: customer.userId,
      vendorId: vendor.userId,
      products,
      status: orderStatus.posted,
      chatId: chat._id,
    };
    /* eslint-enable no-underscore-dangle */

    const saved = await this.trySave(order);
    return saved;
  }

  /**
   * @description Provides current state of the model to client
   * @param {ObjectId} id Id of the order in the DB
   * @return {ChangeStream | null, Order | null} change stream for events
   * on order or null if no soch order, and the order to send current
   * state
   */
  async watch(id) {
    const order = await this.model.findOne({ _id: id });

    if (order === null) return { changeStream: null, order };

    // filter out requested customer
    const pipeline = [
      {
        $match: {
          'documentKey._id': order._id,
        },
      }];
    const options = { fullDocument: 'updateLookup' };
    const changeStream = this.model.watch(pipeline, options);
    return { changeStream, order };
  }
}

module.exports = OrderService;
