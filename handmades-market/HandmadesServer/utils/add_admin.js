const yargs = require('yargs');
const crypto = require('crypto');
const mongoose = require('mongoose');
const model = require('../models/user');
const userTypes = require('./userTypes');

const mongooseOptions = {
  useNewUrlParser: true,
  useFindAndModify: false,
  useCreateIndex: true,
  useUnifiedTopology: true,
};

function hashPassword(password) {
  const hash = crypto.createHash('sha256');
  const salt = crypto.randomBytes(16)
    .toString();
  const pass = hash.update(password + salt)
    .digest('hex');
  return {
    data: pass,
    salt,
  };
}

const argv = yargs
  .option('login', {
    description: 'the email as a login',
    alias: 'l',
    type: 'string',
    demandOption: true,
  })
  .option('name', {
    description: 'admin first name',
    alias: 'n',
    type: 'string',
    demandOption: true,
  })
  .option('pass', {
    description: 'account password',
    alias: 'p',
    type: 'string',
    demandOption: true,
  })
  .option('secName', {
    description: 'admin second name',
    alias: 's',
    type: 'string',
  })
  .option('surName', {
    description: 'admin surname',
    alias: 'f',
    type: 'string',
  })
  .option('db', {
    description: 'database address',
    alias: 'd',
    type: 'string',
    default: 'mongodb://localhost:27017/routes',
  })
  .wrap(yargs.terminalWidth())
  .help()
  .alias('help', 'h')
  .argv;

yargs.db = yargs.db || 'mongodb://localhost:27017/routes';

(async () => {
  const db = await mongoose.connect(yargs.db, mongooseOptions);
  const admin = model(mongoose);
  const descr = admin({
    fName: argv.name,
    sName: argv.secName,
    surName: argv.surName,
    login: argv.login,
    password: hashPassword(argv.pass),
    userType: userTypes.admin,
  });
  try {
    const res = await descr.save(descr);
    console.log('saved');
    console.log(res);
  } catch (e) {
    console.log('failed');
    console.log(e.message);
  }
  await db.disconnect();
})();
