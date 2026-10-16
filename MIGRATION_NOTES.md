# Migration Notes

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
