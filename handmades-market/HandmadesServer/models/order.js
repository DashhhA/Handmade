const status = require('../utils/OrderStatus');

module.exports = (() => {
  let model;
  return (mongoose) => {
    const schema = new mongoose.Schema({
      customerId: {
        type: mongoose.ObjectId,
        required: true,
      },
      vendorId: {
        type: mongoose.ObjectId,
        required: true,
      },
      products: [{
        product: {
          type: mongoose.ObjectId,
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
      }],
      status: {
        type: String,
        required: true,
        enum: Object.values(status),
      },
      chatId: {
        type: mongoose.ObjectId,
      },
    });

    if (model !== undefined) return model;
    /* eslint-disable new-cap */
    model = new mongoose.model('Order', schema);
    /* eslint-enable new-cap */
    return model;
  };
})();
