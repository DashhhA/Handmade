const mongoose = require('mongoose');
const reqTypes = require('./RequestTypes');
const emitterEvents = require('../utils/events');
const { SocketRequestError, UnableException } = require('../utils/errors');
const typeSpecific = require('./TypeSpecificController');

// TODO put request id and localEvents to evqnt data authomatically

/**
 * Checks, that request body contains all fields, needed to watch a model
 */
function verifyWatchModelBody(body) {
  if (!Object.prototype.hasOwnProperty.call(body, 'model')) {
    throw new SocketRequestError('no "body.model" field');
  }
  if (typeof body.model !== 'string') {
    throw new SocketRequestError('"body.model" must be string');
  }
  if (!Object.values(reqTypes.MODEL_TYPES).includes(body.model)) {
    throw new SocketRequestError('unknown "body.model"');
  }
  if (!Object.prototype.hasOwnProperty.call(body, 'id')) {
    throw new SocketRequestError('no "body.id" field');
  }
  if (typeof body.id !== 'string') {
    throw new SocketRequestError('"body.id" must be string');
  }
}

/**
 * Checks, that request body contains id to unwatch
 */
function verifyUnwatch(body) {
  if (!Object.prototype.hasOwnProperty.call(body, 'id')) {
    throw new SocketRequestError('no "body.id" field');
  }
  if (typeof body.id !== 'string') {
    throw new SocketRequestError('"body.id" must be string');
  }
}

/**
 * @description Checks, that watch list request contains all needed fields
 */
function verifyWatchList(body) {
  if (!Object.prototype.hasOwnProperty.call(body, 'model')) {
    throw new SocketRequestError('no "body.model" field');
  }
  if (typeof body.model !== 'string') {
    throw new SocketRequestError('"body.model" must be string');
  }
  if (!Object.values(reqTypes.MODEL_TYPES).includes(body.model)) {
    throw new SocketRequestError('unknown "body.model"');
  }
  if (Object.prototype.hasOwnProperty.call(body, 'path')) {
    if (!Object.prototype.hasOwnProperty.call(body.path, 'id')) {
      throw new SocketRequestError('no "body.path.id" field');
    }
    if (typeof body.path.id !== 'string') {
      throw new SocketRequestError('"body.path.id" must be string');
    }
    // TODO: check, that model conains requested properties
    if (!Object.prototype.hasOwnProperty.call(body.path, 'props')) {
      throw new SocketRequestError('no "body.path.props" field');
    }
    if (typeof body.path.props !== 'string') {
      throw new SocketRequestError('"body.path.props" must be string');
    }
  }
}

/**
 * @description Checks, that watch_chat request has valid fields
 */
function verifyWatchChat(body) {
  if (!Object.prototype.hasOwnProperty.call(body, 'chatId')) {
    throw new SocketRequestError('no "body.chatId" field');
  }
  if (typeof body.chatId !== 'string') {
    throw new SocketRequestError('"body.chatId" must be string');
  }
}

/**
 * @description Checks, that new_chat request has valid fields
 */
function verifyNewChat(body) {
  if (!Object.prototype.hasOwnProperty.call(body, 'type')) {
    throw new SocketRequestError('no "body.type" field');
  }
  if (typeof body.type !== 'string') {
    throw new SocketRequestError('"body.type" must be string');
  }
  if (!Object.values(reqTypes.CHAT_TYPES).includes(body.type)) {
    throw new SocketRequestError('unknown "body.type"');
  }
  if (!Array.isArray(body.users)) {
    throw new SocketRequestError('"body.users" must be array');
  }
  if (body.type === reqTypes.CHAT_TYPES.private && body.users.length !== 1) {
    throw new SocketRequestError('private chat is only with 1 user');
  }
  if (body.type === reqTypes.CHAT_TYPES.comments && body.users.length > 0) {
    throw new SocketRequestError('comment may not have any users');
  }
}

/**
 * @description Checks, that message request has valid fields
 */
function verifyMessage(body) {
  if (!Object.prototype.hasOwnProperty.call(body, 'text')) {
    throw new SocketRequestError('no "body.text" field');
  }
  if (typeof body.text !== 'string') {
    throw new SocketRequestError('"body.text" must be string');
  }
  if (!Object.prototype.hasOwnProperty.call(body, 'chatId')) {
    throw new SocketRequestError('no "body.chatId" field');
  }
  if (typeof body.chatId !== 'string') {
    throw new SocketRequestError('"body.chatId" must be string');
  }
  if (!Object.prototype.hasOwnProperty.call(body, 'time')) {
    throw new SocketRequestError('no "body.time" field');
  }
  if (typeof body.time !== 'string') {
    throw new SocketRequestError('"body.time" must be string');
  }
}

function checkEditItem(body) {
  if (!Object.prototype.hasOwnProperty.call(body, 'path')) {
    throw new SocketRequestError('no "body.path" field');
  }
  if (typeof body.path !== 'string') {
    throw new SocketRequestError('"body.path" must be a string');
  }
  if (!Object.prototype.hasOwnProperty.call(body, 'update')) {
    throw new SocketRequestError('no "body.update" field');
  }
  if (typeof body.update !== 'object') {
    throw new SocketRequestError('"body.update" must be an object');
  }
  if (!Object.prototype.hasOwnProperty.call(body, 'id')) {
    throw new SocketRequestError('no "body.id" field');
  }
  if (typeof body.id !== 'string') {
    throw new SocketRequestError('"body.id" must be a string');
  }
}

