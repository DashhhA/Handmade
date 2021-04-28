const { assert } = require('chai');
const mocha = require('mocha');
const events = require('events');
const path = require('path');
const emitterEvents = require('../../utils/events');
const UserModelService = require('../../services/UserModelService');
const authService = require('../../services/UserAuthService');
const authorizedUserService = require('../../services/AuthorizedUserService');
const newUserService = require('../../services/NewUserService');
// assuming, test is runned from project root
require('dotenv').config({ path: path.resolve(process.cwd(), 'test/unit/test.env') });

let mongoose;
let globalEvents;
let localEvents;
let data0;
let userModelService;

mocha.before(async () => {
  mongoose = await require('../../loaders/MongooseLoader');
  userModelService = new UserModelService(mongoose);
  globalEvents = new events.EventEmitter();
  localEvents = new events.EventEmitter();
  data0 = {
    id: 'id10',
    eventEmitter: localEvents,
  };

  newUserService(globalEvents, userModelService);
  authService(globalEvents, userModelService);
  authorizedUserService(globalEvents, userModelService);

  // drop db
  await mongoose.models.User.deleteMany();
  await mongoose.models.Customer.deleteMany();
});

mocha.after(() => {
  mongoose.connection.close();
});

mocha.describe('Customer lifecickle', () => {
  it('Create customer', (done) => {
    const body = {
      body: {
        fName: 'fName',
        login: 'login@l',
        password: 'qwerty',
        userType: 'customer',
      },
    };
    const data = Object.assign(data0, body);

    globalEvents.emit(emitterEvents.EVENT_NEW_USER, data);
    localEvents.once(emitterEvents.EVENT_RESPONSE, (resp) => {
      try {
        assert.isTrue(resp.message.success, 'creation of user must succeed');
        done();
      } catch (e) {
        done(e);
      }
    });
  });

  let user;
  it('Created user and customer model should point to each other', async () => {
    user = await mongoose.models.User.findOne({ login: 'login@l' });
    const customer = await mongoose.models.Customer.findOne({ _id: user.modelId });
    assert.deepEqual(user.modelId, customer._id, 'user.modelId should point to customer');
    assert.deepEqual(customer.userId, user._id, 'customer.userId should point to user');
  });

  it('customer watcher should be successfullty attached', (done) => {
    const data = {
      id: 'customer_watch_req_id',
      eventEmitter: localEvents,
      modelId: user.modelId,
      modelType: 'customer',
    };
    globalEvents.emit(emitterEvents.EVENT_WATCH, data);
    localEvents.once(emitterEvents.EVENT_RESPONSE, (resp) => {
      try {
        assert.equal(resp.id, data.id);
        assert.deepEqual(resp.message.updated, { orders: [], userId: user._id });
        done();
      } catch (e) {
        done(e);
      }
    });
  });

  it('should not throw update on orders list', (done) => {
    localEvents.once(emitterEvents.EVENT_RESPONSE, (resp) => {
      if (resp === 'customer_watch_req_id') {
        done('update was thrown');
      }
    });

    mongoose.models.Customer.updateOne(
      { _id: user.modelId },
      { $push: { orders: mongoose.Types.ObjectId() } },
    ).exec();
    setTimeout(done, 10);
  });

  it('customer watcher should be remvoed on request', (done) => {
    const data = {
      id: 'unwatch_req_id',
      eventEmitter: localEvents,
      reqId: 'customer_watch_req_id',
    };
    localEvents.once(emitterEvents.EVENT_RESPONSE, (resp) => {
      assert.equal(resp.id, data.id);
      assert(resp.message.success);
      done();
    });
    globalEvents.emit(emitterEvents.EVENT_UNWATCH, data);
  });

  it('list watcher on orders list should throw refresh event on eattached', (done) => {
    const data = {
      id: 'list_req_id',
      eventEmitter: localEvents,
      modelType: 'customer',
      path: {
        id: user.modelId.toString(),
        props: 'orders',
      },
    };

    localEvents.once(emitterEvents.EVENT_RESPONSE, (resp) => {
      try {
        assert.equal(resp.id, data.id);
        assert.equal(resp.message.event, 'refresh');
        done();
      } catch (e) {
        done(e);
      }
    });

    globalEvents.emit(emitterEvents.EVENT_WATCH_LIST, data);
  });

  it('list watcher should throw [refresh] event on delete', (done) => {
    localEvents.once(emitterEvents.EVENT_RESPONSE, (resp) => {
      try {
        assert.equal(resp.message.event, 'refresh');
        assert.equal(resp.message.updated.length, 0);
        done();
      } catch (e) {
        done(e);
      }
    });

    mongoose.models.Customer.updateOne(
      { _id: user.modelId },
      { $pop: { orders: 1 } },
    ).exec();
  });

  it('list watcher should throw [refresh] event on first insertion', (done) => {
    localEvents.once(emitterEvents.EVENT_RESPONSE, (resp) => {
      try {
        assert.equal(resp.message.event, 'refresh');
        assert.equal(resp.message.updated.length, 1);
        done();
      } catch (e) {
        done(e);
      }
    });

    mongoose.models.Customer.updateOne(
      { _id: user.modelId },
      { $push: { orders: mongoose.Types.ObjectId() } },
    ).exec();
  });

  it('list watcher should throw [insert] event on second insertion', (done) => {
    const id = mongoose.Types.ObjectId();
    localEvents.once(emitterEvents.EVENT_RESPONSE, (resp) => {
      try {
        assert.equal(resp.message.event, 'insert');
        assert.equal(resp.message.updated, id.toString());
        done();
      } catch (e) {
        done(e);
      }
    });

    mongoose.models.Customer.updateOne(
      { _id: user.modelId },
      { $push: { orders: id } },
    ).exec();
  });

  it('list watcher should throw [insert] event on third insertion', (done) => {
    const id = mongoose.Types.ObjectId();
    localEvents.once(emitterEvents.EVENT_RESPONSE, (resp) => {
      try {
        assert.equal(resp.message.event, 'insert');
        assert.equal(resp.message.updated, id.toString());
        done();
      } catch (e) {
        done(e);
      }
    });

    mongoose.models.Customer.updateOne(
      { _id: user.modelId },
      { $push: { orders: id } },
    ).exec();
  });

  it('list watcher should throw [refresh] event deletion', (done) => {
    localEvents.once(emitterEvents.EVENT_RESPONSE, (resp) => {
      try {
        assert.equal(resp.message.event, 'refresh');
        assert.equal(resp.message.updated.length, 2);
        done();
      } catch (e) {
        done(e);
      }
    });

    mongoose.models.Customer.updateOne(
      { _id: user.modelId },
      { $pop: { orders: 1 } },
    ).exec();
  });

  it('list watcher should throw [refresh] event deletion', (done) => {
    localEvents.once(emitterEvents.EVENT_RESPONSE, (resp) => {
      try {
        assert.equal(resp.message.event, 'refresh');
        assert.equal(resp.message.updated.length, 1);
        done();
      } catch (e) {
        done(e);
      }
    });

    mongoose.models.Customer.updateOne(
      { _id: user.modelId },
      { $pop: { orders: 1 } },
    ).exec();
  });

  it('list watcher should throw [refresh] event deletion', (done) => {
    localEvents.once(emitterEvents.EVENT_RESPONSE, (resp) => {
      try {
        assert.equal(resp.message.event, 'refresh');
        assert.equal(resp.message.updated.length, 0);
        done();
      } catch (e) {
        done(e);
      }
    });

    mongoose.models.Customer.updateOne(
      { _id: user.modelId },
      { $pop: { orders: 1 } },
    ).exec();
  });

  it('list watcher should be remvoed on request', (done) => {
    const data = {
      id: 'unwatch_req_id1',
      eventEmitter: localEvents,
      reqId: 'list_req_id',
    };
    localEvents.once(emitterEvents.EVENT_RESPONSE, (resp) => {
      assert.equal(resp.id, data.id);
      assert(resp.message.success);
      done();
    });
    globalEvents.emit(emitterEvents.EVENT_UNWATCH, data);
  });

  it('list watcher on orders list should throw refresh event on eattached', (done) => {
    const data = {
      id: 'coll_req_id',
      eventEmitter: localEvents,
      modelType: 'customer',
    };

    localEvents.once(emitterEvents.EVENT_RESPONSE, (resp) => {
      try {
        assert.equal(resp.id, data.id);
        assert.equal(resp.message.event, 'refresh');
        done();
      } catch (e) {
        done(e);
      }
    });

    globalEvents.emit(emitterEvents.EVENT_WATCH_LIST, data);
  });

  let customerId;
  it('collection watcher should throw [insert] event on new customer', (done) => {
    localEvents.once(emitterEvents.EVENT_RESPONSE, (resp) => {
      try {
        assert.equal(resp.id, 'coll_req_id');
        assert.equal(resp.message.event, 'insert');
        assert.notEqual(resp.message.updated.userId, undefined);
        done();
      } catch (e) {
        done(e);
      }
    });

    const obj = mongoose.models.Customer({
      userId: mongoose.Types.ObjectId(),
    });
    obj.save(obj);
    customerId = obj._id;
  });

  it('collection watcher should throw [delete] event on customer deletion', (done) => {
    localEvents.once(emitterEvents.EVENT_RESPONSE, (resp) => {
      try {
        assert.equal(resp.id, 'coll_req_id');
        assert.equal(resp.message.event, 'delete');
        assert.notEqual(resp.message.updated.dbId, undefined);
        done();
      } catch (e) {
        done(e);
      }
    });

    mongoose.models.Customer.deleteOne({ _id: customerId }).exec();
  });

  it('collection watcher should be remvoed on request', (done) => {
    const data = {
      id: 'unwatch_req_id2',
      eventEmitter: localEvents,
      reqId: 'coll_req_id',
    };
    localEvents.once(emitterEvents.EVENT_RESPONSE, (resp) => {
      assert.equal(resp.id, data.id);
      assert(resp.message.success);
      done();
    });
    globalEvents.emit(emitterEvents.EVENT_UNWATCH, data);
  });
});
