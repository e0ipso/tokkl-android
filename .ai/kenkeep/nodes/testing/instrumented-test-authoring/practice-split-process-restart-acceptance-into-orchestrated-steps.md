---
type: practice
title: Split process restart acceptance into orchestrated steps
description: >-
  Instrumentation cannot survive killing its own process, so restart assertions
  span isolated steps.
tags:
  - testing
  - android
  - lifecycle
kk_schema_version: 3
kk_id: practice-split-process-restart-acceptance-into-orchestrated-steps
kk_derived_from:
  - '9c8de91d-411b-498c-b4ed-241fabaa877e:practice:3'
kk_relates_to:
  - practice-use-the-instrumented-runner-and-its-device-selection
kk_depends_on: []
kk_confidence: high
---
A test cannot kill the app's process and then check what survived, because the instrumentation runs in that process and dies with it. Restart tests split into steps instead, and the orchestrator runs each step in a new process.

<!-- kk:related:start -->
# Related

- Related: [practice-use-the-instrumented-runner-and-its-device-selection](../instrumented-runner/practice-use-the-instrumented-runner-and-its-device-selection.md)
<!-- kk:related:end -->

<!-- kk:citations:start -->
# Citations

[1] 9c8de91d-411b-498c-b4ed-241fabaa877e:practice:3
<!-- kk:citations:end -->
