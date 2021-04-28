const { assert } = require('chai');
const mocha = require('mocha');
const events = require('events');
const path = require('path');
const authorizedUserService = require('../../services/AuthorizedUserService');
const AuthorizedUserController = require('../../controllers/AuthorizedUserController');
const UserModelService = require('../../services/UserModelService');
const { UnableException } = require('../../utils/errors');
const reqTypes = require('../../controllers/RequestTypes');
const emitterEvents = require('../../utils/events');
// assuming, test is runned from project root
require('dotenv').config({ path: path.resolve(process.cwd(), 'test/unit/test.env') });

let mongoose;
let userModelService;
let globalEvents;
let user1;
let user2;
let user3;
let localEvents1;
let localEvents2;
let localEvents3;
let controller1;
let controller2;
let controller3;

mocha.before(async () => {
  mongoose = await require('../../loaders/MongooseLoader');
  userModelService = new UserModelService(mongoose);
  globalEvents = new events.EventEmitter();
  localEvents1 = new events.EventEmitter();
  localEvents2 = new events.EventEmitter();
  localEvents3 = new events.EventEmitter();
  // launch auth services for globalEvents
  authorizedUserService(globalEvents, userModelService);
});

mocha.after(async () => {
  await mongoose.connection.close();
});

mocha.describe('Messaging', () => {
  it('Populate DB with users', async () => {
    // drop collections
    await mongoose.models.User.deleteMany();
    await mongoose.models.Chat.deleteMany();
    await mongoose.models.Message.deleteMany();

    const user1descr = {
      fName: 'name1',
      login: 'login1',
      password: {
        data: 'c8b2505b76926abdc733523caa9f439142f66aa7293a7baaac0aed41a191eef6',
        salt: 'salt',
      },
      userType: 'customer',
      modelId: mongoose.Types.ObjectId(),
    };

    const user2descr = {
      fName: 'name2',
      login: 'name2',
      password: {
        data: 'c8b2505b76926abdc733523caa9f439142f66aa7293a7baaac0aed41a191eef6',
        salt: 'salt',
      },
      userType: 'customer',
      modelId: mongoose.Types.ObjectId(),
    };

    const user3descr = {
      fName: 'name3',
      login: 'name3',
      password: {
        data: 'c8b2505b76926abdc733523caa9f439142f66aa7293a7baaac0aed41a191eef6',
        salt: 'salt',
      },
      userType: 'vendor',
      modelId: mongoose.Types.ObjectId(),
    };

    user1 = await userModelService.trySave(user1descr);
    user2 = await userModelService.trySave(user2descr);
    user3 = await userModelService.trySave(user3descr);

    controller1 = new AuthorizedUserController(user1, undefined, globalEvents, localEvents1);
    controller2 = new AuthorizedUserController(user2, undefined, globalEvents, localEvents2);
    controller3 = new AuthorizedUserController(user3, undefined, globalEvents, localEvents3);
  });

  it('User1 should apply chat wathcer', (done) => {
    const req = {
      id: 'id_user1_watch_chat1',
      eventEmitter: localEvents1,
      type: reqTypes.WATCH_CHATS,
      body: {},
    };

    localEvents1.once(emitterEvents.EVENT_RESPONSE, async (resp) => {
      try {
        assert.equal(resp.id, 'id_user1_watch_chat1');
        assert.equal(resp.message.event, 'refresh');
        done();
      } catch (e) {
        done(e);
      }
    });

    controller1.processRequest(req);
  });

  it('User1 creates private chat with User2', (done) => {
    const req = {
      id: 'id_user1_create_chat12',
      eventEmitter: localEvents1,
      type: reqTypes.NEW_CHAT,
      body: {
        type: reqTypes.CHAT_TYPES.private,
        users: [user2.login],
      },
    };

    localEvents1.once(emitterEvents.EVENT_RESPONSE, async (resp) => {
      try {
        assert.equal(resp.id, 'id_user1_create_chat12');
        assert.notEqual(resp.message.data.chatId, undefined);
        const chat = await mongoose.models.Chat.findById(resp.message.data.chatId);
        assert.notEqual(chat, null);
        done();
      } catch (e) {
        done(e);
      }
    });

    controller1.processRequest(req);
  });

  it('User1 should be notified about chat creation', (done) => {
    localEvents1.once(emitterEvents.EVENT_RESPONSE, async (resp) => {
      try {
        assert.equal(resp.id, 'id_user1_watch_chat1');
        assert.equal(resp.message.event, 'insert');
        done();
      } catch (e) {
        done(e);
      }
    });
  });

  it('second chat between User1 and User2 should not be created', (done) => {
    const req = {
      id: 'id_user2_create_chat12_err',
      eventEmitter: localEvents2,
      type: reqTypes.NEW_CHAT,
      body: {
        type: reqTypes.CHAT_TYPES.private,
        users: [user1.login],
      },
    };

    localEvents2.once(emitterEvents.EVENT_REJECT, (resp) => {
      try {
        assert.equal(resp.id, 'id_user2_create_chat12_err');
        done();
      } catch (e) {
        done(e);
      }
    });

    controller2.processRequest(req);
  });

  let user1user2chat;
  it('User2 should apply chat watcer', (done) => {
    const req = {
      id: 'id_user2_watch_chat',
      eventEmitter: localEvents2,
      type: reqTypes.WATCH_CHATS,
      body: {},
    };

    localEvents2.once(emitterEvents.EVENT_RESPONSE, async (resp) => {
      try {
        assert.equal(resp.id, 'id_user2_watch_chat');
        assert.equal(resp.message.event, 'refresh');
        assert.equal(resp.message.updated.length, 1);
        [user1user2chat] = resp.message.updated;
        done();
      } catch (e) {
        done(e);
      }
    });

    controller2.processRequest(req);
  });

  it('User3 should create private chat with User2', (done) => {
    const req = {
      id: 'id_user3_create_chat123',
      eventEmitter: localEvents3,
      type: reqTypes.NEW_CHAT,
      body: {
        type: reqTypes.CHAT_TYPES.private,
        users: [user2.login],
      },
    };

    localEvents3.once(emitterEvents.EVENT_RESPONSE, (resp) => {
      try {
        assert.equal(resp.id, 'id_user3_create_chat123');
        done();
      } catch (e) {
        done(e);
      }
    });

    controller3.processRequest(req);
  });
  let user2user3chat;
  it('User2 should be notified about chat creation', (done) => {
    localEvents2.once(emitterEvents.EVENT_RESPONSE, async (resp) => {
      try {
        assert.equal(resp.id, 'id_user2_watch_chat');
        assert.equal(resp.message.event, 'insert');
        user2user3chat = resp.message.updated;
        done();
      } catch (e) {
        done(e);
      }
    });
  });
  it('User1 should not be notified about private chat creation '
  + 'between User2 and User3', (done) => {
    localEvents1.once(emitterEvents.EVENT_RESPONSE, (resp) => {
      if (resp.id === 'id_user1_watch_chat1') {
        done('update was thrown');
      }
    });
    localEvents1.once(emitterEvents.EVENT_REJECT, (resp) => {
      if (resp.id === 'id_user1_watch_chat1') {
        done('error was thrown');
      }
    });
    setTimeout(done, 200);
  });
  it('User1 must fail to apply to not existing chat', (done) => {
    const req = {
      id: 'id_user1_watch_msg_err',
      eventEmitter: localEvents1,
      type: reqTypes.WATCH_CHAT,
      body: {
        chatId: '12bb11111111',
      },
    };

    localEvents1.once(emitterEvents.EVENT_REJECT, (err) => {
      try {
        assert.isTrue(err instanceof UnableException);
        assert.equal(err.message, 'no soch chat');
        done();
      } catch (e) {
        done(e);
      }
    });

    controller1.processRequest(req);
  });
  it('User1 must fail to apply to private chat without him', (done) => {
    const req = {
      id: 'id_user1_watch_msg_err1',
      eventEmitter: localEvents1,
      type: reqTypes.WATCH_CHAT,
      body: {
        chatId: user2user3chat,
      },
    };

    localEvents1.once(emitterEvents.EVENT_REJECT, (err) => {
      try {
        assert.isTrue(err instanceof UnableException);
        assert.equal(err.message, 'User cannot access this chat');
        done();
      } catch (e) {
        done(e);
      }
    });

    controller1.processRequest(req);
  });
  it('User3 must apply listener to private chat with him', (done) => {
    const req = {
      id: 'id_user3_watch_msg',
      eventEmitter: localEvents3,
      type: reqTypes.WATCH_CHAT,
      body: {
        chatId: user2user3chat.toString(),
      },
    };

    localEvents3.once(emitterEvents.EVENT_RESPONSE, (resp) => {
      try {
        assert.equal(resp.event, 'update');
        assert.equal(resp.message.event, 'refresh');
        assert.equal(resp.message.updated.length, 0);
        done();
      } catch (e) {
        done(e);
      }
    });

    controller3.processRequest(req);
  });
  it('User3 must be notified when deleted from chat', (done) => {
    localEvents3.once(emitterEvents.EVENT_REJECT, (err) => {
      try {
        assert.equal(err.message, 'User does not more fit this chat requirements');
        done();
      } catch (e) {
        done(e);
      }
    });
    mongoose.models.Chat.updateOne(
      { _id: user2user3chat },
      { $pull: { users: user3._id } },
      { multi: true },
    ).exec();
  });

  it('User2 must apply listener to private chat with him', (done) => {
    const req = {
      id: 'id_user2_watch_msg23',
      eventEmitter: localEvents2,
      type: reqTypes.WATCH_CHAT,
      body: {
        chatId: user2user3chat.toString(),
      },
    };

    localEvents2.once(emitterEvents.EVENT_RESPONSE, (resp) => {
      try {
        assert.equal(resp.event, 'update');
        assert.equal(resp.message.event, 'refresh');
        assert.equal(resp.message.updated.length, 0);
        done();
      } catch (e) {
        done(e);
      }
    });

    controller2.processRequest(req);
  });

  it('User2 must be notified when chat deleted', (done) => {
    localEvents2.once(emitterEvents.EVENT_REJECT, (err) => {
      try {
        assert.equal(err.message, 'chat removed');
        done();
      } catch (e) {
        done(e);
      }
    });
    mongoose.models.Chat.deleteOne({
      _id: user2user3chat,
    }).exec();
  });

  it('User1 must apply listener to chat with him', (done) => {
    const req = {
      id: 'id_user1_watch_msg',
      eventEmitter: localEvents1,
      type: reqTypes.WATCH_CHAT,
      body: {
        chatId: user1user2chat.toString(),
      },
    };

    localEvents1.once(emitterEvents.EVENT_RESPONSE, (resp) => {
      try {
        assert.equal(resp.event, 'update');
        assert.equal(resp.message.event, 'refresh');
        assert.equal(resp.message.updated.length, 0);
        done();
      } catch (e) {
        done(e);
      }
    });

    controller1.processRequest(req);
  });

  it('User2 must apply message to chat', (done) => {
    const req = {
      id: 'id_user2_new_msg',
      eventEmitter: localEvents2,
      type: reqTypes.MESSAGE,
      body: {
        chatId: user1user2chat.toString(),
        text: 'Hello User2 -> User1',
        time: (new Date()).toString(),
      },
    };

    localEvents2.once(emitterEvents.EVENT_RESPONSE, (resp) => {
      try {
        assert.equal(resp.id, 'id_user2_new_msg');
        assert.isTrue(resp.message.success);
        done();
      } catch (e) {
        done(e);
      }
    });

    controller2.processRequest(req);
  });

  it('User1 should be notified about new message', (done) => {
    localEvents1.once(emitterEvents.EVENT_RESPONSE, (resp) => {
      try {
        assert.equal(resp.id, 'id_user1_watch_msg');
        assert.equal(resp.message.event, 'insert');
        done();
      } catch (e) {
        done(e);
      }
    });
  });

  it('User1 must apply message to chat', (done) => {
    const req = {
      id: 'id_user1_new_msg',
      eventEmitter: localEvents1,
      type: reqTypes.MESSAGE,
      body: {
        chatId: user1user2chat.toString(),
        text: 'Hello User1 -> User2',
        time: (new Date()).toString(),
      },
    };

    localEvents1.once(emitterEvents.EVENT_RESPONSE, (resp) => {
      try {
        assert.equal(resp.id, 'id_user1_new_msg');
        assert.isTrue(resp.message.success);
        done();
      } catch (e) {
        done(e);
      }
    });

    controller1.processRequest(req);
  });

  it('User1 should be notified about new message', (done) => {
    localEvents1.once(emitterEvents.EVENT_RESPONSE, (resp) => {
      try {
        assert.equal(resp.id, 'id_user1_watch_msg');
        assert.equal(resp.message.event, 'insert');
        done();
      } catch (e) {
        done(e);
      }
    });
  });

  it('User2 must apply listener to chat with him', (done) => {
    const req = {
      id: 'id_user2_watch_msg',
      eventEmitter: localEvents2,
      type: reqTypes.WATCH_CHAT,
      body: {
        chatId: user1user2chat.toString(),
      },
    };

    localEvents2.once(emitterEvents.EVENT_RESPONSE, (resp) => {
      try {
        assert.equal(resp.id, 'id_user2_watch_msg');
        assert.equal(resp.event, 'update');
        assert.equal(resp.message.event, 'refresh');
        assert.equal(resp.message.updated.length, 2);
        done();
      } catch (e) {
        done(e);
      }
    });

    controller2.processRequest(req);
  });

  it('User1 messages lisyener must be removed', (done) => {
    const req = {
      id: 'id_user1_unwatch',
      eventEmitter: localEvents1,
      type: reqTypes.UNWATCH_MODEL,
      body: {
        id: 'id_user1_watch_msg',
      },
    };

    localEvents1.once(emitterEvents.EVENT_RESPONSE, () => done());

    controller1.processRequest(req);
  });

  it('User1 chats lisyener must be removed', (done) => {
    const req = {
      id: 'id_user1_unwatch',
      eventEmitter: localEvents1,
      type: reqTypes.UNWATCH_MODEL,
      body: {
        id: 'id_user1_watch_chat1',
      },
    };

    localEvents1.once(emitterEvents.EVENT_RESPONSE, () => done());

    controller1.processRequest(req);
  });

  it('User2 messages lisyener must be removed', (done) => {
    const req = {
      id: 'id_user1_unwatch',
      eventEmitter: localEvents2,
      type: reqTypes.UNWATCH_MODEL,
      body: {
        id: 'id_user2_watch_msg',
      },
    };

    localEvents2.once(emitterEvents.EVENT_RESPONSE, () => done());

    controller2.processRequest(req);
  });

  it('User2 chats lisyener must be removed', (done) => {
    const req = {
      id: 'id_user1_unwatch',
      eventEmitter: localEvents2,
      type: reqTypes.UNWATCH_MODEL,
      body: {
        id: 'id_user2_watch_chat',
      },
    };

    localEvents2.once(emitterEvents.EVENT_RESPONSE, () => done());

    controller2.processRequest(req);
  });
});
