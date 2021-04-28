/**
 * @fileoverview Reuest types, supported by server
 */

module.exports = {
  NEW_USER: 'new_user',
  AUTH_USER: 'auth_user',
  LOGOUT: 'logout',
  REMOVE_USER: 'remove_user',
  WATCH_MODEL: 'watch_model',
  WATCH_LIST: 'watch_list',
  UNWATCH_MODEL: 'unwatch_model',
  CURRENT_USER: 'current',
  WATCH_CHAT: 'watch_chat',
  WATCH_CHATS: 'watch_chats',
  NEW_CHAT: 'new_chat',
  MESSAGE: 'message',
  MAKE_PURCHASE: 'make_purchase',
  ADD_MARKET: 'add_market',
  ADD_PRODUCT: 'add_product',
  EDIT_ITEM: 'edit_item',
  MODEL_TYPES: {
    user: 'user',
    customer: 'customer',
    vendor: 'vendor',
    order: 'order',
    market: 'market',
    product: 'product',
  },
  CHAT_TYPES: {
    private: 'private',
    comments: 'comments',
  }
};
