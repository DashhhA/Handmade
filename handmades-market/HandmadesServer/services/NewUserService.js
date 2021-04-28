const crypto = require('crypto');
const mogooseTypes = require('mongoose').Types;
const events = require('../utils/events');
const { DuplicateKeyException, UnableException, UnknownException } = require('../utils/errors');
const response = require('../controllers/ResponseGenerator');
const getModelService = require('./ModelService');

/**
 * @description Check data to contain needed fields (password in this case, to be salted)
 */
async function verifyFields(data) {
  if (!Object.prototype.hasOwnProperty.call(data.body, 'password')) {
    throw new UnableException('no "body.password" field', data.id, {});
  }
  if (typeof data.body.password !== 'string') {
    throw new UnableException('password must be a string', data.id, {});
  }
  if (data.body.password.length < 5) {
    throw new UnableException('password must be at least 5 characters', data.id, {});
  }
  return data;
}

/**
 * @description form a response
 */
async function respond(data, emitter) {
  const result = response.onSuccessTask(data.id);
  emitter.emit(events.EVENT_RESPONSE, result);
}

/**
 * @description Hash password with salt
 */
async function hashPassword(data) {
  const hash = crypto.createHash('sha256');
  const salt = crypto.randomBytes(16).toString();
  const pass = hash.update(data.body.password + salt).digest('hex');
  const user = data.body;
  user.password = {
    data: pass,
    salt,
  };
  return user;
}

module.exports = (globalEvents, userModelService) => {
  const modelService = getModelService(userModelService.conn);
  /**
   * @description Saves user and the corresponding model
   */
  async function saveModel(user) {
    const modelId = mogooseTypes.ObjectId();
    const modified = { ...user, modelId };
    const saved = await userModelService.trySave(modified);
    await modelService.newUser(saved);
  }

  globalEvents.on(events.EVENT_NEW_USER, (data) => {
    const localEvents = data.eventEmitter;
    verifyFields(data)
      .then((user) => hashPassword(user))
      .then((user) => saveModel(user))
      .then(() => respond(data, localEvents))
      .then(() => localEvents.emit(events.EVENT_DISCONNECT, { message: 'task complete' }))
      .catch((err) => {
        if (err.code === 11000) {
          // case of a duplicate key error
          const e = new DuplicateKeyException(data.id, err.keyValue);
          localEvents.emit(events.EVENT_REJECT, e);
        } else {
          const e = new UnknownException(err, data.id);
          localEvents.emit(events.EVENT_REJECT, e);
        }
      });
  });
};
