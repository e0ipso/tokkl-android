package com.github.e0ipso.tokkl.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerUrlTest {

    @Test
    fun hostRootWithAndWithoutTrailingSlash() {
        for (base in listOf("https://example.com", "https://example.com/")) {
            val server = parse(base)
            assertEquals(base, "https://example.com/api/v9/me", server.api("me").toString())
            assertEquals(
                base,
                "https://example.com/reports/api/v3/workspace/1/search/time_entries",
                server.reports("workspace", "1", "search", "time_entries").toString(),
            )
        }
    }

    @Test
    fun basePathWithAndWithoutTrailingSlash() {
        for (base in listOf("https://example.com/tokkl", "https://example.com/tokkl/", "https://example.com/tokkl//")) {
            val server = parse(base)
            assertEquals(base, "https://example.com/tokkl/api/v9/me", server.api("me").toString())
            assertEquals(
                base,
                "https://example.com/tokkl/reports/api/v3/workspace/1/search/time_entries",
                server.reports("workspace", "1", "search", "time_entries").toString(),
            )
        }
    }

    @Test
    fun deeperPathPortAndPlainHttp() {
        val server = parse("http://100.64.0.1:8080/apps/tokkl/")
        assertEquals(
            "http://100.64.0.1:8080/apps/tokkl/api/v9/workspaces/7/time_entries",
            server.api("workspaces", "7", "time_entries").toString(),
        )
        assertEquals(
            "http://100.64.0.1:8080/apps/tokkl/reports/api/v3/workspace/7/summary/time_entries",
            server.reports("workspace", "7", "summary", "time_entries").toString(),
        )
    }

    @Test
    fun emptySegmentsInsideTheBasePathAreDropped() {
        assertEquals("https://example.com/a/b/api/v9/me", parse("https://example.com//a//b/").api("me").toString())
    }

    @Test
    fun encodedBasePathIsKept() {
        assertEquals(
            "https://example.com/my%20tokkl/api/v9/me",
            parse("https://example.com/my%20tokkl/").api("me").toString(),
        )
    }

    @Test
    fun endpointSegmentsCannotAddPathOrQuery() {
        val url = parse("https://example.com/tokkl").api("me/../../admin?x=1")
        assertEquals("https://example.com/tokkl/api/v9/me%2F..%2F..%2Fadmin%3Fx=1", url.toString())
        assertNull(url.query)
    }

    @Test
    fun noEndpointSegmentsGivesTheApiRoot() {
        assertEquals("https://example.com/tokkl/api/v9", parse("https://example.com/tokkl/").api().toString())
    }

    @Test
    fun sameServerWithOrWithoutTrailingSlash() {
        assertEquals(parse("https://example.com/tokkl"), parse("https://example.com/tokkl/"))
        assertEquals("https://example.com/tokkl", parse("https://example.com/tokkl/").toString())
    }

    @Test
    fun rejectsWhatIsNotAnHttpBaseUrl() {
        val bad = listOf(
            "", "example.com", "ftp://example.com", "https://",
            "https://example.com/tokkl?x=1", "https://example.com/#top",
            "https://ana@example.com/tokkl", "https://ana:secret@example.com/tokkl",
        )
        for (url in bad) {
            assertNull(url, ServerUrl.parse(url))
        }
    }

    private fun parse(url: String): ServerUrl = ServerUrl.parse(url) ?: error("$url did not parse")
}
