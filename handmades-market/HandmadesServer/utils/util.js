const { customAlphabet } = require('nanoid/async');
const updatePermissions = require('../models/UpdatePermissions');

const nanoid = customAlphabet('ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789', 8);

async function parseJsonAsync(str) {
  try {
    return JSON.parse(str);
  } catch (e) {
    return undefined;
  }
}

async function stringifyAsync(obj) {
  return JSON.stringify(obj);
}

function genId() {
  let id = 0;

  return () => { id += 1; return id; };
}

async function asyncId() {
  const id = await nanoid();
  return id;
}

function isUpdPermitted(user, update, collection) {
  const perm = updatePermissions.get(user.userType);
  if (perm === undefined) return false;
  const collectionPerm = perm[collection];
  if (collectionPerm === undefined) return false;
  function checkPermRec(upd, _perm) {
    return Object.keys(upd).every((key) => {
      if (_perm[key] === undefined) return false;
      if (typeof _perm[key] === 'object') return checkPermRec(upd[key], _perm[key]);
      return _perm[key];
    });
  }

  return checkPermRec(update, collectionPerm);
}

module.exports.parseJsonAsync = parseJsonAsync;
module.exports.stringifyAsync = stringifyAsync;
module.exports.genId = genId();
module.exports.asyncId = asyncId;
module.exports.isUpdPermitted = isUpdPermitted;
