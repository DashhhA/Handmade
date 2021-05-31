const { EventEmitter } = require('events');
const MongooseService = require('./MongooseService');
const messageModel = require('../models/message');
const chatTypes = require('../controllers/RequestTypes').CHAT_TYPES;
/**
 * @description Controlls Customer model
 */

class WithChatsStream extends EventEmitter {
  constructor(changeStream, mongoose) {
    super();
    this.changeStream = changeStream;

    changeStream.on('close', () => { this.emit('close'); });
    changeStream.on('end', () => { this.emit('end'); });
    changeStream.on('error', (err) => { this.emit('error', err); });
    changeStream.on('change', async (change) => {
      if (change.operationType === 'delete') {
        change.fullDocument = { dbId: change.documentKey._id };
        this.emit('change', change);
      } else {
        const chatId = change.fullDocument.chat;
        const chat = await mongoose.models.Chat.findById(chatId);
        if (chat === null || chat === undefined) return;
        if (chat.type === chatTypes.comments) {
          this.emit('change', change);
        }
      }
    });
  }

  close() {
    this.changeStream.close();
  }
}

class MessageService extends MongooseService {
  constructor(mongoose) {
    super(messageModel(mongoose));
    this.mongoose = mongoose;
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
      },
      {
        $addFields: {
          'fullDocument.dbId': '$fullDocument._id',
        },
      },
    ];
    const options = { fullDocument: 'updateLookup' };

    const changeStream = this.model.watch(pipeline, options);

    return { changeStream, messages };
  }

  /**
   * Provides ChangeStream for all messages in comments chats
   * @return {Promise<{messages: *, changeStream: WithChatsStream}>} Change stream and current state
   */
  async watchComments() {
    const messages = await this.model.aggregate([
      { $lookup: { from: 'chats', as: 'chats', localField: 'chat', foreignField: '_id' } },
      { $addFields: { chatFull: { $arrayElemAt: ['$chats', 0] } } },
      { $match: { 'chatFull.type': chatTypes.comments } },
      { $project: { chatFull: 0, chats: 0 } },
    ]);

    const pipeline = [
      { $addFields: { 'fullDocument.dbId': '$fullDocument._id' } },
    ];
    const options = { fullDocument: 'updateLookup' };

    const changeStream = this.model.watch(pipeline, options);

    const withChats = new WithChatsStream(changeStream, this.mongoose);

    return { changeStream: withChats, messages };
  }
}

module.exports = MessageService;
