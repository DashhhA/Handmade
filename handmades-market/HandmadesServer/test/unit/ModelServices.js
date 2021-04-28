const { assert } = require('chai');
const mocha = require('mocha');
const path = require('path');
const marketStatuses = require('../../utils/MarketStatus');
const orderStatus = require('../../utils/OrderStatus');
const ProductService = require('../../services/ProductService');
const getModelService = require('../../services/ModelService');
// assuming, test is runned from project root
require('dotenv').config({ path: path.resolve(process.cwd(), 'test/unit/test.env') });

let mongoose;
let products;
let modelService;

mocha.before(async () => {
  mongoose = await require('../../loaders/MongooseLoader');
  products = new ProductService(mongoose);
});

mocha.after(() => {
  mongoose.connection.close();
});

it('drop DB', async () => {
  require('../../models/market')(mongoose).init();
  require('../../models/vendor')(mongoose).init();
  require('../../models/customer')(mongoose).init();
  modelService = getModelService(mongoose);
  await mongoose.models.Product.deleteMany();
  await mongoose.models.Market.deleteMany();
  await mongoose.models.Vendor.deleteMany();
  await mongoose.models.User.deleteMany();
  await mongoose.models.Customer.deleteMany();
  await mongoose.models.Order.deleteMany();
});

let vendor1;
let vendor2;
let market1_v1;
let market2_v1;
let market1_v2;
let product1_m1_v1;
let product2_m1_v1;
let product1_m2_v1;
let product1_m1_v2;
let product2_m1_v2;

mocha.describe('ProductService', () => {
  it('Save product to db', async () => {
    const saved = await products.save(
      { _id: mongoose.Types.ObjectId() },
      'T',
      'De',
      2,
    );
    assert.equal(saved.code.length, 8);
  });

  it('Get oproducts with vendors', async () => {
    await mongoose.models.Product.deleteMany();
    const v1Id = mongoose.Types.ObjectId();
    const v2Id = mongoose.Types.ObjectId();
    const m1v1Id = mongoose.Types.ObjectId();
    const m2v1Id = mongoose.Types.ObjectId();
    const m1v2Id = mongoose.Types.ObjectId();
    //products
    product1_m1_v1 = await mongoose.models.Product({
      marketId: m1v1Id,
      code: 'code_p1_m1_v1',
      title: 'title_p1_m1_v1',
      description: 'Des',
      quantity: 10,
    }).save();
    product2_m1_v1 = await mongoose.models.Product({
      marketId: m1v1Id,
      code: 'code_p2_m1_v1',
      title: 'title_p2_m1_v1',
      description: 'Des',
      quantity: 0,
    }).save();
    product1_m2_v1 = await mongoose.models.Product({
      marketId: m2v1Id,
      code: 'code_p1_m2_v1',
      title: 'title_p1_m2_v1',
      description: 'Des',
      quantity: 2,
    }).save();
    product1_m1_v2 = await mongoose.models.Product({
      marketId: m1v2Id,
      code: 'code_p1_m1_v2',
      title: 'title_p1_m1_v2',
      description: 'Des',
      quantity: 1,
    }).save();
    product2_m1_v2 = await mongoose.models.Product({
      marketId: m1v2Id,
      code: 'code_p2_m1_v2',
      title: 'title_p2_m1_v2',
      description: 'Des',
      quantity: 4,
    }).save();
    //markets
    market1_v1 = await mongoose.models.Market({
      _id: m1v1Id,
      vendorId: v1Id,
      products: [product1_m1_v1._id, product2_m1_v1._id],
      name: 'name_m1_v1',
      description: 'des',
      status: marketStatuses.approved,
    }).save();
    market2_v1 = await mongoose.models.Market({
      _id: m2v1Id,
      vendorId: v1Id,
      products: [product1_m2_v1._id],
      name: 'name_m2_v1',
      description: 'des',
      status: marketStatuses.approved,
    }).save();
    market1_v2 = await mongoose.models.Market({
      _id: m1v2Id,
      vendorId: v2Id,
      products: [product1_m1_v2._id, product2_m1_v2._id],
      name: 'name_m1_v2',
      description: 'des',
      status: marketStatuses.approved,
    }).save();
    // vendors
    vendor1 = await mongoose.models.Vendor({
      _id: v1Id,
      userId: mongoose.Types.ObjectId(),
      orders: [],
      markets: [m1v1Id, m2v1Id],
    }).save();
    vendor2 = await mongoose.models.Vendor({
      _id: v2Id,
      userId: mongoose.Types.ObjectId(),
      orders: [],
      markets: [m1v2Id],
    }).save();

    const q1 = await products.getWithVendor([product1_m1_v1.code, product1_m2_v1.code]);
    const p1_m1_v1 = q1.find((el) => el.code === product1_m1_v1.code);
    const p1_m2_v1 = q1.find((el) => el.code === product1_m2_v1.code);
    assert.exists(p1_m1_v1);
    assert.deepEqual(p1_m1_v1.marketId, market1_v1._id);
    assert.deepEqual(p1_m1_v1.vendor._id, vendor1._id);
    assert.exists(p1_m2_v1);
    assert.deepEqual(p1_m2_v1.marketId, market2_v1._id);
    assert.deepEqual(p1_m2_v1.vendor._id, vendor1._id);
  });

  it('Get oproducts with vendors [1]', async () => {
    const q = await products.getWithVendor([product1_m1_v1.code, product2_m1_v2.code]);
    const p1_m1_v1 = q.find((el) => el.code === product1_m1_v1.code);
    const p2_m1_v2 = q.find((el) => el.code === product2_m1_v2.code);
    assert.exists(p1_m1_v1);
    assert.deepEqual(p1_m1_v1.marketId, market1_v1._id);
    assert.deepEqual(p1_m1_v1.vendor._id, vendor1._id);
    assert.exists(p2_m1_v2);
    assert.deepEqual(p2_m1_v2.marketId, market1_v2._id);
    assert.deepEqual(p2_m1_v2.vendor._id, vendor2._id);
  });
});

