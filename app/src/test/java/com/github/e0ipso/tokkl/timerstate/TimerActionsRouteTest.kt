package com.github.e0ipso.tokkl.timerstate

import com.github.e0ipso.tokkl.ClassReferences
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Timer actions go to the server only, never through the change queue. The test reads the
 * constant pool of the actions' compiled classes through [ClassReferences]; core:data's
 * TimerActionsTest checks what the actions send.
 */
class TimerActionsRouteTest {

    @Test
    fun actionsNeverGoThroughTheChangeQueue() {
        val classes = ClassReferences.app.filterKeys { name -> OWN.any { name == it || name.startsWith("$it$") } }
        assertTrue("the scan found the actions", "$PACKAGE/timerstate/TimerActions" in classes)

        val offenders = classes.flatMap { (name, references) ->
            (references.classes + references.members.map { it.owner })
                .filter { reference -> FORBIDDEN.any { reference.startsWith("$PACKAGE/$it") } }
                .map { "$name uses $it" }
        }

        assertTrue("Timer actions go to the server only:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }

    private companion object {
        const val PACKAGE = "com/github/e0ipso/tokkl"

        /** The classes of the actions and their results. */
        val OWN = listOf("$PACKAGE/timerstate/TimerActions", "$PACKAGE/timerstate/TimerActionResult", "$PACKAGE/timerstate/SessionLengths")

        /** The change queue, its store and its sender. */
        val FORBIDDEN = listOf(
            "cache/ChangeQueue",
            "cache/ChangeStore",
            "cache/RoomChangeStore",
            "cache/ChangeDao",
            "cache/ChangeSender",
            "cache/PendingChange",
            "cache/BackgroundSend",
            "cache/EntryChanges",
        )
    }
}
