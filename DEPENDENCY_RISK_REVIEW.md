# Dependency Risk Review (Milestone 4)

Review date: 2026-10-16. Scope: the migrated stack on branch `mission-demo` (Java 21, Spring Framework 6.2.19,
Jakarta EE 10).

This is a documentation-only review. It changes no dependency version or scope, adds no scanner service or
runtime network call, and applies no remediation. Every "recommended upgrade" or "sign-off required" item
needs separate bank authorization.

## Method

1. **Resolved graph.** `mvn dependency:tree` on JDK 21 / Maven 3.9.16, default `H2` profile (BUILD SUCCESS).
   Versions below come from that resolved tree, not from the pom. The optional `HSQLDB`, `MySQL`, and
   `PostgreSQL` profile drivers and the build plugins are reviewed separately at their pom-declared versions.
2. **One-time public advisory lookups** on 2026-10-16:
   - OSV database (`api.osv.dev` `querybatch` plus one `vulns/<id>` read per hit) for each Maven coordinate
     and version listed below. Webjars were also checked under their npm package names, because OSV
     records some front-end advisories only for npm.
   - Vendor and lifecycle pages: spring.io security advisories, endoflife.date (Spring Framework, Spring
     Boot, Hibernate ORM), the Jib `default_base_image.md` documentation, and the GitHub advisory pages
     for jackson-core, logback, and H2.
   - "No advisory found" means OSV returned no entry for that exact coordinate and version on 2026-10-16.
     It does not prove the component has no vulnerabilities.

Dispositions:

- **keep**: no advisory, or the advisory does not apply to how this app uses the component.
- **recommended upgrade**: a fixed version exists, and the upgrade is low-risk (usually test-only).
- **sign-off required**: the fix is outside the approved target matrix, would change behavior or
  WAR contents, or no OSS fix exists. The bank must accept the risk or authorize the change.

## Findings: runtime and compile dependencies (shipped in `WEB-INF/lib`)

