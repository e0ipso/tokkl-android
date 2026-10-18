package com.github.e0ipso.tokkl.time

import com.github.e0ipso.tokkl.ClassReferences
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app shows and reads times in the account's zone only (docs/PRD.md S7), so no app class
 * reads the device's zone: no `ZoneId.systemDefault()`, no `TimeZone.getDefault()`, no `now()`
 * without a clock and no Calendar without a zone. Screens take all of it from [AccountTime].
 * The test reads the constant pool of every compiled app class through [ClassReferences].
 */
class NoDeviceZoneTest {

    @Test
    fun noAppClassReadsTheDeviceZone() {
        assertTrue("the scan found the app's classes", "com/github/e0ipso/tokkl/time/AccountTime" in ClassReferences.app)

        val offenders = ClassReferences.app
            .filterKeys { it !in ALLOWED }
            .flatMap { (name, references) -> references.members.filter(::readsDeviceZone).map { "$name uses $it" } }

        assertTrue(
            "Take the zone, the clock and the week from AccountSettings.time() instead:\n" + offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    /** Shows the scan finds each way in, so the test above cannot pass by finding nothing. */
    @Test
    fun scanSeesEveryWayToReadTheDeviceZone() {
        val found = references(DeviceZoneReader::class.java).members
            .filter(::readsDeviceZone)
            .map { "${it.owner}.${it.name}" }
            .toSet()

        assertEquals(
            setOf(
                "java/time/ZoneId.systemDefault",
                "java/util/TimeZone.getDefault",
                "android/icu/util/TimeZone.getDefault",
                "java/time/Clock.systemDefaultZone",
                "java/time/LocalDate.now",
                "java/time/LocalDateTime.now",
                "java/time/ZonedDateTime.now",
                "java/util/Calendar.getInstance",
                "java/util/GregorianCalendar.<init>",
            ),
            found,
        )
    }

    /** A zone or a clock passed in is how [AccountTime] works, so the scan lets it through. */
    @Test
    fun scanLetsAGivenZoneOrClockThrough() {
        val members = references(GivenZoneReader::class.java).members

        assertTrue("the scan sees the calls", members.any { it.owner == "java/time/LocalDate" && it.name == "now" })
        assertEquals(emptyList<ClassReferences.Member>(), members.filter(::readsDeviceZone))
    }

    private fun references(type: Class<*>): ClassReferences.References =
        ClassReferences.read(type).getValue(type.name.replace('.', '/'))

    private fun readsDeviceZone(member: ClassReferences.Member): Boolean {
        val parameters = member.descriptor.substringAfter('(').substringBefore(')')
        return when (member.owner) {
            "java/time/ZoneId" -> member.name == "systemDefault"
            "java/util/TimeZone", "android/icu/util/TimeZone" -> member.name == "getDefault"
            "java/time/Clock" -> member.name == "systemDefaultZone"
            in ZONED_TYPES -> member.name == "now" && parameters.isEmpty()
            in CALENDARS -> member.name in setOf("getInstance", "<init>") && "TimeZone;" !in parameters
            else -> false
        }
    }

    private companion object {
        /**
         * App classes that deliberately read the device's zone. None does yet. A class added
         * here needs a comment that says why the account's zone will not do.
         */
        val ALLOWED: Set<String> = emptySet()

        /** The java.time types whose `now()` without arguments takes the device's zone. */
        val ZONED_TYPES = setOf(
            "java/time/LocalDate",
            "java/time/LocalDateTime",
            "java/time/LocalTime",
            "java/time/ZonedDateTime",
            "java/time/OffsetDateTime",
            "java/time/OffsetTime",
            "java/time/Year",
            "java/time/YearMonth",
            "java/time/MonthDay",
        )

        /** Calendars that take the device's zone unless they are given one. */
        val CALENDARS = setOf("java/util/Calendar", "java/util/GregorianCalendar", "android/icu/util/Calendar")
    }
}

/** Reads the device's zone in each way the scan looks for. Only its class file is read; nothing calls it. */
@Suppress("unused")
private object DeviceZoneReader {
    fun zoneId(): ZoneId = ZoneId.systemDefault()

    fun timeZone(): TimeZone = TimeZone.getDefault()

    fun icuTimeZone(): android.icu.util.TimeZone = android.icu.util.TimeZone.getDefault()

    fun clock(): Clock = Clock.systemDefaultZone()

    fun today(): LocalDate = LocalDate.now()

    fun localNow(): LocalDateTime = LocalDateTime.now()

    fun zonedNow(): ZonedDateTime = ZonedDateTime.now()

    fun calendar(): Calendar = Calendar.getInstance()

    fun gregorian(): Calendar = GregorianCalendar()
}

/** Takes the zone or the clock from its caller, as [AccountTime] does. Only its class file is read. */
@Suppress("unused")
private object GivenZoneReader {
    fun today(clock: Clock): LocalDate = LocalDate.now(clock)

    fun zonedNow(zone: ZoneId): ZonedDateTime = ZonedDateTime.now(zone)

    fun calendar(zone: TimeZone): Calendar = Calendar.getInstance(zone)

    fun zone(name: String): ZoneId = ZoneId.of(name)
}
