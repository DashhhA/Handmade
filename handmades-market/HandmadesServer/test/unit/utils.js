const { assert } = require('chai');
const mocha = require('mocha');
const utils = require('../../utils/util');
const modelTypes = require('../../controllers/RequestTypes').MODEL_TYPES;

mocha.describe('Utils', () => {
  it('genId should return session-unique identifiers', () => {
    const id1 = utils.genId();
    const id2 = utils.genId();
    const id3 = utils.genId();

    assert(id1 !== id2 && id2 !== id3 && id1 !== id3);
  });

  it('parseJsonAsync should parse correct json', () => {
    const jsonPromise = utils.parseJsonAsync('{"foo": 1}');
    jsonPromise.then(
      (parsed) => assert(parsed === { foo: 1 }),
      () => assert(false),
    );
  });

  it('parseJsonAsync should reject incorrect json', () => {
    const jsonPromise = utils.parseJsonAsync('{foo: 1}');
    jsonPromise.then(() => assert(false), () => assert(true));
  });

  mocha.describe('User update permission', () => {
    const availableTypes = Object.values(modelTypes)
      .filter((v) => ![modelTypes.user, modelTypes.order, modelTypes.market, modelTypes.product].includes(v));
    availableTypes.forEach((type) => {
      it(`Login update should be permitted for ${type}`, () => {
        const user = {
          userType: type,
        };
        const update = { login: 'new@login' };

        assert.isTrue(utils.isUpdPermitted(user, update, 'user'));
      });
      it(`Password update should be permitted for ${type}`, () => {
        const user = {
          userType: type,
        };
        const update = { password: { data: 'data', salt: 'n_salt' } };

        assert.isTrue(utils.isUpdPermitted(user, update, 'user'));
      });
    });

    it('Customer should be able to update order status', () => {
      const user = {
        userType: modelTypes.customer,
      };
      const update = { status: 'new_st' };

      assert.isTrue(utils.isUpdPermitted(user, update, 'order'));
    });

    it('Vendor should be able to update order status', () => {
      const user = {
        userType: modelTypes.vendor,
      };
      const update = { status: 'new_st' };

      assert.isTrue(utils.isUpdPermitted(user, update, 'order'));
    });

    it('Vendor should be able to update market name', () => {
      const user = {
        userType: modelTypes.vendor,
      };
      const update = { name: 'new_nm' };

      assert.isTrue(utils.isUpdPermitted(user, update, 'market'));
    });

    it('Customer should not be able to update market', () => {
      const user = {
        userType: modelTypes.customer,
      };
      const update = { name: 'new_nm' };

      assert.isFalse(utils.isUpdPermitted(user, update, 'market'));
    });

    it('Customer should not be able to update order vendorId', () => {
      const user = {
        userType: modelTypes.customer,
      };
      const update = { vendorId: 'new_id' };

      assert.isFalse(utils.isUpdPermitted(user, update, 'order'));
    });

    it('Two correct updates should pass', () => {
      const user = {
        userType: modelTypes.vendor,
      };
      const update = { name: 'nn', description: 'dd' };

      assert.isTrue(utils.isUpdPermitted(user, update, 'market'));
    });

    it('Two correct updates should pass[1]', () => {
      const user = {
        userType: modelTypes.vendor,
      };
      const update = { fName: 'nf', sName: 'nS' };

      assert.isTrue(utils.isUpdPermitted(user, update, 'user'));
    });

    it('Should not pass if one fifie is is not permitted', () => {
      const user = {
        userType: modelTypes.vendor,
      };
      const update = { fName: 'nf', sName: 'nS', userType: 't' };

      assert.isFalse(utils.isUpdPermitted(user, update, 'user'));
    });
  });
});
