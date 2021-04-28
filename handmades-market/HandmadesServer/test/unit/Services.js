const { assert } = require('chai');
const mocha = require('mocha');
const events = require('events');
const path = require('path');
const emitterEvents = require('../../utils/events');
const newUserService = require('../../services/NewUserService');
const UserModelService = require('../../services/UserModelService');
require('dotenv').config({ path: path.resolve(process.cwd(), 'test/unit/test.env') });

let userModelService;
let mongoose;

mocha.before(async () => {
  mongoose = await require('../../loaders/MongooseLoader');
  userModelService = new UserModelService(mongoose);
});

mocha.after(() => {
  mongoose.connection.close();
});

mocha.describe('Services', () => {
  mocha.describe('New user', () => {
    it(`${emitterEvents.EVENT_REJECT} should be thrown when no `
      + 'password field in body', (done) => {
      const eventsEmitter = new events.EventEmitter();
      const localEvents = new events.EventEmitter();
      const id = 100;
      const data = {
        id,
        body: {},
        eventEmitter: localEvents,
      };

      newUserService(eventsEmitter, userModelService);
      eventsEmitter.emit(emitterEvents.EVENT_NEW_USER, data);
      localEvents.once(emitterEvents.EVENT_REJECT, (err) => {
        assert(err.id === id, 'response should contain id of the request');
        done();
      });
    });

    it(`${emitterEvents.EVENT_REJECT} should be thrown when not string `
      + 'password', (done) => {
      const eventsEmitter = new events.EventEmitter();
      const localEvents = new events.EventEmitter();
      const data = {
        id: 1001,
        eventEmitter: localEvents,
        body: {
          password: 1,
        },
      };

      newUserService(eventsEmitter, userModelService);
      eventsEmitter.emit(emitterEvents.EVENT_NEW_USER, data);
      localEvents.once(emitterEvents.EVENT_REJECT, (err) => {
        assert(err.message === 'password must be a string');
        done();
      });
    });

    it(`${emitterEvents.EVENT_REJECT} should be thrown when too short `
      + 'password', (done) => {
      const eventsEmitter = new events.EventEmitter();
      const localEvents = new events.EventEmitter();
      const data = {
        id: 1001,
        eventEmitter: localEvents,
        body: {
          password: '123',
        },
      };

      newUserService(eventsEmitter, userModelService);
      eventsEmitter.emit(emitterEvents.EVENT_NEW_USER, data);
      localEvents.once(emitterEvents.EVENT_REJECT, (err) => {
        assert(err.message === 'password must be at least 5 characters');
        done();
      });
    });

    it('NewUserService should be activated on good data', (done) => {
      const eventsEmitter = new events.EventEmitter();
      const localEvents = new events.EventEmitter();
      const dummyUserService = {
        trySave: () => done(),
      };
      const data = {
        id: 1001,
        eventEmitter: localEvents,
        body: {
          password: '123456',
        },
      };

      newUserService(eventsEmitter, dummyUserService);
      eventsEmitter.emit(emitterEvents.EVENT_NEW_USER, data);
    });
  });
});
