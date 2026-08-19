package me.rerere.rikkahub.ui.pages.knowledge

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Search01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.KnowledgeCategory
import me.rerere.rikkahub.data.model.KnowledgeItem
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.toLocalDateTime
import org.koin.androidx.compose.koinViewModel
import java.time.Instant

@Composable
fun KnowledgePage(vm: KnowledgeVM = koinViewModel()) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val uiState = vm.uiState.collectAsStateWithLifecycle().value
    val removedText = stringResource(R.string.knowledge_page_removed)
    val undoText = stringResource(R.string.knowledge_page_undo)
    var editingItem by remember { mutableStateOf<KnowledgeItem?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                navigationIcon = {
                    BackButton()
                },
                title = {
                    Text(stringResource(R.string.knowledge_page_title))
                },
                actions = {
                    IconButton(onClick = {
                        editingItem = null
                        showEditor = true
                    }) {
                        Icon(
                            imageVector = HugeIcons.Add01,
                            contentDescription = stringResource(R.string.knowledge_page_add),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            SearchBar(
                query = uiState.query,
                onQueryChange = vm::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            CategoryFilterRow(
                categoryCounts = uiState.categoryCounts,
                selected = uiState.category,
                onSelect = vm::setCategory,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
            )

            if (uiState.items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.knowledge_page_empty),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    )
                }
                return@Scaffold
            }

            LazyColumn(
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(uiState.items, key = { it.id.toString() }) { item ->
                    SwipeableKnowledgeCard(
                        item = item,
                        onClick = {
                            editingItem = item
                            showEditor = true
                        },
                        onDelete = {
                            scope.launch {
                                val entity = vm.getById(item.id.toString()) ?: return@launch
                                vm.deleteItem(item.id.toString())
                                val result = snackbarHostState.showSnackbar(
                                    message = removedText,
                                    actionLabel = undoText,
                                    withDismissAction = true,
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    vm.restoreItem(entity)
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .animateItem(),
                    )
                }
            }
        }
    }

    if (showEditor) {
        KnowledgeEditorDialog(
            item = editingItem,
            onDismiss = { showEditor = false },
            onSave = { title, content, category, tags ->
                val item = editingItem
                if (item == null) {
                    vm.addItem(title, content, category, tags)
                } else {
                    vm.updateItem(
                        item.copy(
                            title = title,
                            content = content,
                            category = category,
                            tags = tags,
                        )
                    )
                }
                showEditor = false
            },
        )
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(stringResource(R.string.knowledge_page_search_hint))
        },
        leadingIcon = {
            Icon(
                imageVector = HugeIcons.Search01,
                contentDescription = null,
            )
        },
        singleLine = true,
        modifier = modifier,
    )
}

@Composable
private fun CategoryFilterRow(
    categoryCounts: List<KnowledgeCategoryCount>,
    selected: KnowledgeCategory?,
    onSelect: (KnowledgeCategory?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val countOf: (KnowledgeCategory?) -> Int = { category ->
        if (category == null) {
            categoryCounts.sumOf { it.count }
        } else {
            categoryCounts.firstOrNull { it.category == category }?.count ?: 0
        }
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = {
                    Text(stringResource(R.string.knowledge_page_all, countOf(null)))
                },
            )
        }
        items(KnowledgeCategory.entries.toList()) { category ->
            FilterChip(
                selected = selected == category,
                onClick = { onSelect(category) },
                label = {
                    Text(stringResource(categoryLabel(category), countOf(category)))
                },
            )
        }
    }
}

@StringRes
private fun categoryLabel(category: KnowledgeCategory): Int = when (category) {
    KnowledgeCategory.STUDY -> R.string.knowledge_category_study
    KnowledgeCategory.WORK -> R.string.knowledge_category_work
    KnowledgeCategory.PERSONAL -> R.string.knowledge_category_personal
    KnowledgeCategory.PROJECT -> R.string.knowledge_category_project
    KnowledgeCategory.REFERENCE -> R.string.knowledge_category_reference
    KnowledgeCategory.CODE -> R.string.knowledge_category_code
    KnowledgeCategory.DESIGN -> R.string.knowledge_category_design
}

@Composable
private fun SwipeableKnowledgeCard(
    item: KnowledgeItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        initialValue = SwipeToDismissBoxValue.Settled,
    )

    LaunchedEffect(dismissState.currentValue) {
        when (dismissState.currentValue) {
            SwipeToDismissBoxValue.EndToStart -> {
                onDelete()
            }

            else -> {}
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.errorContainer,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = HugeIcons.Delete01,
                    contentDescription = stringResource(R.string.knowledge_page_delete),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
        enableDismissFromStartToEnd = false,
        modifier = modifier,
    ) {
        KnowledgeCard(item = item, onClick = onClick)
    }
}

@Composable
private fun KnowledgeCard(
    item: KnowledgeItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CustomColors.cardColorsOnSurfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = item.title.ifBlank { stringResource(R.string.knowledge_page_untitled) },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(categoryLabel(item.category)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (item.tags.isNotEmpty()) {
                Text(
                    text = item.tags.joinToString(" · ") { "#$it" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Text(
                text = item.content,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = Instant.ofEpochMilli(item.updatedAt).toLocalDateTime(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun KnowledgeEditorDialog(
    item: KnowledgeItem?,
    onDismiss: () -> Unit,
    onSave: (title: String, content: String, category: KnowledgeCategory, tags: List<String>) -> Unit,
) {
    var title by remember(item) { mutableStateOf(item?.title.orEmpty()) }
    var content by remember(item) { mutableStateOf(item?.content.orEmpty()) }
    var category by remember(item) { mutableStateOf(item?.category ?: KnowledgeCategory.STUDY) }
    var tags by remember(item) { mutableStateOf(item?.tags?.joinToString(",").orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (item == null) R.string.knowledge_page_add_title
                    else R.string.knowledge_page_edit_title
                )
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.knowledge_page_title_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text(stringResource(R.string.knowledge_page_content_label)) },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.knowledge_page_category_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .fillMaxWidth(),
                ) {
                    KnowledgeCategory.entries.forEach { option ->
                        FilterChip(
                            selected = category == option,
                            onClick = { category = option },
                            label = { Text(stringResource(categoryLabel(option))) },
                        )
                    }
                }
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text(stringResource(R.string.knowledge_page_tags_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        title.trim(),
                        content.trim(),
                        category,
                        tags.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                    )
                },
                enabled = title.isNotBlank() || content.isNotBlank(),
            ) {
                Text(stringResource(R.string.knowledge_page_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.knowledge_page_cancel))
            }
        },
    )
}
