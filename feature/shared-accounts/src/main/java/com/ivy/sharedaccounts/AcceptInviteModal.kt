package com.ivy.sharedaccounts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ivy.data.model.Account
import com.ivy.data.model.AccountId
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.legacy.IvyWalletPreview
import com.ivy.wallet.ui.theme.modal.IvyModal
import com.ivy.wallet.ui.theme.modal.ModalAddSave
import com.ivy.wallet.ui.theme.modal.ModalTitle
import com.ivy.wallet.ui.theme.toComposeColor
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import java.util.UUID

@Composable
fun BoxWithConstraintsScope.AcceptInviteModal(
    visible: Boolean,
    accounts: ImmutableList<Account>,
    onAcceptInvite: (invitationCode: String, linkedAccountId: AccountId?) -> Unit,
    onDismiss: () -> Unit,
    id: UUID = UUID.randomUUID()
) {
    var invitationCode by remember(visible) {
        mutableStateOf("")
    }
    var selectedAccount by remember(visible) {
        mutableStateOf<Account?>(null)
    }

    IvyModal(
        id = id,
        visible = visible,
        dismiss = onDismiss,
        PrimaryAction = {
            ModalAddSave(
                item = null,
                enabled = invitationCode.isNotBlank(),
            ) {
                onAcceptInvite(
                    invitationCode.trim(),
                    selectedAccount?.id
                )
                onDismiss()
            }
        }
    ) {
        Spacer(Modifier.height(32.dp))

        ModalTitle(
            text = "Accept Invitation"
        )

        Spacer(Modifier.height(24.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
        ) {
            Text(
                text = "Invitation Code",
                style = UI.typo.b2.style(
                    color = UI.colors.pureInverse,
                    fontWeight = FontWeight.SemiBold
                )
            )

            Spacer(Modifier.height(12.dp))

            BasicTextField(
                value = invitationCode,
                onValueChange = { invitationCode = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(UI.colors.medium, UI.shapes.r4)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                textStyle = UI.typo.b2.style(
                    color = UI.colors.pureInverse,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(UI.colors.pureInverse),
                decorationBox = { innerTextField ->
                    if (invitationCode.isEmpty()) {
                        Text(
                            text = "Paste invitation code here",
                            style = UI.typo.b2.style(
                                color = UI.colors.mediumInverse,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                    innerTextField()
                }
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = "Link to Account (Optional)",
                style = UI.typo.b2.style(
                    color = UI.colors.pureInverse,
                    fontWeight = FontWeight.SemiBold
                )
            )

            Spacer(Modifier.height(12.dp))

            // Account selection
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(UI.colors.medium, UI.shapes.r4)
                    .padding(8.dp)
            ) {
                // "None" option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedAccount = null }
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(
                        Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(
                                if (selectedAccount == null) UI.colors.pureInverse
                                else UI.colors.medium
                            )
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "None",
                        style = UI.typo.b2.style(
                            color = UI.colors.pureInverse,
                            fontWeight = if (selectedAccount == null) FontWeight.Bold else FontWeight.Medium
                        )
                    )
                }

                // List of accounts
                accounts.forEach { account ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedAccount = account }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(
                            Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selectedAccount?.id == account.id) UI.colors.pureInverse
                                    else account.color.value.toComposeColor()
                                )
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = account.name.value,
                            style = UI.typo.b2.style(
                                color = UI.colors.pureInverse,
                                fontWeight = if (selectedAccount?.id == account.id) FontWeight.Bold else FontWeight.Medium
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Enter the invitation code you received to join a shared account.",
                style = UI.typo.c.style(
                    color = UI.colors.mediumInverse,
                    fontWeight = FontWeight.Normal
                )
            )
        }

        Spacer(Modifier.height(48.dp))
    }
}

@Preview
@Composable
private fun Preview() {
    IvyWalletPreview {
        AcceptInviteModal(
            visible = true,
            accounts = persistentListOf(),
            onAcceptInvite = { _, _ -> },
            onDismiss = {}
        )
    }
}
