package com.github.e0ipso.tokkl

import android.os.Bundle
import org.junit.FixMethodOrder
import org.junit.runner.Description
import org.junit.runner.manipulation.Filter

/**
 * Splits the suite into shards as AndroidJUnitRunner's `numShards` does, except that a class
 * with [FixMethodOrder] stays whole in one shard. Those classes are restart tests whose steps
 * hand state to the next step in a new process, so a step on another emulator finds nothing.
 * Every other test is placed by the hash of its own description, as the runner places it.
 *
 * [com.github.e0ipso.tokkl.accessibility.AccessibilityTestRunner] installs it in place of the
 * runner's `numShards` and `shardIndex`, which reach it as [SHARD_COUNT] and [SHARD_INDEX].
 */
class StepAwareShardingFilter(arguments: Bundle) : Filter() {
    private val count = arguments.getString(SHARD_COUNT)?.toIntOrNull()
        ?: error("$SHARD_COUNT is missing")
    private val index = arguments.getString(SHARD_INDEX)?.toIntOrNull()
        ?: error("$SHARD_INDEX is missing")

    init {
        require(count > 0 && index in 0 until count) { "shard $index of $count does not exist" }
    }

    override fun shouldRun(description: Description): Boolean {
        if (!description.isTest) return description.children.any(::shouldRun)
        val ordered = description.testClass?.isAnnotationPresent(FixMethodOrder::class.java) == true
        val key = if (ordered) description.className.hashCode() else description.hashCode()
        return Math.floorMod(key, count) == index
    }

    override fun describe() = "step-aware shard $index of $count"

    companion object {
        const val SHARD_COUNT = "tokklShardCount"
        const val SHARD_INDEX = "tokklShardIndex"
    }
}
