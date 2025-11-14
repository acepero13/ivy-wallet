package com.ivy.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration130to131_SharedAccounts : Migration(130, 131) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Create shared_accounts table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS shared_accounts (
                id BLOB NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                currency TEXT NOT NULL,
                owners TEXT NOT NULL,
                createdBy TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                remoteId TEXT,
                isSynced INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        // Create shared_transactions table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS shared_transactions (
                id BLOB NOT NULL PRIMARY KEY,
                sharedAccountId BLOB NOT NULL,
                type TEXT NOT NULL,
                amount REAL NOT NULL,
                title TEXT,
                description TEXT,
                categoryId BLOB,
                time INTEGER NOT NULL,
                createdBy TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                updatedBy TEXT NOT NULL,
                deleted INTEGER NOT NULL DEFAULT 0,
                remoteId TEXT,
                isSynced INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(sharedAccountId) REFERENCES shared_accounts(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )

        // Create indices for shared_transactions
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_shared_transactions_sharedAccountId ON shared_transactions(sharedAccountId)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_shared_transactions_time ON shared_transactions(time)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_shared_transactions_deleted ON shared_transactions(deleted)"
        )
    }
}
