package com.ivy.receipts.di

import com.ivy.receipts.category.CategoryDetector
import com.ivy.receipts.category.CompositeCategoryDetector
import com.ivy.receipts.parser.ReceiptParseable
import com.ivy.receipts.parser.ReceiptPatterns
import com.ivy.receipts.parser.SpatialReceiptParser
import com.ivy.receipts.parser.locales.GermanReceiptPatterns
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ReceiptsModule {

    @Binds
    @Singleton
    abstract fun bindReceiptParser(impl: SpatialReceiptParser): ReceiptParseable

    @Binds
    @Singleton
    abstract fun bindCategoryDetector(impl: CompositeCategoryDetector): CategoryDetector

    companion object {
        @Provides
        @Singleton
        fun provideReceiptPatterns(): ReceiptPatterns {
            return GermanReceiptPatterns()
        }
    }
}
