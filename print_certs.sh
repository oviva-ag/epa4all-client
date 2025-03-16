#!/usr/bin/env bash

openssl x509 -text < ca.pem | tee ca.txt
openssl x509 -text < cert.pem | tee cert.txt
openssl storeutl -noout -text -certs chain.pem | tee chain.txt
openssl ocsp -respin ./ocsp.der -text -issuer ca.pem -cert cert.pem | tee ocsp.txt

openssl pkcs12 -nodes -password pass:1234 < root-ca-test.p12 > root-ca-test.txt

#openssl pkcs12 -in vau/vau-proxy-server/src/main/resources/root-ca-test.p12 -nodes -password pass:1234 | openssl x509 -noout -text > root-ca-test.txt
#openssl pkcs12 -in vau/vau-proxy-server/src/main/resources/root-ca-pu.p12 -nodes -password pass:1234 | openssl x509 -noout -text > root-ca-pu.txt
