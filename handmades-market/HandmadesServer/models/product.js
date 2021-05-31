module.exports = (() => {
  let model;
  return (mongoose) => {
    const schema = new mongoose.Schema({
      marketId: {
        type: mongoose.ObjectId,
        required: true,
      },
      code: {
        unique: true,
        required: true,
        type: String,
      },
      name: {
        type: String,
        required: true,
      },
      description: {
        type: String,
        required: true,
      },
      price: {
        type: Number,
        required: true,
      },
      quantity: {
        type: Number,
        required: true,
        validate: {
          validator: (v) => v >= 0,
          message: () => 'quantity must not be negative',
        },
      },
      photoUrls: [String],
      chatId: {
        type: mongoose.ObjectId,
      },
      tag: String,
    });

    if (model !== undefined) return model;
    /* eslint-disable new-cap */
    model = new mongoose.model('Product', schema);
    /* eslint-enable new-cap */
    return model;
  };
})();
