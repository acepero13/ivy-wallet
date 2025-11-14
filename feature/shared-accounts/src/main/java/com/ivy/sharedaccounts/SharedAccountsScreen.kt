package com.ivy.sharedaccounts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivy.base.legacy.Theme
import com.ivy.data.model.SharedAccount
import com.ivy.data.model.SharedAccountId
import com.ivy.data.model.primitive.AssetCode
import com.ivy.data.model.primitive.NotBlankTrimmedString
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.legacy.IvyWalletPreview
import com.ivy.navigation.SharedAccountsScreen
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.ui.R
import com.ivy.wallet.ui.theme.Gray
import com.ivy.wallet.ui.theme.GreenLight
import com.ivy.wallet.ui.theme.White
import com.ivy.wallet.ui.theme.components.CircleButtonFilled
import com.ivy.wallet.ui.theme.components.IvyIcon
import com.ivy.wallet.ui.theme.findContrastTextColor
import com.ivy.wallet.ui.theme.toComposeColor
import kotlinx.collections.immutable.persistentListOf
import java.time.Instant
import java.util.UUID

@Composable
fun BoxWithConstraintsScope.SharedAccountsScreen(screen: SharedAccountsScreen) {
    val viewModel: SharedAccountsViewModel = screenScopedViewModel()
    val state = viewModel.uiState()

    UI(
        state = state,
        onEvent = viewModel::onEvent
    )
}

@Composable
private fun BoxWithConstraintsScope.UI(
    state: SharedAccountsState,
    onEvent: (SharedAccountsEvent) -> Unit = {}
) {
    val nav = navigation()
    val listState = rememberLazyListState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        state = listState
    ) {
        item {
            Spacer(Modifier.height(32.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Spacer(Modifier.width(24.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.shared_accounts),
                        style = UI.typo.b1.style(
                            color = UI.colors.pureInverse,
                            fontWeight = FontWeight.ExtraBold
                        )
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = stringResource(R.string.shared_accounts_subtitle),
                        style = UI.typo.b2.style(
                            color = UI.colors.pureInverse,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }

                Spacer(Modifier.width(16.dp))

                CircleButtonFilled(
                    icon = R.drawable.ic_plus,
                    onClick = {
                        onEvent(SharedAccountsEvent.OnCreateSharedAccount)
                    },
                    clickAreaPadding = 12.dp
                )

                Spacer(Modifier.width(24.dp))
            }

            Spacer(Modifier.height(24.dp))
        }

        if (state.isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = UI.colors.pureInverse
                    )
                }
            }
        } else if (state.sharedAccounts.isEmpty()) {
            item {
                EmptyState(
                    onCreateClick = {
                        onEvent(SharedAccountsEvent.OnCreateSharedAccount)
                    }
                )
            }
        } else {
            items(
                items = state.sharedAccounts,
                key = { it.id.value }
            ) { sharedAccount ->
                SharedAccountCard(
                    sharedAccount = sharedAccount,
                    baseCurrency = state.baseCurrency,
                    onClick = {
                        onEvent(SharedAccountsEvent.OnSharedAccountClick(sharedAccount.id))
                    }
                )
                Spacer(Modifier.height(12.dp))
            }

            item {
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun EmptyState(
    onCreateClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IvyIcon(
            icon = R.drawable.ic_custom_account_s,
            tint = Gray.toArgb().toComposeColor(),
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Text(
            text = stringResource(R.string.no_shared_accounts),
            style = UI.typo.b1.style(
                color = Gray,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.no_shared_accounts_description),
            style = UI.typo.b2.style(
                color = Gray,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        )

        Spacer(Modifier.height(32.dp))

        CircleButtonFilled(
            icon = R.drawable.ic_plus,
            onClick = onCreateClick
        )
    }
}

@Composable
private fun SharedAccountCard(
    sharedAccount: SharedAccount,
    baseCurrency: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(UI.shapes.r4)
            .background(GreenLight, UI.shapes.r4)
            .border(2.dp, UI.colors.medium, UI.shapes.r4)
            .clickable(onClick = onClick)
            .padding(all = 20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            IvyIcon(
                icon = R.drawable.ic_custom_account_s,
                tint = White
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sharedAccount.name.value,
                    style = UI.typo.b1.style(
                        color = White,
                        fontWeight = FontWeight.ExtraBold
                    )
                )

                Spacer(Modifier.height(2.dp))

                Text(
                    text = "${sharedAccount.owners.size} ${if (sharedAccount.owners.size == 1) "owner" else "owners"}",
                    style = UI.typo.b2.style(
                        color = White,
                        fontWeight = FontWeight.Medium
                    )
                )
            }

            Text(
                text = sharedAccount.currency.code,
                style = UI.typo.b2.style(
                    color = White,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

@Preview
@Composable
private fun Preview() {
    IvyWalletPreview {
        UI(
            state = SharedAccountsState(
                sharedAccounts = persistentListOf(
                    SharedAccount(
                        id = SharedAccountId(UUID.randomUUID()),
                        name = NotBlankTrimmedString.unsafe("Family Expenses"),
                        currency = AssetCode.unsafe("USD"),
                        owners = listOf("user1", "user2"),
                        createdBy = "user1",
                        createdAt = Instant.now(),
                        updatedAt = Instant.now()
                    ),
                    SharedAccount(
                        id = SharedAccountId(UUID.randomUUID()),
                        name = NotBlankTrimmedString.unsafe("Vacation Fund"),
                        currency = AssetCode.unsafe("EUR"),
                        owners = listOf("user1", "user2", "user3"),
                        createdBy = "user1",
                        createdAt = Instant.now(),
                        updatedAt = Instant.now()
                    )
                ),
                baseCurrency = "USD",
                isLoading = false
            )
        )
    }
}

@Preview
@Composable
private fun PreviewEmpty() {
    IvyWalletPreview {
        UI(
            state = SharedAccountsState(
                sharedAccounts = persistentListOf(),
                baseCurrency = "USD",
                isLoading = false
            )
        )
    }
}
