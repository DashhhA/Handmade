module.exports = (() => {
  let model;
  return (mongoose) => {
    const schema = new mongoose.Schema({
      time: {
        type: Date,
        required: true,
      },
      from: {
        type: mongoose.ObjectId,
        required: true,
      },
      chat: {
        type: mongoose.ObjectId,
        required: true,
      },
      body: {
        type: String,
        required: true,
      },
      read: {
        type: Boolean,
        required: true,
      },
      deleted: String,
    });

    if (model !== undefined) return model;
    /* eslint-disable new-cap */
    model = new mongoose.model('Message', schema);
    /* eslint-enable new-cap */
    return model;
  };
})();
