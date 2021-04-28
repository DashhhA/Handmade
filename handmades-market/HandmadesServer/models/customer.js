module.exports = (() => {
  let model;
  return (mongoose) => {
    const schema = new mongoose.Schema({
      userId: {
        type: mongoose.ObjectId,
        required: true,
      },
      orders: [{
        type: mongoose.ObjectId,
      }],
    });

    if (model !== undefined) return model;
    /* eslint-disable new-cap */
    model = new mongoose.model('Customer', schema);
    /* eslint-enable new-cap */
    return model;
  };
})();
