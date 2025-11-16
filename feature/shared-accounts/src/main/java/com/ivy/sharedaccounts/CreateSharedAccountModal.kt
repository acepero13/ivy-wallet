package com.ivy.sharedaccounts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.legacy.IvyWalletPreview
import com.ivy.legacy.utils.isNotNullOrBlank
import com.ivy.ui.R
import com.ivy.wallet.ui.theme.modal.IvyModal
import com.ivy.wallet.ui.theme.modal.ModalAddSave
import com.ivy.wallet.ui.theme.modal.ModalTitle
import java.util.UUID

@Composable
fun BoxWithConstraintsScope.CreateSharedAccountModal(
    visible: Boolean,
    baseCurrency: String,
    onCreateAccount: (name: String, currency: String) -> Unit,
    onDismiss: () -> Unit,
    id: UUID = UUID.randomUUID()
) {
    var accountName by remember(visible) {
        mutableStateOf("")
    }
    var currency by remember(visible, baseCurrency) {
        mutableStateOf(baseCurrency)
    }

    IvyModal(
        id = id,
        visible = visible,
        dismiss = onDismiss,
        PrimaryAction = {
            ModalAddSave(
                item = null,
                enabled = accountName.isNotBlank(),
            ) {
                val finalCurrency = currency.trim().ifBlank { baseCurrency }
                onCreateAccount(
                    accountName.trim(),
                    finalCurrency
                )
                onDismiss()
            }
        }
    ) {
        Spacer(Modifier.height(32.dp))

        ModalTitle(
            text = stringResource(R.string.create_shared_account)
        )

        Spacer(Modifier.height(24.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
        ) {
            Text(
                text = "Account Name",
                style = UI.typo.b2.style(
                    color = UI.colors.pureInverse,
                    fontWeight = FontWeight.SemiBold
                )
            )

            Spacer(Modifier.height(12.dp))

            BasicTextField(
                value = accountName,
                onValueChange = { accountName = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(UI.colors.medium, UI.shapes.r4)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                textStyle = UI.typo.b1.style(
                    color = UI.colors.pureInverse,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(UI.colors.pureInverse),
                decorationBox = { innerTextField ->
                    if (accountName.isEmpty()) {
                        Text(
                            text = stringResource(R.string.shared_account_name_hint),
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
                text = "Currency",
                style = UI.typo.b2.style(
                    color = UI.colors.pureInverse,
                    fontWeight = FontWeight.SemiBold
                )
            )

            Spacer(Modifier.height(12.dp))

            BasicTextField(
                value = currency,
                onValueChange = { currency = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(UI.colors.medium, UI.shapes.r4)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                textStyle = UI.typo.b1.style(
                    color = UI.colors.pureInverse,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(UI.colors.pureInverse),
                decorationBox = { innerTextField ->
                    if (currency.isEmpty()) {
                        Text(
                            text = baseCurrency,
                            style = UI.typo.b2.style(
                                color = UI.colors.mediumInverse,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                    innerTextField()
                }
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.shared_account_info),
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
        CreateSharedAccountModal(
            visible = true,
            baseCurrency = "USD",
            onCreateAccount = { _, _ -> },
            onDismiss = {}
        )
    }
}
