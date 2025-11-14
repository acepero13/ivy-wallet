package com.ivy.data.repository.mapper

import arrow.core.Either
import arrow.core.raise.either
import com.ivy.data.db.entity.SharedTransactionEntity
import com.ivy.data.db.entity.SharedTransactionType as EntitySharedTransactionType
import com.ivy.data.model.CategoryId
import com.ivy.data.model.SharedAccountId
import com.ivy.data.model.SharedTransaction
import com.ivy.data.model.SharedTransactionId
import com.ivy.data.model.SharedTransactionType
import com.ivy.data.model.primitive.NotBlankTrimmedString
import javax.inject.Inject

class SharedTransactionMapper @Inject constructor() {
    suspend fun SharedTransactionEntity.toDomain(): Either<String, SharedTransaction> = either {
        SharedTransaction(
            id = SharedTransactionId(id),
            sharedAccountId = SharedAccountId(sharedAccountId),
            type = when (type) {
                EntitySharedTransactionType.INCOME -> SharedTransactionType.INCOME
                EntitySharedTransactionType.EXPENSE -> SharedTransactionType.EXPENSE
            },
            amount = amount,
            title = title?.let { NotBlankTrimmedString.from(it).getOrNull() },
            description = description?.let { NotBlankTrimmedString.from(it).getOrNull() },
            category = categoryId?.let { CategoryId(it) },
            time = time,
            createdBy = createdBy,
            createdAt = createdAt,
            updatedAt = updatedAt,
            updatedBy = updatedBy,
            deleted = deleted
        )
    }

    fun SharedTransaction.toEntity(
        remoteId: String? = null,
        isSynced: Boolean = false
    ): SharedTransactionEntity {
        return SharedTransactionEntity(
            sharedAccountId = sharedAccountId.value,
            type = when (type) {
                SharedTransactionType.INCOME -> EntitySharedTransactionType.INCOME
                SharedTransactionType.EXPENSE -> EntitySharedTransactionType.EXPENSE
            },
            amount = amount,
            title = title?.value,
            description = description?.value,
            categoryId = category?.value,
            time = time,
            createdBy = createdBy,
            createdAt = createdAt,
            updatedAt = updatedAt,
            updatedBy = updatedBy,
            deleted = deleted,
            remoteId = remoteId,
            isSynced = isSynced,
            id = id.value
        )
    }
}
