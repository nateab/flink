SONAR_AUTH_TOKEN_VAULT_KV := token
SONAR_HOST := http://sonarqube.dp.confluent.io:9000
SONAR_SCANNER_ARGS := -X -Dsonar.host.url=$(SONAR_HOST)

SONAR_PROJECT_NAME := $(SEMAPHORE_PROJECT_NAME)

ifeq ($(SEMAPHORE_GIT_PR_NUMBER),)
	SONAR_SCANNER_ARGS += -Dsonar.branch.name=$(SEMAPHORE_GIT_BRANCH)
else
	SONAR_SCANNER_ARGS += -Dsonar.pullrequest.key=$(SEMAPHORE_GIT_PR_NUMBER)
	SONAR_SCANNER_ARGS += -Dsonar.pullrequest.branch=$(SEMAPHORE_GIT_PR_BRANCH)
	SONAR_SCANNER_ARGS += -Dsonar.pullrequest.base=$(SEMAPHORE_GIT_BRANCH)
endif


.PHONY: sonar-scan
sonar-scan:
	@sonar-scanner $(SONAR_SCANNER_ARGS) -Dsonar.login=$(shell vault kv get -field $(SONAR_AUTH_TOKEN_VAULT_KV) "v1/ci/kv/sonarqube/semaphore")

.PHONY: sonarqube-gate-pip-deps
sonarqube-gate-pip-deps:
ifeq ($(shell pip show confluent-ci-tools 2> /dev/null),)
	@echo "ci-tools already installed"
else
	pip3 install -U confluent-ci-tools
endif


.PHONY: sonar-gate
sonar-gate: sonarqube-gate-pip-deps
	@sonarqube-gate-coverage \
		$(SONAR_PROJECT_NAME) \
		--pr-id $(SEMAPHORE_GIT_PR_NUMBER) \
		--token $(shell vault kv get -field $(SONAR_AUTH_TOKEN_VAULT_KV) "v1/ci/kv/sonarqube/semaphore") \
		--host $(SONAR_HOST)
