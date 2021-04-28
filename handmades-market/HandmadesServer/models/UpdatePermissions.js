/**
 * @description Objects, containing fields of models? available for update from
 * client
 * //TODO check id when changing model
 */
const modelTypes = require('../controllers/RequestTypes').MODEL_TYPES;

const user = {
  fName: true,
  sName: true,
  surName: true,
  login: true,
  password: true,
};

const customer = {
  user,
  order: {
    status: true,
  },
};

const vendor = {
  user,
  market: {
    name: true,
    description: true,
  },
  order: {
    status: true,
  },
};

module.exports = new Map([
  [modelTypes.customer, customer],
  [modelTypes.vendor, vendor],
]);
