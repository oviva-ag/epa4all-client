[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=oviva-ag_epa4all-client&metric=alert_status&token=4959774e1684547503bc8fb9d3d0a47a250cd7ad)](https://sonarcloud.io/summary/new_code?id=oviva-ag_epa4all-client)
[![Maven Central Version](https://img.shields.io/maven-central/v/com.oviva.telematik/epa4all-client)](https://central.sonatype.com/artifact/com.oviva.telematik/epa4all-client)

# epa4all-client - the document client for ePA 3.0

[Example Usage](https://github.com/oviva-ag/epa4all-client/blob/main/epa4all-client/src/test/java/com/oviva/telematik/epa4all/client/internal/E2eEpa4AllClientImplTest.java#L26-L46)
```java
try (var cf =
    Epa4AllClientFactoryBuilder.newBuilder()
        .konnektorProxyAddress(
            new InetSocketAddress(KONNEKTOR_PROXY_HOST, KONNEKTOR_PROXY_PORT))
        .konnektorService(TestKonnektors.riseKonnektor_RU())
        .environment(Environment.RU)
        .build()) {

  final var insurantId = "X11...";

  var client = cf.newClient();

  var document = ExportFixture.buildFhirDocument(client.authorInstitution(), insurantId);
  assertDoesNotThrow(() -> client.writeDocument(insurantId, document));
}
```

[Maven Dependency](https://central.sonatype.com/artifact/com.oviva.telematik/epa4all-client)
```xml
<dependency>
    <groupId>com.oviva.telematik</groupId>
    <artifactId>epa4all-client</artifactId>
    <version>0.0.2-rc.0</version>
</dependency>
```

# Architectural Overview
![](./docs/ePA_3_0_overview.png)

![](./docs/vau_tunnel.png)

## References
- [TI Leitfaden for DiGAs](https://wiki.gematik.de/pages/viewpage.action?pageId=512716463)
- [GemSpec Trusted-Environment Authorization, chapter 3.3](https://gemspec.gematik.de/docs/gemILF/gemILF_PS_ePA/gemILF_PS_ePA_V3.2.3/#3.3)
- [Gematik OpenAPI Spec I_Authorization_Service](https://github.com/gematik/ePA-Basic/blob/ePA-3.0.3/src/openapi/I_Authorization_Service.yaml)
- [Authorization Code: Structure](https://gemspec.gematik.de/docs/gemSpec/gemSpec_IDP_Dienst/gemSpec_IDP_Dienst_V1.7.0/#7.3)

## Glossary

- **LEI** - Service Provider (Leistungs Institution)
- **TI** - Telematik Infrastruktur

## Overview
from [ILF PS ePA4all](https://gemspec.gematik.de/docs/gemILF/gemILF_PS_ePA/gemILF_PS_ePA_V3.2.3/#3.3.2)
