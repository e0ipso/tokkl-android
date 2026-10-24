package com.github.e0ipso.tokkl.reports

/** Deliberately not reconstructible from the buckets/groups, which must not replace totals. */
internal val APP_SUMMARY_FIXTURE = """{
  "start_date":"2026-10-21","end_date":"2026-10-27","resolution":"week","week_start":"2026-10-21",
  "totals":{"seconds":7317,"billable_seconds":6001,"entry_count":9,
    "amounts":[{"currency":"XYZ","cents":101},{"currency":"USD","cents":0},{"currency":"EUR","cents":723}]},
  "series":[{"start":"2026-10-28","seconds":3,"billable_seconds":1},{"start":"2026-10-21","seconds":7,"billable_seconds":5}],
  "groups":[
    {"id":2,"title":"Small first","color":"#123456","seconds":3,"billable_seconds":1,"amount_cents":0,"sub_groups":[]},
    {"id":1,"title":"Large second","color":"#abcdef","seconds":7,"billable_seconds":5,"sub_groups":[]},
    {"id":null,"title":"Server label","color":null,"seconds":2,"billable_seconds":0,"amount_cents":null,"sub_groups":[]}
  ]
}"""
