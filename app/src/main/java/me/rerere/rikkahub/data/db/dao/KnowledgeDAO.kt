package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.KnowledgeEntity

@Dao
interface KnowledgeDAO {
    @Query("SELECT * FROM knowledge ORDER BY updated_at DESC")
    fun getAllFlow(): Flow<List<KnowledgeEntity>>

    @Query("SELECT * FROM knowledge ORDER BY updated_at DESC")
    suspend fun getAll(): List<KnowledgeEntity>

    @Query("SELECT * FROM knowledge WHERE category = :category ORDER BY updated_at DESC")
    fun getByCategoryFlow(category: String): Flow<List<KnowledgeEntity>>

    @Query("SELECT * FROM knowledge WHERE category = :category ORDER BY updated_at DESC")
    suspend fun getByCategory(category: String): List<KnowledgeEntity>

    @Query("SELECT * FROM knowledge WHERE id = :id")
    suspend fun getById(id: String): KnowledgeEntity?

    @Query("SELECT * FROM knowledge WHERE tags LIKE '%' || :tag || '%' ORDER BY updated_at DESC")
    suspend fun getByTag(tag: String): List<KnowledgeEntity>

    @Query("SELECT * FROM knowledge ORDER BY usage_count DESC, updated_at DESC LIMIT :limit")
    suspend fun getPopular(limit: Int): List<KnowledgeEntity>

    @Query("SELECT DISTINCT category FROM knowledge")
    suspend fun getCategories(): List<String>

    @Insert
    suspend fun insert(entity: KnowledgeEntity): Long

    @Update
    suspend fun update(entity: KnowledgeEntity)

    @Query("DELETE FROM knowledge WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE knowledge SET usage_count = usage_count + 1, updated_at = :updatedAt WHERE id = :id")
    suspend fun bumpUsage(id: String, updatedAt: Long)
}
