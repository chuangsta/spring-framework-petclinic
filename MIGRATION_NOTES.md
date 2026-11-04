# Migration Notes

> The `JAVA_HOME`/`PATH` commands below are kept as recorded on the original macOS machine. For current,
> platform-neutral setup and build commands, see `AGENTS.md` and `readme.md`.

## Milestone 1 — Characterization tests and baseline

### Baseline (before any edit, commit `4bac3bd`, Maven 3.9.16)

| JDK | Command | Result |
|-----|---------|--------|
| Temurin 8 | `mvn test` | Tests run: 62, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS |
| Temurin 21.0.12.1+1 | `mvn test` | Tests run: 62, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS |

### What changed

Test-only additions. No production code, configuration, or dependency changes.

Owner characterization:
- `OwnerControllerTests` (+10 tests): all four search states (empty last name searches with `""`; zero
  results re-render `owners/findOwners` with a `notFound` error on `lastName`; one result redirects to
  that owner's id; several results render `owners/ownersList` with `selections`); create ignores a posted
  `id`, saves exactly once with the exact bound fields, and redirects to `/owners/{newId}`; update applies
  the path `ownerId` over a posted `id`, saves exactly once, and redirects to `/owners/{ownerId}`; invalid
  submissions (all required fields missing → `NotEmpty`; non-numeric or 11-significant-digit telephone →
  `Digits`) re-render the form and never call `saveOwner`.
- `AbstractClinicServiceTests` (+2 tests, inherited by the `jdbc`, `jpa`, and `spring-data-jpa` profile
  classes): an empty last name returns all 10 seeded owners; last-name search is a prefix match.

After the owner additions: `mvn test` on JDK 8 and JDK 21 → Tests run: 78, Failures: 0, Errors: 0, Skipped: 0.

Pet characterization:
- `PetControllerTests` (+10 tests; `reset(clinicService)` per test): valid create saves exactly once with the
  exact bound name/type/`yyyy/MM/dd` birth date, linked to the URL owner, redirecting to `/owners/{ownerId}`;
  a duplicate name within the owner (case-insensitive, existing pets only) gets a `duplicate` error on `name`
  and no save, while an unsaved same-name pet is ignored; missing name, type, or birth date on create each
  re-render `pets/createOrUpdatePetForm` with a `required` error and no save; edit GET loads by `petId`; edit
  POST saves the bound fields (including posted `id`) linked to the URL owner; an edit with a posted `id` does
  not require `type`. The misnamed `testProcessCreationFormHasErrors` (it posts to the edit URL) was renamed
  to `testProcessUpdateFormWithoutIdRequiresType`, keeping its assertions and adding the `type` error code and
  a no-save check.
- **Bank risk flag, pinned as-is:** `testCrossOwnerPetEditSucceedsAsIs_bankRiskFlag` shows George's pet loads
  and saves under Betty's URL path and is re-linked to Betty. Not fixed.
- New pure-JUnit `OwnerTests` (+2, `getPet` case-insensitivity and ignore-new) and `PetValidatorTests` (+3).

After the pet additions: `mvn test` on JDK 8 and JDK 21 → Tests run: 93, Failures: 0, Errors: 0, Skipped: 0.

Visit characterization:
- `VisitControllerTests` (+4 tests; `reset(clinicService)` per test, app conversion service): an empty
  description re-renders `pets/createOrUpdateVisitForm` with a `NotEmpty` error on `description` and no save;
  a valid visit saves exactly once with the exact description, linked to the path pet, dated today when no
  date is posted, and redirects to `/owners/{ownerId}`; an explicit `yyyy/MM/dd` date binds exactly; the
  visit list is date-descending for an out-of-order fixture.
- New pure-JUnit `VisitTests` (+3): a new visit defaults to today, `Pet.addVisit` links the pet, and
  `Pet.getVisits()` sorts newest-first.

After the visit additions: `mvn test` on JDK 8 and JDK 21 → Tests run: 100, Failures: 0, Errors: 0, Skipped: 0.

### Risks

- Tests pin current behavior, including quirks. Example: `@Digits(integer=10)` counts significant digits,
  so the existing 11-character telephone `01316761638` (leading zero) is accepted. A migration that changes
  this will fail the existing create test, which is intended.
- Pet edit POST binds the pet from the request (hidden `id`), not from `findPetById`, and a pet with an `id`
  is saved without a `type`. Both behaviors, and the cross-owner edit, are pinned on purpose.
- `VisitController`'s `@ModelAttribute("visit")` runs on every request, including the visit list, and
  attaches an unsaved visit dated today to the loaded pet. With the mocked service that visit appears first
  in `visits`; the ordering test pins this rather than hiding it.
- Controller tests use a mocked `ClinicService`; persistence behavior is covered only by the service-level
  profile tests.

### How a reviewer verifies

```sh
export PATH=/Users/chuangzhu/.m2/tools/apache-maven-3.9.16/bin:$PATH
export JAVA_HOME=/Users/chuangzhu/.m2/tools/jdk-21.0.12.1+1/Contents/Home && mvn test
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home && mvn test
mvn test -Dtest='OwnerControllerTests,ClinicService*Tests'   # focused owner coverage
mvn test -Dtest='PetControllerTests,PetValidatorTests,OwnerTests'   # focused pet coverage
mvn test -Dtest='VisitControllerTests,VisitTests'   # focused visit coverage
git diff 4bac3bd --stat -- src/main pom.xml                  # expect no output (test-only diff)
```

## Milestone 2 — Java 21 build toolchain

### What changed

Build tooling only. No production code, test code, or runtime dependency changes.

- `pom.xml` build plugins: maven-compiler-plugin 3.8.1 → 3.14.1, maven-surefire-plugin 2.22.1 → 3.5.5
  (same `**/*Tests.java` includes), maven-war-plugin 3.3.1 → 3.5.1, jacoco-maven-plugin 0.8.6 → 0.8.13.
- `maven.compiler.source`/`target` stay at `1.8`. Compiled classes remain class-file version 52.
- Maven wrapper (`.mvn/wrapper/maven-wrapper.properties`): Maven 3.5.4 → 3.9.16.
- CI (`.github/workflows/maven-build.yml`): the 8/11/17 matrix is replaced by Java 21 (Temurin, since the
  `adopt` distribution has no Java 21 builds); checkout/setup-java actions move to v4. Triggers are unchanged.
- `readme.md`: documents the JDK 21 / Maven 3.9.16 toolchain under `~/.m2/tools`, `JAVA_HOME`/`PATH`
  selection, and the `mvn test` / `mvn -DskipTests package` / `./mvnw jetty:run-war` instructions.

Result: `mvn test` on JDK 21 → Tests run: 100, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS.

### Why compiler output stays at release 8 until Milestone 3

The Spring 5 runtime's ASM cannot parse class-file version 65. A release-21 probe failed 88 of 100 tests
with `Unsupported class file major version 65` during component scanning. Aligning Spring 5 to 5.3.39 would
fix that, but it is an unapproved intermediate runtime change. Release 21 therefore moves with the atomic
Spring 6.2/Jakarta migration in Milestone 3.

### Risks

- Stale `target/` output: classes compiled earlier at release 21 are not always recompiled by the
  incremental compiler and cause the same 88 errors. Run `mvn clean` once if `javap` shows major version 65.
- JDK 8 still builds this milestone. After Milestone 3 it cannot. The JDK 8 baseline counts above remain the
  reference.
- Pre-existing context only: `spring-framework.version` declares 5.3.22, but `spring-core` resolves to
  5.3.11. It is intentionally not aligned here. Milestone 3 replaces the Spring line.
- Surefire 3.x keeps the explicit `**/*Tests.java` includes, so the executed test set is unchanged (100 tests).

### How a reviewer verifies

```sh
export JAVA_HOME=/Users/chuangzhu/.m2/tools/jdk-21.0.12.1+1/Contents/Home
export PATH=/Users/chuangzhu/.m2/tools/apache-maven-3.9.16/bin:$PATH
mvn clean -q && mvn test                     # 100 tests, 0 failures/errors/skips
javap -v target/classes/org/springframework/samples/petclinic/model/Owner.class | grep major   # 52
./mvnw --version                             # Apache Maven 3.9.16
git diff 8614a5f --stat -- src               # expect no output (no code changes)
```

## Milestone 3 — Spring Framework 6.2 / Jakarta EE 10 on Java 21

### What changed

One atomic change: compiler output, runtime stack, and namespaces moved together.

- Compiler: `maven.compiler.release` 21 replaces `source`/`target` 1.8. Classes are class-file version 65.
  `maven.compiler.parameters=true` is required to preserve existing route binding: Spring 6.1+ no longer
  reads parameter names from debug info, so without it the unannotated `@PathVariable int petId` fails at
  request time. It is a compiler setting, not a dependency, and no controller source changed.
- Dependency matrix:

  | Component | From | To |
  | --- | --- | --- |
  | Spring Framework | 5.3.22 (core resolved 5.3.11) | 6.2.19 |
  | Spring Data BOM | 2021.1.0-RC1 | 2025.0.13 (spring-data-jpa 3.5.13); `repo.spring.io/milestone` removed |
  | Hibernate | `org.hibernate:hibernate-entitymanager` 5.6.11 + `hibernate-ehcache` | `org.hibernate.orm:hibernate-core` 6.6.58.Final |
  | Hibernate Validator | 6.2.0.Final | 8.0.6.Final |
  | Servlet API (provided) | `tomcat-servlet-api` + `tomcat-jasper-el` 9.0.46 | `jakarta.servlet:jakarta.servlet-api` 6.0.0 |
  | JAXB | `javax.xml.bind:jaxb-api` 2.3.1, `jaxb-runtime` 2.3.4, `javax.activation-api` | `jakarta.xml.bind-api` 4.0.5 + `jaxb-runtime` 4.0.9 |
  | JSTL | `taglibs-standard-jstlel` 1.2.5 | `org.glassfish.web:jakarta.servlet.jsp.jstl` 3.0.1 + `jakarta.servlet.jsp.jstl-api` 3.0.0 (provided) |
  | Cache | Ehcache 2 (`EhCacheCacheManager`, `cache/ehcache.xml`) | Caffeine 3.3.1 (`CaffeineCacheManager`) |
  | Jetty plugin | `org.eclipse.jetty:jetty-maven-plugin` 9.4.43 | `org.eclipse.jetty.ee10:jetty-ee10-maven-plugin` 12.0.40 |

- Compatibility additions required by Spring 6.2, **approved by the user** and verified by the green suite:
  - Jackson BOM 2.12.4 → 2.19.4. Spring 6.2 needs Jackson 2.14+ (`DatatypeFeature`); MVC contexts failed to load.
  - JUnit Jupiter 5.8.1 → 5.14.4 (still JUnit 5). spring-test 6.2 calls `ExtensionContext.getExecutableInvoker()`.
  - `org.glassfish.expressly:expressly` 5.0.0, **test scope only**. It replaces the EL implementation that
    the removed provided `tomcat-jasper-el` supplied to Hibernate Validator in tests. At runtime Jetty EE10
    supplies EL; validation messages render unchanged (`must not be empty`).
- Namespaces: `javax.persistence`, `javax.validation`, `javax.servlet`, `javax.xml.bind` → `jakarta.*` in
  20 files (model, JPA repositories, controllers, `PetclinicInitializer`, `ValidatorTests`).
  `javax.sql.DataSource` is Java SE and unchanged.
- Cache: `tools-config.xml` defines `CaffeineCacheManager` with cache `vets` and
  `maximumSize=100,expireAfterWrite=60s`. Setting the cache names keeps the manager static, like the
  Ehcache setup, which only had configured caches. In memory only, no disk. `@Cacheable("vets")` is unchanged.
  `cache/ehcache.xml` and `cache/ehcache.xsd` are deleted.
- Container: `WEB-INF/jetty-web.xml` configures `org.eclipse.jetty.ee10.webapp.WebAppContext` with
  `parentLoaderPriority=true` retained. `mvn jetty:run-war` on port 8080 still works.
- Date binding fix (behavior preservation): Spring 6 `TemporalAccessorParser` falls back to ISO parsing
  when a `@DateTimeFormat` pattern fails and no fallback patterns are set, so `2021-06-15` was silently
  accepted. `Pet.birthDate` and `Visit.date` now declare `fallbackPatterns = "yyyy/MM/dd"` (same pattern),
  which turns that ISO fallback off. ISO input is rejected with `typeMismatch` ("invalid date") as before.
  New tests `PetControllerTests.testProcessCreationFormRejectsIsoBirthDate` and
  `VisitControllerTests.testProcessNewVisitFormRejectsIsoDate` pin this. Both fail without the fix.
- Decisions:
  - JSTL taglib URIs: kept the legacy `http://java.sun.com/jsp/jstl/*` URIs. Pages render correctly on
    Jetty EE10 with JSTL 3.0.1.
  - JSTL API scope: `provided` works. Jetty supplies the API, and the implementation jar ships in the WAR.
  - Connection pool: kept `tomcat-jdbc` 9.0.46 (it does not use the servlet API; suite and smoke run green).
  - H2: kept 2.1.214 (suite and smoke run green).
  - Hibernate 6 JPQL: the `left join fetch` queries and raw `Query` usage worked unchanged. No query edits.
  - Webjars (user-approved): Bootstrap 3.3.6 and jQuery 3.5.1 stay unchanged. Their existing page error
    `Bootstrap's JavaScript requires jQuery version 1.9.1 or higher, but lower than version 3` is retained
    as the only allowed baseline page error. Browser validation must show only localhost requests, no
    console errors, and no new page errors beyond that exact message.

Result: `mvn test` on JDK 21 → Tests run: 102, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
(100 earlier tests + 2 ISO-date regression tests).

### Risks

- JDK 8 can no longer build the project (class-file 65). The JDK 8 baseline counts in Milestone 1 remain the reference.
- Jackson 2.19.4, JUnit Jupiter 5.14.4, and test-scope Expressly 5.0.0 widen the dependency surface beyond the
  original plan. The user approved them; Expressly must stay test scope (Jetty supplies EL at runtime).
- Spring 6's ISO date fallback applies to any future `@DateTimeFormat(pattern=...)` field that does not set `fallbackPatterns`.
- Hibernate logs `HHH90000025` because the H2 dialect is set explicitly. This is harmless, and it is left as is.
- Jetty log noise grows (about 1,900 WARN lines, mostly "scanned from multiple locations"). The plugin
  classpath and the WAR both contain JSTL 3.0.1 and AspectJ classes. Benign.
- Pre-existing, unchanged: Bootstrap 3.3.6 throws `Bootstrap's JavaScript requires jQuery version 1.9.1 or
  higher, but lower than version 3` against the baseline jQuery 3.5.1 webjar on every page. This is a browser
  page error, unrelated to the server stack. The user approved retaining it with the webjars unchanged; any
  other console or page error, or any non-localhost request, is a regression.
- Lifecycle: Spring 6.2 open-source support ends June 2026; see `library/bank-risk-flags.md`. Flag only.
- Bank risk flags (cross-owner pet edit, no authn/authz, raw exception page, demo data, debug/SQL
  logging, no audit trail) are unchanged and still flag-only.

### How a reviewer verifies

```sh
export JAVA_HOME=/Users/chuangzhu/.m2/tools/jdk-21.0.12.1+1/Contents/Home
export PATH=/Users/chuangzhu/.m2/tools/apache-maven-3.9.16/bin:$PATH
mvn clean -q && mvn test                    # 102 tests, 0 failures/errors/skips
javap -v target/classes/org/springframework/samples/petclinic/model/Owner.class | grep major   # 65
mvn dependency:tree                         # spring 6.2.19, spring-data-jpa 3.5.13, hibernate-core 6.6.58.Final, caffeine 3.3.1; no javax EE / ehcache
rg -n "javax\.(servlet|persistence|validation|xml\.bind)" src/   # no matches
mvn -DskipTests jetty:run-war -Djetty.http.port=8080          # then, in another shell:
curl -s http://localhost:8080/                       # Welcome page
curl -s "http://localhost:8080/owners?lastName=Davis"  # Betty Davis + Harold Davis
curl -s http://localhost:8080/vets.xml; curl -s http://localhost:8080/vets.json
```

In a browser, open `/owners/2` (Betty Davis, pet Basil, hamster, 2012-08-06). Create a synthetic owner.
Add a pet with birth date `2021-06-15` (expect "invalid date"), then with `2021/06/15` (pet row shows
2021-06-15). Submit an empty visit description (expect "must not be empty"), then a described visit (row
appears on the owner page). Throughout, confirm every request goes to `localhost:8080`, the console shows
no errors, and the only page error is the approved Bootstrap/jQuery message above. Also check
`mvn dependency:tree` shows `expressly` 5.0.0 only in test scope. Stop the server afterwards.

## Milestone 4 — Cleanup and dependency review

### What changed

Behavior-preserving cleanup only. No production code, dependency versions, routes, views, or validation changed.

- `pom.xml`: removed the retired `maven-eclipse-plugin` (2.10) and its version property. It had no
  executions, generated pre-m2e Eclipse metadata only when invoked as `mvn eclipse:eclipse`, and is not
  used by m2e, the build lifecycle, or CI.
- `pom.xml`: removed the `maven-assembly-plugin` (3.3.0) declaration and its version property. It had no
  executions, nothing in the repository or CI invoked `assembly:single`, and its `jar-with-dependencies`
  descriptor does not apply to this WAR project.
- `ClinicServiceJdbcTests`: removed the unused JUnit 4 `SpringJUnit4ClassRunner` import and the unused
  `ContextConfiguration` import. The class already runs on JUnit 5 through `@SpringJUnitConfig`.
- `readme.md`: describes the final stack (Java 21, Spring Framework 6.2, Spring Data JPA 3.5, Hibernate ORM
  6.6, Jakarta EE 10, Jetty 12 EE10 plugin). The obsolete Jetty 9.4 / Tomcat 9 javax-container prerequisite
  is replaced with an optional Jakarta EE 10 container (Jetty 12 EE10 or Tomcat 10.1). The deployed WAR name
  is corrected to `petclinic.war`, and an empty IntelliJ "prerequisites" section is removed.
- Left in place on purpose:
  - The Jib plugin (2.6.0) is unchanged. Its default base image predates Jakarta EE 10. This is flag-only
    for the dependency review; the readme notes it.
  - The `mockito-core` dependency of `wro4j-maven-plugin` ("Required for Java 9 and above") was kept,
    because removing it could not be shown to be safe without changing the CSS build.
  - `junit-jupiter-engine` and `mockito-junit-jupiter` have no `test` scope, so they ship in the WAR.
    Changing their scope changes WAR contents, so it is left for the dependency review.

Result: `mvn test` on JDK 21 → Tests run: 102, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS.
`mvn -DskipTests package` still builds `target/petclinic.war`.

### Risks

- Developers who generated Eclipse metadata with `mvn eclipse:eclipse` must use m2e (Import → Existing
  Maven project) instead. The readme already documents m2e.
- Anyone who ran `mvn assembly:single` would now get the plugin's defaults, which need a descriptor. The
  goal never produced a usable artifact for this WAR.
- The Jib image and the bank risk flags in `library/bank-risk-flags.md` are unchanged and still flag-only.

### How a reviewer verifies

```sh
export JAVA_HOME=/Users/chuangzhu/.m2/tools/jdk-21.0.12.1+1/Contents/Home
export PATH=/Users/chuangzhu/.m2/tools/apache-maven-3.9.16/bin:$PATH
git diff HEAD~1 -- pom.xml src/       # only removals: two plugin blocks, two properties, two imports
mvn test                               # 102 tests, 0 failures/errors/skips
mvn -DskipTests package                # target/petclinic.war builds
rg -n "eclipse-plugin|assembly-plugin|SpringJUnit4ClassRunner" pom.xml src/   # no matches
rg -n "jib-maven-plugin" pom.xml       # still present, unchanged
```

### Dependency vulnerability review

Documentation only. `DEPENDENCY_RISK_REVIEW.md` reviews the graph resolved by `mvn dependency:tree` (JDK 21,
default H2 profile), plus the optional profile drivers and build plugins. It uses one-time public advisory
lookups on 2026-10-16 (OSV, spring.io, endoflife.date, the Jib docs). No version, scope, or code changed, and no
scanner or runtime network call was added.

- Advisories found on shipped dependencies:
  - spring-webmvc 6.2.19: two Critical advisories with no OSS 6.2 fix (the app uses neither SSE nor `XsltView`).
  - Jackson 2.19.4: the 2.19 line receives no fixes.
  - logback 1.2.6: unmaintained line, including High CVE-2023-6378.
  - H2 2.1.214: CVE-2022-45868.
  - Bootstrap 3.3.6 and jQuery UI 1.12.1: XSS advisories.
- Test-only dependencies with advisories (assertj, json-path/json-smart) are recommended upgrades.
- The HSQLDB, MySQL, and PostgreSQL profile drivers all have advisories and need sign-off before use.
- The bank sign-off register has 11 items:
  1. Cross-owner pet edit.
  2. No authentication or authorization.
  3. Raw exception message in `exception.jsp`.
  4. Demo credentials and PII-like seed data.
  5. Debug and SQL logging.
  6. No audit trail.
  7. Spring 6.2 OSS end of support (2026-06-30; commercial support needed).
  8. Spring Data 2025.0 / Hibernate 6.6 lifecycle.
  9. Stale Jib base image (`gcr.io/distroless/java/jetty`, `javax`).
  10. Behavior-preservation pins.
  11. Compile-scope JUnit Jupiter / Mockito jars shipped in `WEB-INF/lib`. The recommended test-scope fix is
      documented and not applied.

Risks: advisory data reflects the lookup date only, and every disposition other than "keep" needs a bank decision.
Reviewer verification: follow the "How a reviewer verifies" section of `DEPENDENCY_RISK_REVIEW.md`, and confirm
`git diff HEAD~1 -- pom.xml src/` is empty for the review commit.

## Milestone 5 — Spring Boot 3.5.16 dependency management (Boot foundation)

Recorded 2026-11-03 on branch `migration/boot-foundation`, which starts from `mission-demo` commit `bae1eba`.
This is the first of two Boot 3 milestones. It changes dependency management only. The application still
starts through `PetclinicInitializer` under the Jetty EE10 Maven plugin. The Boot runtime, the Boot Maven
plugin and the executable WAR come in the next milestone.

### What changed

`pom.xml` only (commit `d190fad`). No Java, XML, JSP, resource, test or workflow file changed.

- `org.springframework.boot:spring-boot-dependencies` 3.5.16 is imported as a BOM in `dependencyManagement`,
  after the existing Jackson 2.19.4 and Spring Data 2025.0.13 BOMs. The Boot parent POM is **not** used, so
  resource filtering, compiler settings (`release` 21, `parameters`), Surefire includes, wro4j, WAR name,
  plugins and the H2/HSQLDB/MySQL/PostgreSQL profiles are unchanged. No Boot jar is on the classpath yet.
- `org.junit:junit-bom` `${junit-jupiter.version}` (5.14.4) is imported before the Boot BOM. Without it Boot
  would move the JUnit Platform family (including the Surefire launcher) back to 5.12.2/1.12.2.
- The bare Boot BOM import moved several transitive versions. Each one is pinned back in
  `dependencyManagement` to the version resolved at `bae1eba`:

  | Artifact | Scope in graph | Pinned at (baseline) |
  | --- | --- | --- |
  | `com.fasterxml:classmate` | compile | 1.5.1 |
  | `jakarta.annotation:jakarta.annotation-api` | compile | 2.0.0 |
  | `org.jboss.logging:jboss-logging` | compile | 3.5.0.Final |
  | `org.jspecify:jspecify` | compile | 1.0.1 |
  | `net.bytebuddy:byte-buddy-agent` | test | 1.11.19 |
  | `net.minidev:json-smart`, `accessors-smart` | test | 2.4.7 |
  | `org.ow2.asm:asm` | test | 9.1 |

- **Approved logging exception (the only existing-pin change):**

  | Artifact | From | To | Scope (unchanged) |
  | --- | --- | --- | --- |
  | `org.slf4j:slf4j-api` | 1.7.32 | 2.0.18 | compile |
  | `ch.qos.logback:logback-classic` | 1.2.6 | 1.5.34 | runtime |
  | `ch.qos.logback:logback-core` | 1.2.6 | 1.5.34 | runtime (now managed explicitly) |

  `src/main/resources/logback.xml` is byte-identical (sha256 `50edb985…68ae4` in source and `target/classes`).
  Log levels are unchanged, including the `debug` level for `org.springframework.samples.petclinic`.

### Why

- The Boot BOM is the dependency foundation for the Boot runtime cutover. Importing it without the parent keeps
  every existing build behavior.
- Logging: in an isolated readiness build, Boot 3.5.16 failed to start with the pinned Logback 1.2.6 and SLF4J
  1.7.32. It failed even with `org.springframework.boot.logging.LoggingSystem=none`, before Jetty started:
  `java.lang.AbstractMethodError: Receiver class org.springframework.boot.logging.logback.RootLogLevelConfigurator
  does not define or inherit an implementation of the resolved method 'abstract void
  configure(ch.qos.logback.classic.LoggerContext)' of interface ch.qos.logback.classic.spi.Configurator`.
  `spring-boot.jar` registers that configurator through `META-INF/services`, and Logback 1.2 calls the older
  method signature. The user approved SLF4J 2.0.18 / Logback 1.5.34 as the only existing-pin exception. With
  that stack, the same readiness build started and used the unchanged `logback.xml`. The upgrade is made here,
  alone, so that reviewers see it separately from the runtime change.

### Results (JDK Temurin 21.0.13+9, Maven wrapper 3.9.16)

- `./mvnw clean test` → `Tests run: 102, Failures: 0, Errors: 0, Skipped: 0`, BUILD SUCCESS. Each of the
  `jdbc`, `jpa` and `spring-data-jpa` service test classes runs 13 tests. No test file changed.
- `./mvnw -DskipTests package` → BUILD SUCCESS, `target/petclinic.war`.
- `./mvnw dependency:list` for `-PH2`, `-PHSQLDB`, `-PMySQL` and `-PPostgreSQL`: compared with `bae1eba`, the only
  differences are `slf4j-api` 2.0.18 and `logback-classic`/`logback-core` 1.5.34. No scope changed and no
  artifact was added or removed. `tomcat-jdbc`/`tomcat-juli` stay 9.0.46 (the Boot BOM's own Tomcat 10.1.55
  management does not reach them, because `tomcat-jdbc` is declared with an explicit version).
- WAR inventory (62 jar files in `WEB-INF/lib`, same count as baseline): only the three logging jars differ. The
  compile-scope JUnit/Mockito jars still ship (flag-only item, unchanged). No Boot jar ships.
  *Count corrected on 2026-11-03:* this line first said 63 jars. That number counted the `WEB-INF/lib/` directory
  entry of the archive listing. Baseline and foundation WARs both contain 62 jar files; the jar populations and the
  three logging replacements were unaffected.
- Effective POM outside `dependencyManagement`: only the new version properties and the two logging versions
  differ. No parent, plugin or profile change. The filtered `data-access.properties` is identical.
- `./mvnw -DskipTests jetty:run-war -Djetty.http.port=8080` (jetty-ee10-maven-plugin 12.0.40) starts. Five smoke
  GETs return 200: `/` (Welcome), `/owners?lastName=Davis` (Betty Davis, Harold Davis), `/owners/2` (Betty
  Davis, Basil, hamster), `/vets.xml` and `/vets.json` (six vets).
- Browser (agent-browser, read-only): home → Find owners → Davis search → Betty Davis → home → Veterinarians.
  Every request went to `localhost` with status 200, `petclinic.css` loaded, there were no console messages,
  and the only page error on each page was the approved
  `Bootstrap's JavaScript requires jQuery version 1.9.1 or higher, but lower than version 3`.

### Risks

- The Boot BOM manages many more artifacts than this project uses. A dependency added later without an explicit
  version takes Boot's version. The next milestone adds Boot starters, so its dependency lists must be compared
  again with these foundation lists, and any further drift needs a pin or explicit approval.
- Logging behavior is preserved, not hardened: `logback.xml` (with its `debug` level) and `jpa.showSql=true` in
  `data-access.properties` are unchanged. The debug/SQL logging bank risk flag stays flag-only.
- Spring Boot 3.5 OSS support ended on 2026-06-30, so Boot 3.5.16 gets no further OSS releases. Using it here is
  a flag-only lifecycle risk. It is not production risk acceptance and does not imply commercial support.
- Unchanged and still flag-only: cross-owner pet edit, no authentication/authorization, raw exception page,
  demo credentials and synthetic PII-like seed data, debug/SQL logging, no audit trail, compile-scope test jars
  in the WAR, stale Jib base image. The visit-list route (`/owners/{ownerId}/pets/{petId}/visits`) still returns
  HTTP 404 because `visitList.jsp` does not exist; this is preserved baseline behavior.

### How a reviewer verifies

```sh
export JAVA_HOME=/home/factory-user/.m2/tools/jdk-21.0.13+9 && ./mvnw clean test   # 102, 0 failures/errors/skips
export JAVA_HOME=/home/factory-user/.m2/tools/jdk-21.0.13+9 && ./mvnw -DskipTests package
git diff bae1eba --stat                                     # pom.xml plus these two notes files
git diff bae1eba -- src .github                             # empty
for p in H2 HSQLDB MySQL PostgreSQL; do
  export JAVA_HOME=/home/factory-user/.m2/tools/jdk-21.0.13+9 && ./mvnw -q -P$p dependency:list -DoutputFile=/tmp/deps-$p.txt
done                                                         # repeat on bae1eba and diff: only slf4j-api/logback-* differ
unzip -Z1 target/petclinic.war | grep -E 'slf4j|logback|spring-boot'   # slf4j-api-2.0.18, logback-*-1.5.34, no spring-boot
sha256sum src/main/resources/logback.xml                    # same as on bae1eba
export JAVA_HOME=/home/factory-user/.m2/tools/jdk-21.0.13+9 && ./mvnw -DskipTests jetty:run-war -Djetty.http.port=8080
curl -s "http://localhost:8080/owners?lastName=Davis"       # in another shell: Betty Davis + Harold Davis
```

Adjust `JAVA_HOME` to your own JDK 21 folder; see `AGENTS.md`.
