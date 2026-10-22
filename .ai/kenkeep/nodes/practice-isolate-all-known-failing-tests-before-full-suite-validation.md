---
type: practice
title: Isolate all known failing tests before full-suite validation
description: >-
  Inventory every failure, reproduce it in a focused run, and iterate there
  before validating the full suite.
tags:
  - testing
  - ci
  - agents
kk_schema_version: 3
kk_id: practice-isolate-all-known-failing-tests-before-full-suite-validation
kk_derived_from: []
kk_relates_to:
  - practice-run-instrumented-tests-only-through-the-script
  - practice-reuse-drain-checks-by-content-and-test-configuration
kk_depends_on: []
kk_confidence: high
---
When repairing Tokkl Android tests, first inventory the complete set of failed or errored tests from the available JUnit reports and logs. Retain the run, revision, and runtime or API level for each failure. Do not infer the failure set from the first stack trace or treat a still-running job as a complete report. This policy applies to all JVM, host, UI, instrumented, and CI test failures on every supported API level.

Create a reproducible focused run selecting those exact test cases and the minimal fixture dependencies, setup, or restart steps they require. For instrumented tests, use `scripts/instrumented-tests.sh` with exact `class#method` filters and the failing AVD. Use the equivalent focused selectors for JVM and host tests.

Iterate diagnosis and fixes on this subset, checking that every selected case ran and passed. Add new failures as they are discovered, and preserve assertions and prerequisites. Do not wait hours for a full suite to finish between repair iterations.

Once the focused run is green, run the affected surrounding tests and required broader or full-suite checks to validate the integrated fix.

<!-- kk:related:start -->
# Related

- Related: [practice-run-instrumented-tests-only-through-the-script](testing/practice-run-instrumented-tests-only-through-the-script.md)
- Related: [practice-reuse-drain-checks-by-content-and-test-configuration](testing/practice-reuse-drain-checks-by-content-and-test-configuration.md)
<!-- kk:related:end -->
