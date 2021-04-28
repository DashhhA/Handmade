/* eslint-disable max-classes-per-file */
const { EventEmitter } = require('events');
const mongoose = require('mongoose');
/**
 * @description returns object valuse by the parh of properties
 */

function getPath(obj, props) {
  const keys = props.split('.');
  let ans = obj;
  for (let i = 0; i < keys.length; i++) {
    const key = keys[i];
    if (ans === undefined) return undefined;
    ans = ans[key];
  }
  return ans;
}

class ChangeStreamWrapper extends EventEmitter {
  constructor(changeStream, path) {
    super();
    this.changeStream = changeStream;
    const { props } = path;

    changeStream.on('close', () => { this.emit('close'); });
    changeStream.on('end', () => { this.emit('end'); });
    changeStream.on('error', (err) => { this.emit('error', err); });
    changeStream.on('change', (change) => {
      if (change.operationType === 'update') {
        const { updatedFields } = change.updateDescription;
        const updKey = Object.keys(updatedFields).find((key) => key.includes(props));
        if (updKey === undefined) return;
        const mChange = { ...change };
        if (Array.isArray(updatedFields[updKey])) {
          mChange.operationType = 'refresh';
          mChange.fullDocument = updatedFields[updKey];
        } else {
          mChange.operationType = 'insert';
          mChange.fullDocument = updatedFields[updKey];
        }
        this.emit('change', mChange);
      }
      if (change.operationType === 'delete') {
        // TODO
      }
    });
  }

  close() {
    this.changeStream.close();
  }
}

class CollectionStreamWrapper extends EventEmitter {
  constructor(changeStream) {
    super();
    this.changeStream = changeStream;

    changeStream.on('close', () => { this.emit('close'); });
    changeStream.on('end', () => { this.emit('end'); });
    changeStream.on('error', (err) => { this.emit('error', err); });
    changeStream.on('change', async (change) => {
      if (change.operationType === 'insert') {
        change.fullDocument.dbId = change.fullDocument._id;
        this.emit('change', change);
      }
      if (change.operationType === 'delete') {
        change.fullDocument = { dbId: change.documentKey._id };
        this.emit('change', change);
      }
    });
  }

  close() {
    this.changeStream.close();
  }
}

class MongooseService {
  constructor(model) {
    this.model = model;
    model.init();
  }

  /**
   * @description Returns id of the created object or throws error
   * @return {Object} The created object
   */
  async trySave(descr) {
    const obj = this.model(descr);
    const saved = await obj.save(obj);
    return saved;
  }

  /**
   * @param {ObjectId} id Model id
   * @return {Model | null} Model or null if can't find
   */
  async getById(id) {
    const model = await this.model.findById(id);
    return model;
  }

  async removeById(id) {
    const deleted = await this.model.deleteOne({ _id: id });
    return deleted.deletedCount > 0;
  }

  /**
   * @description Puts a watcher on this collection or an array in some
   * document in it
   * @param {object | undefined} path Path to document an property, containing
   * array, or undefined if all collection is watched
   * @return {ChangeStream | null, list | null} ChangeStream for the requested
   * list and it's current state, or nulls if no soch path
   */
  async watchList(path) {
    if (path === undefined) {
      const currentState = await this.model.find();
      const options = { fullDocument: 'updateLookup' };
      const changeStream = this.model.watch([], options);
      return {
        changeStream: new CollectionStreamWrapper(changeStream),
        list: currentState.map((el) => ({ ...el.toObject(), dbId: el._id })),
      };
    }
    const { id, props } = path;
    const doc = await this.model.findOne({ _id: id });
    const currentState = doc === null ? null : getPath(doc, props);
    if (currentState === null || currentState === undefined) {
      return { changeStream: null, list: null };
    }

    const pipeline = [{ $match: { 'documentKey._id': mongoose.Types.ObjectId(id) } }];
    const changeStream = this.model.watch(pipeline);

    return {
      changeStream: new ChangeStreamWrapper(changeStream, path),
      list: currentState,
    };
  }

  /**
   * @description Updates element by id
   * @param {ObjectId} id Id of the element to update
   * @param {Object} update Update
   */
  async update(id, update) {
    await this.model.updateOne(
      { _id: id },
      { $set: update },
    );
  }
}
/* eslint-enable max-classes-per-file */

module.exports = MongooseService;
