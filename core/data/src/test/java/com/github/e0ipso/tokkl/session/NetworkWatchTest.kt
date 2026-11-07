package com.github.e0ipso.tokkl.session

import com.github.e0ipso.tokkl.platform.DefaultNetwork
import com.github.e0ipso.tokkl.platform.NetworkReport
import com.github.e0ipso.tokkl.platform.NetworkState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The activity's watch and the picker's offline flag follow the default network's reports. */
class NetworkWatchTest {

    private val network = FakeNetwork(NetworkState(id = 1, internet = true, validated = true))
    private val changes = mutableListOf<Boolean>()
    private val watch = NetworkWatch(network, CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined) {
        changes += it
    }

    @Test
    fun startSaysWhetherTheDefaultNetworkReachesTheInternetAndWatchesUntilStop() {
        assertTrue(watch.start())
        assertEquals("registered while watching", 1, network.reports.subscriptionCount.value)

        network.reports.tryEmit(NetworkReport.Capabilities(NetworkState(1, internet = true, validated = true)))
        network.reports.tryEmit(NetworkReport.Lost(1))
        network.reports.tryEmit(NetworkReport.Available(2))
        network.reports.tryEmit(NetworkReport.Capabilities(NetworkState(2, internet = true, validated = false)))
        watch.stop()
        network.reports.tryEmit(NetworkReport.Lost(2))

        assertEquals("the unchanged first report asks nothing", listOf(false, true), changes)
        assertEquals("unregistered after stop", 0, network.reports.subscriptionCount.value)
    }

    @Test
    fun aSecondStartKeepsOneWatch() {
        watch.start()
        watch.start()

        assertEquals(1, network.reports.subscriptionCount.value)
    }

    @Test
    fun withoutADefaultNetworkTheWatchStartsOffline() {
        network.now = null

        assertFalse(watch.start())
    }

    @Test
    fun onlineFollowsCapabilitiesAndLosses() = runBlocking {
        val reports = flowOf(
            NetworkReport.Available(1),
            NetworkReport.Capabilities(NetworkState(1, internet = true, validated = false)),
            NetworkReport.Capabilities(NetworkState(1, internet = false, validated = false)),
            NetworkReport.Lost(1),
        )
        val online = object : DefaultNetwork {
            override fun current(): NetworkState? = null
            override val reports: Flow<NetworkReport> = reports
        }.online.toList()

        assertEquals(listOf(true, false, false), online)
    }

    private class FakeNetwork(var now: NetworkState?) : DefaultNetwork {
        override fun current(): NetworkState? = now

        override val reports = MutableSharedFlow<NetworkReport>(extraBufferCapacity = 16)
    }
}