class AuthorizedUserController {
  /**
   * @param user User, got from authentication
   * @param socket The socket, which wants to authorise
   * @param globalEvents EventEmitter to throw gloabal events
   * @param localEvents EventEmitter to throw events, related to this connection
   */
  constructor(user, socket, globalEvents, localEvents) {
    this.user = user;
    this.socket = socket;
    this.globalEvents = globalEvents;
    this.localEvents = localEvents;
    this.typeSpecific = typeSpecific(user, globalEvents, localEvents);
  }

  processRequest(request) {
    switch (request.type) {
      case reqTypes.LOGOUT:
        this.localEvents.emit(emitterEvents.EVENT_LOGOUT, request.id);
        break;
      case reqTypes.REMOVE_USER: {
        const data = {
          id: request.id,
          eventEmitter: this.localEvents,
          login: this.user.login,
        };
        this.globalEvents.emit(emitterEvents.EVENT_REMOVE_USER, data);
        break;
      }

      // TOOD: convert id to ObjectId here
      case reqTypes.WATCH_MODEL: {
        verifyWatchModelBody(request.body);
        const data = {
          id: request.id,
          eventEmitter: this.localEvents,
          modelId: request.body.id,
          modelType: request.body.model,
        };
        this.globalEvents.emit(emitterEvents.EVENT_WATCH, data);
        break;
      }

      case reqTypes.WATCH_LIST: {
        verifyWatchList(request.body);
        const data = {
          id: request.id,
          eventEmitter: this.localEvents,
          modelType: request.body.model,
          path: request.body.path,
        };
        this.globalEvents.emit(emitterEvents.EVENT_WATCH_LIST, data);
        break;
      }

      case reqTypes.UNWATCH_MODEL: {
        verifyUnwatch(request.body);
        const data = {
          id: request.id,
          eventEmitter: this.localEvents,
          reqId: request.body.id,
        };
        this.globalEvents.emit(emitterEvents.EVENT_UNWATCH, data);
        break;
      }

      case reqTypes.CURRENT_USER: {
        const data = {
          id: request.id,
          eventEmitter: this.localEvents,
          userId: this.user._id,
        };
        this.globalEvents.emit(emitterEvents.EVENT_CURRENT, data);
        break;
      }

      case reqTypes.WATCH_CHAT: {
        verifyWatchChat(request.body);
        try {
          const data = {
            id: request.id,
            eventEmitter: this.localEvents,
            userId: this.user._id,
            chatId: mongoose.Types.ObjectId(request.body.chatId),
          };
          this.globalEvents.emit(emitterEvents.EVENT_WATCH_CHAT, data);
        } catch (e) {
          throw new UnableException('Not an id', request.id, { id: request.body.chatId });
        }
        break;
      }

      case reqTypes.WATCH_CHATS: {
        const data = {
          id: request.id,
          eventEmitter: this.localEvents,
          userId: this.user._id,
        };
        this.globalEvents.emit(emitterEvents.EVENT_WATCH_CHATS, data);
        break;
      }

      case reqTypes.NEW_CHAT: {
        verifyNewChat(request.body);
        const data = {
          id: request.id,
          eventEmitter: this.localEvents,
          type: request.body.type,
          users: request.body.users,
        };
        if (data.type === reqTypes.CHAT_TYPES.private) {
          data.userId = this.user._id;
        }
        this.globalEvents.emit(emitterEvents.EVENT_NEW_CHAT, data);
        break;
      }

      case reqTypes.MESSAGE: {
        verifyMessage(request.body);
        const time = new Date(request.body.time);
        if (Number.isNaN(time.valueOf())) {
          throw new UnableException('Invalid date', request.id, { date: request.body.time });
        }
        try {
          const data = {
            id: request.id,
            eventEmitter: this.localEvents,
            text: request.body.text,
            chatId: mongoose.Types.ObjectId(request.body.chatId),
            userId: this.user._id,
            time,
          };
          this.globalEvents.emit(emitterEvents.EVENT_MESSAGE, data);
        } catch (e) {
          throw new UnableException('Not an id', request.id, { id: request.body.chatId });
        }
        break;
      }

      case reqTypes.EDIT_ITEM: {
        checkEditItem(request.body);
        let itemId;
        if (request.body.path === reqTypes.MODEL_TYPES.user) {
          itemId = this.user._id;
        } else {
          itemId = mongoose.Types.ObjectId(request.body.id);
        }
        const data = {
          id: request.id,
          eventEmitter: this.localEvents,
          path: request.body.path,
          itemId,
          update: request.body.update,
          user: this.user,
        };
        this.globalEvents.emit(emitterEvents.EVENT_EDIT_ITEM, data);
        break;
      }

      default:
        this.typeSpecific(request);
    }
  }
}

module.exports = AuthorizedUserController;
