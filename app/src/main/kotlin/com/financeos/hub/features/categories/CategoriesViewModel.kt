package com.financeos.hub.features.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financeos.hub.core.database.entities.CategoryEntity
import com.financeos.hub.data.repositories.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val categoryRepo: CategoryRepository,
) : ViewModel() {

    val categories = categoryRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun createCategory(name: String, emoji: String, color: String) {
        viewModelScope.launch {
            categoryRepo.create(
                CategoryEntity(
                    id        = UUID.randomUUID().toString(),
                    name      = name,
                    emoji     = emoji,
                    color     = color,
                    isSystem  = false,
                    isActive  = true,
                    sortOrder = 100,
                )
            )
        }
    }

    fun deleteCategory(id: String) {
        viewModelScope.launch { categoryRepo.deactivate(id) }
    }
}
