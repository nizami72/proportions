package az.dahcraf.proportions.ui.card

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import az.dahcraf.proportions.data.IngredientLineInput
import az.dahcraf.proportions.data.RecipeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

data class CardLineState(
    val key: Long,
    val name: String = "",
    val baselineAmount: Double = 0.0,
    val amountText: String = "",
)

/** Two ways of editing amounts on an already-saved card (docs/TZ.md p.3.6). */
enum class CardMode {
    /** Editing a field rescales every other field from its baseline by the same coefficient. */
    CALCULATE,

    /** Editing a field only changes that field - for fixing a typo or reshaping the recipe. */
    EDIT_RATIOS,
}

data class RecipeCardUiState(
    val isLoading: Boolean = true,
    val isNewRecipe: Boolean = true,
    val recipeName: String = "",
    val lines: List<CardLineState> = emptyList(),
    val isDirtyFromBaseline: Boolean = false,
    val mode: CardMode = CardMode.CALCULATE,
    /** Set right after [RecipeCardViewModel.addLine] so the screen can scroll the new row into view. */
    val scrollToLineKey: Long? = null,
)

class RecipeCardViewModel(
    private val repository: RecipeRepository,
    private var currentRecipeId: Long?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecipeCardUiState(isNewRecipe = currentRecipeId == null))
    val uiState: StateFlow<RecipeCardUiState> = _uiState.asStateFlow()

    private var nextKey = 0L
    private fun newKey() = nextKey++

    init {
        val id = currentRecipeId
        if (id != null) {
            viewModelScope.launch {
                repository.observeRecipe(id).collect { withLines ->
                    if (withLines != null) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                recipeName = withLines.recipe.name,
                                lines = withLines.lines.sortedBy { line -> line.position }.map { line ->
                                    CardLineState(
                                        key = newKey(),
                                        name = line.name,
                                        baselineAmount = line.baselineAmount,
                                        amountText = formatAmount(line.baselineAmount),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        } else {
            _uiState.update { it.copy(isLoading = false, lines = listOf(CardLineState(key = newKey()))) }
        }
    }

    fun onRecipeNameChange(name: String) {
        _uiState.update { it.copy(recipeName = name) }
    }

    fun onIngredientNameChange(key: Long, name: String) {
        _uiState.update { state -> state.copy(lines = state.lines.map { if (it.key == key) it.copy(name = name) else it }) }
    }

    fun setMode(mode: CardMode) {
        _uiState.update { state ->
            state.copy(
                mode = mode,
                // Edit-ratios mode never shows the "recalculated, not saved" banner (p.3.6) -
                // switching back to Calculate mode re-derives it from the current values.
                isDirtyFromBaseline = if (mode == CardMode.EDIT_RATIOS) false else isDirty(state.lines),
            )
        }
    }

    /**
     * Calculate mode (docs/TZ.md p.3.2): the edited field defines the new coefficient against
     * its own saved baseline, every other field is scaled from *its* baseline by that
     * coefficient. Edit-ratios mode (p.3.6) only ever touches the edited field itself. Baselines
     * themselves are only touched by [save].
     */
    fun onAmountChange(key: Long, rawText: String) {
        _uiState.update { state ->
            if (state.mode == CardMode.EDIT_RATIOS) {
                val newLines = state.lines.map { if (it.key == key) it.copy(amountText = rawText) else it }
                return@update state.copy(lines = newLines, isDirtyFromBaseline = false)
            }
            val edited = state.lines.find { it.key == key } ?: return@update state
            val parsed = parseAmount(rawText)
            val newLines = if (parsed == null || edited.baselineAmount == 0.0) {
                state.lines.map { if (it.key == key) it.copy(amountText = rawText) else it }
            } else {
                val coeff = parsed / edited.baselineAmount
                state.lines.map { line ->
                    when {
                        line.key == key -> line.copy(amountText = rawText)
                        line.baselineAmount == 0.0 -> line
                        else -> line.copy(amountText = formatAmount(line.baselineAmount * coeff))
                    }
                }
            }
            state.copy(lines = newLines, isDirtyFromBaseline = isDirty(newLines))
        }
    }

    fun addLine() {
        val newLine = CardLineState(key = newKey())
        _uiState.update { it.copy(lines = it.lines + newLine, scrollToLineKey = newLine.key) }
    }

    fun onScrolledToLine() {
        _uiState.update { it.copy(scrollToLineKey = null) }
    }

    fun removeLine(key: Long) {
        _uiState.update { state -> state.copy(lines = state.lines.filterNot { it.key == key }) }
    }

    fun save(onSaved: (Long) -> Unit) {
        val state = _uiState.value
        val trimmedName = state.recipeName.trim()
        val parsedLines = state.lines.mapNotNull { line ->
            val amount = parseAmount(line.amountText)
            val name = line.name.trim()
            if (name.isBlank() || amount == null) null else Triple(line.key, name, amount)
        }
        if (trimmedName.isBlank() || parsedLines.isEmpty()) return

        viewModelScope.launch {
            val id = repository.saveRecipe(
                recipeId = currentRecipeId,
                name = trimmedName,
                lines = parsedLines.map { (_, name, amount) -> IngredientLineInput(name, amount) },
            )
            currentRecipeId = id
            _uiState.update { current ->
                current.copy(
                    isNewRecipe = false,
                    recipeName = trimmedName,
                    isDirtyFromBaseline = false,
                    mode = CardMode.CALCULATE,
                    lines = parsedLines.map { (key, name, amount) ->
                        CardLineState(key = key, name = name, baselineAmount = amount, amountText = formatAmount(amount))
                    },
                )
            }
            onSaved(id)
        }
    }

    private fun isDirty(lines: List<CardLineState>): Boolean = lines.any { line ->
        val parsed = parseAmount(line.amountText) ?: return@any false
        abs(parsed - line.baselineAmount) > 0.005
    }

    companion object {
        fun factory(repository: RecipeRepository, recipeId: Long?) = viewModelFactory {
            initializer { RecipeCardViewModel(repository, recipeId) }
        }
    }
}
