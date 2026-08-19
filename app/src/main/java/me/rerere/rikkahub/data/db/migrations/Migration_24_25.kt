package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration_24_25 : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `knowledge` (
                `id` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `content` TEXT NOT NULL,
                `category` TEXT NOT NULL,
                `tags` TEXT NOT NULL DEFAULT '',
                `source_type` TEXT NOT NULL DEFAULT 'manual',
                `source_path` TEXT,
                `source_metadata` TEXT,
                `embedding` TEXT,
                `usage_count` INTEGER NOT NULL DEFAULT 0,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_knowledge_category` ON `knowledge`(`category`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_knowledge_usage_count` ON `knowledge`(`usage_count`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_knowledge_created_at` ON `knowledge`(`created_at`)")
    }
}
