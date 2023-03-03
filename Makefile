MAVEN_NANO_VERSION := true
include ./mk-include/cc-begin.mk
include ./mk-include/cc-maven.mk
include ./mk-include/cc-semver.mk
include ./mk-include/cc-end.mk

FLINK_VERSION = $(shell ./mvnw org.apache.maven.plugins:maven-help-plugin:3.1.0:evaluate -Dexpression=project.version | egrep '^[0-9]')


.PHONY: flink-deploy
flink-deploy:
ifeq ($(CI),true)
	@echo "VERSION FOUND: $(FLINK_VERSION)"
ifneq ($(RELEASE_BRANCH),$(_empty))
	./tools/ci/compile.sh || exit $?
	ln -s build-target flink
	tar -chf confluent-flink.tar.gz flink
	make mvn-push-nanoversion-tag
	./mvnw deploy:deploy-file -DgroupId=io.confluent.flink \
		-DartifactId=flink \
		-Dversion=$(FLINK_VERSION) \
		-Dpackaging=tar.gz \
		-Dfile=confluent-flink.tar.gz \
		-DrepositoryId=confluent-codeartifact-internal \
		-Durl=$(MAVEN_DEPLOY_REPO_URL)
endif
endif
