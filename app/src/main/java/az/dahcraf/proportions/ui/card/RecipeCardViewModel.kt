package az.dahcraf.proportions.ui.card

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import az.dahcraf.proportions.data.IngredientLineInput
import az.dahcraf.proportions.data.RecipeRepository
import kotlinx.coroutines.Job
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
    /** The name this row had when it was last loaded from - or saved to - the database. */
    val baselineName: String = "",
    /** False for a row added via [RecipeCardViewModel.addLine] that has never been saved yet. */
    val existedAtLoad: Boolean = false,
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
    /** The recipe name as currently persisted - compared against [recipeName] to detect edits. */
    val recipeNameBaseline: String = "",
    val lines: List<CardLineState> = emptyList(),
    val isDirtyFromBaseline: Boolean = false,
    val mode: CardMode = CardMode.CALCULATE,
    /** Whether saving now would actually change anything already persisted (see [RecipeCardViewModel.recomputeCanSave]). */
    val canSave: Boolean = false,
    /** Set right after [RecipeCardViewModel.addLine] so the screen can scroll the new row into view. */
    val scrollToLineKey: Long? = null,
    /** Ingredient row whose name field is focused - only that row's dropdown is shown (docs/TZ.md p.3.3). */
    val activeSuggestionKey: Long? = null,
    val suggestions: List<String> = emptyList(),
)

class RecipeCardViewModel(
    private val repository: RecipeRepository,
    private var currentRecipeId: Long?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecipeCardUiState(isNewRecipe = currentRecipeId == null))
    val uiState: StateFlow<RecipeCardUiState> = _uiState.asStateFlow()

    private var nextKey = 0L
    private fun newKey() = nextKey++

    /** Every state mutation goes through here so [RecipeCardUiState.canSave] never drifts out of sync. */
    private fun updateState(transform: (RecipeCardUiState) -> RecipeCardUiState) {
        _uiState.update { recomputeCanSave(transform(it)) }
    }

    init {
        val id = currentRecipeId
        if (id != null) {
            viewModelScope.launch {
                repository.observeRecipe(id).collect { withLines ->
                    if (withLines != null) {
                        updateState {
                            it.copy(
                                isLoading = false,
                                recipeName = withLines.recipe.name,
                                recipeNameBaseline = withLines.recipe.name,
                                lines = withLines.lines.sortedBy { line -> line.position }.map { line ->
                                    CardLineState(
                                        key = newKey(),
                                        name = line.name,
                                        baselineName = line.name,
                                        baselineAmount = line.baselineAmount,
                                        amountText = formatAmount(line.baselineAmount),
                                        existedAtLoad = true,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        } else {
            updateState { it.copy(isLoading = false, lines = listOf(CardLineState(key = newKey()))) }
        }
    }

    fun onRecipeNameChange(name: String) {
        updateState { it.copy(recipeName = name) }
    }

    private var suggestionsJob: Job? = null

    fun onIngredientNameChange(key: Long, name: String) {
        updateState { state -> state.copy(lines = state.lines.map { if (it.key == key) it.copy(name = name) else it }) }
        refreshSuggestions(key, name)
    }

    fun onIngredientNameFocusChanged(key: Long, focused: Boolean) {
        if (focused) {
            _uiState.update { it.copy(activeSuggestionKey = key) }
            val current = _uiState.value.lines.find { it.key == key }?.name.orEmpty()
            refreshSuggestions(key, current)
        } else {
            _uiState.update { state ->
                if (state.activeSuggestionKey == key) state.copy(activeSuggestionKey = null, suggestions = emptyList()) else state
            }
        }
    }

    fun onSuggestionSelected(key: Long, name: String) {
        suggestionsJob?.cancel()
        updateState { state ->
            state.copy(
                lines = state.lines.map { if (it.key == key) it.copy(name = name) else it },
                suggestions = emptyList(),
            )
        }
    }

    private fun refreshSuggestions(key: Long, prefix: String) {
        suggestionsJob?.cancel()
        suggestionsJob = viewModelScope.launch {
            val results = repository.suggestIngredientNames(prefix)
            _uiState.update { if (it.activeSuggestionKey == key) it.copy(suggestions = results) else it }
        }
    }

    fun setMode(mode: CardMode) {
        updateState { state ->
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
        updateState { state ->
            if (state.mode == CardMode.EDIT_RATIOS) {
                val newLines = state.lines.map { if (it.key == key) it.copy(amountText = rawText) else it }
                return@updateState state.copy(lines = newLines, isDirtyFromBaseline = false)
            }
            val edited = state.lines.find { it.key == key } ?: return@updateState state
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
        updateState { it.copy(lines = it.lines + newLine, scrollToLineKey = newLine.key) }
    }

    fun onScrolledToLine() {
        _uiState.update { it.copy(scrollToLineKey = null) }
    }

    fun removeLine(key: Long) {
        updateState { state ->
            state.copy(
                lines = state.lines.filterNot { it.key == key },
                activeSuggestionKey = state.activeSuggestionKey.takeUnless { it == key },
                suggestions = if (state.activeSuggestionKey == key) emptyList() else state.suggestions,
            )
        }
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
            updateState { current ->
                current.copy(
                    isNewRecipe = false,
                    recipeName = trimmedName,
                    recipeNameBaseline = trimmedName,
                    isDirtyFromBaseline = false,
                    mode = CardMode.CALCULATE,
                    lines = parsedLines.map { (key, name, amount) ->
                        CardLineState(
                            key = key,
                            name = name,
                            baselineName = name,
                            baselineAmount = amount,
                            amountText = formatAmount(amount),
                            existedAtLoad = true,
                        )
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

    /**
     * Mirrors [save]'s own validation/filtering so "can save" means "saving now would actually
     * persist something different from what's already there" - not just "the form is valid",
     * which was always true for an already-saved, untouched card and left the button permanently
     * enabled.
     */
    private fun recomputeCanSave(state: RecipeCardUiState): RecipeCardUiState {
        if (state.isLoading) return state.copy(canSave = false)
        val trimmedName = state.recipeName.trim()
        val validLines = state.lines.mapNotNull { line ->
            val amount = parseAmount(line.amountText) ?: return@mapNotNull null
            val name = line.name.trim()
            if (name.isBlank()) null else name to amount
        }
        if (trimmedName.isBlank() || validLines.isEmpty()) return state.copy(canSave = false)
        if (state.isNewRecipe) return state.copy(canSave = true)

        val baselineLines = state.lines.mapNotNull { line ->
            if (!line.existedAtLoad) null else line.baselineName to line.baselineAmount
        }
        val changed = trimmedName != state.recipeNameBaseline ||
            validLines.size != baselineLines.size ||
            validLines.zip(baselineLines).any { (current, baseline) ->
                current.first != baseline.first || abs(current.second - baseline.second) > 0.005
            }
        return state.copy(canSave = changed)
    }

    companion object {
        fun factory(repository: RecipeRepository, recipeId: Long?) = viewModelFactory {
            initializer { RecipeCardViewModel(repository, recipeId) }
        }
    }
}
