const { assert } = require('chai');
const mocha = require('mocha');
const path = require('path');
const events = require('events');
const UserModelService = require('../../services/UserModelService');
const newUserService = require('../../services/NewUserService');
const userModel = require('../../models/user');
// assuming, test is runned from project root
require('dotenv').config({ path: path.resolve(process.cwd(), 'test/unit/test.env') });

let mongoose;
let userModelService;
let user;

mocha.before(async () => {
  mongoose = await require('../../loaders/MongooseLoader');
  user = {
    fName: 'fName',
    login: 'login',
    password: {
      data: 'data',
      salt: 'salt',
    },
    userType: 'customer',
    modelId: mongoose.Types.ObjectId(),
  };
});

mocha.after((done) => {
  mongoose.connection.close();
  done();
});

mocha.describe('NewUserService', () => {
  it('launch service', async () => {
    userModelService = new UserModelService(mongoose);
    const globalEvents = new events.EventEmitter();

    // launch auth service for globalEvents
    newUserService(globalEvents, userModelService);
  });

  it('drop db', async () => {
    // drop db
    const users = userModel(mongoose);
    await users.deleteMany();
  });

  it('User should be saved', (done) => {
    userModelService.trySave(user)
      .then(() => done(), (err) => { throw err; });
  });

  it('Same user should not be saved', (done) => {
    userModelService.trySave(user)
      .then(() => done('User saved'))
      .catch((err) => {
        if (err.code === 11000) {
          done();
        } else {
          done('wrong error');
        }
      });
  });

  it('User type customer should be saved', (done) => {
    const customer = {
      fName: 'fName',
      login: 'loginC',
      password: {
        data: 'data',
        salt: 'salt',
      },
      userType: 'customer',
      modelId: mongoose.Types.ObjectId(),
    };
    userModelService.trySave(customer)
      .then(() => done(), (err) => done(err));
  });

  it('User type vendor should be saved', (done) => {
    const vendor = {
      fName: 'fName',
      login: 'loginV',
      password: {
        data: 'data',
        salt: 'salt',
      },
      userType: 'vendor',
      modelId: mongoose.Types.ObjectId(),
    };
    userModelService.trySave(vendor)
      .then(() => done(), (err) => done(err));
  });

  it('User type admin should be saved', (done) => {
    const admin = {
      fName: 'fName',
      login: 'loginA',
      password: {
        data: 'data',
        salt: 'salt',
      },
      userType: 'admin',
      modelId: mongoose.Types.ObjectId(),
    };
    userModelService.trySave(admin)
      .then(() => done(), (err) => done(err));
  });

  it('User type moderator should be saved', (done) => {
    const moderator = {
      fName: 'fName',
      login: 'loginM',
      password: {
        data: 'data',
        salt: 'salt',
      },
      userType: 'admin',
      modelId: mongoose.Types.ObjectId(),
    };
    userModelService.trySave(moderator)
      .then(() => done(), (err) => done(err));
  });

  it('User of wrong type should NOT be saved', (done) => {
    const moderator = {
      fName: 'fName',
      login: 'loginW',
      password: {
        data: 'data',
        salt: 'salt',
      },
      userType: 'none',
      modelId: mongoose.Types.ObjectId(),
    };
    userModelService.trySave(moderator)
      .then(() => done('User saved'), (err) => {
        if (err.errors.userType.kind === 'enum') {
          done();
        } else {
          done('Wrong error');
        }
      });
  });

  it('User should be found by login', (done) => {
    userModelService.getByLogin(user.login).then(
      (found) => {
        if (found.login === user.login) done();
        else done('found wrong entry');
      },
      (err) => { done(err); },
    );
  });
});
