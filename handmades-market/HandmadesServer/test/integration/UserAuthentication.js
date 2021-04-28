const tls = require('tls');
const fs = require('fs');
const { assert } = require('chai');
const mocha = require('mocha');
require('dotenv').config({ path: './test/integration/test.env' });
const reqTypes = require('../../controllers/RequestTypes');
const serverEvents = require('../../controllers/ServerEvents');
const UserModelService = require('../../services/UserModelService');

const options = {
  ca: fs.readFileSync(process.env.CA_PATH),
  host: 'localhost',
  port: 8042,
  rejectUnauthorized: true,
  requestCert: true,
};

let socket;
const user = {
  fName: 'name',
  login: 'email@mail.com',
  password: 'passHash',
  userType: 'customer',
};

mocha.after(() => {
  socket.destroy();
});

mocha.describe('User authentication', () => {
  // TODO restructure tests
  it('Drop DB', (done) => {
    assert(process.env.DB_ADDR !== 'mongodb://localhost:27017/routes');
    require('../../loaders/MongooseLoader').then((mongoose) => {
      const userModelService = new UserModelService(mongoose);
      const { users } = mongoose.connection.collections;
      if (users) {
        users.drop((err) => {
          if (err) { throw err; } else {
            console.log('collection dropped');
          }
        });
      }
    }).finally(() => done());
  });

  it('Connect to server', (done) => {
    socket = tls.connect(options, () => {
      socket.setEncoding('utf8');
      if (socket.authorized) {
        done();
      } else {
        done('connection unauthorized');
      }
    });
  });

  it('Add new user', (done) => {
    const request = {
      id: Math.round(Math.random() * 1e6),
      type: reqTypes.NEW_USER,
      body: user,
    };

    socket.once('data', (data) => {
      const obj = JSON.parse(data);
      if (obj.event !== serverEvents.response) {
        done('wrong response event');
      } else if (obj.message.success !== true) {
        done('unable to create user');
      } else {
        done();
      }
    });
    socket.write(JSON.stringify(request));
  });

  it('Connection closed after user creation', (done) => {
    socket.once('data', (data) => {
      const obj = JSON.parse(data);
      assert.equal(obj.event, serverEvents.socketClosed);
      socket.destroy();

      socket = tls.connect(options, () => {
        socket.setEncoding('utf8');
      });

      done();
    });
  });

  it('current user should not be authenticated', (done) => {
    const request = {
      id: Math.round(Math.random() * 1e6),
      type: reqTypes.CURRENT_USER,
      body: {},
    };

    socket.once('data', (data) => {
      const obj = JSON.parse(data);
      assert.equal(obj.event, serverEvents.error);
      assert.equal(obj.message.message, 'User unauthorized');
      done();
    });
    socket.write(JSON.stringify(request));
  });

  it('Authenticate as this user', (done) => {
    const request = {
      id: Math.round(Math.random() * 1e6),
      type: reqTypes.AUTH_USER,
      body: {
        login: user.login,
        password: user.password,
      },
    };

    socket.once('data', (data) => {
      const obj = JSON.parse(data);
      assert(obj.message.success);
      done();
    });
    socket.write(JSON.stringify(request));
  });

  it('Current authenticated user got', (done) => {
    const request = {
      id: Math.round(Math.random() * 1e6),
      type: reqTypes.CURRENT_USER,
      body: {},
    };

    socket.once('data', (data) => {
      const obj = JSON.parse(data);
      assert.equal(obj.message.user.login, user.login);
      done();
    });
    socket.write(JSON.stringify(request));
  });
});
