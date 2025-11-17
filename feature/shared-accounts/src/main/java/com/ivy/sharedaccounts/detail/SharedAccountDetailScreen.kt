package com.ivy.sharedaccounts.detail

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ivy.data.model.SharedAccount
import com.ivy.data.model.SharedAccountId
import com.ivy.data.model.SharedTransaction
import com.ivy.data.model.SharedTransactionType
import com.ivy.data.model.primitive.AssetCode
import com.ivy.data.model.primitive.NotBlankTrimmedString
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.legacy.IvyWalletPreview
import com.ivy.navigation.SharedAccountDetailScreen
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.sharedaccounts.addtransaction.AddSharedTransactionModal
import com.ivy.ui.R
import com.ivy.wallet.ui.theme.Gray
import com.ivy.wallet.ui.theme.Green
import com.ivy.wallet.ui.theme.GreenLight
import com.ivy.wallet.ui.theme.Red
import com.ivy.wallet.ui.theme.White
import com.ivy.wallet.ui.theme.components.CircleButtonFilled
import com.ivy.wallet.ui.theme.components.IvyIcon
import com.ivy.wallet.ui.theme.toComposeColor
import kotlinx.collections.immutable.persistentListOf
import java.time.Instant
import java.util.UUID

@Composable
fun BoxWithConstraintsScope.SharedAccountDetailScreen(screen: SharedAccountDetailScreen) {
    val viewModel: SharedAccountDetailViewModel = screenScopedViewModel()

    LaunchedEffect(screen.sharedAccountId) {
        android.util.Log.d("SharedAccountDetailScreen", "LaunchedEffect triggered, loading data")
        val accountId = SharedAccountId(screen.sharedAccountId)
        viewModel.setSharedAccountId(accountId)
        viewModel.loadDataForAccount(accountId)
    }

    ScreenContent(viewModel = viewModel)
}

@Composable
private fun BoxWithConstraintsScope.ScreenContent(viewModel: SharedAccountDetailViewModel) {
    val uiState = viewModel.uiState()

    UI(
        state = uiState,
        onEvent = viewModel::onEvent
    )
}

