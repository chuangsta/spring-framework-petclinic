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

### Risks

- Tests pin current behavior, including quirks. Example: `@Digits(integer=10)` counts significant digits,
  so the existing 11-character telephone `01316761638` (leading zero) is accepted. A migration that changes
  this will fail the existing create test, which is intended.
- Controller tests use a mocked `ClinicService`; persistence behavior is covered only by the service-level
  profile tests.

### How a reviewer verifies

```sh
export PATH=/Users/chuangzhu/.m2/tools/apache-maven-3.9.16/bin:$PATH
export JAVA_HOME=/Users/chuangzhu/.m2/tools/jdk-21.0.12.1+1/Contents/Home && mvn test
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home && mvn test
mvn test -Dtest='OwnerControllerTests,ClinicService*Tests'   # focused owner coverage
git diff 4bac3bd --stat -- src/main pom.xml                  # expect no output (test-only diff)
```
