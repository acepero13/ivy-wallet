package com.ivy.data.sync

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maps database entity data to sync operation data for remote synchronization.
 *
 * This mapper works with raw data maps to avoid circular dependencies
 * between the sync and core data modules.
 */
@Singleton
class SyncOperationMapper @Inject constructor() {

    /**
     * Convert entity data map to sync operation data map for shared accounts
     */
    fun accountEntityToSyncData(entityData: Map<String, Any?>): Map<String, Any?> {
        return mapOf(
            "id" to entityData["id"],
            "name" to entityData["name"],
            "currency" to entityData["currency"],
            "owners" to entityData["owners"],
            "createdBy" to entityData["createdBy"],
            "createdAt" to entityData["createdAt"],
            "updatedAt" to entityData["updatedAt"],
            "deleted" to (entityData["deleted"] ?: false)
        )
    }

    /**
     * Convert entity data map to sync operation data map for shared transactions
     */
    fun transactionEntityToSyncData(entityData: Map<String, Any?>): Map<String, Any?> {
        return mapOf(
            "id" to entityData["id"],
            "sharedAccountId" to entityData["sharedAccountId"],
            "type" to entityData["type"],
            "amount" to entityData["amount"],
            "title" to entityData["title"],
            "description" to entityData["description"],
            "category" to entityData["category"],
            "time" to entityData["time"],
            "createdBy" to entityData["createdBy"],
            "createdAt" to entityData["createdAt"],
            "updatedAt" to entityData["updatedAt"],
            "updatedBy" to entityData["updatedBy"],
            "deleted" to (entityData["deleted"] ?: false)
        )
    }
}
