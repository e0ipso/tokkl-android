package com.github.e0ipso.tokkl.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ApiResultTest {

    private val success: ApiResult<Int> = ApiResult.Success(2)
    private val failure: ApiResult<Int> = ApiResult.Failure(ApiError.AuthFailed)

    @Test
    fun flatMapChainsASuccessAndPassesAFailureOn() {
        assertEquals(ApiResult.Success("2"), success.flatMap { ApiResult.Success(it.toString()) })
        assertEquals(failure, success.flatMap { failure })
        assertSame(failure, failure.flatMap<Int, String> { error("not called") })
    }

    @Test
    fun foldReadsEitherSide() {
        assertEquals("value 2", success.fold({ "value $it" }, { "error $it" }))
        assertEquals("error AuthFailed", failure.fold({ "value $it" }, { "error $it" }))
    }

    @Test
    fun getOrElseGivesTheValueOrTheFallback() {
        assertEquals(2, success.getOrElse { error("not called") })
        assertEquals(-1, failure.getOrElse { if (it.error == ApiError.AuthFailed) -1 else 0 })
    }

    @Test
    fun getOrElseLetsACallerReturnTheFailure() {
        fun twice(result: ApiResult<Int>): ApiResult<Int> {
            val value = result.getOrElse { return it }
            return ApiResult.Success(value * 2)
        }

        assertEquals(ApiResult.Success(4), twice(success))
        assertEquals(failure, twice(failure))
    }

    @Test
    fun onSuccessAndOnFailureRunOnlyOnTheirSide() {
        val seen = mutableListOf<String>()

        assertSame(success, success.onSuccess { seen += "success $it" }.onFailure { seen += "failure $it" })
        assertSame(failure, failure.onSuccess { seen += "success $it" }.onFailure { seen += "failure $it" })

        assertEquals(listOf("success 2", "failure AuthFailed"), seen)
    }
}
