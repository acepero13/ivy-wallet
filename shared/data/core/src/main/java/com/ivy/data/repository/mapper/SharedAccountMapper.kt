package com.ivy.data.repository.mapper

import arrow.core.Either
import arrow.core.raise.either
import com.ivy.data.db.entity.SharedAccountEntity
import com.ivy.data.model.SharedAccount
import com.ivy.data.model.SharedAccountId
import com.ivy.data.model.primitive.AssetCode
import com.ivy.data.model.primitive.NotBlankTrimmedString
import org.json.JSONArray
import javax.inject.Inject

class SharedAccountMapper @Inject constructor() {

    suspend fun SharedAccountEntity.toDomain(): Either<String, SharedAccount> = either {
        SharedAccount(
            id = SharedAccountId(id),
            name = NotBlankTrimmedString.from(name).bind(),
            currency = AssetCode.from(currency).bind(),
            owners = parseOwners(owners),
            createdBy = createdBy,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    fun SharedAccount.toEntity(
        remoteId: String? = null,
        isSynced: Boolean = false
    ): SharedAccountEntity {
        return SharedAccountEntity(
            name = name.value,
            currency = currency.code,
            owners = serializeOwners(owners),
            createdBy = createdBy,
            createdAt = createdAt,
            updatedAt = updatedAt,
            remoteId = remoteId,
            isSynced = isSynced,
            id = id.value
        )
    }

    private fun parseOwners(ownersJson: String): List<String> {
        return try {
            val jsonArray = JSONArray(ownersJson)
            List(jsonArray.length()) { jsonArray.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun serializeOwners(owners: List<String>): String {
        return JSONArray(owners).toString()
    }
}
