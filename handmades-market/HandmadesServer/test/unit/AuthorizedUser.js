const { assert } = require('chai');
const mocha = require('mocha');
const events = require('events');
const path = require('path');
const net = require('net');
const emitterEvents = require('../../utils/events');
const UserModelService = require('../../services/UserModelService');
const authService = require('../../services/UserAuthService');
const { UnauthorizedException, UnableException } = require('../../utils/errors');
const SocketAuth = require('../../controllers/SocketAuth');
const userModel = require('../../models/user');
const reqTypes = require('../../controllers/RequestTypes');
const authorizedUserService = require('../../services/AuthorizedUserService');
// assuming, test is runned from project root
require('dotenv').config({ path: path.resolve(process.cwd(), 'test/unit/test.env') });

let mongoose;
let globalEvents;
let localEvents;
let data0;
let userModelService;
let client;
let testServer;
let users;
let user1;
let user4watch;

mocha.before(async () => {
  mongoose = await require('../../loaders/MongooseLoader');
  userModelService = new UserModelService(mongoose);
  globalEvents = new events.EventEmitter();
  localEvents = new events.EventEmitter();
  data0 = {
    id: 'id10',
    eventEmitter: localEvents,
  };
  testServer = net.createServer((socket) => {
    new SocketAuth(socket, globalEvents, localEvents);
  });
  testServer.listen(8001, () => {});
  client = net.connect({ port: 8001 });

  // launch auth services for globalEvents
  authService(globalEvents, userModelService);
  authorizedUserService(globalEvents, userModelService);
});

mocha.after(async () => {
  await mongoose.connection.close();
  client.destroy();
  testServer.unref();
});

