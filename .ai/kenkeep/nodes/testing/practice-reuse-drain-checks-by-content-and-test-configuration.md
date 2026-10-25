---
type: practice
title: Reuse drain checks by content and test configuration
description: >-
  Commit IDs and cherry-picks do not invalidate checks when all checked inputs
  and the test environment are unchanged.
tags:
  - sift
  - testing
  - agents
kk_schema_version: 3
kk_id: practice-reuse-drain-checks-by-content-and-test-configuration
kk_derived_from: []
kk_relates_to:
  - practice-run-instrumented-tests-only-through-the-script
kk_depends_on: []
kk_confidence: high
---
Record each Tokkl Android drain check's content fingerprint, command, device/API, pinned server image, exit code, test counts and durable log path.

Reuse passing evidence after a cherry-pick or documentation-only commit only when all checked inputs and the test configuration match. Otherwise rerun the affected checks. Commit IDs alone cannot identify duplicate device runs.

<!-- kk:related:start -->
# Related

- Related: [practice-run-instrumented-tests-only-through-the-script → practice-keep-instrumented-credentials-out-of-process-arguments](instrumented-runner/practice-keep-instrumented-credentials-out-of-process-arguments.md)
- Related: [practice-run-instrumented-tests-only-through-the-script → practice-pass-class-and-package-lists-through-runner-filter-files](instrumented-runner/practice-pass-class-and-package-lists-through-runner-filter-files.md)
- Related: [practice-run-instrumented-tests-only-through-the-script → practice-use-the-instrumented-runner-and-its-device-selection](instrumented-runner/practice-use-the-instrumented-runner-and-its-device-selection.md)
- Related: [practice-run-instrumented-tests-only-through-the-script → practice-use-the-pinned-test-server-and-tls-proxies](instrumented-runner/practice-use-the-pinned-test-server-and-tls-proxies.md)
- Related: [practice-run-instrumented-tests-only-through-the-script → practice-verify-device-test-results-and-process-isolation-receipts](instrumented-runner/practice-verify-device-test-results-and-process-isolation-receipts.md)
<!-- kk:related:end -->