| Component | Resolved version | Advisory ID / severity (OSV, 2026-10-16) | Disposition |
|---|---|---|---|
| `org.springframework:spring-webmvc` | 6.2.19 | GHSA-j9f9-w8pj-32f8 / CVE-2026-47890 (Critical): SSE stream corruption with view fragments. GHSA-pc63-qcmh-9cmg / CVE-2026-47884 (Critical): `XsltView` path limitation (SSRF/RCE with a `/**` view mapping). Both affect 6.2.0–6.2.19. The only OSS fix is 7.0.9; there is no OSS 6.2.x fix. | **Sign-off required.** The app uses neither SSE nor `XsltView` (no matches in `src/`), so current exposure is low. Fixes on the 6.2 line come only through commercial support; see register item 7. |
| `spring-core`, `-beans`, `-context`, `-aop`, `-expression`, `-tx`, `-jdbc`, `-orm`, `-oxm`, `-web`, `-context-support`, `-jcl` | 6.2.19 | No advisory found | Keep. Covered by the Spring 6.2 support decision (item 7). |
| `org.springframework.data:spring-data-jpa` / `spring-data-commons` | 3.5.13 (BOM 2025.0.13) | No advisory found | Keep. Lifecycle review required (item 8). |
| `org.hibernate.orm:hibernate-core` | 6.6.58.Final | No advisory found | Keep. Lifecycle review required (item 8). |
| `org.hibernate.validator:hibernate-validator` | 8.0.6.Final | No advisory found | Keep |
| `com.fasterxml.jackson.core:jackson-core` | 2.19.4 | GHSA-72hv-8253-57qq / CVE-2026-18401 (Moderate); GHSA-r7wm-3cxj-wff9 / CVE-2026-68494 (High); GHSA-7hhh-6rmp-j9qf / CVE-2026-89425 (High); GHSA-p6pp-m3f8-5c89 / CVE-2026-89407 (High). All are parser DoS issues. Fixed in 2.18.11 / 2.21.7 / 2.22.3. The 2.19 line gets no fix. | **Sign-off required.** Recommended upgrade is a Jackson BOM of 2.21.7 or later, which is outside the approved matrix (2.19.4). The app only *serializes* `/vets.json` and does not parse untrusted JSON (no `@RequestBody` or `ObjectMapper` use in `src/main/java`), so exposure is low. |
| `com.fasterxml.jackson.core:jackson-databind` (+ `jackson-annotations`) | 2.19.4 | 11 advisories, including High: GHSA-j3rv-43j4-c7qm / CVE-2026-54512 and GHSA-rmj7-2vxq-3g9f / CVE-2026-54513 (PolymorphicTypeValidator bypasses), GHSA-q4xh-88c3-wmh7 / CVE-2026-68497, GHSA-cxp5-3px4-pw24 / CVE-2026-91777, GHSA-wv8q-qhhj-9h54 / CVE-2026-91776 (DoS). Moderate: GHSA-3pjw-73gf-8qr5, GHSA-5jmj-h7xm-6q6v, GHSA-gx83-3vf8-gh7j, GHSA-hgj6-7826-r7m5, GHSA-vvgp-rfg2-7rr6, GHSA-wjgm-6hv5-3cvf. Fixed in 2.18.11 / 2.21.7 / 2.22.3. | **Sign-off required.** Same upgrade path as jackson-core. The deserialization and polymorphic-typing issues do not apply to output-only serialization with default typing off. |
| `ch.qos.logback:logback-core` / `logback-classic` | 1.2.6 | GHSA-vmq6-5m68-f53m / CVE-2023-6378 (High, receiver deserialization; fixed in 1.2.13). GHSA-668q-qrv7-99fm / CVE-2021-42550 (Moderate; fixed in 1.2.9). GHSA-pr98-23f8-jwxv / CVE-2024-12798 (Moderate) and GHSA-25qh-j22f-pwp8 / CVE-2025-11226 (Moderate): fixed only in 1.3.x/1.5.x. Low: CVE-2024-12801, CVE-2026-1225, CVE-2026-9828, CVE-2026-10532. | **Sign-off required.** The 1.2.x line is unmaintained. 1.2.13 fixes the High item. A full fix needs logback 1.3+/1.5 with SLF4J 2, which is outside the matrix. Most items need attacker write access to `logback.xml` or a configured receiver; the app configures only a `ConsoleAppender`. |
| `org.slf4j:slf4j-api` | 1.7.32 | No advisory found | Keep. Tied to the logback decision. |
| `com.h2database:h2` (default `H2` profile) | 2.1.214 | GHSA-22wj-vf5f-wrvj / CVE-2022-45868 (High; disputed by the vendor): cleartext `-webAdminPassword` when the console starts from the CLI. Fixed in 2.2.220. | **Sign-off required (behavior-preservation pin, item 10).** The app does not start the H2 console. An upgrade to 2.2.x can change SQL and seed-script behavior. Use as an in-memory demo database only. |
| `org.apache.tomcat:tomcat-jdbc` / `tomcat-juli` | 9.0.46 | No advisory found for these coordinates | **Sign-off required (pin, item 10).** Pinned on purpose to preserve pool behavior. Tomcat server CVEs do not apply to the standalone pool jar, but the pin is from 2021. |
| `org.aspectj:aspectjrt` / `aspectjweaver` | 1.9.7 | No advisory found | Keep. Old (2021) but works on Java 21 for proxy-based AOP; all tests pass. |
| `com.github.ben-manes.caffeine:caffeine` | 3.3.1 | No advisory found | Keep |
| `org.glassfish.web:jakarta.servlet.jsp.jstl` | 3.0.1 | No advisory found | Keep |
| `jakarta.xml.bind:jakarta.xml.bind-api` / `org.glassfish.jaxb:jaxb-runtime` (+ `jaxb-core`, `txw2`, `istack-commons-runtime`, `angus-activation`) | 4.0.5 / 4.0.9 | No advisory found | Keep |
| `org.webjars:bootstrap` | 3.3.6 | Moderate XSS: CVE-2016-10735, CVE-2018-14040, CVE-2018-14042, CVE-2018-20676, CVE-2018-20677 (fixed in 3.4.0), CVE-2019-8331 (fixed in 3.4.1), CVE-2024-6485 (npm record; no fix on 3.x). | **Sign-off required (pin, item 10).** The user approved keeping this webjar pair. An upgrade changes CSS/JS behavior and the LESS build. |
| `org.webjars:jquery-ui` | 1.12.1 | No Maven record. npm `jquery-ui@1.12.1`: CVE-2021-41182, CVE-2021-41183, CVE-2021-41184 (Moderate XSS; fixed in 1.13.0), CVE-2022-31160 (Moderate XSS; fixed in 1.13.2). | **Sign-off required (pin, item 10).** Only the datepicker is used, with `dateFormat` only (`createOrUpdatePetForm.jsp:11`, `createOrUpdateVisitForm.jsp:12`). The vulnerable `altField`, `*Text`, `.position({of})`, and `checkboxradio` options are not used. |
| `org.webjars:jquery` | 3.5.1 | No advisory found (Maven or npm) | Keep. The known Bootstrap/jQuery version page error is approved baseline behavior. |
| `io.micrometer:micrometer-observation` / `-commons` | 1.15.12 | No advisory found | Keep |
| `org.antlr:antlr4-runtime` | 4.13.2 | No advisory found | Keep |
| `net.bytebuddy:byte-buddy` | 1.17.8 | No advisory found | Keep |
| `org.jboss.logging:jboss-logging`, `io.smallrye:jandex`, `com.fasterxml:classmate`, `hibernate-commons-annotations` | 3.5.0.Final / 3.2.0 / 1.5.1 / 7.0.3.Final | No advisory found | Keep |
| `org.junit.jupiter:junit-jupiter-engine` / `junit-jupiter-api` (+ `junit-platform-engine`, `junit-platform-commons`, `opentest4j`, `apiguardian-api`) | 5.14.4 / 1.14.4 | No advisory found | **Sign-off required: packaging finding (item 11).** Resolved with `compile` scope, so these jars ship in `WEB-INF/lib`. |
| `org.mockito:mockito-junit-jupiter` | 4.0.0 | No advisory found | **Sign-off required: packaging finding (item 11).** |