mocha.describe('Order service', () => {
  let user;
  it('Make order', async () => {
    const modelId = mongoose.Types.ObjectId();
    user = await mongoose.models.User({
      fName: 'fName',
      login: 'login',
      password: {
        data: 'data',
        salt: 'salt',
      },
      userType: 'customer',
      modelId,
    }).save();
    await mongoose.models.Customer({
      _id: modelId,
      userId: user._id,
    }).save();
    const order = await modelService.newOrder(user._id, [
      { code: product1_m1_v1.code, quantity: 1 },
      { code: product1_m2_v1.code, quantity: 1 },
    ]);
    assert.deepEqual(order.customerId, user._id);
    assert.equal(order.products.length, 2);
    assert.equal(order.status, orderStatus.posted);
  });

  it('Order with products with quantity more than in stock should not be accepted', (done) => {
    modelService.newOrder(user._id, [
      { code: product2_m1_v1.code, quantity: 1 },
      { code: product1_m2_v1.code, quantity: 1 },
    ]).then(
      () => done('Expected to throw error'),
      (err) => { try {
        assert.equal(err.message, 'Requested products (codes [code_p2_m1_v1]) more than in stock (1 > 0)');
        done();
      } catch (e) { done(e); } },
    );
  });

  it('Order with duplicate products should not be accepted', (done) => {
    modelService.newOrder(user._id, [
      { code: product2_m1_v1.code, quantity: 1 },
      { code: product2_m1_v1.code, quantity: 1 },
    ]).then(
      () => done('Expected to throw error'),
      (err) => { try {
        assert.equal(err.message, 'Order must not have duplicate products');
        done();
      } catch (e) { done(e); } },
    );
  });

  it('Order with unexisting products should not be accepted', (done) => {
    modelService.newOrder(user._id, [
      { code: product2_m1_v1.code, quantity: 1 },
      { code: 'wring 1', quantity: 1 },
      { code: 'wring 2', quantity: 1 },
    ]).then(
      () => done('Expected to throw error'),
      (err) => { try {
        assert.equal(err.message, 'No products with code: wring 1,wring 2');
        done();
      } catch (e) { done(e); } },
    );
  });

  it('Order products must be from same vendor', (done) => {
    modelService.newOrder(user._id, [
      { code: product2_m1_v1.code, quantity: 1 },
      { code: product1_m1_v2.code, quantity: 1 },
    ]).then(
      () => done('Expected to throw error'),
      (err) => { try {
        assert.equal(err.message, 'Products must be from same vendor');
        done();
      } catch (e) { done(e); } },
    );
  });
});
