package com.ivy.sharedaccounts.addtransaction

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ivy.data.model.SharedAccountId
import com.ivy.data.model.SharedTransactionType
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.legacy.IvyWalletPreview
import com.ivy.navigation.screenScopedViewModel
import com.ivy.ui.R
import com.ivy.wallet.ui.theme.Green
import com.ivy.wallet.ui.theme.GreenLight
import com.ivy.wallet.ui.theme.Red
import com.ivy.wallet.ui.theme.White
import com.ivy.wallet.ui.theme.components.IvyButton
import java.util.UUID

@Composable
fun AddSharedTransactionModal(
    visible: Boolean,
    sharedAccountId: SharedAccountId?,
    currency: String,
    onDismiss: () -> Unit,
    onTransactionSaved: () -> Unit
) {
    if (!visible || sharedAccountId == null) return

    val viewModel: AddSharedTransactionViewModel = screenScopedViewModel()

    LaunchedEffect(sharedAccountId) {
        viewModel.setSharedAccountId(sharedAccountId, currency)
        viewModel.onTransactionSaved = onTransactionSaved
    }

    val state = viewModel.uiState()

    Dialog(onDismissRequest = {
        if (!state.isSaving) {
            viewModel.onEvent(AddSharedTransactionEvent.OnDismiss)
            onDismiss()
        }
    }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .clip(RoundedCornerShape(24.dp))
                .background(UI.colors.pure)
                .padding(24.dp)
        ) {
            Text(
                text = stringResource(R.string.add_transaction),
                style = UI.typo.b1.style(
                    color = UI.colors.pureInverse,
                    fontWeight = FontWeight.ExtraBold
                )
            )

            Spacer(Modifier.height(24.dp))

            // Transaction Type Toggle
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                TypeButton(
                    text = stringResource(R.string.income),
                    selected = state.type == SharedTransactionType.INCOME,
                    color = Green,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        viewModel.onEvent(AddSharedTransactionEvent.OnTypeChange(SharedTransactionType.INCOME))
                    }
                )

                Spacer(Modifier.width(12.dp))

                TypeButton(
                    text = stringResource(R.string.expense),
                    selected = state.type == SharedTransactionType.EXPENSE,
                    color = Red,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        viewModel.onEvent(AddSharedTransactionEvent.OnTypeChange(SharedTransactionType.EXPENSE))
                    }
                )
            }

            Spacer(Modifier.height(16.dp))

            // Amount Input
            TextField(
                value = state.amount,
                onValueChange = { viewModel.onEvent(AddSharedTransactionEvent.OnAmountChange(it)) },
                label = {
                    Text(
                        text = stringResource(R.string.amount),
                        style = UI.typo.b2.style(color = UI.colors.pureInverse)
                    )
                },
                placeholder = {
                    Text(
                        text = "0.00",
                        style = UI.typo.b1.style(color = UI.colors.medium)
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = UI.colors.medium,
                    unfocusedContainerColor = UI.colors.medium,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(12.dp))

            // Title Input
            TextField(
                value = state.title,
                onValueChange = { viewModel.onEvent(AddSharedTransactionEvent.OnTitleChange(it)) },
                label = {
                    Text(
                        text = stringResource(R.string.title),
                        style = UI.typo.b2.style(color = UI.colors.pureInverse)
                    )
                },
                placeholder = {
                    Text(
                        text = stringResource(R.string.optional),
                        style = UI.typo.b2.style(color = UI.colors.medium)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = UI.colors.medium,
                    unfocusedContainerColor = UI.colors.medium,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(12.dp))

            // Description Input
            TextField(
                value = state.description,
                onValueChange = { viewModel.onEvent(AddSharedTransactionEvent.OnDescriptionChange(it)) },
                label = {
                    Text(
                        text = stringResource(R.string.description),
                        style = UI.typo.b2.style(color = UI.colors.pureInverse)
                    )
                },
                placeholder = {
                    Text(
                        text = stringResource(R.string.optional),
                        style = UI.typo.b2.style(color = UI.colors.medium)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = UI.colors.medium,
                    unfocusedContainerColor = UI.colors.medium,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(24.dp))

            // Action Buttons
            if (state.isSaving) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = if (state.type == SharedTransactionType.INCOME) Green else Red
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IvyButton(
                        modifier = Modifier.weight(1f),
                        text = stringResource(R.string.cancel),
                        backgroundGradient = com.ivy.wallet.ui.theme.Gradient.solid(UI.colors.medium),
                        textStyle = UI.typo.b2.style(
                            color = UI.colors.pureInverse,
                            fontWeight = FontWeight.Bold
                        ),
                        onClick = {
                            viewModel.onEvent(AddSharedTransactionEvent.OnDismiss)
                            onDismiss()
                        }
                    )

                    Spacer(Modifier.width(12.dp))

                    IvyButton(
                        modifier = Modifier.weight(1f),
                        text = stringResource(R.string.add),
                        backgroundGradient = com.ivy.wallet.ui.theme.Gradient.solid(
                            if (state.type == SharedTransactionType.INCOME) Green else Red
                        ),
                        textStyle = UI.typo.b2.style(
                            color = White,
                            fontWeight = FontWeight.Bold
                        ),
                        onClick = {
                            viewModel.onEvent(AddSharedTransactionEvent.OnSave)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TypeButton(
    text: String,
    selected: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) color else UI.colors.medium)
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) color else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = UI.typo.b2.style(
                color = if (selected) White else UI.colors.pureInverse,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        )
    }
}

@Preview
@Composable
private fun Preview() {
    IvyWalletPreview {
        AddSharedTransactionModal(
            visible = true,
            sharedAccountId = SharedAccountId(UUID.randomUUID()),
            currency = "USD",
            onDismiss = {},
            onTransactionSaved = {}
        )
    }
}