Container-provided (`provided` scope, not shipped): `jakarta.servlet-api` 6.0.0, `jakarta.servlet.jsp.jstl-api`
3.0.0, `jakarta.el-api` 5.0.0. No advisories were found. The deploying container's own patch level is what matters.

## Findings: test-only dependencies (not shipped)

| Component | Resolved version | Advisory ID / severity | Disposition |
|---|---|---|---|
| `org.assertj:assertj-core` | 3.21.0 | GHSA-rqfh-9r24-8c9r / CVE-2026-24400 (High): XXE in `isXmlEqualTo`. Fixed in 3.27.7. | Recommended upgrade (test-only, low risk). The tests do not use `isXmlEqualTo`. |
| `com.jayway.jsonpath:json-path` | 2.6.0 | GHSA-pfh2-hfmq-phg5 / CVE-2023-51074 (Moderate). Fixed in 2.9.0. | Recommended upgrade (test-only) |
| `net.minidev:json-smart` (via json-path) | 2.4.7 | GHSA-493p-pfq6-5258 / CVE-2023-1370 (High, recursion DoS). Fixed in 2.4.9. | Recommended upgrade (test-only; follows the json-path upgrade) |
| `org.mockito:mockito-core`, `byte-buddy-agent`, `objenesis`, `hamcrest`, `spring-test`, `expressly`, `asm` | 4.0.0 / 1.11.19 / 3.2 / 2.2 / 6.2.19 / 5.0.0 / 9.1 | No advisory found | Keep |

## Findings: optional database profiles (not in the default build)

