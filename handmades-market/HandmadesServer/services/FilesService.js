const fs = require('fs');
const logger = require('./Logger')(module);
const utils = require('../utils/util');
// TODO check files ownership and owerwrites, delete unused files

async function getFile(name) {
  const fd = await fs.promises.open(`files/${name}`, 'r');
  const res = await fd.readFile();
  fd.close();
  return res;
}

module.exports = (socket) => {
  let bytesToRead = 0;
  let dest = null;
  // different types of data are sent continuosely
  // name - first
  // file - second
  async function processRequest(data) {
    const req = await utils.parseJsonAsync(data);
    if (req.action === 'get') {
      const res = await getFile(req.name);
      socket.write(res.length.toString());
      await new Promise((resolve) => {
        socket.write(Buffer.from(res.buffer), () => {
          resolve();
        });
      });
    }
    if (req.action === 'save') {
      console.log(req);
      bytesToRead = req.size;
      dest = fs.createWriteStream(`files/${req.name}`);
    }
  }
  socket.on('data', (data) => {
    if (bytesToRead >= data.length) {
      if (dest !== null && dest !== undefined) dest.write(data);
      bytesToRead -= data.length;
      if (bytesToRead <= 0) {
        dest.destroy();
        dest = null;
        socket.write(Buffer.of(0));
      }
    } else {
      processRequest(data)
        .then(
          () => socket.write(Buffer.of(0)),
          (err) => socket.write(err.message),
        );
    }
  });

  socket.on('error', (err) => {
    logger.error('file socket error', err);
  });
}
