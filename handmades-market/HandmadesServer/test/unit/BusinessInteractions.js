const { assert } = require('chai');
const mocha = require('mocha');
const events = require('events');
const path = require('path');
const authorizedUserService = require('../../services/AuthorizedUserService');
const UserModelService = require('../../services/UserModelService');
const newUserService = require('../../services/NewUserService');
const reqTypes = require('../../controllers/RequestTypes');
const emitterEvents = require('../../utils/events');
const orderStatus = require('../../utils/OrderStatus');
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
let customerUser;
let vendorUser;
let vendorLocalEvents;
let customer1Events;

let marketId;
let prodId1;
let prodId2;
let orderId;

mocha.before(async () => {
  mongoose = await require('../../loaders/MongooseLoader');
  userModelService = new UserModelService(mongoose);
  globalEvents = new events.EventEmitter();
  vendorLocalEvents = new events.EventEmitter();
  customer1Events = new events.EventEmitter();
});

mocha.after(async () => {
  await mongoose.connection.close();
});

mocha.describe('Buisness interactions', () => {
  it('Drop DB', async () => {
    // launch auth services for globalEvents
    authorizedUserService(globalEvents, userModelService);
    newUserService(globalEvents, userModelService);

    await mongoose.models.User.deleteMany();
    await mongoose.models.Customer.deleteMany();
    await mongoose.models.Vendor.deleteMany();
  });

  it('Register customer', async () => {
    const req = {
      id: 'id_new_customer',
      eventEmitter: customer1Events,
      body: {
        fName: 'Customer_1',
        login: 'login@c1',
        password: 'qwerty',
        userType: reqTypes.MODEL_TYPES.customer,
      },
    };
    globalEvents.emit(emitterEvents.EVENT_NEW_USER, req);
    await checkResponse(customer1Events, (resp) => {
      assert.isTrue(resp.message.success);
    });

    customerUser = await mongoose.models.User.findOne({ login: req.body.login });
    assert.exists(customerUser);
    const customerModel = await mongoose.models.Customer.findById(customerUser.modelId);
    assert.exists(customerModel);
  });

  it('Register vendor', async () => {
    const req = {
      id: 'id_new_vendor',
      eventEmitter: customer1Events,
      body: {
        fName: 'Vendor_1',
        login: 'login@v1',
        password: 'qwerty',
        userType: reqTypes.MODEL_TYPES.vendor,
      },
    };
    globalEvents.emit(emitterEvents.EVENT_NEW_USER, req);
    await checkResponse(customer1Events, (resp) => {
      assert.isTrue(resp.message.success);
    });

    vendorUser = await mongoose.models.User.findOne({ login: req.body.login });
    assert.exists(vendorUser);
    const vendorModel = await mongoose.models.Vendor.findById(vendorUser.modelId);
    assert.exists(vendorModel);
  });

  it('Customer adds listener to vendor markets', async () => {
    const req = {
      id: 'id_customer_watch_markets',
      eventEmitter: customer1Events,
      modelType: reqTypes.MODEL_TYPES.vendor,
      path: {
        id: vendorUser.modelId.toString(),
        props: 'markets',
      },
    };

    setTimeout(() => globalEvents.emit(emitterEvents.EVENT_WATCH_LIST, req), 0);
    await checkResponse(customer1Events, (resp) => {
      assert.equal(req.id, resp.id);
      assert.equal(resp.message.event, 'refresh');
    });
  });

  it('Vendor adds market', async () => {
    const data = {
      id: 'id_vendor_add_market',
      eventEmitter: vendorLocalEvents,
      userId: vendorUser._id,
      name: 'market_name',
      description: 'descr',
    };
    setTimeout(() => globalEvents.emit(emitterEvents.EVENT_ADD_MARKET, data), 0);
    await checkResponse(vendorLocalEvents, (resp) => {
      assert.equal(resp.id, data.id);
      assert.isTrue(resp.message.success);
      assert.exists(resp.message.data.marketId);
      marketId = resp.message.data.marketId;
    });
  });

  it('Customer should be informed about market creation', async () => {
    await checkResponse(customer1Events, (resp) => {
      assert.equal(resp.message.event, 'refresh');
      assert.deepEqual(resp.message.updated, [marketId]);
    });
  });

  it('Customer adds listener to market products', async () => {
    const req = {
      id: 'id_customer_watch_products',
      eventEmitter: customer1Events,
      modelType: reqTypes.MODEL_TYPES.market,
      path: {
        id: marketId.toString(),
        props: 'products',
      },
    };

    setTimeout(() => globalEvents.emit(emitterEvents.EVENT_WATCH_LIST, req), 0);
    await checkResponse(customer1Events, (resp) => {
      assert.equal(req.id, resp.id);
      assert.equal(resp.message.event, 'refresh');
    });
  });

  it('Vendor adds product to market', async () => {
    const req = {
      id: 'id_vendor_add_product',
      eventEmitter: vendorLocalEvents,
      marketId,
      name: 'Product 1',
      description: 'descr',
      quantity: 10,
      price: 1.5,
      photoUrls: ['g'],
    };

    setTimeout(() => globalEvents.emit(emitterEvents.EVENT_ADD_PRODUCT, req), 0);
    await checkResponse(vendorLocalEvents, (resp) => {
      assert.equal(resp.id, req.id);
      assert.isTrue(resp.message.success);
      assert.exists(resp.message.data.productId);
      prodId1 = resp.message.data.productId;
    });
  });

  it('Customer should be informed about product creation', async () => {
    await checkResponse(customer1Events, (resp) => {
      assert.equal(resp.id, 'id_customer_watch_products');
      assert.equal(resp.message.event, 'refresh');
    });
  });

  it('Vendor adds product to market [1]', async () => {
    const req = {
      id: 'id_vendor_add_product1',
      eventEmitter: vendorLocalEvents,
      marketId,
      name: 'Product 2',
      description: 'descr2',
      quantity: 1,
      price: 1.5,
      photoUrls: ['g'],
    };

    setTimeout(() => globalEvents.emit(emitterEvents.EVENT_ADD_PRODUCT, req), 0);
    await checkResponse(vendorLocalEvents, (resp) => {
      assert.equal(resp.id, req.id);
      assert.isTrue(resp.message.success);
      assert.exists(resp.message.data.productId);
      prodId2 = resp.message.data.productId;
    });
  });

  it('Customer should be informed about product creation[1]', async () => {
    await checkResponse(customer1Events, (resp) => {
      assert.equal(resp.id, 'id_customer_watch_products');
      assert.equal(resp.message.event, 'insert');
    });
  });

  it('Customer adds watcher to own orders', async () => {
    const req = {
      id: 'id_customer_watch_orders',
      eventEmitter: customer1Events,
      modelType: reqTypes.MODEL_TYPES.customer,
      path: {
        id: customerUser.modelId.toString(),
        props: 'orders',
      },
    };

    setTimeout(() => globalEvents.emit(emitterEvents.EVENT_WATCH_LIST, req), 0);
    await checkResponse(customer1Events, (resp) => {
      assert.equal(req.id, resp.id);
      assert.equal(resp.message.event, 'refresh');
    });
  });

  it('Vendor adds watcher to own orders', async () => {
    const req = {
      id: 'id_vendor_watch_orders',
      eventEmitter: vendorLocalEvents,
      modelType: reqTypes.MODEL_TYPES.vendor,
      path: {
        id: vendorUser.modelId.toString(),
        props: 'orders',
      },
    };

    setTimeout(() => globalEvents.emit(emitterEvents.EVENT_WATCH_LIST, req), 0);
    await checkResponse(vendorLocalEvents, (resp) => {
      assert.equal(req.id, resp.id);
      assert.equal(resp.message.event, 'refresh');
    });
  });

  it('Customer creates order', async () => {
    const prod1 = await mongoose.models.Product.findById(prodId1);
    const prod2 = await mongoose.models.Product.findById(prodId2);
    const req = {
      id: 'id_customer_order',
      eventEmitter: customer1Events,
      userId: customerUser._id,
      products: [
        { code: prod1.code, quantity: 3 },
        { code: prod2.code, quantity: 1 },
      ],
      comment: 'ha',
    };

    setTimeout(() => globalEvents.emit(emitterEvents.EVENT_PURCHASE, req), 0);
    await checkResponse(customer1Events, (resp) => {
      assert.equal(req.id, resp.id);
      assert.exists(resp.message.data.orderId);
      orderId = resp.message.data.orderId;
    });
  });

  it('Customer and Vendor should be notified about new order', async () => {
    const promise1 = checkResponse(customer1Events, (resp) => {
      assert.equal(resp.id, 'id_customer_watch_orders');
      assert.equal(resp.message.event, 'refresh');
    });

    const promise2 = checkResponse(vendorLocalEvents, (resp) => {
      assert.equal(resp.id, 'id_vendor_watch_orders');
      assert.equal(resp.message.event, 'refresh');
    });

    await Promise.all([promise1, promise2]);
  });

  it('Customer adds watcher to order', async () => {
    const req = {
      id: 'id_customer_watch_order',
      eventEmitter: customer1Events,
      modelId: orderId,
      modelType: 'order',
    };

    setTimeout(() => globalEvents.emit(emitterEvents.EVENT_WATCH, req), 0);
    await checkResponse(customer1Events, (resp) => {
      assert.equal(req.id, resp.id);
      assert.equal(resp.message.event, 'update');
    });
  });

  it('Vendor updates order state', async () => {
    const req = {
      id: 'id_vendor_edit_order',
      eventEmitter: vendorLocalEvents,
      path: reqTypes.MODEL_TYPES.order,
      itemId: orderId,
      update: { status: orderStatus.processing },
      user: vendorUser,
    };

    setTimeout(() => globalEvents.emit(emitterEvents.EVENT_EDIT_ITEM, req), 0);
    await checkResponse(vendorLocalEvents, (resp) => {
      assert.equal(req.id, resp.id);
      assert.isTrue(resp.message.success);
    });
  });

  it('Customer should be notified about order update', async () => {
    await checkResponse(customer1Events, (resp) => {
      assert.equal(resp.id, 'id_customer_watch_order');
      assert.equal(resp.message.event, 'update');
      assert.equal(resp.message.updated.status, orderStatus.processing);
    });
  });

  it('Customer markets watcher should be removed', async () => {
    const req = {
      id: 'id_rm_customer_watch_markets',
      eventEmitter: customer1Events,
      reqId: 'id_customer_watch_markets',
    };

    setTimeout(() => globalEvents.emit(emitterEvents.EVENT_UNWATCH, req), 0);
    await checkResponse(customer1Events, (resp) => {
      assert.equal(req.id, resp.id);
      assert.isTrue(resp.message.success);
    });
  });

  it('Customer products watcher should be removed', async () => {
    const req = {
      id: 'id_rm_customer_watch_products',
      eventEmitter: customer1Events,
      reqId: 'id_customer_watch_products',
    };

    setTimeout(() => globalEvents.emit(emitterEvents.EVENT_UNWATCH, req), 0);
    await checkResponse(customer1Events, (resp) => {
      assert.equal(req.id, resp.id);
      assert.isTrue(resp.message.success);
    });
  });

  it('Customer orders watcher should be removed', async () => {
    const req = {
      id: 'id_rm_customer_watch_orders',
      eventEmitter: customer1Events,
      reqId: 'id_customer_watch_orders',
    };

    setTimeout(() => globalEvents.emit(emitterEvents.EVENT_UNWATCH, req), 0);
    await checkResponse(customer1Events, (resp) => {
      assert.equal(req.id, resp.id);
      assert.isTrue(resp.message.success);
    });
  });

  it('Vendor orders watcher should be removed', async () => {
    const req = {
      id: 'id_rm_vendor_watch_orders',
      eventEmitter: vendorLocalEvents,
      reqId: 'id_vendor_watch_orders',
    };

    setTimeout(() => globalEvents.emit(emitterEvents.EVENT_UNWATCH, req), 0);
    await checkResponse(vendorLocalEvents, (resp) => {
      assert.equal(req.id, resp.id);
      assert.isTrue(resp.message.success);
    });
  });

  it('Customer order watcher should be removed', async () => {
    const req = {
      id: 'id_rm_customer_watch_order',
      eventEmitter: customer1Events,
      reqId: 'id_customer_watch_order',
    };

    setTimeout(() => globalEvents.emit(emitterEvents.EVENT_UNWATCH, req), 0);
    await checkResponse(customer1Events, (resp) => {
      assert.equal(req.id, resp.id);
      assert.isTrue(resp.message.success);
    });
  });

  it('Customer deletion should result in deletion of user and model', async () => {
    const req = {
      id: 'id_delete_customer',
      eventEmitter: customer1Events,
      login: customerUser.login,
    };

    globalEvents.emit(emitterEvents.EVENT_REMOVE_USER, req);
    await new Promise((resolve) => {
      customer1Events.once(emitterEvents.EVENT_LOGOUT, () => {
        resolve();
      });
    });

    const user = await mongoose.models.User.findById(customerUser._id);
    const customer = await mongoose.models.Customer.findById(customerUser.modelId);
    assert.isNull(user);
    assert.isNull(customer);
  });

  it('Vendor deletion should result in deletion of user and model', async () => {
    const req = {
      id: 'id_delete_vendor',
      eventEmitter: vendorLocalEvents,
      login: vendorUser.login,
    };

    globalEvents.emit(emitterEvents.EVENT_REMOVE_USER, req);
    await new Promise((resolve) => {
      vendorLocalEvents.once(emitterEvents.EVENT_LOGOUT, () => {
        resolve();
      });
    });

    const user = await mongoose.models.User.findById(vendorUser._id);
    const vendor = await mongoose.models.Vendor.findById(vendorUser.modelId);
    assert.isNull(user);
    assert.isNull(vendor);
  })
});
