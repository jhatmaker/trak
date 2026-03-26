# Trak backend — SAM build targets
# Called by `sam build` for resources that use BuildMethod: makefile

# SharedLayer: copies shared/ into the layer structure so files are accessible
# at /opt/nodejs/shared/... matching all function require() paths.
# Installs @anthropic-ai/sdk (not in Lambda runtime) into the layer.
build-SharedLayer:
	mkdir -p $(ARTIFACTS_DIR)/nodejs
	cp -r shared $(ARTIFACTS_DIR)/nodejs/
	cd $(ARTIFACTS_DIR)/nodejs/shared && npm install --omit=dev --no-audit --no-fund
