/* eslint-disable max-classes-per-file */
const { EventEmitter } = require('events');
const MongooseService = require('./MongooseService');
const chatModel = require('../models/chat');
const reqTypes = require('../controllers/RequestTypes');

class PrivateChatError extends Error { }

/**
 * @description Returns a function, to check if user is in this chat
 * @param {Chat} chat Chat
 * @return {(chat: Chat) => boolean} Function, checking if user is in this chat
 */
function selectCheckUserByChat(chat, userId) {
  switch (chat.type) {
    case reqTypes.CHAT_TYPES.comments:
      return () => true;
    case reqTypes.CHAT_TYPES.private:
      return (c) => c.users.includes(userId);
    default:
      // TODO: unreachable
      return () => false;
  }
}

class MessageStreamWrapper extends EventEmitter {
  constructor(messageStream, chatStream, checkUsers) {
    super();
    this.messageStream = messageStream;
    this.chatStream = chatStream;

    messageStream.on('close', () => { this.emit('close'); });
    messageStream.on('end', () => { this.emit('end'); });
    messageStream.on('error', (err) => { this.emit('error', err); });
    messageStream.on('change', (change) => { this.emit('change', change); });

    chatStream.on('change', (change) => {
      if (change.operationType === 'delete') {
        this.emit('error', new PrivateChatError('chat removed'));
        this.close();
      }
      if (change.operationType === 'update') {
        const chat = change.fullDocument;
        if (!checkUsers(chat)) {
          this.emit('error', new PrivateChatError('User does not more fit this chat requirements'));
          this.close();
        }
      }
    });
  }

  close() {
    this.messageStream.close();
    this.chatStream.close();
  }
}

/**
 * @description Controlls Chat model
 */
class ChatService extends MongooseService {
  constructor(mongoose, userModelService, messagesService) {
    super(chatModel(mongoose));
    this.users = userModelService;
    this.messages = messagesService;
  }

  /**
   * @description Creates new Chat instance in DB
   * @param {string} type chat type
   * @param {Array} users Array of user logins, added to this chat
   * @param {ObjectId | undefined} userId Id of user, who created the chat
   * @throws PrivateChatError
   */
  async save(type, users, userId) {
    /* eslint-disable no-underscore-dangle */
    const promises = users.map(async (login) => {
      const user = await this.users.getByLogin(login);
      return user._id;
    });
    /* eslint-enable no-underscore-dangle */

    let ids = await Promise.all(promises);
    if (type === reqTypes.CHAT_TYPES.private) {
      ids = ids.concat(userId);
      // Only one private chat between two same users possible
      const res = await this.model.findOne({
        $and: [
          { type: reqTypes.CHAT_TYPES.private },
          {
            $or: [
              { users: { $all: ids } },
              { users: { $all: ids.reverse() } },
            ],
          },
        ],
      });

      if (res !== null) {
        throw new PrivateChatError('Only one private chat between two same users possible');
      }
    }
    const chat = {
      users: ids,
      type,
    };

    const saved = await this.trySave(chat);
    return saved;
  }

  /**
   * Provides current chats, containing geven user
   * @param {ObjectId} id User id for search
   * @return {ChangeStream, Array[Chat]}
   */
  async watchUserChats(id) {
    const chats = await this.model.find({ users: id });
    const pipeline = [
      {
        $match: {
          'fullDocument.users': id,
        },
      }];
    const changeStream = this.model.watch(pipeline);

    return { changeStream, chats };
  }

  /**
   * @description Provides current state of messages in this chat
   * @param {ObjectId} chatId Id of the chat
   * @param {ObjectId} userId Id of user, attempting to watch
   * @return {ChangeStream, messages}
   * @throws PrivateChatError when user is not in the chat, or no soch chat
   */
  async watchMessages(chatId, userId) {
    const user = await this.users.getById(userId);
    if (user === null) {
      throw new PrivateChatError('no soch user');
    }
    const { changeStream: chatStream, chat } = await this.watch(chatId);
    if (chat === null) {
      throw new PrivateChatError('no soch chat');
    }
    const {
      changeStream: messagesStream, messages,
    } = await this.messages.watchMessagesByChat(chatId);
    const checkUsers = selectCheckUserByChat(chat, userId);
    if (!checkUsers(chat)) {
      messagesStream.close();
      chatStream.close();
      throw new PrivateChatError('User cannot access this chat');
    }
    const changeStream = new MessageStreamWrapper(messagesStream, chatStream, checkUsers);

    return { changeStream, messages };
  }

  /**
   * @description Provides current state of the model to client
   * @param {ObjectId} id Id of the chat in the DB
   * @return {ChangeStream | null, Chat | null} change stream for events
   * on chat or null if no soch chat, and the chat to send current
   * state
   */
  async watch(id) {
    const chat = await this.model.findById(id);

    if (chat === null) return { changeStream: null, chat };

    // filter out requested customer
    const pipeline = [
      {
        $match: {
          'documentKey._id': id,
        },
      }];
    const options = { fullDocument: 'updateLookup' };
    const changeStream = this.model.watch(pipeline, options);
    return { changeStream, chat };
  }

  /**
   * @description Adds new message to DB
   * @param {Object} descr description of the message
   * @return {Message} Saved message from DB
   * @throws {PrivateChatError} when cannot add message for some reason
   */
  async newMessage(descr) {
    const chat = await this.getById(descr.chat);
    if (chat === null) {
      throw new PrivateChatError('No soch chat');
    }
    const user = await this.users.getById(descr.from);
    if (user === null) {
      throw new PrivateChatError('No soch user');
    }
    const message = await this.messages.trySave(descr);
    return message;
  }
}

/* eslint-enable max-classes-per-file */

module.exports = {
  ChatService,
  PrivateChatError,
};
