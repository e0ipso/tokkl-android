package com.github.e0ipso.tokkl.clients

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.github.e0ipso.tokkl.model.Client
import com.github.e0ipso.tokkl.model.ClientId
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.ui.theme.TokklTheme

// Design-time previews of the clients page, with sample clients.

@PreviewLightDark
@Composable
private fun ClientsPreview() {
    TokklTheme {
        ClientsScreen(
            ClientsPageState(
                ClientsState(
                    data = ClientsData(SAMPLE_CLIENTS, projectCounts = mapOf(ClientId(1) to 3, ClientId(2) to 1)),
                ),
            ),
            actions = ClientsActions(),
        )
    }
}

@PreviewLightDark
@Composable
private fun ClientsOfflinePreview() {
    TokklTheme {
        ClientsScreen(
            ClientsPageState(
                ClientsState(),
                true,
                listOf(Client(ClientId(1), WorkspaceId(7), "Acme", archived = false)),
            ),
            actions = ClientsActions(),
        )
    }
}

private val SAMPLE_CLIENTS = listOf(
    Client(ClientId(1), WorkspaceId(7), "Acme", notes = "Invoices go to the accounts team at the end of the month."),
    Client(ClientId(2), WorkspaceId(7), "City council"),
    Client(ClientId(3), WorkspaceId(7), "Gone", archived = true),
)
