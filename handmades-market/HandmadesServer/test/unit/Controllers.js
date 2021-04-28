const { assert } = require('chai');
const mocha = require('mocha');
const net = require('net');
const events = require('events');
const mongoose = require('mongoose');
const serverEvents = require('../../controllers/ServerEvents');
const SocketAuth = require('../../controllers/SocketAuth');
const emitterEvents = require('../../utils/events');
const reqTypes = require('../../controllers/RequestTypes');
const AuthorizedUserController = require('../../controllers/AuthorizedUserController');
const { SocketRequestError, UnableException } = require('../../utils/errors');

const userId = mongoose.Types.ObjectId();

mocha.describe('Controllers', () => {
  mocha.describe('Socket authorization', () => {
    const testEvents = new events.EventEmitter();
    const localEvents = new events.EventEmitter();
    const testServer = net.createServer((socket) => new SocketAuth(socket, testEvents, localEvents));
    testServer.listen(8000, () => {});
    const wrongClients = [
      net.connect({ port: 8000 }),
      net.connect({ port: 8000 }),
      net.connect({ port: 8000 }),
    ];

    function checkResponse(data) {
      assert(data !== undefined, 'socket must return error message');
      const parsed = JSON.parse(data);
      assert(parsed !== undefined, 'socket error message shoulbe JSON-formatted');
      return parsed;
    }

    it('server should reject connections with'
        + 'wrong formatted first request [not json]', () => {
      const testRequest = '{request: let me in}';

      wrongClients[0].write(testRequest);
      wrongClients[0].on('data', (data) => {
        assert(checkResponse(data).event === serverEvents.socketClosed,
          'wrong event field');
      });
    });

    it('server should reject connections with'
        + 'wrong formatted first request [wrong data]', () => {
      const testRequest = '{"id": 0}';

      wrongClients[1].write(testRequest);
      wrongClients[1].on('data', (data) => {
        assert(checkResponse(data).event === serverEvents.socketClosed,
          'wrong event field');
      });
    });

    it('server should reject connections with'
        + 'unknown request type', () => {
      const testRequest = '{"id": 0, "type": "wrong_type", "body": {}}';

      wrongClients[2].write(testRequest);
      wrongClients[2].on('data', (data) => {
        assert(checkResponse(data).event === serverEvents.socketClosed,
          'wrong event field');
      });
    });

    it('Wrong request connections should be closed', () => {
      testServer.getConnections((err, count) => {
        assert(count === 0, 'Open connection found');
        wrongClients.forEach((c) => c.destroy());
        testServer.unref();
      });
    });

    it(`${emitterEvents.EVENT_NEW_USER} event should be thrown`
      + 'on registration request', (done) => {
      const testClient = net.connect({ port: 8000 });
      testClient.write(JSON.stringify({
        id: 0,
        type: reqTypes.NEW_USER,
        body: {},
      }));

      testEvents.once(emitterEvents.EVENT_NEW_USER, () => {
        testClient.destroy();
        done();
      });
    });

    it(`${emitterEvents.EVENT_AUTH_USER} event should be thrown`
      + 'on authorization request', (done) => {
      const testClient = net.connect({ port: 8000 });
      testClient.write(JSON.stringify({
        id: 0,
        type: reqTypes.AUTH_USER,
        body: {},
      }));

      testEvents.once(emitterEvents.EVENT_AUTH_USER, () => {
        testClient.destroy();
        done();
      });
    });
  });
  mocha.describe('Authorized requests', () => {
    const localEvents = new events.EventEmitter();
    const globalEvents = new events.EventEmitter();
    const user = { _id: userId };
    const authorizedUserController = new AuthorizedUserController(
      user, undefined, globalEvents, localEvents,
    );

    it(`${emitterEvents.EVENT_WATCH} should be thrown on valid request body`, (done) => {
      const request = {
        type: reqTypes.WATCH_MODEL,
        body: {
          id: 'string',
          model: reqTypes.MODEL_TYPES.user,
        },
      };
      globalEvents.once(emitterEvents.EVENT_WATCH, (data) => {
        assert.equal(data.modelId, request.body.id);
        done();
      });
      authorizedUserController.processRequest(request);
    });

    it('wrong request bodies should be rejected [id]', () => {
      const request = {
        type: reqTypes.WATCH_MODEL,
        body: {
          id: 1,
          model: 'user',
        },
      };

      assert.throws(
        () => authorizedUserController.processRequest(request),
        SocketRequestError,
      );
    });

    it('wrong request bodies should be rejected [model]', () => {
      const request = {
        type: reqTypes.WATCH_MODEL,
        body: {
          id: 'string',
          model: 1,
        },
      };

      assert.throws(
        () => authorizedUserController.processRequest(request),
        SocketRequestError,
      );
    });

    it('wrong request bodies should be rejected [wrong model]', () => {
      const request = {
        type: reqTypes.WATCH_MODEL,
        body: {
          id: 'string',
          model: 'wrong',
        },
      };

      assert.throws(
        () => authorizedUserController.processRequest(request),
        SocketRequestError,
      );
    });

    it(`${emitterEvents.EVENT_UNWATCH} should be thrown on unwatch request`, (done) => {
      const request = {
        type: reqTypes.UNWATCH_MODEL,
        body: {
          id: 'string',
        },
      };
      globalEvents.once(emitterEvents.EVENT_UNWATCH, (data) => {
        assert.equal(data.reqId, request.body.id);
        done();
      });
      authorizedUserController.processRequest(request);
    });

    it(`${emitterEvents.EVENT_WATCH_LIST} should be thrown on right `
      + '"wathc_list" request [1]', (done) => {
      const request = {
        type: reqTypes.WATCH_LIST,
        body: {
          model: reqTypes.MODEL_TYPES.customer,
        },
      };
      globalEvents.once(emitterEvents.EVENT_WATCH_LIST, (data) => {
        assert.equal(data.path, undefined);
        done();
      });
      authorizedUserController.processRequest(request);
    });

    it(`${emitterEvents.EVENT_WATCH_LIST} should be thrown on right `
      + '"wathc_list" request [2]', (done) => {
      const request = {
        type: reqTypes.WATCH_LIST,
        body: {
          model: reqTypes.MODEL_TYPES.customer,
          path: {
            id: 'some id',
            props: 'some.props',
          },
        },
      };
      globalEvents.once(emitterEvents.EVENT_WATCH_LIST, (data) => {
        assert.equal(data.path, request.body.path);
        done();
      });
      authorizedUserController.processRequest(request);
    });

    it('SocketRequestError should be thrown on '
      + '"wathc_list" request with wrong model field [1]', () => {
      const request = {
        type: reqTypes.WATCH_LIST,
        body: {
          model: 'wrong',
          path: {
            id: 'some id',
            props: 'some.props',
          },
        },
      };
      assert.throws(
        () => authorizedUserController.processRequest(request),
        SocketRequestError,
      );
    });

    it('SocketRequestError should be thrown on '
      + '"wathc_list" request with wrong model field [2]', () => {
      const request = {
        type: reqTypes.WATCH_LIST,
        body: {
          model: 1,
          path: {
            id: 'some id',
            props: 'some.props',
          },
        },
      };
      assert.throws(
        () => authorizedUserController.processRequest(request),
        SocketRequestError,
      );
    });

    it('SocketRequestError should be thrown on '
      + '"wathc_list" request with no model field', () => {
      const request = {
        type: reqTypes.WATCH_LIST,
        body: {
          path: {
            id: 'some id',
            props: 'some.props',
          },
        },
      };
      assert.throws(
        () => authorizedUserController.processRequest(request),
        SocketRequestError,
      );
    });

    it('SocketRequestError should be thrown on '
      + '"wathc_list" request with wrong path field [1]', () => {
      const request = {
        type: reqTypes.WATCH_LIST,
        body: {
          model: 'wrong',
          path: {
            props: 'some.props',
          },
        },
      };
      assert.throws(
        () => authorizedUserController.processRequest(request),
        SocketRequestError,
      );
    });

    it('SocketRequestError should be thrown on '
      + '"wathc_list" request with wrong path field [2]', () => {
      const request = {
        type: reqTypes.WATCH_LIST,
        body: {
          model: 'wrong',
          path: {
            id: 'some id',
          },
        },
      };
      assert.throws(
        () => authorizedUserController.processRequest(request),
        SocketRequestError,
      );
    });

    it('SocketRequestError should be thrown on '
      + '"wathc_list" request with wrong path field [3]', () => {
      const request = {
        type: reqTypes.WATCH_LIST,
        body: {
          model: 'wrong',
          path: {
            id: 1,
            props: 'some.props',
          },
        },
      };
      assert.throws(
        () => authorizedUserController.processRequest(request),
        SocketRequestError,
      );
    });

    it('SocketRequestError should be thrown on '
      + '"wathc_list" request with wrong path field [4]', () => {
      const request = {
        type: reqTypes.WATCH_LIST,
        body: {
          model: 'wrong',
          path: {
            id: 'str',
            props: 1,
          },
        },
      };
      assert.throws(
        () => authorizedUserController.processRequest(request),
        SocketRequestError,
      );
    });

    it(`${emitterEvents.EVENT_WATCH_CHAT} should be thrown on valid `
    + '"watch_chat" request', (done) => {
      const request = {
        type: reqTypes.WATCH_CHAT,
        body: {
          chatId: '12bb11111111',
        },
      };
      globalEvents.once(emitterEvents.EVENT_WATCH_CHAT, () => {
        done();
      });
      authorizedUserController.processRequest(request);
    });

    it('SocketRequestError should be thrown on valid "watch_chat" request'
    + 'with wrong body[1]', () => {
      const request = {
        type: reqTypes.WATCH_CHAT,
        body: {
          chatId: 1,
        },
      };
      assert.throws(
        () => authorizedUserController.processRequest(request),
        SocketRequestError,
      );
    });

    it('SocketRequestError should be thrown on valid "watch_chat" request'
    + 'with wrong body[2]', () => {
      const request = {
        type: reqTypes.WATCH_CHAT,
        body: {},
      };
      assert.throws(
        () => authorizedUserController.processRequest(request),
        SocketRequestError,
      );
    });

    it(`${emitterEvents.EVENT_WATCH_CHATS} should be thrown on valid `
    + '"watch_chats" request', (done) => {
      const request = {
        type: reqTypes.WATCH_CHATS,
        body: {},
      };
      globalEvents.once(emitterEvents.EVENT_WATCH_CHATS, (data) => {
        try {
          assert.equal(data.userId, userId);
          done();
        } catch (e) {
          done(e);
        }
      });
      authorizedUserController.processRequest(request);
    });

    it(`${emitterEvents.EVENT_NEW_CHAT} should be thrown on valid `
      + `"${reqTypes.NEW_CHAT}" request`, (done) => {
      const request = {
        type: reqTypes.NEW_CHAT,
        body: {
          type: reqTypes.CHAT_TYPES.private,
          users: ['id_1'],
        },
      };
      globalEvents.once(emitterEvents.EVENT_NEW_CHAT, () => {
        done();
      });
      authorizedUserController.processRequest(request);
    });

    it(`${emitterEvents.EVENT_NEW_CHAT} should be thrown on valid `
      + `"${reqTypes.NEW_CHAT}" request[1]`, (done) => {
      const request = {
        type: reqTypes.NEW_CHAT,
        body: {
          type: reqTypes.CHAT_TYPES.comments,
          users: [],
        },
      };
      globalEvents.once(emitterEvents.EVENT_NEW_CHAT, () => {
        done();
      });
      authorizedUserController.processRequest(request);
    });

    it('SocketRequestError should be thrown on invalid '
      + `"${reqTypes.NEW_CHAT}" request[1]`, () => {
      const request = {
        type: reqTypes.NEW_CHAT,
        body: {
          type: 'wrong',
          users: ['id_1'],
        },
      };
      assert.throws(
        () => authorizedUserController.processRequest(request),
        SocketRequestError,
      );
    });

    it('SocketRequestError should be thrown on invalid '
      + `"${reqTypes.NEW_CHAT}" request[2]`, () => {
      const request = {
        type: reqTypes.NEW_CHAT,
        body: {
          type: reqTypes.CHAT_TYPES.private,
          users: ['id_1', 'id_2'],
        },
      };
      assert.throws(
        () => authorizedUserController.processRequest(request),
        SocketRequestError,
      );
    });

    it('SocketRequestError should be thrown on invalid '
      + `"${reqTypes.NEW_CHAT}" request[3]`, () => {
      const request = {
        type: reqTypes.NEW_CHAT,
        body: {
          type: reqTypes.CHAT_TYPES.comments,
          users: 'str',
        },
      };
      assert.throws(
        () => authorizedUserController.processRequest(request),
        SocketRequestError,
      );
    });

    it('SocketRequestError should be thrown on invalid '
      + `"${reqTypes.NEW_CHAT}" request[4]`, () => {
      const request = {
        type: reqTypes.NEW_CHAT,
        body: {
          type: reqTypes.CHAT_TYPES.comments,
          users: ['id1'],
        },
      };
      assert.throws(
        () => authorizedUserController.processRequest(request),
        SocketRequestError,
      );
    });

    it(`${emitterEvents.EVENT_MESSAGE} should be thrown on valid `
      + `"${reqTypes.MESSAGE}" request`, (done) => {
      const request = {
        type: reqTypes.MESSAGE,
        body: {
          text: 'text',
          chatId: '12bb11111111',
          time: '2021-03-30 18:00:20:321 UTC',
        },
      };
      globalEvents.once(emitterEvents.EVENT_MESSAGE, () => {
        done();
      });
      authorizedUserController.processRequest(request);
    });

    it('SocketRequestError should be thrown on invalid '
      + `"${reqTypes.MESSAGE}" request`, () => {
      const request = {
        type: reqTypes.MESSAGE,
        body: {
          text: 'text',
          chatId: '2b',
          time: '2021-03-30 18:00:20:321 UTC',
        },
      };
      assert.throws(
        () => authorizedUserController.processRequest(request),
        UnableException,
      );
    });

    it('SocketRequestError should be thrown on invalid '
      + `"${reqTypes.MESSAGE}" request[1]`, () => {
      const request = {
        type: reqTypes.MESSAGE,
        body: {
          text: 'text',
          chatId: 1,
          time: '2021-03-30 18:00:20:321 UTC',
        },
      };
      assert.throws(
        () => authorizedUserController.processRequest(request),
        SocketRequestError,
      );
    });

    it('SocketRequestError should be thrown on invalid '
      + `"${reqTypes.MESSAGE}" request[2]`, () => {
      const request = {
        type: reqTypes.MESSAGE,
        body: {
          text: [],
          chatId: '12bb11111111',
          time: '2021-03-30 18:00:20:321 UTC',
        },
      };
      assert.throws(
        () => authorizedUserController.processRequest(request),
        SocketRequestError,
      );
    });

    it('UnableException should be thrown on invalid '
      + `"${reqTypes.MESSAGE}" request[3]`, () => {
      const request = {
        type: reqTypes.MESSAGE,
        body: {
          text: 'text',
          chatId: '12bb11111111',
          time: 'wrong date',
        },
      };
      assert.throws(
        () => authorizedUserController.processRequest(request),
        UnableException,
      );
    });

    it(`${emitterEvents.EVENT_EDIT_ITEM} should be thrown on valid ${reqTypes.EDIT_ITEM}`
      + ' request', (done) => {
      const req = {
        id: 'id_edit',
        type: reqTypes.EDIT_ITEM,
        body: {
          id: '12bb11111111',
          path: 'coll',
          update: {},
        },
      };
      globalEvents.once(emitterEvents.EVENT_EDIT_ITEM, (data) => {
        try {
          assert.equal(req.id, data.id);
          assert.exists(data.update);
          assert.exists(data.path);
          assert.isTrue(data.itemId instanceof mongoose.Types.ObjectId);
          done();
        } catch (e) {
          done(e);
        }
      });
      authorizedUserController.processRequest(req);
    });

    it('Error should be thrown on invalid '
      + `"${reqTypes.EDIT_ITEM}" request`, () => {
      const request = {
        id: 'id_edit',
        type: reqTypes.EDIT_ITEM,
        body: {
          id: 'wrong',
          path: 'coll',
          update: {},
        },
      };

      assert.throws(
        () => authorizedUserController.processRequest(request),
        Error,
      );
    });

    it('SocketRequestError should be thrown on invalid '
      + `"${reqTypes.EDIT_ITEM}" request[1]`, () => {
      const request = {
        id: 'id_edit',
        type: reqTypes.EDIT_ITEM,
        body: {
          id: '12bb11111111',
          path: 'coll',
        },
      };

      assert.throws(
        () => authorizedUserController.processRequest(request),
        SocketRequestError,
      );
    });

    it('SocketRequestError should be thrown on invalid '
      + `"${reqTypes.EDIT_ITEM}" request[1]`, () => {
      const request = {
        id: 'id_edit',
        type: reqTypes.EDIT_ITEM,
        body: {
          id: '12bb11111111',
          path: () => {},
          update: {},
        },
      };

      assert.throws(
        () => authorizedUserController.processRequest(request),
        SocketRequestError,
      );
    });
  });

  mocha.describe('User type specific requests', () => {
    const customerEvents = new events.EventEmitter();
    const vendorEvents = new events.EventEmitter();
    const globalEvents = new events.EventEmitter();
    const vendor = {
      _id: mongoose.Types.ObjectId(),
      userType: reqTypes.MODEL_TYPES.vendor,
    };
    const customer = {
      _id: mongoose.Types.ObjectId(),
      userType: reqTypes.MODEL_TYPES.customer,
    };
    const customerController = new AuthorizedUserController(
      customer, undefined, globalEvents, customerEvents,
    );
    const vendorController = new AuthorizedUserController(
      vendor, undefined, globalEvents, vendorEvents,
    );

    it('Vendor should not be able to make an order', async () => {
      const req = {
        id: 'id_vendor_order',
        eventEmitter: vendorEvents,
        type: reqTypes.MAKE_PURCHASE,
        body: {
          products: [{ code: 'id_code', quantity: 1 }],
        },
      };
      assert.throws(
        () => vendorController.processRequest(req),
        'User of type "Vendor" is not allowed to do this operation (make_purchase)',
      );
    });

    it(`Customer should not be able to do ${reqTypes.ADD_MARKET}`, async () => {
      const req = {
        id: 'id_vendor_order',
        eventEmitter: customerEvents,
        type: reqTypes.ADD_MARKET,
        body: {},
      };
      assert.throws(
        () => customerController.processRequest(req),
        `User of type "Customer" is not allowed to do this operation (${reqTypes.ADD_MARKET})`,
      );
    });

    it(`Customer should not be able to do ${reqTypes.ADD_PRODUCT}`, async () => {
      const req = {
        id: 'id_vendor_order',
        eventEmitter: customerEvents,
        type: reqTypes.ADD_PRODUCT,
        body: {},
      };
      assert.throws(
        () => customerController.processRequest(req),
        `User of type "Customer" is not allowed to do this operation (${reqTypes.ADD_PRODUCT})`,
      );
    });

    it(`${emitterEvents.EVENT_PURCHASE} should be thrown on ${reqTypes.MAKE_PURCHASE}`
      + 'request from customer', (done) => {
      const req = {
        id: 'id_vendor_order',
        eventEmitter: customerEvents,
        type: reqTypes.MAKE_PURCHASE,
        body: {
          products: [{ code: 'id_code', quantity: 1 }],
        },
      };
      globalEvents.once(emitterEvents.EVENT_PURCHASE, (data) => {
        try {
          assert.equal(req.id, data.id);
          done();
        } catch (e) {
          done(e);
        }
      });
      customerController.processRequest(req);
    });

    it(`${emitterEvents.EVENT_PURCHASE} should be thrown on ${reqTypes.MAKE_PURCHASE}`
      + 'request from customer', (done) => {
      const req = {
        id: 'id_customer_purchase1',
        eventEmitter: customerEvents,
        type: reqTypes.MAKE_PURCHASE,
        body: {
          products: [{ code: 'id_code', quantity: 1 }, { code: 'id_code1', quantity: 3 }],
          comment: 'string',
        },
      };
      globalEvents.once(emitterEvents.EVENT_PURCHASE, (data) => {
        try {
          assert.equal(req.id, data.id);
          assert.exists(data.userId);
          assert.exists(data.products);
          assert.exists(data.comment);
          done();
        } catch (e) {
          done(e);
        }
      });
      customerController.processRequest(req);
    });

    it(`SocketRequestError should be thrown on ${reqTypes.MAKE_PURCHASE}`
      + 'request from with wrong body', () => {
      const req = {
        id: 'id_customer_purchase2',
        eventEmitter: customerEvents,
        type: reqTypes.MAKE_PURCHASE,
        body: {
          products: [{ code: 'id_code', quantity: '' }, { code: 'id_code1', quantity: 3 }],
          comment: 'string',
        },
      };
      assert.throws(
        () => customerController.processRequest(req),
        '"body.products" has wrong item (quantity must be number)',
      );
    });

    it(`SocketRequestError should be thrown on ${reqTypes.MAKE_PURCHASE}`
      + 'request from with wrong body[1]', () => {
      const req = {
        id: 'id_customer_purchase3',
        eventEmitter: customerEvents,
        type: reqTypes.MAKE_PURCHASE,
        body: {
          products: [{ quantity: 1 }, { code: 'id_code1', quantity: 3 }],
          comment: 'string',
        },
      };
      assert.throws(
        () => customerController.processRequest(req),
        'no "body.products" has wrong item (no "code field")',
      );
    });

    it(`SocketRequestError should be thrown on ${reqTypes.MAKE_PURCHASE}`
      + 'request from with wrong body[2]', () => {
      const req = {
        id: 'id_customer_purchase4',
        eventEmitter: customerEvents,
        type: reqTypes.MAKE_PURCHASE,
        body: {
          products: [{ code: 'id_code', quantity: 1 }, { code: 'id_code1', quantity: 3 }],
          comment: 1,
        },
      };
      assert.throws(
        () => customerController.processRequest(req),
      );
    });

    it(`SocketRequestError should be thrown on ${reqTypes.MAKE_PURCHASE}`
      + 'request from with wrong body[3]', () => {
      const req = {
        id: 'id_customer_purchase5',
        eventEmitter: customerEvents,
        type: reqTypes.MAKE_PURCHASE,
        body: {
          products: { code: 'id_code', quantity: 1 },
          comment: 'str',
        },
      };
      assert.throws(
        () => customerController.processRequest(req),
      );
    });

    it(`${emitterEvents.EVENT_ADD_MARKET} should be thrown on valid request from`
    + 'vendor', (done) => {
      const req = {
        id: 'id_vendor_add_market',
        eventEmitter: vendorEvents,
        type: reqTypes.ADD_MARKET,
        body: {
          name: 'name',
          description: 'str',
        },
      };
      globalEvents.once(emitterEvents.EVENT_ADD_MARKET, (data) => {
        try {
          assert.equal(req.id, data.id);
          assert.exists(data.userId);
          assert.exists(data.name);
          assert.exists(data.description);
          assert.equal(data.imageUrl, undefined);
          done();
        } catch (e) {
          done(e);
        }
      });
      vendorController.processRequest(req);
    });

    it(`${emitterEvents.EVENT_ADD_MARKET} should be thrown on valid request from`
    + ' vendor [1]', (done) => {
      const req = {
        id: 'id_vendor_add_market',
        eventEmitter: vendorEvents,
        type: reqTypes.ADD_MARKET,
        body: {
          name: 'name',
          description: 'str',
          imageUrl: 'uuuu',
        },
      };
      globalEvents.once(emitterEvents.EVENT_ADD_MARKET, (data) => {
        try {
          assert.equal(req.id, data.id);
          assert.exists(data.imageUrl);
          done();
        } catch (e) {
          done(e);
        }
      });
      vendorController.processRequest(req);
    });

    it(`SocketRequestError should be thrown on ${reqTypes.ADD_MARKET} `
      + 'request from with wrong body', () => {
      const req = {
        id: 'id_vendor_add_market1',
        eventEmitter: vendorEvents,
        type: reqTypes.ADD_MARKET,
        body: {
          name: 1,
          description: 'str',
        },
      };
      assert.throws(
        () => vendorController.processRequest(req),
      );
    });

    it(`SocketRequestError should be thrown on ${reqTypes.ADD_MARKET} `
      + 'request from with wrong body[1]', () => {
      const req = {
        id: 'id_vendor_add_market1',
        eventEmitter: vendorEvents,
        type: reqTypes.ADD_MARKET,
        body: {
          name: '',
          description: 1,
        },
      };
      assert.throws(
        () => vendorController.processRequest(req),
      );
    });

    it(`SocketRequestError should be thrown on ${reqTypes.ADD_MARKET} `
      + 'request from with wrong body[2]', () => {
      const req = {
        id: 'id_vendor_add_market1',
        eventEmitter: vendorEvents,
        type: reqTypes.ADD_MARKET,
        body: {
          name: '',
          description: 1,
          imageUrl: 10,
        },
      };
      assert.throws(
        () => vendorController.processRequest(req),
      );
    });

    it(`${emitterEvents.EVENT_ADD_PRODUCT} should be thrown on valid request from`
    + 'vendor', (done) => {
      const req = {
        id: 'id_vendor_add_product',
        eventEmitter: vendorEvents,
        type: reqTypes.ADD_PRODUCT,
        body: {
          name: 'name',
          description: 'str',
          marketId: '12bb11111111',
          quantity: 1,
        },
      };
      globalEvents.once(emitterEvents.EVENT_ADD_PRODUCT, (data) => {
        try {
          assert.equal(req.id, data.id);
          assert.exists(data.name);
          assert.exists(data.description);
          assert.isTrue(data.marketId instanceof mongoose.Types.ObjectId);
          assert.exists(data.quantity);
          done();
        } catch (e) {
          done(e);
        }
      });
      vendorController.processRequest(req);
    });

    it(`SocketRequestError should be thrown on ${reqTypes.ADD_PRODUCT}`
      + 'request from with wrong body', () => {
      const req = {
        id: 'id_vendor_add_product1',
        eventEmitter: vendorEvents,
        type: reqTypes.ADD_PRODUCT,
        body: {
          name: 'name',
          description: 'str',
          marketId: 'not_12',
          quantity: 1,
        },
      };
      assert.throws(
        () => vendorController.processRequest(req),
      );
    });
    it(`SocketRequestError should be thrown on ${reqTypes.ADD_PRODUCT} `
      + 'request from with wrong body[1]', () => {
      const req = {
        id: 'id_vendor_add_product2',
        eventEmitter: vendorEvents,
        type: reqTypes.ADD_PRODUCT,
        body: {
          name: 'name',
          description: 'str',
          marketId: '12bb11111111',
          quantity: '',
        },
      };
      assert.throws(
        () => vendorController.processRequest(req),
      );
    });
    it(`SocketRequestError should be thrown on ${reqTypes.ADD_PRODUCT} `
      + 'request from with wrong body[2]', () => {
      const req = {
        id: 'id_vendor_add_product3',
        eventEmitter: vendorEvents,
        type: reqTypes.ADD_PRODUCT,
        body: {
          name: 'name',
          description: 1,
          marketId: '12bb11111111',
          quantity: 1,
        },
      };
      assert.throws(
        () => vendorController.processRequest(req),
      );
    });

    it(`SocketRequestError should be thrown on ${reqTypes.ADD_PRODUCT} `
      + 'request from with wrong body[3]', () => {
      const req = {
        id: 'id_vendor_add_product4',
        eventEmitter: vendorEvents,
        type: reqTypes.ADD_PRODUCT,
        body: {
          name: 1,
          description: 'str',
          marketId: '12bb11111111',
          quantity: 1,
        },
      };
      assert.throws(
        () => vendorController.processRequest(req),
      );
    });

    it(`SocketRequestError should be thrown on ${reqTypes.ADD_PRODUCT} `
      + 'request from with wrong body', () => {
      const req = {
        id: 'id_vendor_edit1',
        eventEmitter: vendorEvents,
        type: reqTypes.ADD_PRODUCT,
        body: {
          name: 1,
          description: 'str',
          marketId: '12bb11111111',
          quantity: 1,
        },
      };
      assert.throws(
        () => vendorController.processRequest(req),
      );
    });

    it(`SocketRequestError should be thrown on ${reqTypes.ADD_PRODUCT} `
      + 'request from with wrong body[1]', () => {
      const req = {
        id: 'id_vendor_edit2',
        eventEmitter: vendorEvents,
        type: reqTypes.ADD_PRODUCT,
        body: {
          name: 'str',
          description: 1,
          marketId: '12bb11111111',
          quantity: 1,
        },
      };
      assert.throws(
        () => vendorController.processRequest(req),
      );
    });

    it(`SocketRequestError should be thrown on ${reqTypes.ADD_PRODUCT} `
      + 'request from with wrong body[2]', () => {
      const req = {
        id: 'id_vendor_edit3',
        eventEmitter: vendorEvents,
        type: reqTypes.ADD_PRODUCT,
        body: {
          name: 'str',
          description: 'str',
          marketId: 'wrong',
          quantity: 1,
        },
      };
      assert.throws(
        () => vendorController.processRequest(req),
      );
    });

    it(`SocketRequestError should be thrown on ${reqTypes.ADD_PRODUCT} `
      + 'request from with wrong body[3]', () => {
      const req = {
        id: 'id_vendor_edit4',
        eventEmitter: vendorEvents,
        type: reqTypes.ADD_PRODUCT,
        body: {
          name: 'str',
          description: 'str',
          marketId: '12bb11111111',
          quantity: 's',
        },
      };
      assert.throws(
        () => vendorController.processRequest(req),
      );
    });
  });
});
