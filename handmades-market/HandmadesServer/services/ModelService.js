const CustomerService = require('./CustomerService');
const VendorService = require('./VendorService');
const UserModelService = require('./UserModelService');
const MessageService = require('./MessageService');
const ProductService = require('./ProductService');
const OrderService = require('./OrderService');
const MarketService = require('./MarketService');
const { ChatService } = require('./ChatService');
const { OrderException, MarketException, ProductException } = require('../utils/errors');
const userTypes = require('../utils/userTypes');
const modelTypes = require('../controllers/RequestTypes').MODEL_TYPES;
const chatTypes = require('../controllers/RequestTypes').CHAT_TYPES;
/**
 * @description Holds services for all models, used in the app, except the user
 * model, and controlls proper creation of modells, needed for some user type,
 * and managing this models.
 */

class ModelService {
  constructor(mongoose) {
    this.customers = new CustomerService(mongoose);
    this.vendors = new VendorService(mongoose);
    this.messages = new MessageService(mongoose);
    this.users = new UserModelService(mongoose);
    this.chats = new ChatService(mongoose, this.users, this.messages);
    this.products = new ProductService(mongoose);
    this.orders = new OrderService(mongoose);
    this.markets = new MarketService(mongoose);
  }

  /**
   * Selects service for model by name
   * @param {string} name Model name
   * @return Service for the model
   */
  selectService(name) {
    switch (name) {
      case modelTypes.customer:
        return this.customers;
      case modelTypes.vendor:
        return this.vendors;
      case modelTypes.user:
        return this.users;
      case 'order':
        return this.orders;
      case 'message':
        return this.messages;
      default:
        throw new Error(`No soch model "${name}"`);
        // TODO error, unreachable
    }
  }

  /**
   * @description Handles creation od additional models depending on the user
   * type
   * @param {User} user Created user
   */
  async newUser(user) {
    switch (user.userType) {
      case userTypes.customer:
        await this.customers.save(user);
        break;

      case userTypes.vendor:
        await this.vendors.save(user);
        break;

      default:
        // TODO error
        // unreachable!
    }
  }

  async removeUser(login) {
    const user = await this.users.getByLogin(login);
    if (user !== null) {
      await this.users.removeByLogin(login);
      switch (user.userType) {
        case userTypes.customer:
          await this.customers.removeById(user.modelId);
          break;

        case userTypes.vendor:
          await this.vendors.removeById(user.modelId);
          break;

        default:
          // TODO error
          // unreachable!
      }
    }
  }

  /**
   * @description Sends updates of customer state to client
   * @param {ObjectId} id Customer id in DB
   * @return {ChangeStream | null, Customer | null} change stream for events
   * on customer or null if no soch customer, and tehe customer to send current
   * state
   */
  watchCustomer(id) {
    return this.customers.watch(id);
  }

  /**
   * @description Watches vendor state
   * @param {ObjectId} id Vendor id in DB
   * @return {ChangeStream | null, Vendor | null} change stream for events
   * on vendor or null if no soch vendor, and the vendor to send current
   * state
   */
  watchVendor(id) {
    return this.vendors.watch(id);
  }

  /**
   * @description Watches order state
   * @param {ObjectId} id Order id in DB
   * @return {ChangeStream | null, Order | null} change stream for events
   * on order or null if no soch order, and the order to send current
   * state
   */
  watchOrder(id) {
    return this.orders.watch(id);
  }

  /**
   * @description Watches market state
   * @param {ObjectId} id Markst id in DB
   * @return {ChangeStream | null, Market | null} change stream for events
   * on market or null if no soch market, and the market to send current
   * state
   */
  watchMarket(id) {
    return this.markets.watch(id);
  }

  /**
   * @description Watches product state
   * @param {ObjectId} id Product id in DB
   * @return {ChangeStream | null, Product | null} change stream for events
   * on product or null if no soch product, and the product to send current
   * state
   */
  watchProduct(id) {
    return this.products.watch(id);
  }