| Component | Version (pom) | Advisory ID / severity | Disposition |
|---|---|---|---|
| `org.hsqldb:hsqldb` (`HSQLDB` profile) | 2.5.2 | GHSA-77xx-rxvh-q682 / CVE-2022-41853 (Critical, RCE through untrusted input). Fixed in 2.7.1. | **Sign-off required** before this profile is used anywhere other than a developer machine. |
| `mysql:mysql-connector-java` (`MySQL` profile) | 8.0.28 | GHSA-m6vm-37g8-gqvh / CVE-2023-22102 (High). Fixed in 8.2.0 under the relocated `com.mysql:mysql-connector-j`. | **Sign-off required (pin, item 10).** The upgrade changes the artifact coordinates. |
| `org.postgresql:postgresql` (`PostgreSQL` profile) | 42.5.0 | GHSA-24rp-q3w6-vc56 / CVE-2024-1597 (Critical; only with `preferQueryMode=simple`, which is not the default). GHSA-562r-vg33-8x8h / CVE-2022-41946 (Moderate). GHSA-98qh-xjc8-98pq / CVE-2026-42198 (High). Fixed in 42.7.11. | **Sign-off required (pin, item 10).** Recommended upgrade before any production use of this profile. |

## Findings: build and developer plugins (not shipped)

| Component | Version | Advisory ID / severity | Disposition |
|---|---|---|---|
| `org.eclipse.jetty.ee10:jetty-ee10-maven-plugin` (+ `jetty-server`, `jetty-http`) | 12.0.40 | No advisory found | Keep. Developer runtime only. |
| `com.google.cloud.tools:jib-maven-plugin` | 2.6.0 | No advisory for the plugin. See item 9 for the base image. | **Sign-off required (item 9)** |
| `ro.isdc.wro4j:wro4j-maven-plugin` (+ its `mockito-core` 4.0.0 plugin dependency) | 1.8.0 | No advisory found for the plugin coordinate. Its build-time transitive dependencies were not individually checked. | Keep. It runs only at build time to compile LESS. |

## Bank sign-off register

These risks exist in the application or platform today. They are flagged, not fixed. Each one needs a
recorded bank decision (accept, or authorize a separate change) before production use.

1. **Cross-owner pet edit.** `PetController.initUpdateForm`/`processUpdateForm`
   (`src/main/java/org/springframework/samples/petclinic/web/PetController.java:94-112`) load and save a pet
   by `petId` without checking that it belongs to the `{ownerId}` in the URL. Editing a pet under another
   owner's path succeeds. This behavior was kept on purpose and is pinned by characterization tests.
   *Disposition: sign-off required.*
2. **No in-app authentication or authorization.** There is no login, no role checks, and no record-level
   access control on any route. *Disposition: sign-off required.*
3. **Raw exception message in the error JSP.** `src/main/webapp/WEB-INF/jsp/exception.jsp:12` renders
   `${exception.message}`. The `SimpleMappingExceptionResolver` default view is set in
   `src/main/resources/spring/mvc-core-config.xml:62`. *Disposition: sign-off required.*
4. **Demo database credentials and PII-like seed data.** The `pom.xml` profiles contain `sa` with an empty
   password (H2/HSQLDB) and `petclinic`/`petclinic` and `postgres`/`petclinic` (MySQL/PostgreSQL).
   `src/main/resources/db/*/data.sql` seeds names, street addresses, and phone numbers. These are sample
   values, but they look like PII. *Disposition: sign-off required.*
5. **Debug and SQL logging of owner data.** `src/main/resources/logback.xml:17` sets
   `org.springframework.samples.petclinic` to `debug`, and `src/main/resources/spring/data-access.properties:11`
   sets `jpa.showSql=true`. Queries against owner, pet, and visit tables are printed to the console.
   *Disposition: sign-off required.*
6. **No user-attributed audit trail.** No create, update, or delete is recorded with a user identity.
   *Disposition: sign-off required.*
