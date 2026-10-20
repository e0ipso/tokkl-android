package com.github.e0ipso.tokkl.benchmark

// Runs of each benchmark. CI fails a median more than 10% above the last green run on main, so
// the medians must hold still from run to run. On tokkl-api36, ten cold starts varied by 7%
// around their median and ten Stop taps by 38%, so the taps, which take about two seconds a
// run against ten for a cold start, run more often.

/** Cold starts, about ten seconds each. */
const val START_ITERATIONS = 15

/** Start and Stop tap pairs, about two seconds each. */
const val TAP_ITERATIONS = 30
