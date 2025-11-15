package com.ivy.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration131to132_Invitations : Migration(131, 132) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS invitations (
                id TEXT NOT NULL PRIMARY KEY,
                sharedAccountId TEXT NOT NULL,
                inviterUid TEXT NOT NULL,
                inviteeEmail TEXT NOT NULL,
                token TEXT NOT NULL,
                status TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                expiresAt INTEGER NOT NULL,
                acceptedAt INTEGER,
                acceptedBy TEXT,
                FOREIGN KEY(sharedAccountId) REFERENCES shared_accounts(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )

        // Create indices for fast lookups
        db.execSQL("CREATE INDEX IF NOT EXISTS index_invitations_sharedAccountId ON invitations(sharedAccountId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_invitations_token ON invitations(token)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_invitations_inviteeEmail ON invitations(inviteeEmail)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_invitations_status ON invitations(status)")
    }
}
