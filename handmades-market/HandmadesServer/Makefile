unit:
	./node_modules/mocha/bin/mocha test/unit/*.js

integration:
	./node_modules/mocha/bin/mocha test/integration/*.js & \
	node -r dotenv/config server.js dotenv_config_path=test/integration/test.env

.PHONY: unit
.PHONY: integration
