---
type: map
title: 'Quick Settings tile: default listening and cached actions'
description: >-
  RunningTileService follows the cache while visible and acts on the state it
  displayed.
tags:
  - android
  - tile
  - offline
kk_schema_version: 3
kk_id: map-quick-settings-tile-default-listening-and-cached-actions
kk_derived_from:
  - '9c8de91d-411b-498c-b4ed-241fabaa877e:map:3'
kk_relates_to:
  - map-change-queue-sends-entry-changes-in-order
  - practice-never-wait-for-server-on-start-stop
kk_depends_on: []
kk_confidence: high
---
`app/src/main/java/com/github/e0ipso/tokkl/tile/RunningTileService.kt` is a default, non-active Quick Settings tile. Each `onStartListening` starts a fresh cache collection, and `onStopListening` cancels it. Android binds it when the shade shows it. `requestListeningState` is for active tiles and does not refresh this listening mode.

The tile stops its displayed running entry, or continues the latest stopped entry when idle, through the offline change queue. The tap uses the displayed state rather than guessing from a newer cache value; before the first emission it takes no action. The action has its own scope, so stopping the listening interval does not cancel a queued write.

<!-- kk:related:start -->
# Related

- Related: [map-change-queue-sends-entry-changes-in-order](../data-and-offline/map-change-queue-sends-entry-changes-in-order.md)
- Related: [practice-never-wait-for-server-on-start-stop](../data-and-offline/practice-never-wait-for-server-on-start-stop.md)
<!-- kk:related:end -->

<!-- kk:citations:start -->
# Citations

[1] 9c8de91d-411b-498c-b4ed-241fabaa877e:map:3
<!-- kk:citations:end -->
