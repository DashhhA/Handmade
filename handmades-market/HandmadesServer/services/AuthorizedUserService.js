const events = require('../utils/events');
const {
  UnknownException, UnableException, OrderException, MarketException, ProductException
} = require('../utils/errors');
const { PrivateChatError } = require('./ChatService');
const response = require('../controllers/ResponseGenerator');
const modelTypes = require('../controllers/RequestTypes').MODEL_TYPES;
const getModelService = require('./ModelService');
const utils = require('../utils/util');

/**
 * @description Selects function to generate response on update by model type
 * @param modelType Model type
 * @return { (id, updated, event) -> object } Function, generating response
 */
function selectRespFcn(modelType) {
  if (modelType === modelTypes.user) {
    return response.onSingleUpdated;
  }
  return response.onModelUpdated;
}

module.exports = (() => {
  const changeStreams = new Map();
  return (globalEvents, userModelService) => {
    const modelService = getModelService(userModelService.conn);
    /**
     * @description Puts a watcher on a model depending on modelType
     * @param {string} modelId Id of the model in DB
     * @param {string} modelType Type of the model, one of
     * RequestTypes.MODEL_TYPES
     * @return { changeStream | null, model | null } change stream for events
     * on model or null if no soch model, and tehe model to send current
     */
    async function watchModelByType(modelId, modelType) {
      switch (modelType) {
        case modelTypes.user: {
          const ans = await userModelService.watch(modelId);
          return { changeStream: ans.changeStream, model: ans.user };
        }
        case modelTypes.customer: {
          const ans = await modelService.watchCustomer(modelId);
          return { changeStream: ans.changeStream, model: ans.customer };
        }
        case modelTypes.vendor: {
          const ans = await modelService.watchVendor(modelId);
          return { changeStream: ans.changeStream, model: ans.vendor };
        }
        case modelTypes.order: {
          const ans = await modelService.watchOrder(modelId);
          return { changeStream: ans.changeStream, model: ans.order };
        }
        case modelTypes.market: {
          const ans = await modelService.watchMarket(modelId);
          return { changeStream: ans.changeStream, model: ans.market };
        }
        case modelTypes.product: {
          const ans = await modelService.watchProduct(modelId);
          return { changeStream: ans.changeStream, model: ans.product };
        }
        default:
          // TODO warning to console. Unreachable, checked in
          // AuthorizedUserController.js
          return { changeStream: null, model: null };
      }
    }

    /**
     * @description Puts a watcher on list
     * @param {string} modelType Type of the model, one of
     * @param {object | undefined} path Path to document in the collection,
     * containing list
     * RequestTypes.MODEL_TYPES
     * @return {changeStream | null, list| null}
     */
    async function watchList(modelType, path) {
      if (modelType === modelTypes.user) {
        const ans = await userModelService.watchList(path);
        return ans;
      }

      const ans = await modelService.watchList(modelType, path);
      return ans;
    }

    // user delete event
    globalEvents.on(events.EVENT_REMOVE_USER, (data) => {
      const { id, eventEmitter: localEvents, login } = data;
      modelService.removeUser(login)
        .then(() => {
          localEvents.emit(events.EVENT_LOGOUT, id);
        })
        .catch((err) => {
          localEvents.emit(events.EVENT_REJECT, UnknownException(err, id));
        });
    });

    // provide currents user
    // user delete event
    globalEvents.on(events.EVENT_CURRENT, async (data) => {
      const { id, eventEmitter: localEvents, userId } = data;
      try {
        const user = await userModelService.getById(userId);
        if (user === null) {
          const err = new UnableException('User deleted', id, {});
          localEvents.emit(events.EVENT_REJECT, err);
        } else {
          const resp = response.userResponse(id, user);
          localEvents.emit(events.EVENT_RESPONSE, resp);
        }
      } catch (e) {
        localEvents.emit(events.EVENT_REJECT, new UnknownException(e, id));
      }
    });

    // watch model event
    globalEvents.on(events.EVENT_WATCH, async (data) => {
      const {
        id, eventEmitter: localEvents, modelId, modelType,
      } = data;
      const { changeStream, model } = await watchModelByType(modelId, modelType);
      if (changeStream === null) {
        const err = new UnableException('No soch model', id, { modelId });
        localEvents.emit(events.EVENT_REJECT, err);
      } else {
        const genRespFcn = selectRespFcn(modelType);
        changeStream.on('change', (next) => {
          const resp = genRespFcn(id, next.fullDocument, next.operationType);
          localEvents.emit(events.EVENT_RESPONSE, resp);
        });
        changeStreams.set(id, changeStream);
        localEvents.on(events.EVENT_SOCKET_CLOSED, () => {
          changeStream.close();
          changeStreams.delete(id);
        });
        const resp = genRespFcn(id, model, 'update');
        localEvents.emit(events.EVENT_RESPONSE, resp);
      }
    });

    // watch list event
    globalEvents.on(events.EVENT_WATCH_LIST, async (data) => {
      const {
        id, eventEmitter: localEvents, modelType, path,
      } = data;

      const { changeStream, list } = await watchList(modelType, path);

      if (changeStream === null) {
        const err = new UnableException('Error finding list model', id, { });
        localEvents.emit(events.EVENT_REJECT, err);
      } else {
        changeStream.on('change', (next) => {
          const resp = response.onModelUpdated(id, next.fullDocument, next.operationType);
          localEvents.emit(events.EVENT_RESPONSE, resp);
        });
        changeStreams.set(id, changeStream);
        localEvents.on(events.EVENT_SOCKET_CLOSED, () => {
          changeStream.close();
          changeStreams.delete(id);
        });
        // send all current list items
        const resp = response.onModelUpdated(id, list, 'refresh');
        localEvents.emit(events.EVENT_RESPONSE, resp);
      }
    });

    // close change stream
    globalEvents.on(events.EVENT_UNWATCH, (data) => {
      const { id, eventEmitter: localEvents, reqId } = data;
      const changeStream = changeStreams.get(reqId);
      if (changeStream === null || changeStream === undefined) {
        const err = new UnableException('No soch request', id, { reqId });
        localEvents.emit(events.EVENT_REJECT, err);
      } else {
        changeStream.close();
        const resp = response.onSuccessTask(id);
        localEvents.emit(events.EVENT_RESPONSE, resp);
        changeStreams.delete(reqId);
      }
    });

    // watch all messages in chat
    globalEvents.on(events.EVENT_WATCH_CHAT, async (data) => {
      const {
        id, eventEmitter: localEvents, userId, chatId,
      } = data;
      try {
        const { changeStream, messages } = await modelService.watchMessages(chatId, userId);
        changeStream.on('change', (next) => {
          const resp = response.onModelUpdated(id, next.fullDocument, next.operationType);
          localEvents.emit(events.EVENT_RESPONSE, resp);
        });
        changeStream.on('error', (e) => {
          if (e instanceof PrivateChatError) {
            localEvents.emit(
              events.EVENT_REJECT,
              new UnableException(e.message, id, {}),
            );
          } else {
            localEvents.emit(
              events.EVENT_REJECT,
              new UnknownException(e, id),
            );
          }
          // changeStreams.get(id).close();
          changeStreams.delete(id);
        });
        changeStreams.set(id, changeStream);
        localEvents.on(events.EVENT_SOCKET_CLOSED, () => {
          changeStream.close();
          changeStreams.delete(id);
        });
        // send all current list items
        const resp = response.onModelUpdated(id, messages, 'refresh');
        localEvents.emit(events.EVENT_RESPONSE, resp);
      } catch (e) {
        if (e instanceof PrivateChatError) {
          localEvents.emit(
            events.EVENT_REJECT,
            new UnableException(e.message, id, {}),
          );
        } else {
          localEvents.emit(
            events.EVENT_REJECT,
            new UnknownException(e, id),
          );
        }
      }
    });

    // watch chats
    globalEvents.on(events.EVENT_WATCH_CHATS, async (data) => {
      const { id, eventEmitter: localEvents, userId } = data;

      const { changeStream, chats } = await modelService.watchUserChats(userId);
      changeStream.on('change', (next) => {
        const resp = response.onModelUpdated(id, next.documentKey._id, next.operationType);
        localEvents.emit(events.EVENT_RESPONSE, resp);
      });
      changeStreams.set(id, changeStream);
      localEvents.on(events.EVENT_SOCKET_CLOSED, () => {
        changeStream.close();
        changeStreams.delete(id);
      });
      // send all current list items
      const resp = response.onModelUpdated(id, chats.map((el) => el._id), 'refresh');
      localEvents.emit(events.EVENT_RESPONSE, resp);
    });

    // new chat
    globalEvents.on(events.EVENT_NEW_CHAT, async (data) => {
      const {
        id, eventEmitter: localEvents, type, users, userId,
      } = data;
      try {
        const chat = await modelService.newChat(type, users, userId);
        localEvents.emit(
          events.EVENT_RESPONSE,
          response.onSuccessWithData(id, { chatId: chat._id }),
        );
      } catch (e) {
        if (e instanceof PrivateChatError) {
          localEvents.emit(
            events.EVENT_REJECT,
            new UnableException(e.message, id, {}),
          );
        } else {
          localEvents.emit(
            events.EVENT_REJECT,
            new UnknownException(e, id),
          );
        }
      }
    });

    // message
    globalEvents.on(events.EVENT_MESSAGE, async (data) => {
      const {
        id, eventEmitter: localEvents, text, chatId, userId, time,
      } = data;
      try {
        await modelService.newMessage(time, userId, chatId, text);
        const resp = response.onSuccessTask(id);
        localEvents.emit(events.EVENT_RESPONSE, resp);
      } catch (e) {
        if (e instanceof PrivateChatError) {
          localEvents.emit(
            events.EVENT_REJECT,
            new UnableException(e.message, id, {}),
          );
        } else {
          localEvents.emit(
            events.EVENT_REJECT,
            new UnknownException(e, id),
          );
        }
      }
    });

    globalEvents.on(events.EVENT_PURCHASE, async (data) => {
      const {
        id, eventEmitter: localEvents, userId, products, comment,
      } = data;
      try {
        const order = await modelService.newOrder(userId, products);
        if (comment !== undefined) {
          await modelService.newMessage(new Date(), userId, order.chatId, comment);
        }
        const resp = response.onSuccessWithData(id, { orderId: order._id });
        localEvents.emit(events.EVENT_RESPONSE, resp);
      } catch (e) {
        if (e instanceof OrderException) {
          localEvents.emit(
            events.EVENT_REJECT,
            new UnableException(e.message, id, {}),
          );
        } else {
          localEvents.emit(
            events.EVENT_REJECT,
            new UnknownException(e, id),
          );
        }
      }
    });

    globalEvents.on(events.EVENT_EDIT_ITEM, async (data) => {
      const {
        id, eventEmitter: localEvents, path, itemId, update, user,
      } = data;
      const updatePermitted = utils.isUpdPermitted(user, update, path);
      if (!updatePermitted) {
        localEvents.emit(
          events.EVENT_REJECT,
          new UnableException('operation not permitted', id, {}),
        );
        return;
      }
      try {
        await modelService.update(path, itemId, update);
        localEvents.emit(events.EVENT_RESPONSE, response.onSuccessTask(id));
      } catch (e) {
        localEvents.emit(events.EVENT_REJECT, new UnknownException(e, id));
      }
    });

    globalEvents.on(events.EVENT_ADD_MARKET, async (data) => {
      const {
        id, eventEmitter: localEvents, userId, name, description, imageUrl,
      } = data;

      try {
        const market = await modelService.newMarket(userId, name, description, imageUrl);
        const resp = response.onSuccessWithData(id, { marketId: market._id });
        localEvents.emit(events.EVENT_RESPONSE, resp);
      } catch (e) {
        if (e instanceof MarketException) {
          localEvents.emit(
            events.EVENT_REJECT,
            new UnableException(e.message, id, {}),
          );
        } else {
          localEvents.emit(
            events.EVENT_REJECT,
            new UnknownException(e, id),
          );
        }
      }
    });

    globalEvents.on(events.EVENT_ADD_PRODUCT, async (data) => {
      const {
        id,
        eventEmitter: localEvents,
        marketId,
        name,
        description,
        quantity,
        price,
        photoUrls,
      } = data;

      const descr = {
        name,
        description,
        quantity,
        price,
        photoUrls,
      };
      try {
        const product = await modelService.newProduct(marketId, descr);
        const resp = response.onSuccessWithData(id, { productId: product._id });
        localEvents.emit(events.EVENT_RESPONSE, resp);
      } catch (e) {
        if (e instanceof ProductException) {
          localEvents.emit(
            events.EVENT_REJECT,
            new UnableException(e.message, id, {}),
          );
        } else {
          localEvents.emit(
            events.EVENT_REJECT,
            new UnknownException(e, id),
          );
        }
      }
    });
  };
})();
