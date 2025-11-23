package com.ivy.receipts

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt entry point for accessing OcrResultHandler from composables.
 *
 * Since OcrResultHandler is not a ViewModel, we can't use hiltViewModel().
 * Instead, we use EntryPointAccessors to get it from the Hilt graph.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface OcrResultHandlerEntryPoint {
    fun ocrResultHandler(): OcrResultHandler
}
