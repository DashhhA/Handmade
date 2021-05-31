const status = require('../utils/OrderStatus');
const paymentTypes = require('../controllers/RequestTypes').PAYMENT_TYPES;
const deliveryTypes = require('../controllers/RequestTypes').DELIVERY_TYPES;

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
      time: {
        type: Date,
        required: true,
      },
      address: {
        type: String,
        required: true,
      },
      paymentType: {
        type: String,
        required: true,
        enum: Object.values(paymentTypes),
      },
      deliveryType: {
        type: String,
        required: true,
        enum: Object.values(deliveryTypes),
      },
      packing: {
        checked: {
          type: Boolean,
          required: true,
        },
        price: Number,
      },
      urgent: {
        checked: {
          type: Boolean,
          required: true,
        },
        price: Number,
      },
      deliveryPrice: Number,
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
