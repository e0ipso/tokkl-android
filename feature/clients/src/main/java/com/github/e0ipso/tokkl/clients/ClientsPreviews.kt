package com.github.e0ipso.tokkl.clients

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.github.e0ipso.tokkl.cache.CachedClient
import com.github.e0ipso.tokkl.ui.theme.TokklTheme

// Design-time previews of the clients page, with sample clients.

@PreviewLightDark
@Composable
private fun ClientsPreview() {
    TokklTheme {
        ClientsContent(
            state = ClientsState(data = ClientsData(SAMPLE_CLIENTS, projectCounts = mapOf(1L to 3, 2L to 1))),
            offline = false,
            cachedClients = emptyList(),
            actions = ClientsActions(),
        )
    }
}

@PreviewLightDark
@Composable
private fun ClientsOfflinePreview() {
    TokklTheme {
        ClientsContent(
            state = ClientsState(),
            offline = true,
            cachedClients = listOf(CachedClient(1, 7, "Acme", archived = false)),
            actions = ClientsActions(),
        )
    }
}

private val SAMPLE_CLIENTS = listOf(
    Client(1, 7, "Acme", notes = "Invoices go to the accounts team at the end of the month."),
    Client(2, 7, "City council"),
    Client(3, 7, "Gone", archived = true),
)
