const crypto = require('crypto');
const events = require('../utils/events');
const { UnableException, UnknownException } = require('../utils/errors');
const response = require('../controllers/ResponseGenerator');

/**
 * @description Vaidates password by it's hash and salt
 * @param pass Password to validate
 * @param hashSalt Object, containing password hash and salt
 * @param hash Cryptographyc hash function
 * @return {boolean} true if the password is correct, false otherwise
 */
function validatePass(pass, hashSalt, hash) {
  const { data, salt } = hashSalt;
  const candidate = hash.update(pass + salt).digest('hex');
  return data === candidate;
}

class NoUserException extends Error {
  /**
   * @param message Error message
   */
  constructor() {
    super('No such user');
    this.name = this.constructor.name;
    Error.captureStackTrace(this, this.constructor);
  }
}

/**
 * @description Checks, user with this password and login exists
 * @param data Dict, containing password and login fields
 * @param userModelService Object to interact with data layer
 * @param hash Cryptographyc hash function
 * @return {user|null} true if authorization successfull, else null
 */
async function authUser(data, userModelService) {
  const hash = crypto.createHash('sha256');
  const { login, password } = data;
  const user = await userModelService.getByLogin(login);
  if (user === null) {
    throw new NoUserException();
  }
  const hashSalt = user.password;
  if (validatePass(password, hashSalt, hash)) {
    return user;
  }
  return null;
}

/**
 * @description Check data to contain needed fields
 */
async function verifyFields(data) {
  if (!Object.prototype.hasOwnProperty.call(data.body, 'login')) {
    throw new UnableException('no "body.login" field', data.id, {});
  }
  if (typeof data.body.login !== 'string') {
    throw new UnableException('login must be a string', data.id, {});
  }
  if (!Object.prototype.hasOwnProperty.call(data.body, 'password')) {
    throw new UnableException('no "body.password" field', data.id, {});
  }
  if (typeof data.body.password !== 'string') {
    throw new UnableException('password must be a string', data.id, {});
  }
  return data.body;
}

/**
 * Form a response event
 */
function respond(eventEmitter, user, id) {
  const resp = response.onPasswordChecked(user, id);
  eventEmitter.emit(events.EVENT_RESPONSE, resp);
}

function reject(eventEmitter, err, id) {
  if (err instanceof UnableException) {
    eventEmitter.emit(events.EVENT_REJECT, err);
  } else if (err instanceof NoUserException) {
    const e = new UnableException(err.message, id, {});
    eventEmitter.emit(events.EVENT_REJECT, e);
  } else {
    eventEmitter.emit(events.EVENT_REJECT, UnknownException(err, id));
  }
}

module.exports = (() => (globalEvents, userModelService) => {
  globalEvents.on(events.EVENT_AUTH_USER, (data) => {
    const localEvents = data.eventEmitter;

    verifyFields(data)
      .then((loginData) => authUser(loginData, userModelService))
      .then((user) => {
        if (user) {
          localEvents.emit(events.EVENT_USER_AUTHENTICATED, user);
        }
        respond(localEvents, user, data.id);
      })
      .catch((err) => reject(localEvents, err, data.id));
  });
})();
