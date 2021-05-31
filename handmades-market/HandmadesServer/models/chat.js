const types = require('../controllers/RequestTypes').CHAT_TYPES;
const message = require('./message');

module.exports = (() => {
  let model;
  return (mongoose) => {
    const messageSchema = message(mongoose).schema;
    const schema = new mongoose.Schema({
      users: [{ type: mongoose.ObjectId }],
      type: {
        type: String,
        enum: Object.values(types),
      },
      recent: {
        message: messageSchema,
        user: mongoose.ObjectId,
      },
    });

    if (model !== undefined) return model;
    /* eslint-disable new-cap */
    model = new mongoose.model('Chat', schema);
    /* eslint-enable new-cap */
    return model;
  };
})();
