/**
 * @fileoverview load tight - coupled database services and models. Exports
 * services for accessing database models
 */
const mongoose = require('mongoose');
const logger = require('../services/Logger')(module);

const mongooseOptions = {
  useNewUrlParser: true,
  useFindAndModify: false,
  useCreateIndex: true,
  useUnifiedTopology: true,
  replicaSet: process.env.DB_REPLICA_SET,
  poolSize: 1e5,
};

module.exports = (() => new Promise((resolve, reject) => {
  mongoose.connect(process.env.DB_ADDR, mongooseOptions)
    .then(() => {
      logger.info('connected to database');
      resolve(mongoose);
    })
    .catch((err) => {
      logger.error(err);
      reject(err);
    });
}))();
