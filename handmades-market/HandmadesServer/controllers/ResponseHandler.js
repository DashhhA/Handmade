const { v4: uuid } = require('uuid');
const events = require('../utils/events');
const serverEvents = require('./ServerEvents');
const utils = require('../utils/util');
const { UnableException, UnknownException } = require('../utils/errors');
// TODO: Make 3-argument function for constructing responses f(is, event, message)
/**
 * Enshures response valid
 */
function makeResponse(data) {
  // TODO: check fields
  return data;
}

function disconnectResponse(data) {
  return {
    id: uuid(),
    event: serverEvents.socketClosed,
    message: {
      message: data.message,
    },
  };
}

function rejectResponse(err) {
  if (err instanceof UnableException) {
    return {
      id: err.id,
      event: serverEvents.error,
      message: {
        message: err.message,
        data: err.data,
      },
    };
  } if (err instanceof UnknownException) {
    return {
      id: err.id,
      event: serverEvents.error,
      message: {
        message: 'Unknown error',
        data: err.message,
      },
    };
  }
  return {
    id: uuid(),
    event: serverEvents.error,
    message: {
      message: 'Server error',
      data: err.message,
    },
  };
}

class ResponseHandler {
  constructor(socket, eventEmitter) {
    eventEmitter.on(events.EVENT_RESPONSE, async (data) => {
      const resp = makeResponse(data);
      const str = await utils.stringifyAsync(resp);
      socket.write(str);
    });

    eventEmitter.on(events.EVENT_DISCONNECT, async (data) => {
      const resp = disconnectResponse(data);
      const str = await utils.stringifyAsync(resp);
      socket.write(str);
    });

    eventEmitter.on(events.EVENT_REJECT, async (err) => {
      const resp = rejectResponse(err);
      const str = await utils.stringifyAsync(resp);
      socket.write(str);
    });
  }
}

module.exports = ResponseHandler;
