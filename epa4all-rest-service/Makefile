
VERSION?=$(shell mvn -q -Dexec.executable=echo -Dexec.args='$${project.version}' --non-recursive exec:exec)
IMAGE_NAME=$(shell mvn -q -Dexec.executable=echo -Dexec.args='$${project.artifactId}' --non-recursive exec:exec)

DOCKER_REPO=ghcr.io/oviva-ag/

GIT_COMMIT=`git rev-parse HEAD`

.PHONY: update-version test unit-test dist build clean install docker

build clean test update-version:
	@$(MAKE) -C ../ $@

verify:
	@mvn -T 8 -B -am verify -f ../

install:
	@mvn -T 8 -B -am install -f ../

docker: install
	@docker build -t $(IMAGE_NAME):v$(VERSION) .

dist: install
ifndef RELEASE_TAG
	$(error RELEASE_TAG is not set)
endif
	docker buildx build --push --platform linux/amd64,linux/arm64 --label git-commit=$(GIT_COMMIT) --tag "$(DOCKER_REPO)$(IMAGE_NAME):$(RELEASE_TAG)" .