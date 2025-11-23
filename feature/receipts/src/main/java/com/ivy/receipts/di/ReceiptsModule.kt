package com.ivy.receipts.di

import com.ivy.receipts.parser.ReceiptParseable
import com.ivy.receipts.parser.SpatialReceiptParser
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ReceiptsModule {

    @Provides
    @Singleton
    fun provideReceiptParser(): ReceiptParseable {
        return SpatialReceiptParser()
    }


}
