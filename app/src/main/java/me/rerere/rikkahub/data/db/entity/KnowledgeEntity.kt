package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "knowledge",
    indices = [
        Index(value = ["category"]),
        Index(value = ["usage_count"]),
        Index(value = ["created_at"]),
    ],
)
data class KnowledgeEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo("title")
    val title: String,
    @ColumnInfo("content")
    val content: String,
    @ColumnInfo("category")
    val category: String,
    @ColumnInfo("tags")
    val tags: String = "",
    @ColumnInfo("source_type")
    val sourceType: String = DEFAULT_SOURCE_TYPE,
    @ColumnInfo("source_path")
    val sourcePath: String? = null,
    @ColumnInfo("source_metadata")
    val sourceMetadata: String? = null,
    @ColumnInfo("embedding")
    val embedding: String? = null,
    @ColumnInfo("usage_count")
    val usageCount: Long = 0,
    @ColumnInfo("created_at")
    val createdAt: Long,
    @ColumnInfo("updated_at")
    val updatedAt: Long,
) {
    companion object {
        const val DEFAULT_SOURCE_TYPE = "manual"
    }
}
