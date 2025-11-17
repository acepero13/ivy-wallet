package com.ivy.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration132to133_LinkedAccount : Migration(132, 133) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Add linkedAccountId column to shared_accounts table
        db.execSQL("ALTER TABLE shared_accounts ADD COLUMN linkedAccountId TEXT DEFAULT NULL")
    }
}
