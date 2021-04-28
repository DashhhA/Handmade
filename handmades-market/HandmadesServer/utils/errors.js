/**
 * @fileoverview Custom errors
 */

/* eslint-disable max-classes-per-file */
/**
 * @description thrown, when wrong data got from socket
 */
class SocketRequestError extends Error {
  constructor(message) {
    super(message);
    this.name = this.constructor.name;
    Error.captureStackTrace(this, this.constructor);
  }
}

/**
 * @description thrown, when unable to satisfy request
 */
class UnableException extends Error {
  /**
   * @param message Error message
   * @param id id of the request
   * @param data Data for answer
   */
  constructor(message, id, data) {
    super(message);
    this.name = this.constructor.name;
    this.id = id;
    this.data = data;
    Error.captureStackTrace(this, this.constructor);
  }
}

/**
 * @description thrown, ond duplicate key when writing to database
 */
class DuplicateKeyException extends UnableException {
  /**
   * @param id id of the request
   * @param dupKey key, that whas duplicated
   */
  constructor(id, dupKey) {
    super('Duplicate key', id, { dupKey });
  }
}

/**
 * @description Thrown, when trying to asses authorized user functional,
 * when not authorized
 */
class UnauthorizedException extends UnableException {
  /**
   * @param id id of the request
   */
  constructor(id) {
    super('User unauthorized', id, { });
  }
}

/**
 * @description Thrown, when cannot post order
 */
class OrderException extends Error {
  constructor(message) {
    super(message);
    this.name = this.constructor.name;
    Error.captureStackTrace(this, this.constructor);
  }
}

/**
 * @description Thrown, when cannot create market
 */
class MarketException extends Error {
  constructor(message) {
    super(message);
    this.name = this.constructor.name;
    Error.captureStackTrace(this, this.constructor);
  }
}

/**
 * @description Thrown, when cannot create product
 */
class ProductException extends Error {
  constructor(message) {
    super(message);
    this.name = this.constructor.name;
    Error.captureStackTrace(this, this.constructor);
  }
}

/**
 * @description Thrown, when unknown error occures
 */
class UnknownException extends Error {
  /**
   * @param {Exception} exception The unknown exception
   * @param {string} id id of the request
   */
  constructor(exception, id) {
    super(exception.message);
    this.name = this.constructor.name;
    this.code = exception.code;
    this.stack = exception.stack;
    this.id = id;
  }
}

/* eslint-enable max-classes-per-file */

/**
  * @description Connection close reasons
  */
const connCloseReasons = {
  WRONG_REQUEST: 'wron request format',
  OTHER: 'server error',
};

module.exports = {
  SocketRequestError,
  UnableException,
  UnknownException,
  DuplicateKeyException,
  OrderException,
  UnauthorizedException,
  MarketException,
  ProductException,
  connCloseReasons,
};
