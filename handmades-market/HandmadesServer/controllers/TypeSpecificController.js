const { ObjectId } = require('mongoose').Types;
const events = require('../utils/events');
const userTypes = require('../utils/userTypes');
const reqTypes = require('./RequestTypes');
const { UnableException, SocketRequestError } = require('../utils/errors');

/**
* @throws SocketRequestError
*/
function checkMakePurchase(body) {
  if (!Object.prototype.hasOwnProperty.call(body, 'products')) {
    throw new SocketRequestError('no "body.products" field');
  }
  if (!Array.isArray(body.products)) {
    throw new SocketRequestError('"body.products" must be an array');
  }
  if (body.products.length < 1) {
    throw new SocketRequestError('"body.products" must contain at least one field');
  }
  body.products.forEach((el) => {
    if (!Object.prototype.hasOwnProperty.call(el, 'code')) {
      throw new SocketRequestError('no "body.products" has wrong item (no "code field")');
    }
    if (typeof el.code !== 'string') {
      throw new SocketRequestError('"body.products" has wrong item (code must be string)');
    }
    if (!Object.prototype.hasOwnProperty.call(el, 'quantity')) {
      throw new SocketRequestError('no "body.products" has wrong item (no "quantity field")');
    }
    if (typeof el.quantity !== 'number') {
      throw new SocketRequestError('"body.products" has wrong item (quantity must be number)');
    }
  });
  if (body.comment !== undefined) {
    if (typeof body.comment !== 'string') {
      throw new SocketRequestError('"body.comment" must be a string');
    }
  }
}

function checkAddMarket(body) {
  if (!Object.prototype.hasOwnProperty.call(body, 'name')) {
    throw new SocketRequestError('no "body.name" field');
  }
  if (typeof body.name !== 'string') {
    throw new SocketRequestError('"body.name" must be a string');
  }
  if (!Object.prototype.hasOwnProperty.call(body, 'description')) {
    throw new SocketRequestError('no "body.description" field');
  }
  if (typeof body.description !== 'string') {
    throw new SocketRequestError('"body.description" must be a string');
  }
  if (body.imageUrl !== undefined && typeof body.imageUrl !== 'string') {
    throw new SocketRequestError('"body.imageUrl" must be a string');
  }
}

function checkAddProduct(body) {
  if (!Object.prototype.hasOwnProperty.call(body, 'name')) {
    throw new SocketRequestError('no "body.name" field');
  }
  if (typeof body.name !== 'string') {
    throw new SocketRequestError('"body.name" must be a string');
  }
  if (!Object.prototype.hasOwnProperty.call(body, 'description')) {
    throw new SocketRequestError('no "body.description" field');
  }
  if (typeof body.description !== 'string') {
    throw new SocketRequestError('"body.description" must be a string');
  }
  if (!Object.prototype.hasOwnProperty.call(body, 'marketId')) {
    throw new SocketRequestError('no "body.marketId" field');
  }
  if (typeof body.marketId !== 'string') {
    throw new SocketRequestError('"body.marketId" must be a string');
  }
  if (!Object.prototype.hasOwnProperty.call(body, 'quantity')) {
    throw new SocketRequestError('no "body.quantity" field');
  }
  if (typeof body.quantity !== 'number') {
    throw new SocketRequestError('"body.quantity" must be a string');
  }
  if (!Object.prototype.hasOwnProperty.call(body, 'price')) {
    throw new SocketRequestError('no "body.price" field');
  }
  if (typeof body.price !== 'number') {
    throw new SocketRequestError('"body.price" must be a string');
  }
}

/**
 * @return {(Request) => Unit} A function to call on request of this user type
 */
module.exports = (user, globalEvents, localEvents) => {
  function formData(req, data) {
    return {
      id: req.id,
      eventEmitter: localEvents,
      ...data,
    };
  }

  /**
  * @throws UnableException, SocketRequestError
  */
  function customerActions(req) {
    switch (req.type) {
      case reqTypes.MAKE_PURCHASE: {
        checkMakePurchase(req.body);
        const body = {
          userId: user._id,
          products: req.body.products,
          comment: req.body.comment,
        };
        const data = formData(req, body);
        globalEvents.emit(events.EVENT_PURCHASE, data);
        break;
      }
      default:
        throw new UnableException('User of type "Customer" is not allowed to '
          + `do this operation (${req.type})`);
    }
  }

  /**
  * @throws UnableException, SocketRequestError
  */
  function vendorActions(req) {
    switch (req.type) {
      case reqTypes.ADD_MARKET: {
        checkAddMarket(req.body);
        const body = {
          userId: user._id,
          name: req.body.name,
          description: req.body.description,
        };
        if (req.body.imageUrl !== undefined) body.imageUrl = req.body.imageUrl;
        const data = formData(req, body);
        globalEvents.emit(events.EVENT_ADD_MARKET, data);
        break;
      }
      case reqTypes.ADD_PRODUCT: {
        checkAddProduct(req.body);
        const body = {
          marketId: ObjectId(req.body.marketId),
          name: req.body.name,
          description: req.body.description,
          quantity: req.body.quantity,
          price: req.body.price,
          photoUrls: req.body.photoUrls,
        };
        const data = formData(req, body);
        globalEvents.emit(events.EVENT_ADD_PRODUCT, data);
        break;
      }
      default:
        throw new UnableException('User of type "Vendor" is not allowed to '
          + `do this operation (${req.type})`);
    }
  }

  switch (user.userType) {
    case userTypes.customer:
      return customerActions;
    case userTypes.vendor:
      return vendorActions;
    default:
      return () => {};
      // TODO unreachable
  }
};
