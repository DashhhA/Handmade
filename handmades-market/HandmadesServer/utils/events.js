/**
 * @fileoverview Events, thrown by different services to interact with each
 * other. Mostly handled in server.js
 */

// TODO refoactor comments
/**
 * @property events.EVENT_SOCKET_NEW_CONNECTION {string} Raised, when a use
 * connection occures
 * @property events.EVENT_NEW_USER {string} Raised, when new user registration
 * attempt comes to a socket
 * @property events.EVENT_AUTH_USER {string} Raised, when user authorization
 * request comes to a socket
 * @property events.EVENT_REJECT {string} Raised, when unable to satisfy request
 * @property {string} events.EVENT_USER_AUTHENTICATED Reaised on successfull
 * @property {string} events.EVENT_LOGOUT Raised, when user logging out
 * @property {string} events.EVENT_REMOVE_USER Raised, when User account removed
 * user auhentication
 * @property {string} events.EVENT_WATCH_USER Raised, on watch user request
 * @property {string} events.EVENT_WATCH_CHAT Raised, on watch messages on the chat
 * @property {string} events.EVENT_WATCH_CHATS Raised, on watch list of user chats
 * @property {string} events.EVENT_NEW_CHAT Raised, on create new chat
 * @property {string} events.Message Raised, on create new message
 * // todo
 */
module.exports = {
  EVENT_SOCKET_NEW_CONNECTION: 'socket_new_connection',
  EVENT_NEW_USER: 'new_user',
  EVENT_AUTH_USER: 'auth_user',
  EVENT_SAVE_MODEL: 'save_model',
  EVENT_REJECT: 'reject',
  EVENT_RESPONSE: 'respond',
  EVENT_DISCONNECT: 'disconnect',
  EVENT_USER_AUTHENTICATED: 'auth_success',
  EVENT_LOGOUT: 'logout',
  EVENT_REMOVE_USER: 'remove_user',
  EVENT_CURRENT: 'current',
  EVENT_WATCH: 'watch',
  EVENT_WATCH_LIST: 'watch_list',
  EVENT_UNWATCH: 'unwatch',
  EVENT_WATCH_CHAT: 'watch_chat',
  EVENT_WATCH_CHATS: 'watch_chats',
  EVENT_NEW_CHAT: 'new_chat',
  EVENT_MESSAGE: 'message',
  EVENT_PURCHASE: 'purchase',
  EVENT_ADD_MARKET: 'add_market',
  EVENT_ADD_PRODUCT: 'add_product',
  EVENT_EDIT_ITEM: 'edit_tem',
  EVENT_SOCKET_CLOSED: 'socket_closed',
};
