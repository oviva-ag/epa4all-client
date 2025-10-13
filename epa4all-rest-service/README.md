# Epa4All Rest Service

Small wrapper around the epa4all client library.

### API Specification

**[openapi.yaml](./src/main/resources/META-INF/openapi/openapi.yaml)**

#### Example
```shell
# Add a new FHIR document
curl -X 'POST' \
  'http://localhost:8080/documents' \
  -H 'Accept: application/json' \
  -H 'Content-Type: application/json' \
  -d '{
  "insurant_id": "X110661675",
  "content_type": "application/fhir+xml",
  "content": "PEJ1bmRsZSB4bWxucz0iaHR0cDovL2...ZXNvdXJjZT4KICAgPC9lbnRyeT4KPC9CdW5kbGU+Cg=="
}'
```

### Run the Container

#### PU with jumphost
> [!WARNING]  
> This is the example for PRODUCTION. Handle with care ;)
```shell
docker run --rm \
  -e 'EPA4ALL_KONNEKTOR_URI=https://10.156.120.103:443' \
  -e 'EPA4ALL_PROXY_ADDRESS=<my proxy IP address>' \
  -e 'EPA4ALL_CREDENTIALS_PATH=/credentials.p12' \
  -e 'EPA4ALL_CREDENTIALS_PASSWORD=<my password>' \
  -e 'EPA4ALL_ENVIRONMENT=PU' \
  -v './credentials.p12:/credentials.p12' \
  -p '8080:8080' \
  ghcr.io/oviva-ag/epa4all-rest-service:latest
```

#### RU with localhost - jumphost forwarded
```shell

# forward the ports from a jumphost to localhost
# assumes an HTTP forward proxy is running on the jumphost with access to the konnektor
ssh alice@jumphost.example.com \
  -L 3128:127.0.0.1:3128
  
# run the service against the RU
docker run --rm \
  -e 'EPA4ALL_KONNEKTOR_URI=https://10.156.145.103:443' \
  -e 'EPA4ALL_PROXY_ADDRESS=host.docker.internal' \
  -e 'EPA4ALL_CREDENTIALS_PATH=/credentials.p12' \
  -e 'EPA4ALL_ENVIRONMENT=RU' \
  -v './credentials.p12:/credentials.p12' \
  -p '127.0.0.1:8080:8080' \
  ghcr.io/oviva-ag/epa4all-rest-service:latest
```

## Configuration Options

| name                            | description                                                                             | default      |
|---------------------------------|-----------------------------------------------------------------------------------------|--------------|
| `EPA4ALL_LOG_LEVEL`*            | Log level for the entire application.                                                   | `INFO`       |
| `EPA4ALL_ADDRESS`*              | Address to bind the server to.                                                          | `0.0.0.0`    |
| `EPA4ALL_PORT`*                 | Port to bind the server to.                                                             | `8080`       |
| `EPA4ALL_KONNEKTOR_URI`*        | URI of the Konnektor to use, e.g. `https://10.1.2.3:443`.                               |              |
| `EPA4ALL_PROXY_ADDRESS`*        | Address of the forward proxy infront of the Konnektor, e.g. `127.0.0.1`.                |              | 
| `EPA4ALL_PROXY_PORT`*           | Port of the forward proxy infront of the Konnektor.                                     | `3128`       | 
| `EPA4ALL_CREDENTIALS_PATH`*     | The PKCS#12 keystore containing the TLS client certificate to connect to the Konnektor. | `./credentials.p12` | 
| `EPA4ALL_CREDENTIALS_PASSWORD`* | The password of the PKCS#12 keystore containing the TLS client certificate.             | `0000`       | 
| `EPA4ALL_WORKPLACE_ID`*         | The workplace ID configured in the Konnektor.                                           | `a`          | 
| `EPA4ALL_CLIENT_SYSTEM_ID`*     | The client system ID configured in the Konnektor.                                       | `c`          | 
| `EPA4ALL_MANDANT_ID`*           | The mandant ID configured in the Konnektor.                                             | `m`          | 
| `EPA4ALL_USER_ID`*              | The user ID configured in the Konnektor.                                                | `admin`      | 
| `EPA4ALL_ENVIRONMENT`*          | The telematik environment, either RU or PU.                                             | `PU`         | 
| `EPA4ALL_TELEMETRY_OPTOUT`      | Basic telemetry to help with development.                                               | `false`      | 
| `EPA4ALL_TELEMATIC_ID`          | The telematic id of the SMC-B card                                                      |              | 
