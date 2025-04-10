
GIT_COMMIT=`git rev-parse HEAD`

.PHONY: update-version test unit-test integration-test setup dist build clean install docker

build:
	@mvn -T 8 $(MAVEN_CLI_OPTS) -am package

clean:
	@mvn -T 8 $(MAVEN_CLI_OPTS) -am clean

test:
	@mvn -B verify

update-version:
	@mvn -B versions:set "-DnewVersion=$(VERSION)"

docker dist:
	@$(MAKE) -C epa4all-rest-service $@