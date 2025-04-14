# Epa4All Rest Service

Small wrapper around the epa4all client library.

## Configuration

| name                            | description                                                                             | default             |
|---------------------------------|-----------------------------------------------------------------------------------------|---------------------|
| `EPA4ALL_LOG_LEVEL`*            | Log level for the entire application.                                                   | `INFO`              |
| `EPA4ALL_ADDRESS`*              | Address to bind the server to.                                                          | `0.0.0.0`           |
| `EPA4ALL_PORT`*                 | Port to bind the server to.                                                             | `8080`              |
| `EPA4ALL_KONNEKTOR_URI`*        | URI of the Konnektor to use, e.g. `https://10.1.2.3:443`.                               |                     |
| `EPA4ALL_PROXY_ADDRESS`*        | Address of the forward proxy infront of the Konnektor, e.g. `127.0.0.1`.                |                     | 
| `EPA4ALL_PROXY_PORT`*           | Port of the forward proxy infront of the Konnektor.                                     | `3128`              | 
| `EPA4ALL_CREDENTIALS_PATH`*     | The PKCS#12 keystore containing the TLS client certificate to connect to the Konnektor. | `./credentials.p12` | 
| `EPA4ALL_CREDENTIALS_PASSWORD`* | The password of the PKCS#12 keystore containing the TLS client certificate.             | `0000`              | 
| `EPA4ALL_WORKPLACE_ID`*         | The workplace ID configured in the Konnektor.                                           | `a`                 | 
| `EPA4ALL_CLIENT_SYSTEM_ID`*     | The client system ID configured in the Konnektor.                                       | `c`                 | 
| `EPA4ALL_MANDANT_ID`*           | The mandant ID configured in the Konnektor.                                             | `m`                 | 
| `EPA4ALL_USER_ID`*              | The user ID configured in the Konnektor.                                                | `admin`             | 
| `EPA4ALL_ENVIRONMENT`*          | The telematik environment, either RU or PU.                                             | `PU`                | 
| `EPA4ALL_TELEMETRY_OPTOUT`      | Basic telemetry to help with development.                                               | `false`             | 
