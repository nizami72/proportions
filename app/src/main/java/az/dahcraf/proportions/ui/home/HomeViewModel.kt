package az.dahcraf.proportions.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import az.dahcraf.proportions.data.RecipeRepository
import az.dahcraf.proportions.data.local.RecipeEntity
import az.dahcraf.proportions.data.local.RecipeWithLines
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val recipes: List<RecipeWithLines> = emptyList(),
)

class HomeViewModel(private val repository: RecipeRepository) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = repository.observeRecipes()
        .map { HomeUiState(isLoading = false, recipes = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun deleteRecipe(recipe: RecipeEntity) {
        viewModelScope.launch { repository.deleteRecipe(recipe) }
    }

    companion object {
        fun factory(repository: RecipeRepository) = viewModelFactory {
            initializer { HomeViewModel(repository) }
        }
    }
}
