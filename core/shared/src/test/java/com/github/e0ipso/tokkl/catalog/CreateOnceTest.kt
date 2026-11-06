package com.github.e0ipso.tokkl.catalog

import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

/** [createOnce] with a scripted server: the answers each create and each read of the list get, in order. */
class CreateOnceTest {

    private data class Item(val id: Long, val name: String)

    private val lost = ApiResult.Failure(ApiError.Network(IOException("the answer was lost")))
    private val creates = mutableListOf<ApiResult<Item>>()
    private val lists = mutableListOf<ApiResult<List<Item>>>()
    private var created = 0
    private var listed = 0

    private fun run(known: Set<Long> = emptySet()): ApiResult<Item> = createOnce(
        create = { created++; creates.removeAt(0) },
        list = { listed++; lists.removeAt(0) },
        made = { it.id !in known && it.name == "Website" },
    )

    @Test
    fun anAnsweredCreateReadsNothingMore() {
        creates += ApiResult.Success(Item(2, "Website"))
        assertEquals(ApiResult.Success(Item(2, "Website")), run())
        assertEquals(listOf(1, 0), listOf(created, listed))
    }

    @Test
    fun aRefusalStandsWithTheServersReasonAndReadsNothingMore() {
        val refused = ApiResult.Failure(ApiError.UnexpectedStatus(400, "Name has already been taken"))
        creates += refused
        assertEquals(refused, run())
        assertEquals(listOf(1, 0), listOf(created, listed))
    }

    @Test
    fun aLostAnswerTakesTheItemTheListThenHasUnderTheName() {
        creates += lost
        lists += ApiResult.Success(listOf(Item(1, "Other"), Item(2, "Website")))
        assertEquals(ApiResult.Success(Item(2, "Website")), run())
        assertEquals("no second create", listOf(1, 1), listOf(created, listed))
    }

    @Test
    fun anItemThePageAlreadyListedIsNotTakenForTheOneTheCreateMade() {
        creates += lost
        lists += ApiResult.Success(listOf(Item(1, "Website")))
        creates += ApiResult.Success(Item(2, "Website"))
        assertEquals(ApiResult.Success(Item(2, "Website")), run(known = setOf(1)))
        assertEquals(listOf(2, 1), listOf(created, listed))
    }

    @Test
    fun whenTheListLacksTheItemTheCreateGoesOnceMore() {
        creates += lost
        lists += ApiResult.Success(emptyList())
        creates += ApiResult.Success(Item(2, "Website"))
        assertEquals(ApiResult.Success(Item(2, "Website")), run())
        assertEquals(listOf(2, 1), listOf(created, listed))
    }

    @Test
    fun theSecondCreateIsLookedForTooAndThenItsFailureStands() {
        creates += lost
        lists += ApiResult.Success(emptyList())
        creates += lost
        lists += ApiResult.Success(emptyList())
        assertEquals(lost, run())
        assertEquals("two creates at most", listOf(2, 2), listOf(created, listed))
    }

    @Test
    fun whenTheListDoesNotLoadTheCreatesFailureStands() {
        creates += lost
        lists += ApiResult.Failure(ApiError.UnexpectedStatus(503, null))
        assertEquals(lost, run())
        assertEquals(listOf(1, 1), listOf(created, listed))
    }
}
