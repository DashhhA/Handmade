const status = require('../utils/MarketStatus');

module.exports = (() => {
  let model;
  return (mongoose) => {
    const schema = new mongoose.Schema({
      vendorId: {
        type: mongoose.ObjectId,
        required: true,
      },
      products: [{
        type: mongoose.ObjectId,
      }],
      name: {
        type: String,
        required: true,
      },
      city: {
        type: String,
        required: true,
      },
      description: {
        type: String,
        required: true,
      },
      status: {
        type: String,
        required: true,
        enum: Object.values(status),
      },
      imageUrl: String,
      tags: [String],
    });

    if (model !== undefined) return model;
    /* eslint-disable new-cap */
    model = new mongoose.model('Market', schema);
    /* eslint-enable new-cap */
    return model;
  };
})();
