const { assert } = require('chai');
const mocha = require('mocha');
const events = require('events');
const path = require('path');
const emitterEvents = require('../../utils/events');
const reqTypes = require('../../controllers/RequestTypes');
const UserModelService = require('../../services/UserModelService');
const authorizedUserService = require('../../services/AuthorizedUserService');
const newUserService = require('../../services/NewUserService');
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
let globalEvents;
let localEvents;
let userModelService;
let user;

mocha.before(async () => {
  mongoose = await require('../../loaders/MongooseLoader');
  userModelService = new UserModelService(mongoose);
  globalEvents = new events.EventEmitter();
  localEvents = new events.EventEmitter();

  newUserService(globalEvents, userModelService);
  authorizedUserService(globalEvents, userModelService);

  // drop db
  await mongoose.models.User.deleteMany();
  await mongoose.models.Vendor.deleteMany();
  await mongoose.models.Market.deleteMany();
});

mocha.after(() => {
  mongoose.connection.close();
});

mocha.describe('Vendor', () => {
  it('Vendor model must be created on user creation', async () => {
    const req = {
      id: 'id_new_customer',
      eventEmitter: localEvents,
      body: {
        fName: 'Customer_1',
        login: 'login@c1',
        password: 'qwerty',
        userType: reqTypes.MODEL_TYPES.vendor,
      },
    };
    globalEvents.emit(emitterEvents.EVENT_NEW_USER, req);

    await checkResponse(localEvents, (resp) => {
      assert.isTrue(resp.message.success);
    });

    user = await mongoose.models.User.findOne({ login: req.body.login });
    assert.exists(user);
    const model = await mongoose.models.Vendor.findById(user.modelId);
    assert.exists(model);
  });

  it('Created user and vendor models should point to each other', async () => {
    const vendor = await mongoose.models.Vendor.findOne({ _id: user.modelId });
    assert.deepEqual(user.modelId, vendor._id, 'user.modelId should point to customer');
    assert.deepEqual(vendor.userId, user._id, 'customer.userId should point to user');
  });

  it('vendor watcher should be successfullty attached', async () => {
    const data = {
      id: 'vendor_watch_req_id',
      eventEmitter: localEvents,
      modelId: user.modelId,
      modelType: reqTypes.MODEL_TYPES.vendor,
    };
    globalEvents.emit(emitterEvents.EVENT_WATCH, data);
    await checkResponse(localEvents, (resp) => {
      assert.equal(resp.id, data.id);
      assert.equal(resp.message.event, 'update');
    });
  });

  it('should not throw update on orders list', (done) => {
    localEvents.once(emitterEvents.EVENT_RESPONSE, (resp) => {
      if (resp === 'vendor_watch_req_id') {
        done('update was thrown');
      }
    });

    setTimeout(done, 100);

    setTimeout(() => {
      mongoose.models.Vendor.updateOne(
        { _id: user.modelId },
        { $push: { orders: mongoose.Types.ObjectId() } },
      ).exec();
    }, 0);
  });

  it('should not throw update on markets list', (done) => {
    localEvents.once(emitterEvents.EVENT_RESPONSE, (resp) => {
      if (resp === 'vendor_watch_req_id') {
        done('update was thrown');
      }
    });

    setTimeout(done, 100);

    setTimeout(() => {
      mongoose.models.Vendor.updateOne(
        { _id: user.modelId },
        { $push: { markets: mongoose.Types.ObjectId() } },
      ).exec();
    }, 0);
  });

  it('vendor watcher should be successfullty removed', async () => {
    const data = {
      id: 'id_vendor_unwatchwatch',
      eventEmitter: localEvents,
      reqId: 'vendor_watch_req_id',
    };
    setTimeout(() => globalEvents.emit(emitterEvents.EVENT_UNWATCH, data), 0);
    await checkResponse(localEvents, (resp) => {
      assert.equal(resp.id, data.id);
      assert.isTrue(resp.message.success);
    });
  });

  let createdMarketId;
  it('Vemdor add market should return success', async () => {
    const data = {
      id: 'id_vendor_add_market',
      eventEmitter: localEvents,
      userId: user._id,
      name: 'market_name',
      description: 'descr',
    };
    setTimeout(() => globalEvents.emit(emitterEvents.EVENT_ADD_MARKET, data), 0);
    await checkResponse(localEvents, (resp) => {
      assert.equal(resp.id, data.id);
      assert.isTrue(resp.message.success);
      assert.exists(resp.message.data.marketId);
      createdMarketId = resp.message.data.marketId;
    });
  });

  it('Market should be created and added to vendor', async () => {
    const market = await mongoose.models.Market.findById(createdMarketId);
    assert.exists(market);
    const vendor = await mongoose.models.Vendor.findById(user.modelId);
    assert.deepInclude(vendor.markets, market._id);
  });

  let createdProductId;
  it('Vendor add product should return success', async () => {
    const data = {
      id: 'id_vendor_add_product',
      eventEmitter: localEvents,
      marketId: createdMarketId,
      name: 'p_name',
      description: 'des',
      quantity: 1,
    };
    setTimeout(() => globalEvents.emit(emitterEvents.EVENT_ADD_PRODUCT, data), 0);
    await checkResponse(localEvents, (resp) => {
      assert.equal(resp.id, data.id);
      assert.isTrue(resp.message.success);
      assert.exists(resp.message.data.productId);
      createdProductId = resp.message.data.productId;
    });
  });

  it('Product should be created and added to market', async () => {
    const product = await mongoose.models.Product.findById(createdProductId);
    assert.exists(product);
    const market = await mongoose.models.Market.findById(product.marketId);
    assert.deepInclude(market.products, product._id);
  });
});
