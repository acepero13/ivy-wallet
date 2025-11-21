package com.ivy.sharedaccounts.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ivy.data.model.Account
import com.ivy.data.model.AccountId
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.ui.R
import com.ivy.wallet.ui.theme.Gray
import com.ivy.wallet.ui.theme.GreenLight
import com.ivy.wallet.ui.theme.Red
import com.ivy.wallet.ui.theme.White
import com.ivy.wallet.ui.theme.components.CircleButtonFilled
import com.ivy.wallet.ui.theme.components.IvyIcon
import com.ivy.wallet.ui.theme.toComposeColor
import kotlinx.collections.immutable.ImmutableList

@Composable
fun LinkAccountModal(
    visible: Boolean,
    accounts: ImmutableList<Account>,
    currentLinkedAccountId: AccountId?,
    onDismiss: () -> Unit,
    onSelectAccount: (AccountId?) -> Unit,
    onDeleteAccount: () -> Unit = {}
) {
    if (!visible) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .background(UI.colors.pure)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(32.dp))

                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CircleButtonFilled(
                        icon = R.drawable.ic_back,
                        onClick = onDismiss
                    )

                    Spacer(Modifier.width(16.dp))

                    Text(
                        text = "Link to Account",
                        style = UI.typo.h2.style(
                            color = UI.colors.pureInverse,
                            fontWeight = FontWeight.ExtraBold
                        )
                    )
                }

                Spacer(Modifier.height(24.dp))

                Text(
                    text = "Link this shared account to a regular account. This will:\n• Show shared transactions in your main balance\n• Use this shared account by default for new transactions",
                    style = UI.typo.b2.style(
                        color = Gray,
                        fontWeight = FontWeight.Medium
                    )
                )

                Spacer(Modifier.height(24.dp))

                // None option
                AccountOption(
                    name = "None (Don't link)",
                    isSelected = currentLinkedAccountId == null,
                    onClick = {
                        android.util.Log.d("LinkAccountModal", "None clicked")
                        onSelectAccount(null)
                    }
                )

                Spacer(Modifier.height(12.dp))

                // Account list
                if (accounts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IvyIcon(
                                icon = R.drawable.ic_custom_account_s,
                                tint = Gray.toArgb().toComposeColor(),
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            Text(
                                text = "No accounts available",
                                style = UI.typo.b2.style(
                                    color = Gray,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center
                                )
                            )
                        }
                    }
                } else {
                    LazyColumn {
                        items(accounts) { account ->
                            AccountOption(
                                name = account.name.value,
                                isSelected = currentLinkedAccountId == account.id,
                                onClick = {
                                    android.util.Log.d("LinkAccountModal", "Account clicked: ${account.name.value}, id: ${account.id.value}")
                                    onSelectAccount(account.id)
                                }
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))

                // Delete Account Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(UI.shapes.r4)
                        .background(Red, UI.shapes.r4)
                        .clickable(onClick = onDeleteAccount)
                        .padding(all = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Delete Shared Account",
                        style = UI.typo.b2.style(
                            color = White,
                            fontWeight = FontWeight.ExtraBold
                        )
                    )
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun AccountOption(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(UI.shapes.r4)
            .background(
                if (isSelected) GreenLight else UI.colors.medium,
                UI.shapes.r4
            )
            .clickable(onClick = onClick)
            .padding(all = 16.dp)
    ) {
        Text(
            text = name,
            style = UI.typo.b2.style(
                color = if (isSelected) White else UI.colors.pureInverse,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
            ),
            modifier = Modifier.weight(1f)
        )

        if (isSelected) {
            IvyIcon(
                icon = R.drawable.ic_check,
                tint = White
            )
        }
    }
}
