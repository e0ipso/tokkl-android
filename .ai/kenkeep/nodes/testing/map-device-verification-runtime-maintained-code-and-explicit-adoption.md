---
type: map
title: 'Device verification runtime: maintained code and explicit adoption'
description: >-
  scripts/device_verification maintains the controller; execution requires a
  frozen matching configuration, adoption and lease.
tags:
  - testing
  - agents
  - tooling
kk_schema_version: 3
kk_id: map-device-verification-runtime-maintained-code-and-explicit-adoption
kk_derived_from:
  - '9c8de91d-411b-498c-b4ed-241fabaa877e:map:7'
kk_relates_to:
  - practice-run-instrumented-tests-only-through-the-script
  - practice-reuse-drain-checks-by-content-and-test-configuration
  - practice-drain-sittings-gate-on-memory-not-a-sitting-cap
kk_depends_on: []
kk_confidence: high
---
`scripts/device_verification/runtime/` is the maintained device-verification implementation. `scripts/device_verification/README.md` documents it. `prepare.py` in that package freezes a runtime snapshot and configuration; `preflight.py` and `audit_plan.py` inspect configuration, inputs and the finite plan. Preparation does not authorize execution.

Execution requires coordinator adoption matching the frozen configuration and runtime hashes and lease ID, with evidence of the required cheap live controller fixtures. Changed source or recipes need a newly reviewed configuration and matching adoption. An active lease keeps its immutable helpers; maintained files do not replace them in place. The `.txt` controller and reclaim copies under `provenance/` are review references and must not be executed.

`runtime/scoped_results.py` validates scoped app results against their declared recipe and frozen input hashes. A scoped result is not a full gate. Operational completion and product test success remain separate claims; both positive and failed observations belong in adoption evidence.

<!-- kk:related:start -->
# Related

- Related: [practice-run-instrumented-tests-only-through-the-script → practice-keep-instrumented-credentials-out-of-process-arguments](instrumented-runner/practice-keep-instrumented-credentials-out-of-process-arguments.md)
- Related: [practice-run-instrumented-tests-only-through-the-script → practice-pass-class-and-package-lists-through-runner-filter-files](instrumented-runner/practice-pass-class-and-package-lists-through-runner-filter-files.md)
- Related: [practice-run-instrumented-tests-only-through-the-script → practice-use-the-instrumented-runner-and-its-device-selection](instrumented-runner/practice-use-the-instrumented-runner-and-its-device-selection.md)
- Related: [practice-run-instrumented-tests-only-through-the-script → practice-use-the-pinned-test-server-and-tls-proxies](instrumented-runner/practice-use-the-pinned-test-server-and-tls-proxies.md)
- Related: [practice-run-instrumented-tests-only-through-the-script → practice-verify-device-test-results-and-process-isolation-receipts](instrumented-runner/practice-verify-device-test-results-and-process-isolation-receipts.md)
- Related: [practice-reuse-drain-checks-by-content-and-test-configuration](practice-reuse-drain-checks-by-content-and-test-configuration.md)
- Related: [practice-drain-sittings-gate-on-memory-not-a-sitting-cap](../build-and-tooling/practice-drain-sittings-gate-on-memory-not-a-sitting-cap.md)
<!-- kk:related:end -->

<!-- kk:citations:start -->
# Citations

[1] 9c8de91d-411b-498c-b4ed-241fabaa877e:map:7
<!-- kk:citations:end -->
