const MongooseService = require('./MongooseService');
const messageModel = require('../models/message');
/**
 * @description Controlls Customer model
 */
class MessageService extends MongooseService {
  constructor(mongoose) {
    super(messageModel(mongoose));
  }

  /**
   * Provides ChangeStream for messages, corresponding to chat
   * @param {ObjectId} chatId Id of the chat, containing messages
   * @return {ChangeStream, Array} Change stream and current state
   */
  async watchMessagesByChat(chatId) {
    const messages = await this.model.find({ chat: chatId });
    const pipeline = [
      {
        $match: {
          'fullDocument.chat': chatId,
        },
      }];
    const options = { fullDocument: 'updateLookup' };

    const changeStream = this.model.watch(pipeline, options);

    return { changeStream, messages };
  }
}

module.exports = MessageService;