@Composable
private fun BoxWithConstraintsScope.UI(
    state: SharedAccountDetailState,
    onEvent: (SharedAccountDetailEvent) -> Unit = {}
) {
    val nav = navigation()
    val listState = rememberLazyListState()
    var showAddTransactionModal by remember { mutableStateOf(false) }

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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                CircleButtonFilled(
                    icon = R.drawable.ic_back,
                    onClick = {
                        nav.back()
                    }
                )

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.sharedAccount?.name?.value ?: "Loading...",
                        style = UI.typo.b1.style(
                            color = UI.colors.pureInverse,
                            fontWeight = FontWeight.ExtraBold
                        )
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = "${state.sharedAccount?.owners?.size ?: 0} ${if (state.sharedAccount?.owners?.size == 1) "owner" else "owners"}",
                        style = UI.typo.b2.style(
                            color = UI.colors.pureInverse,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }

                CircleButtonFilled(
                    icon = R.drawable.ic_custom_family_m,
                    onClick = {
                        onEvent(SharedAccountDetailEvent.OnShareInvite)
                    }
                )

                Spacer(Modifier.width(12.dp))

                CircleButtonFilled(
                    icon = R.drawable.ic_plus,
                    onClick = {
                        showAddTransactionModal = true
                    }
                )
            }

            Spacer(Modifier.height(24.dp))
        }

        // Balance card
        if (state.sharedAccount != null && !state.isLoading) {
            item {
                BalanceCard(
                    balance = state.balance,
                    totalIncome = state.totalIncome,
                    totalExpense = state.totalExpense,
                    currency = state.sharedAccount.currency.code
                )
                Spacer(Modifier.height(16.dp))
            }
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
        } else if (state.transactions.isEmpty()) {
            item {
                EmptyTransactionsState(
                    onAddClick = {
                        showAddTransactionModal = true
                    }
                )
            }
        } else {
            items(
                items = state.transactions,
                key = { it.id.value }
            ) { transaction ->
                SharedTransactionCard(
                    transaction = transaction,
                    currency = state.sharedAccount?.currency?.code ?: "USD",
                    onClick = {
                        onEvent(SharedAccountDetailEvent.OnTransactionClick(transaction.id))
                    }
                )
                Spacer(Modifier.height(12.dp))
            }

            item {
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // Add Transaction Modal
    AddSharedTransactionModal(
        visible = showAddTransactionModal,
        sharedAccountId = state.sharedAccount?.id,
        currency = state.sharedAccount?.currency?.code ?: "USD",
        onDismiss = {
            showAddTransactionModal = false
        },
        onTransactionSaved = {
            showAddTransactionModal = false
        }
    )
}

@Composable
private fun BalanceCard(
    balance: Double,
    totalIncome: Double,
    totalExpense: Double,
    currency: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(UI.shapes.r4)
            .background(GreenLight, UI.shapes.r4)
            .border(2.dp, UI.colors.medium, UI.shapes.r4)
            .padding(all = 20.dp)
    ) {
        Text(
            text = "Balance",
            style = UI.typo.b2.style(
                color = White,
                fontWeight = FontWeight.Medium
            )
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "$currency ${String.format("%.2f", balance)}",
            style = UI.typo.h2.style(
                color = White,
                fontWeight = FontWeight.ExtraBold
            )
        )

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Income",
                    style = UI.typo.c.style(
                        color = White,
                        fontWeight = FontWeight.Medium
                    )
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "$currency ${String.format("%.2f", totalIncome)}",
                    style = UI.typo.b2.style(
                        color = White,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Expenses",
                    style = UI.typo.c.style(
                        color = White,
                        fontWeight = FontWeight.Medium
                    )
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "$currency ${String.format("%.2f", totalExpense)}",
                    style = UI.typo.b2.style(
                        color = White,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

@Composable
private fun EmptyTransactionsState(
    onAddClick: () -> Unit
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
            text = stringResource(R.string.no_transactions),
            style = UI.typo.b1.style(
                color = Gray,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.no_shared_account_transactions_description),
            style = UI.typo.b2.style(
                color = Gray,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        )

        Spacer(Modifier.height(32.dp))

        CircleButtonFilled(
            icon = R.drawable.ic_plus,
            onClick = onAddClick
        )
    }
}

@Composable
private fun SharedTransactionCard(
    transaction: SharedTransaction,
    currency: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(UI.shapes.r4)
            .background(UI.colors.medium, UI.shapes.r4)
            .clickable(onClick = onClick)
            .padding(all = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                transaction.title?.let {
                    Text(
                        text = it.value,
                        style = UI.typo.b2.style(
                            color = UI.colors.pureInverse,
                            fontWeight = FontWeight.ExtraBold
                        )
                    )
                    Spacer(Modifier.height(4.dp))
                }

                transaction.description?.let {
                    Text(
                        text = it.value,
                        style = UI.typo.c.style(
                            color = UI.colors.pureInverse,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Spacer(Modifier.height(4.dp))
                }

                Text(
                    text = transaction.time.toString().substring(0, 10),
                    style = UI.typo.c.style(
                        color = Gray,
                        fontWeight = FontWeight.Medium
                    )
                )
            }

            Spacer(Modifier.width(12.dp))

            Text(
                text = "${if (transaction.type == SharedTransactionType.INCOME) "+" else "-"}$currency ${String.format("%.2f", transaction.amount)}",
                style = UI.typo.b1.style(
                    color = if (transaction.type == SharedTransactionType.INCOME) Green else Red,
                    fontWeight = FontWeight.ExtraBold
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
            state = SharedAccountDetailState(
                sharedAccount = SharedAccount(
                    id = SharedAccountId(UUID.randomUUID()),
                    name = NotBlankTrimmedString.unsafe("Family Expenses"),
                    currency = AssetCode.unsafe("USD"),
                    owners = listOf("user1", "user2"),
                    createdBy = "user1",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now()
                ),
                transactions = persistentListOf(),
                isLoading = false,
                balance = 1500.50,
                totalIncome = 3000.0,
                totalExpense = 1499.50
            )
        )
    }
}

@Preview
@Composable
private fun PreviewWithTransactions() {
    IvyWalletPreview {
        UI(
            state = SharedAccountDetailState(
                sharedAccount = SharedAccount(
                    id = SharedAccountId(UUID.randomUUID()),
                    name = NotBlankTrimmedString.unsafe("Family Expenses"),
                    currency = AssetCode.unsafe("USD"),
                    owners = listOf("user1", "user2"),
                    createdBy = "user1",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now()
                ),
                transactions = persistentListOf(
                    SharedTransaction(
                        id = com.ivy.data.model.SharedTransactionId(UUID.randomUUID()),
                        sharedAccountId = SharedAccountId(UUID.randomUUID()),
                        type = SharedTransactionType.EXPENSE,
                        amount = 50.0,
                        title = NotBlankTrimmedString.unsafe("Groceries"),
                        description = NotBlankTrimmedString.unsafe("Weekly shopping"),
                        category = null,
                        time = Instant.now(),
                        createdBy = "user1",
                        createdAt = Instant.now(),
                        updatedAt = Instant.now(),
                        updatedBy = "user1"
                    )
                ),
                isLoading = false,
                balance = 1500.50,
                totalIncome = 3000.0,
                totalExpense = 1499.50
            )
        )
    }
}
