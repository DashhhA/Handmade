const { assert } = require('chai');
const mocha = require('mocha');
const events = require('events');
const path = require('path');
const emitterEvents = require('../../utils/events');
const UserModelService = require('../../services/UserModelService');
const { UnableException } = require('../../utils/errors');
const authService = require('../../services/UserAuthService');
const userModel = require('../../models/user');
// assuming, test is runned from project root
require('dotenv').config({ path: path.resolve(process.cwd(), 'test/unit/test.env') });

let mongoose;
let globalEvents;
let localEvents;
let data0;
let userModelService;

mocha.before(async () => {
  mongoose = await require('../../loaders/MongooseLoader');
});

mocha.after((done) => {
  mongoose.connection.close();
  done();
});

mocha.describe('AuthService', () => {
  it('launch auth service', async () => {
    userModelService = new UserModelService(mongoose);
    globalEvents = new events.EventEmitter();
    localEvents = new events.EventEmitter();
    data0 = {
      id: 'id10',
      eventEmitter: localEvents,
    };

    // launch auth service for globalEvents
    authService(globalEvents, userModelService);
  });

  it('drop db', async () => {
    // drop db
    const users = userModel(mongoose);
    await users.deleteMany();
  });

  it('populate db with users', async () => {
    const user1 = {
      fName: 'name1',
      login: 'login1',
      password: {
        data: 'c8b2505b76926abdc733523caa9f439142f66aa7293a7baaac0aed41a191eef6',
        salt: 'salt',
      },
      userType: 'customer',
      modelId: mongoose.Types.ObjectId(),
    };
    await userModelService.trySave(user1);
  });

  mocha.describe('Auth request should contain all valid fields', () => {
    it('Error on login field absent', (done) => {
      const body = {
        body: {},
      };
      const data = Object.assign(data0, body);
      globalEvents.emit(emitterEvents.EVENT_AUTH_USER, data);
      localEvents.once(emitterEvents.EVENT_REJECT, (response) => {
        if (response.message === 'no "body.login" field') {
          done();
        } else {
          done('wrong error');
        }
      });
    });

    it('Error on password field absent', (done) => {
      const body = {
        body: {
          login: '--',
        },
      };
      const data = Object.assign(data0, body);
      globalEvents.emit(emitterEvents.EVENT_AUTH_USER, data);
      localEvents.once(emitterEvents.EVENT_REJECT, (response) => {
        if (response.message === 'no "body.password" field') {
          done();
        } else {
          done('wrong error');
        }
      });
    });

    it('Error on login field not string', (done) => {
      const body = {
        body: {
          login: 10,
        },
      };
      const data = Object.assign(data0, body);
      globalEvents.emit(emitterEvents.EVENT_AUTH_USER, data);
      localEvents.once(emitterEvents.EVENT_REJECT, (response) => {
        if (response.message === 'login must be a string') {
          done();
        } else {
          done('wrong error');
        }
      });
    });

    it('Error on password field not string', (done) => {
      const body = {
        body: {
          login: '--',
          password: 10,
        },
      };
      const data = Object.assign(data0, body);
      globalEvents.emit(emitterEvents.EVENT_AUTH_USER, data);
      localEvents.once(emitterEvents.EVENT_REJECT, (response) => {
        if (response.message === 'password must be a string') {
          done();
        } else {
          done('wrong error');
        }
      });
    });
  });

  it('Authorization with correct data should pass', (done) => {
    const body = {
      body: {
        login: 'login1',
        password: 'pass',
      },
    };
    const data = Object.assign(data0, body);
    globalEvents.emit(emitterEvents.EVENT_AUTH_USER, data);
    localEvents.once(emitterEvents.EVENT_RESPONSE, (response) => {
      if (response.message.success) {
        if (response.message.type === 'customer') {
          done();
        } else {
          done('Wrong user type');
        }
      } else {
        done('Authorization must be successfull');
      }
    });
  });

  it('Authorization with incorrect password should fail', (done) => {
    const body = {
      body: {
        login: 'login1',
        password: 'pass1',
      },
    };
    const data = Object.assign(data0, body);
    globalEvents.emit(emitterEvents.EVENT_AUTH_USER, data);
    localEvents.once(emitterEvents.EVENT_RESPONSE, (response) => {
      if (response.message.success === false) {
        if (response.message.type === undefined) {
          done();
        } else {
          done('User type should be undefined');
        }
      } else {
        done('Authorization must fail');
      }
    });
  });

  it('Authorization with incorrect login should return error', (done) => {
    const body = {
      body: {
        login: 'login',
        password: 'pass',
      },
    };
    const data = Object.assign(data0, body);
    globalEvents.emit(emitterEvents.EVENT_AUTH_USER, data);
    localEvents.once(emitterEvents.EVENT_REJECT, (response) => {
      if (response instanceof UnableException
        && response.message === 'No such user'
        && response.id === data0.id
      ) {
        done();
      } else {
        done('Wrong response');
      }
    });
  });
});
