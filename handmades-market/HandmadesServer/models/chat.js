const types = require('../controllers/RequestTypes').CHAT_TYPES;

module.exports = (() => {
  let model;
  return (mongoose) => {
    const schema = new mongoose.Schema({
      users: [{ type: mongoose.ObjectId }],
      type: {
        type: String,
        enum: Object.values(types),
      },
    });

    if (model !== undefined) return model;
    /* eslint-disable new-cap */
    model = new mongoose.model('Chat', schema);
    /* eslint-enable new-cap */
    return model;
  };
})();
