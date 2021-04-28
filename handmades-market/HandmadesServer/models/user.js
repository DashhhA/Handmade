const userTypes = require('../utils/userTypes');

module.exports = (() => {
  let model;
  return (mongoose) => {
    const schema = new mongoose.Schema({
      fName: {
        type: String,
        required: true,
      },
      sName: {
        type: String,
      },
      surName: {
        type: String,
      },
      login: {
        type: String,
        unique: true,
        required: true,
      },
      password: {
        data: {
          type: String,
          required: true,
        },
        salt: {
          type: String,
          required: true,
        },
      },
      userType: {
        type: String,
        required: true,
        enum: Object.values(userTypes),
      },
      modelId: {
        type: mongoose.ObjectId,
        required: true,
      },
    });
    if (model !== undefined) return model;
    /* eslint-disable new-cap */
    model = new mongoose.model('User', schema);
    /* eslint-enable new-cap */
    return model;
  };
})();
