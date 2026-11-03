# AGENTS.md — Agent operating bar for this repository

This repository is treated as a compliance-sensitive legacy banking workload (the petclinic
domain stands in for a core customer-record system). Any agent working here operates under
change control: behavior preservation outranks feature work, and evidence is part of the
deliverable. If a task seems to require breaking a rule below, stop and report the conflict
instead of working around it.

## What this codebase is

Spring Framework Petclinic: Spring Boot 3.5.16 hosting Spring Framework / Spring MVC 6.2.19 on
Java 21, Jakarta EE 10 namespaces, JSP views, three persistence profiles (JPA default / JDBC /
Spring Data JPA) with H2 in memory by default. It is packaged as an executable WAR
(`target/petclinic.war`) on embedded Jetty 12.0.40 EE10; the Jetty EE10 Maven plugin 12.0.40 still
runs the same WAR as a compatibility path. `PetclinicInitializer` (a `SpringBootServletInitializer`)
is the only initializer: the Boot root context imports `business-config.xml` and
`tools-config.xml`, and one `DispatcherServlet` on `/` owns a child context loaded from
`mvc-core-config.xml`. XML stays authoritative; overlapping Boot auto-configurations are excluded.
Logging is SLF4J 2.0.18 / Logback 1.5.34 (the one user-approved pin exception) with the unchanged
`logback.xml`. The migration history is in `MIGRATION_NOTES.md` (Milestones 1–6); the dependency
risk register is in `DEPENDENCY_RISK_REVIEW.md`. Read both before proposing structural changes.

Branch topology:

- `master` — upstream history; never push here.
- `mission-demo` — the trunk the Boot 3 migration mission starts from (this branch).
- `legacy` — pinned at `4bac3bd` (Spring 5.3.22, JDK 8, `javax`, 62 tests). This is the
  readiness-scoring baseline. Do not modify it; remediation of `legacy` happens live on
  stage, not as commits here.

## Build and test (exact commands, verified)

Requires JDK 21 only. Maven comes from the wrapper, so use `./mvnw` from the repo root. Point
`JAVA_HOME` at your JDK 21 (the folder name depends on the machine; check `ls ~/.m2/tools`) in the
same command as `./mvnw` or `java`:

```sh
# Linux, Temurin unpacked under ~/.m2/tools (adjust the version folder to match what you installed)
# macOS: the JDK sits under .../<jdk-folder>/Contents/Home, e.g. "$HOME/.m2/tools/jdk-21.0.12.1+1/Contents/Home"
export JAVA_HOME="$HOME/.m2/tools/jdk-21.0.13+9" && "$JAVA_HOME/bin/java" -version   # must report 21.x

export JAVA_HOME="$HOME/.m2/tools/jdk-21.0.13+9" && ./mvnw clean test                # expect: Tests run: 102, Failures: 0, Errors: 0, Skipped: 0
export JAVA_HOME="$HOME/.m2/tools/jdk-21.0.13+9" && ./mvnw -DskipTests package      # executable target/petclinic.war

# Primary runtime: the executable WAR (stop with Ctrl-C; from another directory, pass the WAR's full path)
export JAVA_HOME="$HOME/.m2/tools/jdk-21.0.13+9" && "$JAVA_HOME/bin/java" -jar target/petclinic.war --server.address=localhost --server.port=8080
#   add --spring.profiles.active=jdbc or --spring.profiles.active=spring-data-jpa; the default profile is jpa

# Compatibility path: the same WAR under the Jetty EE10 Maven plugin, app on http://localhost:8080
export JAVA_HOME="$HOME/.m2/tools/jdk-21.0.13+9" && ./mvnw -DskipTests jetty:run-war
```

If `./mvnw` reports "JAVA_HOME is not defined correctly", `JAVA_HOME` is unset or points at the wrong
folder. Fix that first; do not treat it as a test failure. In a non-interactive shell, run the `export`
in the same command as `./mvnw`, because exports do not carry over between separate shell sessions.

