package me.rerere.rikkahub.ui.pages.knowledge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.model.KnowledgeCategory
import me.rerere.rikkahub.data.model.KnowledgeItem
import me.rerere.rikkahub.data.model.toItem
import me.rerere.rikkahub.data.repository.KnowledgeRepository

data class KnowledgeCategoryCount(
    val category: KnowledgeCategory,
    val count: Int,
)

data class KnowledgeUiState(
    val items: List<KnowledgeItem> = emptyList(),
    val categoryCounts: List<KnowledgeCategoryCount> = emptyList(),
    val query: String = "",
    val category: KnowledgeCategory? = null,
)

class KnowledgeVM(
    private val repository: KnowledgeRepository,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val category = MutableStateFlow<KnowledgeCategory?>(null)

    val uiState = combine(
        repository.getAllFlow(),
        query,
        category,
    ) { entities, q, c ->
        val items = entities.map { it.toItem() }
        val filtered = items.filter { item ->
            (c == null || item.category == c) &&
                (q.isBlank() ||
                    item.title.contains(q, ignoreCase = true) ||
                    item.content.contains(q, ignoreCase = true))
        }
        KnowledgeUiState(
            items = filtered,
            categoryCounts = items.groupingBy { it.category }.eachCount()
                .map { (cat, count) -> KnowledgeCategoryCount(cat, count) }
                .sortedByDescending { it.count },
            query = q,
            category = c,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, KnowledgeUiState())

    fun setQuery(value: String) {
        query.value = value
    }

    fun setCategory(value: KnowledgeCategory?) {
        category.value = value
    }

    fun addItem(
        title: String,
        content: String,
        category: KnowledgeCategory,
        tags: List<String>,
    ) {
        viewModelScope.launch {
            repository.add(
                KnowledgeItem(
                    title = title,
                    content = content,
                    category = category,
                    tags = tags,
                )
            )
        }
    }

    fun updateItem(item: KnowledgeItem) {
        viewModelScope.launch {
            repository.update(item.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    fun deleteItem(id: String) {
        viewModelScope.launch {
            repository.delete(id)
        }
    }

    suspend fun getById(id: String): KnowledgeItem? = repository.getById(id)

    fun restoreItem(item: KnowledgeItem) {
        viewModelScope.launch {
            repository.add(item)
        }
    }
}
