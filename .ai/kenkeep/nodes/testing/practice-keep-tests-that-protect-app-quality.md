---
type: practice
title: Keep tests that protect app quality
description: >-
  Keep tests that can reveal app regressions; drop checks of upstream, language
  features, test helpers or trivia.
tags:
  - testing
  - quality
kk_schema_version: 3
kk_id: practice-keep-tests-that-protect-app-quality
kk_derived_from:
  - '19763361-1d08-4efb-a116-177429a8efa4:practice:0'
kk_relates_to:
  - practice-isolate-all-known-failing-tests-before-full-suite-validation
  - practice-test-against-a-real-tokkl-server
kk_depends_on: []
kk_confidence: high
---
Keep a Tokkl Android test when it checks useful app behavior and can reveal an app regression. Assess each test against that purpose.

Drop tests that only exercise upstream behavior, language features, test infrastructure or trivia. When such a test fails, delete it rather than repairing a check that does not protect app quality.

<!-- kk:related:start -->
# Related

- Related: [practice-isolate-all-known-failing-tests-before-full-suite-validation](../practice-isolate-all-known-failing-tests-before-full-suite-validation.md)
- Related: [practice-test-against-a-real-tokkl-server](practice-test-against-a-real-tokkl-server.md)
<!-- kk:related:end -->

<!-- kk:citations:start -->
# Citations

[1] 19763361-1d08-4efb-a116-177429a8efa4:practice:0
<!-- kk:citations:end -->
