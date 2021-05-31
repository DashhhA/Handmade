/**
 * @description Objects, containing fields of models? available for update from
 * client
 * //TODO check id when changing model
 */
const modelTypes = require('../utils/userTypes');

const user = {
  fName: true,
  sName: true,
  surName: true,
  login: true,
  password: true,
};

const message = {
  read: true,
};

const customer = {
  user,
  message,
  order: {
    status: true,
  },
};

const vendor = {
  user,
  message,
  market: {
    name: true,
    description: true,
  },
  order: {
    status: true,
    packing: true,
    urgent: true,
    deliveryPrice: true,
  },
};

const admin = {
  user,
  message: {
    read: true,
    deleted: true,
  },
  market: {
    status: true,
  },
};

module.exports = new Map([
  [modelTypes.customer, customer],
  [modelTypes.vendor, vendor],
  [modelTypes.admin, admin],
]);
