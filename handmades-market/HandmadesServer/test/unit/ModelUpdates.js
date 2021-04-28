const { assert } = require('chai');
const mocha = require('mocha');
const events = require('events');
const path = require('path');
const authorizedUserService = require('../../services/AuthorizedUserService');
const UserModelService = require('../../services/UserModelService');
const orderStatus = require('../../utils/OrderStatus');
const marketStatus = require('../../utils/MarketStatus');
const reqTypes = require('../../controllers/RequestTypes');
const emitterEvents = require('../../utils/events');
// assuming, test is runned from project root
require('dotenv').config({ path: path.resolve(process.cwd(), 'test/unit/test.env') });

function checkResponse(eventEmitter, check) {
  return new Promise((resolve, reject) => {
    eventEmitter.once(emitterEvents.EVENT_RESPONSE, (resp) => {
      try {
        check(resp);
        resolve();
      } catch (e) {
        reject(e);
      }
    });

    eventEmitter.once(emitterEvents.EVENT_REJECT, (err) => reject(err));
  });
}

let mongoose;
let userModelService;
let globalEvents;
let localEvents;
let order;
let product;
let market;

mocha.before(async () => {
  mongoose = await require('../../loaders/MongooseLoader');
  userModelService = new UserModelService(mongoose);
  globalEvents = new events.EventEmitter();
  localEvents = new events.EventEmitter();
});

mocha.after(async () => {
  await mongoose.connection.close();
});

mocha.describe('Model updates', () => {
  it('Drop DB', async () => {
    authorizedUserService(globalEvents, userModelService);

    await mongoose.models.Order.deleteMany();
    await mongoose.models.Product.deleteMany();
    await mongoose.models.Market.deleteMany();

    order = await mongoose.models.Order({
      customerId: mongoose.Types.ObjectId(),
      vendorId: mongoose.Types.ObjectId(),
      products: [],
      status: orderStatus.posted,
    }).save();

    product = await mongoose.models.Product({
      marketId: mongoose.Types.ObjectId(),
      code: 'ccc',
      title: 't',
      description: 'd',
      quantity: 0,
    }).save();

    market = await mongoose.models.Market({
      vendorId: mongoose.Types.ObjectId(),
      products: [],
      name: 'n',
      description: 'd',
      status: marketStatus.validating,
    }).save();
  });

  it('Add order listener', async () => {
    const req = {
      id: 'id_watch_order',
      eventEmitter: localEvents,
      modelId: order._id,
      modelType: reqTypes.MODEL_TYPES.order,
    };

    setTimeout(() => globalEvents.emit(emitterEvents.EVENT_WATCH, req), 0);
    await checkResponse(localEvents, (resp) => {
      assert.equal(req.id, resp.id);
      assert.equal(resp.message.event, 'update');
    });
  });

  it('Order update listener should notify about update', async () => {
    await mongoose.models.Order.updateOne(
      { _id: order._id },
      { $set: { status: orderStatus.delivered } },
    );

    await checkResponse(localEvents, (resp) => {
      assert.equal(resp.id, 'id_watch_order');
      assert.equal(resp.message.event, 'update');
    });
  });

  it('Add product listener', async () => {
    const req = {
      id: 'id_watch_product',
      eventEmitter: localEvents,
      modelId: product._id,
      modelType: reqTypes.MODEL_TYPES.product,
    };

    setTimeout(() => globalEvents.emit(emitterEvents.EVENT_WATCH, req), 0);
    await checkResponse(localEvents, (resp) => {
      assert.equal(req.id, resp.id);
      assert.equal(resp.message.event, 'update');
    });
  });

  it('Product update listener should notify about update', async () => {
    await mongoose.models.Product.updateOne(
      { _id: product._id },
      { $set: { description: 'jsdfgk' } },
    );

    await checkResponse(localEvents, (resp) => {
      assert.equal(resp.id, 'id_watch_product');
      assert.equal(resp.message.event, 'update');
    });
  });

  it('Add market listener', async () => {
    const req = {
      id: 'id_watch_market',
      eventEmitter: localEvents,
      modelId: market._id,
      modelType: reqTypes.MODEL_TYPES.market,
    };

    setTimeout(() => globalEvents.emit(emitterEvents.EVENT_WATCH, req), 0);
    await checkResponse(localEvents, (resp) => {
      assert.equal(req.id, resp.id);
      assert.equal(resp.message.event, 'update');
    });
  });

  it('Market update listener should notify about update', async () => {
    await mongoose.models.Market.updateOne(
      { _id: market._id },
      { $set: { description: 'jsdsdfd' } },
    );

    await checkResponse(localEvents, (resp) => {
      assert.equal(resp.id, 'id_watch_market');
      assert.equal(resp.message.event, 'update');
    });
  });

  it('All watchers should be removed', async () => {
    const req = {
      id: 'id_rm_watch_order',
      eventEmitter: localEvents,
      reqId: 'id_watch_order',
    };

    setTimeout(() => globalEvents.emit(emitterEvents.EVENT_UNWATCH, req), 0);
    await checkResponse(localEvents, (resp) => {
      assert.equal(req.id, resp.id);
      assert.isTrue(resp.message.success);
    });

    const req1 = {
      id: 'id_rm_watch_product',
      eventEmitter: localEvents,
      reqId: 'id_watch_product',
    };

    setTimeout(() => globalEvents.emit(emitterEvents.EVENT_UNWATCH, req1), 0);
    await checkResponse(localEvents, (resp) => {
      assert.equal(req1.id, resp.id);
      assert.isTrue(resp.message.success);
    });

    const req2 = {
      id: 'id_rm_watch_market',
      eventEmitter: localEvents,
      reqId: 'id_watch_market',
    };

    setTimeout(() => globalEvents.emit(emitterEvents.EVENT_UNWATCH, req2), 0);
    await checkResponse(localEvents, (resp) => {
      assert.equal(req2.id, resp.id);
      assert.isTrue(resp.message.success);
    });
  });
});
