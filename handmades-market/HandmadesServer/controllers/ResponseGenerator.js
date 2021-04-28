const mongoose = require('mongoose');
const events = require('./ServerEvents');

function deepMap(initial, change) {
  if (!(initial instanceof Object)) return change(initial);
  const result = {};
  Object.keys(initial).forEach((key) => {
    if (initial[key] instanceof Object) {
      result[key] = deepMap(initial[key], change);
    } else if (Array.isArray(initial[key])) {
      result[key] = initial[key].map((el) => deepMap(el));
    } else {
      result[key] = change(initial[key]);
    }
  });

  return result;
}

/**
 * Generates a user, suitable for sending to client from databse document
 */
function genUserResponse(user) {
  const resp = {
    login: user.login,
    fName: user.fName,
    sName: user.sName,
    surName: user.surName,
    userType: user.userType,
    modelId: user.modelId,
    dbId: user._id,
  };

  Object.keys(resp).forEach((key) => (resp[key] === undefined ? delete resp[key] : {}));

  return resp;
}

/**
 * @description Modeifies model from DB to send to client
 * @param model Model from DB
 * @return {object} Object to send to client
 */
function genModelUpdate(model) {
  if (model instanceof mongoose.Model) {
    const { _id, __v, ...modified } = model.toObject();
    return modified;
  }
  if (model instanceof mongoose.Types.ObjectId) {
    return model.toString();
  }
  if (Array.isArray(model)) {
    return model;
  }
  const { _id, __v, ...modified } = model;
  return modified;
}

/**
 * Forms a response message on password validation
 * @param {User|null} user User if password valid, null otherwiase
 * @param {string} id Id of request to respond
 * @return Message to client
 */
function onPasswordChecked(user, id) {
  const passValid = !!user;
  const type = passValid ? user.userType : undefined;
  return {
    id,
    event: events.response,
    message: {
      success: passValid,
      type,
    },
  };
}

/**
 * Forms a response about task successfull completion
 * @param {string} id Id of request to respond
 * @return Message to client
 */
function onSuccessTask(id) {
  return {
    id,
    event: events.response,
    message: {
      success: true,
    },
  };
}

/**
 * Forms a response about task successfull completion, and puts additional data
 * @param {string} id Id of request to respond
 * @param {Object} data Data to send
 * @return Message to client
 */
function onSuccessWithData(id, data) {
  return {
    id,
    event: events.response,
    message: {
      success: true,
      data,
    },
  };
}

/**
 * Forms a server event, informing about user update
 * @param {string} id Id of request to listen for changes
 * @param {Object | undefined} updated Current object state, or null if deleted
 * @param {string} event Event, caused change
 * @return {string} Message to client
 */
function onSingleUpdated(id, updated, event) {
  const updatedResp = updated === undefined ? {} : genUserResponse(updated);
  return {
    id,
    event: events.update,
    message: {
      event,
      updated: updatedResp,
    },
  };
}

/**
 * Forms a server event, informing about field update
 * @param {string} id Id of request to listen for changes
 * @param {Object | undefined} updated Current object state, or null if deleted
 * @param {string} event Event, caused change
 * @return {string} Message to client
 */
function onModelUpdated(id, updated, event) {
  const updatedResp = updated === undefined ? {} : genModelUpdate(updated);
  return {
    id,
    event: events.update,
    message: {
      event,
      updated: updatedResp,
    },
  };
}

/**
 * Forms a response, containing user data
 * @param {string} id Id of request
 * @param {Object} user User model from db
 * @return {string} Message to client
 */
function userResponse(id, user) {
  const userToClient = genUserResponse(user);
  return {
    id,
    event: events.response,
    message: {
      success: true,
      user: userToClient,
    },
  };
}

module.exports = {
  onPasswordChecked,
  onSuccessTask,
  onSuccessWithData,
  onSingleUpdated,
  onModelUpdated,
  userResponse,
};