  async newOrder(userId, products) {
    // check, that all products are from same vendor
    const codes2quant = new Map();
    products.forEach((el) => codes2quant.set(el.code, el.quantity));
    if (codes2quant.size < products.length) {
      throw new OrderException('Order must not have duplicate products');
    }
    const uniqueCodes = Array.from(codes2quant.keys());
    const dbProducts = await this.products.getWithVendor(uniqueCodes);
    const dbCodes = dbProducts.map((el) => el.code);
    const dbVendors = dbProducts.map((el) => el.vendor._id);
    if (dbCodes.length < codes2quant.size) {
      const missing = uniqueCodes.filter((el) => !dbCodes.includes(el));
      throw new OrderException(`No products with code: ${missing}`);
    }
    const sameVendors = dbVendors.every((el) => el.equals(dbVendors[0]));
    if (!sameVendors) {
      throw new OrderException('Products must be from same vendor');
    }
    const vendorId = dbVendors[0];
    const user = await this.users.getById(userId);
    if (user === null) {
      throw new OrderException('User not found');
    }
    const customer = await this.customers.getById(user.modelId);
    if (customer === null) {
      throw new OrderException('Customer not found');
    }
    const vendor = await this.vendors.getById(vendorId);
    if (vendor === null) {
      throw new OrderException('Vendor not found');
    }
    const productsToSave = dbProducts.map((el) => {
      if (el.quantity - codes2quant.get(el.code) < 0) {
        throw new OrderException(`Requested products (codes [${el.code}])`
          + ` more than in stock (${codes2quant.get(el.code)} > ${el.quantity})`);
      }
      return {
        product: el._id,
        quantity: codes2quant.get(el.code),
      };
    });

    const chat = await this.chats.save(chatTypes.comment, []);
    const saved = await this.orders.save(customer, vendor, chat, productsToSave);
    await this.customers.addOrder(customer._id, saved._id);
    await this.vendors.addOrder(vendorId, saved._id);
    return saved;
  }

  /**
   * Creates and saves new market
   * @param {ObjectId} userId of the user, created market
   * @param {string} name Name of the market
   * @param {string} description Market description
   * @param {string | undefined} imageUrl Location og the image
   */
  async newMarket(userId, name, description, imageUrl) {
    const user = await this.users.getById(userId);
    if (user === null) {
      throw new MarketException('No soch user');
    }
    const vendor = await this.vendors.getById(user.modelId);
    if (vendor === null) {
      throw new MarketException(`Vendor for user ${user.login} not found`);
    }
    const saved = await this.markets.save(vendor._id, name, description, imageUrl);
    await this.vendors.addMarket(vendor._id, saved._id);
    return saved;
  }

  /**
   * @description Creates and saves new product
   * @param {ObjectId} marketId Id of market to add product
   * @param {Object} descr Market description
   */
  async newProduct(marketId, descr) {
    const market = await this.markets.getById(marketId);
    if (market === null) {
      throw new ProductException('No soch market');
    }
    const description = {
      marketId: market._id,
      ...descr,
    };
    const product = await this.products.save(description);
    await this.markets.addProduct(market._id, product._id);
    return product;
  }

  /**
   * @description Creates new chat
   * @param {string} type chat type
   * @param {Array} users Array of user logins, added to this chat
   * @param {ObjectId | undefined} userId Id of user, who created the chat
   * @return Saved chat
   * @throws ChatService.PrivateChatError
   */
  async newChat(type, users, userId) {
    const chat = await this.chats.save(type, users, userId);
    return chat;
  }

  /**
   * Provides current chats, containing geven user
   * @param {ObjectId} id User id for search
   * @return {ChangeStream, Array[Chat]}
   */
  async watchUserChats(id) {
    const ans = await this.chats.watchUserChats(id);
    return ans;
  }

  /**
   * @description Provides current state of messages in chat
   * @param {ObjectId} chatId Id of the chat
   * @param {ObjectId} userId Id of user, attempting to watch
   * @return {ChangeStream, messages}
   * @throws ChatService.PrivateChatError when user is not in the chat, or no soch chat
   */
  async watchMessages(chatId, userId) {
    const ans = await this.chats.watchMessages(chatId, userId);
    return ans;
  }

  /**
   * @param {Date} time time message sent, UTC
   * @param {ObjectId} from Id of user, who sent the message
   * @param {ObjectId} chat Id of chat for this message
   * @param {string} body Message body
   * @throws {ChatService.PrivateChatError}
   */
  async newMessage(time, from, chat, body) {
    const descr = {
      time, from, chat, body,
    };
    const saved = await this.chats.newMessage(descr);
    return saved;
  }

  async watchList(modelType, path) {
    switch (modelType) {
      case modelTypes.customer: {
        const ans = await this.customers.watchList(path);
        return ans;
      }
      case modelTypes.vendor: {
        const ans = await this.vendors.watchList(path);
        return ans;
      }
      case modelTypes.market: {
        const ans = await this.markets.watchList(path);
        return ans;
      }
      default:
        // TODO warning to console. Unreachable, checked in
        // AuthorizedUserController.js
        return { changeStream: null, list: null };
    }
  }

  /**
   * Performs a "set" update on the collection
   * @param {string} collection Collection name
   * @param {ObjectId} id Id of item to update
   * @param {Object} update The update
   */
  async update(collection, id, update) {
    const service = this.selectService(collection);
    await service.update(id, update);
  }
}

// function enshures, that there is one instance of ModelService
module.exports = (() => {
  let service;

  return (mongoose) => {
    if (service !== undefined) return service;
    service = new ModelService(mongoose);
    return service;
  };
})();
