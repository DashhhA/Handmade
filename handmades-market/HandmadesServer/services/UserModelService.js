const events = require('events');
const MongooseService = require('./MongooseService');
const userModel = require('../models/user');

class UserModelService extends MongooseService {
  constructor(mongoose) {
    super(userModel(mongoose));
    this.conn = mongoose;
  }

  getByLogin(login) {
    return new Promise((resolve, reject) => {
      this.model.findOne({ login }, (err, user) => {
        if (err) reject(err);
        else resolve(user);
      });
    });
  }

  async removeByLogin(login) {
    await this.model.deleteOne({ login });
  }

  /**
   * @param login of the user to watch
   * @return {ChangeStream | null, User | null} change stream for events on user or null
   * if no soch user, and tehe user to send current state
   */
  async watch(login) {
    // get required user id
    let user = (await this.model.findOne({ login }));
    if (user === null) user = await this.model.findById(login);
    if (user === null) return { changeStream: null, user };
    // filter out requested user
    const pipeline = [{ $match: { 'documentKey._id': user._id } }];
    const options = { fullDocument: 'updateLookup' };
    const changeStream = this.model.watch(pipeline, options);
    return { changeStream, user };
  }
}

module.exports = UserModelService;