Smoke checks against the running app (H2 seeded data):

```sh
curl -s http://localhost:8080/                        # Welcome page
curl -s "http://localhost:8080/owners?lastName=Davis" # Betty Davis + Harold Davis
curl -s http://localhost:8080/owners/2               # Betty Davis, pet Basil (hamster)
curl -s http://localhost:8080/vets.xml; curl -s http://localhost:8080/vets.json
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/owners/2/pets/2/visits   # 404, see below
```

## The behavior contract

The 102-test characterization suite in `src/test` is the behavior contract for this system.
It pins current behavior, including deliberate quirks (see "Risks" in `MIGRATION_NOTES.md`).
A change is done when all 102 tests pass **without editing any existing test's assertions**
(adding new tests is allowed and encouraged). Specifically pinned:

- Date binding: `Pet.birthDate` and `Visit.date` bind `yyyy/MM/dd` only. ISO input
  (`2021-06-15`) must be rejected with the `typeMismatch` "invalid date" error; `2021/06/15`
  must be accepted. This relies on the `fallbackPatterns` declarations — do not remove them.
- Cross-owner pet edit (`testCrossOwnerPetEditSucceedsAsIs_bankRiskFlag`): a known
  authorization flaw, found during characterization, pinned as-is. Do not silently fix it;
  it is flag-only for the bank's risk register.
- Telephone `@Digits(integer=10)` counts significant digits, so the 11-character seeded
  number `01316761638` is valid. Do not "correct" the validator.
- Pet edit binds the pet from the posted (hidden) `id`, not from the URL's owner record.
- `VisitController`'s `@ModelAttribute("visit")` attaches an unsaved visit dated today to
  the loaded pet on every request, including the visit list.

## Governance rules

1. **Never push to `master` or `main` and never force-push any branch.** Work on a feature
   branch; open a PR; a human merges.
2. No new runtime dependency, version bump, or scope change without stating the reason and
   the verification in the PR description. Prefer the versions managed by the existing BOMs.
3. Do not change routes, view names, validation messages, or the accepted/rejected input
   formats pinned above. The migration is not done until the contract is green.
4. No credentials, keys, or real PII in code, configs, logs, or commits. The seeded data is
   synthetic and stays synthetic.
5. Keep the working tree clean: one logical change per commit, described migration-note
   style (what changed, why, how a reviewer verifies).
6. Do not modify `.github/workflows/maven-build.yml` triggers without approval.

## Approved exception: the one allowed page error

Baseline browser validation tolerates exactly one page error, unchanged from before the
Spring 6.2 migration:

> `Bootstrap's JavaScript requires jQuery version 1.9.1 or higher, but lower than version 3`

Any other console or page error, or any request to a host other than `localhost`, is a
regression. Webjars (Bootstrap 3.3.6, jQuery 3.5.1, jQuery UI 1.12.1) are pinned as-is.

## Approved baseline behavior: the visit-list 404

`GET /owners/{ownerId}/pets/{petId}/visits` returns HTTP 404 because the handler returns the view
`visitList` and `visitList.jsp` does not exist. This is baseline behavior, preserved on purpose with
user approval. Check it over HTTP; do not add the JSP or describe the route as rendering.

Server-side startup noise (Logback status lines, the Jetty "aliased" base-resource WARN,
`DigesterFactory` schema WARNs) is not a browser error and is not in scope for cleanup.

## Flag-only items (report, do not fix)

Known, documented, and intentionally not changed here: cross-owner pet edit; no
authentication/authorization; raw exception page in `exception.jsp`; demo credentials and
PII-like seed data; debug and SQL logging; no audit trail; compile-scope test jars shipping
in the WAR; the stale Jib base image; the ended OSS support of Spring Boot 3.5 / Spring
Framework 6.2 (lifecycle only, not production risk acceptance). The SLF4J/Logback upgrade was a
compatibility exception and does not change log levels. Full register: `DEPENDENCY_RISK_REVIEW.md`.
