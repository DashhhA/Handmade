/**
 * @fileoverview Start web server, mongoose, etc.
 */
const tls = require('tls');
const fs = require('fs');
const events = require('events');
const emitterEvents = require('./utils/events');
const SocketAuth = require('./controllers/SocketAuth');
const filesService = require('./services/FilesService');
const newUserService = require('./services/NewUserService');
const authService = require('./services/UserAuthService');
const ResponseHandler = require('./controllers/ResponseHandler');
const mongooseLoader = require('./loaders/MongooseLoader');
const UserModelService = require('./services/UserModelService');
const authorizedUserService = require('./services/AuthorizedUserService');

const globalEvents = new events.EventEmitter();

(async () => {
  // connect to database and get services for models
  const mongoose = await mongooseLoader;
  const userModelService = new UserModelService(mongoose);

  /**
    * @property {Buffer} options.key  - Key, with which server certificate was signed
    * @property {Buffer} options.cert - Server certificate
    */
  const options = {
    key: fs.readFileSync(process.env.KEY_PATH),
    cert: fs.readFileSync(process.env.CERT_PATH),

    requestCert: false,
    rejectUnauthorized: true,
  };

  /**
   * Server.
   * Server opens tls web socket for each incoming connection
   */
  const server = tls.createServer(options, (socket) => {
    globalEvents.emit(emitterEvents.EVENT_SOCKET_NEW_CONNECTION, socket);
  });
  const files = tls.createServer(options, (socket) => {
    filesService(socket);
  });

  const port = 8042 || process.env.PORT;
  // bind server to port
  server.listen(port, () => {
    console.log(`server bound [${port}]`);
  });
  const filesPort = 8043 || process.env.FILES_PORT;
  files.listen(filesPort, () => {
    console.log(`files server bound [${filesPort}]`);
  });

  globalEvents.on(emitterEvents.EVENT_SOCKET_NEW_CONNECTION, (socket) => {
    const localEvents = new events.EventEmitter();
    new SocketAuth(socket, globalEvents, localEvents);
    new ResponseHandler(socket, localEvents);
  });

  // init services
  newUserService(globalEvents, userModelService);
  authService(globalEvents, userModelService);
  authorizedUserService(globalEvents, userModelService);
})();
