---
type: map
title: External navigation requests and intent consumption
description: >-
  Features decode serializable taps, consume intent extras once, and retain
  pending requests through configuration changes and process death.
tags:
  - android
  - navigation
  - ui
kk_schema_version: 3
kk_id: map-external-navigation-requests-and-intent-consumption
kk_derived_from: []
kk_relates_to:
  - map-navigation-route-serialization-and-feature-registration
  - map-navigation-stores-and-restored-session-guard
kk_depends_on: []
kk_confidence: high
---
The taps from outside the app reach MainActivity as intents. Each feature that owns the page a tap opens declares the tap as a `@Serializable` `NavigationRequest` and binds a `FeatureRequests`, such as `TimerRequests`, `ReportsRequests` or `PomodoroRequests`, that registers its serializer (`registerRequests`), reads it from an intent's action and extras (`readLinks`, through a `LinkIntent` with no Android in it), names the extras it reads (`linkExtras`) and opens it (`OpenRequest`); a feature that no tap opens binds none: Timer opens the Timer widget's tap and the reminder's, Reports the widget's day report, and Pomodoro its widget's tap and a session notification's tap for its account and phase. Each tap's extra is a `PageTap` in core:navigation: the widget and the reminder add it to their intents with `PageTap.into`, and the feature that opens the page reads it with `PageTap.isIn`, so both sides share one name. `navhost/DeepLinks` asks every feature, and `takeDeepLinks` removes the extras so a rotation opens nothing again. What still waits sits in `NavigationRequests`, MainActivity's view model, which keeps it in its `SavedStateHandle` with the features' request serializers, so it outlives a configuration change and the process; a request an older version saved and this one no longer knows waits no more. One request of each class waits at a time.

<!-- kk:related:start -->
# Related

- Related: [map-navigation-route-serialization-and-feature-registration](map-navigation-route-serialization-and-feature-registration.md)
- Related: [map-navigation-stores-and-restored-session-guard](map-navigation-stores-and-restored-session-guard.md)
<!-- kk:related:end -->
