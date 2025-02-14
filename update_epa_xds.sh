#!/bin/bash

rm -rf ./ePA-XDS-Document

git clone -b ePA-3.0  https://github.com/gematik/ePA-XDS-Document.git

rm -rf ./epa4all-phr-client/src/main/resources/schemas
cp -TR ePA-XDS-Document/src/schema ./epa4all-phr-client/src/main/resources/schemas
