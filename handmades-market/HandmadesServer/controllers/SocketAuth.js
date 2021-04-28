/**
 * @fileoverview Determins authorization and registration requests on given socket
 */

const { v4: uuid } = require('uuid');
const logger = require('../services/Logger')(module);
const utils = require('../utils/util');
const { SocketRequestError, connCloseReasons, UnauthorizedException } = require('../utils/errors');
const serverEvents = require('./ServerEvents');
const reqTypes = require('./RequestTypes');
const emitterEvents = require('../utils/events');
const AuthorizedUserController = require('./AuthorizedUserController');
const responses = require('./ResponseGenerator');

/**
 * @description Verify, that first message from client contains valid fields
 * @param msg Message from client
 */
async function verifyFields(msg) {
  const parsed = await utils.parseJsonAsync(msg);
  if (parsed === undefined) throw new SocketRequestError('wrong request format');
  if (!Object.prototype.hasOwnProperty.call(parsed, 'type')) throw new SocketRequestError('no "type" field');
  if (!Object.prototype.hasOwnProperty.call(parsed, 'id')) throw new SocketRequestError('no "id" field');
  if (!Object.prototype.hasOwnProperty.call(parsed, 'body')) throw new SocketRequestError('no "body" field');
  return parsed;
}

// TODO: move to ResponseGenerator
/**
 * @description Closes socket with error message. Called on wrong requests type
 * and unsuccessful authentication attempt to reduce DoS attacks effectivity
 * @param err Erorr, caused closing
 */
async function rejectConnection(err, socket) {
  let reason = connCloseReasons.OTHER;
  if (err instanceof SocketRequestError) {
    reason = connCloseReasons.WRONG_REQUEST;
  } else {
    reason = connCloseReasons.OTHER;
  }

  const answer = {
    id: uuid(),
    event: serverEvents.socketClosed,
    message: {
      reason,
      description: err.message,
    },
  };

  utils.stringifyAsync(answer)
    .then((ans) => socket.write(ans))
    .catch((e) => logger.error(e))
    .finally(() => socket.destroy());
}

class SocketAuth {
  /**
   * @param socket The socket, which wants to authorise
   * @param globalEvents EventEmitter to throw gloabal events
   * @param localEvents EventEmitter to throw events, related to this connection
   */
  constructor(socket, globalEvents, localEvents) {
    this.id = utils.genId();
    this.socket = socket;
    this.socket.setEncoding('utf8');
    this.authorizedController = null;
    this.globalEvents = globalEvents;
    this.localEvents = localEvents;
    logger.info(`new socket ${this.id} connection`);

    this.socket.on('data', (data) => {
      verifyFields(data)
        .then((req) => this.getType(req))
        .catch((err) => rejectConnection(err, socket));
    });

    this.socket.on('close', (err) => {
      this.removeListeners();
      logger.info(`socket ${this.id} closed with ${err ? 'error' : 'no errors'}`);
    });
    this.socket.on('error', (err) => {
      this.removeListeners();
      logger.error(`socket ${this.id} error`, err);
    });

    this.localEvents.on(emitterEvents.EVENT_USER_AUTHENTICATED, (user) => {
      // TODO: construct controllres depending on user type
      this.authorizedController = new AuthorizedUserController(
        user,
        this.socket,
        this.globalEvents,
        this.localEvents,
      );
    });

    this.localEvents.on(emitterEvents.EVENT_LOGOUT, (id) => {
      this.authorizedController = null;
      const resp = responses.onSuccessTask(id);
      this.localEvents.emit(emitterEvents.EVENT_RESPONSE, resp);
    });
  }

  /**
   * @description Gets the request type and throws corresponding event or error
   */
  getType(request) {
    const supportedReq = Object.values(reqTypes);
    if (supportedReq.includes(request.type)) {
      switch (request.type) {
        case reqTypes.NEW_USER:
          this.globalEvents.emit(emitterEvents.EVENT_NEW_USER, {
            id: request.id,
            body: request.body,
            eventEmitter: this.localEvents,
          });
          break;
        case reqTypes.AUTH_USER:
          // TODO: prevent double authorization
          this.globalEvents.emit(emitterEvents.EVENT_AUTH_USER, {
            id: request.id,
            body: request.body,
            eventEmitter: this.localEvents,
          });
          break;
        default:
          if (this.authorizedController) {
            this.authorizedController.processRequest(request);
          } else {
            const err = new UnauthorizedException(request.id);
            this.localEvents.emit(emitterEvents.EVENT_REJECT, err);
          }
      }
    } else {
      throw new SocketRequestError(`unsuppotred type ${request.type}`);
    }
  }

  /**
   * Sends event to remove watchers
   */
  removeListeners() {
    this.localEvents.emit(emitterEvents.EVENT_SOCKET_CLOSED);
    // TODO: destroy thr emitter
    // this.localEvents.removeAllListeners();
  }
}

module.exports = SocketAuth;