7. **Spring Framework 6.2 OSS end of support.** OSS support for 6.2 ended on 2026-06-30 (endoflife.date;
   6.2.19 of 2026-06-08 is the last OSS release). Commercial support runs to 2032-06-30. Two Critical
   spring-webmvc advisories (CVE-2026-47890, CVE-2026-47884) already have no OSS 6.2 fix. The bank must
   choose commercial support or a later move to Spring Framework 7. *Disposition: sign-off required
   (support/risk acceptance).*
8. **Spring Data 2025.0 / Hibernate ORM 6.6 lifecycle.** Spring Data 2025.0 is the Spring Boot 3.5
   generation, and Boot 3.5 OSS support ended on 2026-06-30. Hibernate ORM 6.6 community support ended
   on 2026-06-09, and 6.6.58.Final is listed as the latest 6.6 release; only extended (commercial) support
   remains. No advisories are open against the resolved versions today. *Disposition: sign-off required
   (lifecycle acceptance).*
9. **Stale Jib/Docker base image.** `jib-maven-plugin` 2.6.0 has no `<from>` configured, so for a WAR it
   defaults to `gcr.io/distroless/java/jetty` (Jib before 3.0). That is a Java 8/11 Jetty 9.4 `javax`
   runtime on an end-of-life Debian base. It cannot run this Jakarta EE 10 WAR, and it does not get OS
   patches. The plugin and image configuration were left unchanged. *Disposition: sign-off required
   (separate container-image review).*
10. **Dependency pins retained for behavior preservation.**
    - H2 2.1.214 (CVE-2022-45868).
    - tomcat-jdbc 9.0.46 pool.
    - Webjars Bootstrap 3.3.6 (7 XSS advisories), jQuery UI 1.12.1 (4 XSS advisories), and jQuery 3.5.1.
      The known Bootstrap/jQuery page error is approved.
    - Logback 1.2.6 / SLF4J 1.7.32 (logback 1.2.x is unmaintained).
    - Jackson 2.19.4 (the 2.19 line gets no fixes; recommended move to 2.21.7+).
    - Profile drivers: HSQLDB 2.5.2, MySQL Connector/J 8.0.28, and PostgreSQL JDBC 42.5.0.

    Each upgrade could change runtime behavior or leave the approved matrix.
    *Disposition: sign-off required for each pin.*
11. **Test-framework jars in the production WAR.** `junit-jupiter-engine` and `mockito-junit-jupiter` are
    declared without `<scope>test</scope>` in `pom.xml`. The resolved tree shows them, plus
    `junit-jupiter-api`, `junit-platform-engine`, `junit-platform-commons`, `opentest4j`, and
    `apiguardian-api`, at `compile` scope. `unzip -l target/petclinic.war` confirms all seven jars in
    `WEB-INF/lib`. *Recommended fix:* add `<scope>test</scope>` to both declarations. This changes WAR
    contents, so it was **not applied**. *Disposition: sign-off required.*

## How a reviewer verifies

```sh
export JAVA_HOME=/Users/chuangzhu/.m2/tools/jdk-21.0.12.1+1/Contents/Home
export PATH=/Users/chuangzhu/.m2/tools/apache-maven-3.9.16/bin:$PATH
mvn dependency:tree                       # resolved versions match the tables above
git diff HEAD~1 -- pom.xml                # empty: the review changed no version or scope
mvn -DskipTests package && unzip -l target/petclinic.war | grep -E "junit|mockito|opentest|apiguardian"
rg -n "petId" src/main/java/org/springframework/samples/petclinic/web/PetController.java   # item 1
rg -n "exception.message" src/main/webapp/WEB-INF/jsp/exception.jsp                        # item 3
rg -n "level=\"debug\"|showSql" src/main/resources                                        # item 5
rg -n "jib-maven-plugin" -A 12 pom.xml                                                     # item 9: no <from>
rg -n "XsltView|SseEmitter|@RequestBody" src/main/java                                     # no matches
```

To re-check the advisories, look up each coordinate and version on https://osv.dev (or the GitHub Advisory
Database). Results can change after the 2026-10-16 lookup date.