mocha.describe('Authenticated user', () => {
  it('drop db and populate it with users', async () => {
    const user1Descr = {
      fName: 'name1',
      login: 'login1',
      password: {
        data: 'c8b2505b76926abdc733523caa9f439142f66aa7293a7baaac0aed41a191eef6',
        salt: 'salt',
      },
      userType: 'customer',
      modelId: mongoose.Types.ObjectId(),
    };

    const user4watchDescr = {
      fName: 'watch',
      login: 'watch',
      password: {
        data: 'c8b2505b76926abdc733523caa9f439142f66aa7293a7baaac0aed41a191eef6',
        salt: 'salt',
      },
      userType: 'customer',
      modelId: mongoose.Types.ObjectId(),
    };

    // drop db
    users = userModel(mongoose);
    await users.deleteMany();

    user1 = await userModelService.trySave(user1Descr);
    user4watch = await userModelService.trySave(user4watchDescr);
  });
  mocha.describe('Unauthorized user should not be able to access authorized functional', () => {
    it('unauthorized user should not be able logout', (done) => {
      const req = {
        id: 'req_id',
        type: reqTypes.LOGOUT,
        body: {},
      };

      client.write(JSON.stringify(req));

      localEvents.once(emitterEvents.EVENT_REJECT, (err) => {
        if (err instanceof UnauthorizedException) {
          done();
        } else {
          done('Wrong exception');
        }
      });
    });

    it('unauthorized user should not be able to remove account', (done) => {
      const req = {
        id: 'req_id',
        type: reqTypes.REMOVE_USER,
        body: {},
      };

      client.write(JSON.stringify(req));

      localEvents.once(emitterEvents.EVENT_REJECT, (err) => {
        if (err instanceof UnauthorizedException) {
          done();
        } else {
          done('Wrong exception');
        }
      });
    });
  });

  mocha.describe('Authorized user functional', () => {
    it('authorise user', (done) => {
      const body = {
        body: {
          login: 'login1',
          password: 'pass',
        },
      };
      const data = Object.assign(data0, body);
      globalEvents.emit(emitterEvents.EVENT_AUTH_USER, data);
      localEvents.once(emitterEvents.EVENT_USER_AUTHENTICATED, () => done());
    });

    it('logout', (done) => {
      const req = {
        id: 'req_id',
        type: reqTypes.LOGOUT,
        body: {},
      };

      client.write(JSON.stringify(req));

      localEvents.once(emitterEvents.EVENT_RESPONSE, (resp) => {
        if (resp.message.success) {
          done();
        } else {
          done('wrong response');
        }
      });
    });

    it('enshure authorized functional unavailable', (done) => {
      const req = {
        id: 'req_id',
        type: reqTypes.LOGOUT,
        body: {},
      };

      client.write(JSON.stringify(req));

      localEvents.once(emitterEvents.EVENT_REJECT, (err) => {
        if (err instanceof UnauthorizedException) {
          done();
        } else {
          done('Wrong exception');
        }
      });
    });

    it('authorise user', (done) => {
      const body = {
        body: {
          login: 'login1',
          password: 'pass',
        },
      };
      const data = Object.assign(data0, body);
      globalEvents.emit(emitterEvents.EVENT_AUTH_USER, data);
      localEvents.once(emitterEvents.EVENT_USER_AUTHENTICATED, () => done());
    });

    it('remove user done', (done) => {
      const req = {
        id: 'req_id',
        type: reqTypes.REMOVE_USER,
        body: {},
      };

      client.write(JSON.stringify(req));

      localEvents.once(emitterEvents.EVENT_RESPONSE, async (resp) => {
        if (resp.message.success) {
          const usr = await userModelService.getByLogin(user1.login);
          if (usr === null) {
            done();
          } else {
            done('User not deleted');
          }
        } else {
          done('wrong response');
        }
      });
    });

    it('User update request should succseed', (done) => {
      const data = {
        id: 'id_update_item',
        eventEmitter: localEvents,
        path: 'user',
        itemId: user4watch._id,
        update: { fName: 'new_fnm' },
        user: user4watch,
      };

      globalEvents.emit(emitterEvents.EVENT_EDIT_ITEM, data);
      localEvents.once(emitterEvents.EVENT_RESPONSE, (resp) => {
        try {
          assert.equal(resp.id, data.id);
          assert.isTrue(resp.message.success);
          done();
        } catch (e) {
          done(e);
        }
      });
    });

    it('User property should be updated', async () => {
      const sample = await mongoose.models.User.findById(user4watch._id);
      assert.equal(sample.fName, 'new_fnm');
    });

    it('user watcher fail to attach to unexisting user', (done) => {
      const data = {
        id: 'watch_req_id',
        eventEmitter: localEvents,
        modelId: 'unknown',
      };
      globalEvents.emit(emitterEvents.EVENT_WATCH, data);
      localEvents.once(emitterEvents.EVENT_REJECT, (resp) => {
        assert(resp instanceof UnableException);
        done();
      });
    });

    it('user watcher should be successfullty attached', (done) => {
      const data = {
        id: 'watch_req_id',
        eventEmitter: localEvents,
        modelId: user4watch.login,
        modelType: 'user',
      };
      localEvents.once(emitterEvents.EVENT_RESPONSE, (resp) => {
        assert.equal(resp.id, data.id);
        assert.hasAllKeys(resp.message.updated, ['login', 'fName', 'userType', 'modelId', 'dbId']);
        done();
      });
      globalEvents.emit(emitterEvents.EVENT_WATCH, data);
    });

    it('user watcher should respond on user changes', (done) => {
      localEvents.once(emitterEvents.EVENT_RESPONSE, (upd) => {
        assert.equal(upd.id, 'watch_req_id', 'wrong update id');
        assert.equal(upd.message.event, 'update', 'wrong event');
        assert.equal(upd.message.updated.fName, 'updated_fName');
        done();
      });
      users.updateOne({ login: user4watch.login }, { fName: 'updated_fName' }).exec();
    });

    it('user watcher should respond on user deletion', (done) => {
      localEvents.once(emitterEvents.EVENT_RESPONSE, (upd) => {
        assert.equal(upd.id, 'watch_req_id', 'wrong update id');
        assert.equal(upd.message.event, 'delete', 'wrong event');
        assert.equal(Object.entries(upd.message.updated).length, 0);
        done();
      });
      users.deleteOne({ login: user4watch.login }).exec();
    });

    it('user watcher should be remvoed on request', (done) => {
      const data = {
        id: 'unwatch_req_id',
        eventEmitter: localEvents,
        reqId: 'watch_req_id',
      };
      localEvents.once(emitterEvents.EVENT_RESPONSE, (resp) => {
        assert.equal(resp.id, data.id);
        assert(resp.message.success);
        done();
      });
      globalEvents.emit(emitterEvents.EVENT_UNWATCH, data);
    });
  });
});
