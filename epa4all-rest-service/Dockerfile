FROM registry.access.redhat.com/ubi9/ubi-minimal:9.5

LABEL org.opencontainers.image.source=https://github.com/oviva-ag/epa4all-client

ARG JAVA_PACKAGE=java-21-openjdk-headless
ARG RUN_JAVA_VERSION=1.3.8

ENV LANG='en_US.UTF-8' LANGUAGE='en_US:en'

# Install java and the run-java script
# Also set up permissions for user `1001`
RUN <<EOF
microdnf -y update
microdnf -y install ca-certificates ${JAVA_PACKAGE}
microdnf clean all
mkdir /deployments
chown 1001 /deployments
chmod "g+rwX" /deployments
chown 1001:root /deployments
curl https://repo1.maven.org/maven2/io/fabric8/run-java-sh/${RUN_JAVA_VERSION}/run-java-sh-${RUN_JAVA_VERSION}-sh.sh -o /deployments/run-java.sh
chown 1001 /deployments/run-java.sh
chmod 540 /deployments/run-java.sh
echo "securerandom.source=file:/dev/urandom" >> /etc/alternatives/jre/conf/security/java.security
echo "securerandom.strongAlgorithms=NativePRNGNonBlocking:SUN,DRBG:SUN" >> /etc/alternatives/jre/conf/security/java.security
EOF


# Java dependencies
COPY --chown=1001 target/lib /deployments/

# application
COPY --chown=1001 target/epa4all-rest-service.jar /deployments/

WORKDIR /deployments

# Configure the JAVA_OPTIONS, you can add -XshowSettings:vm to also display the heap size.
ENV JAVA_MAIN_CLASS=com.oviva.telematik.epa4all.restservice.Main

USER 1001

EXPOSE 8080

CMD [ "/deployments/run-java.sh" ]