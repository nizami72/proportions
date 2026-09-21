package az.dahcraf.proportions.ui.card

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RecipeCardScreen(
    viewModel: RecipeCardViewModel,
    onDone: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val canSave = uiState.recipeName.isNotBlank() &&
        uiState.lines.any { it.name.isNotBlank() && parseAmount(it.amountText) != null }

    val listState = rememberLazyListState()
    val nameFocusRequesters = remember { mutableMapOf<Long, FocusRequester>() }
    LaunchedEffect(uiState.scrollToLineKey) {
        val key = uiState.scrollToLineKey ?: return@LaunchedEffect
        // The new row is always appended last, right before the "+ Add ingredient" item -
        // scrolling to that trailing item brings both into view in one motion.
        listState.animateScrollToItem(uiState.lines.size)
        nameFocusRequesters[key]?.requestFocus()
        viewModel.onScrolledToLine()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isNewRecipe) "New recipe" else uiState.recipeName) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).imePadding()) {
            OutlinedTextField(
                value = uiState.recipeName,
                onValueChange = viewModel::onRecipeNameChange,
                label = { Text("Recipe name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (!uiState.isNewRecipe) {
                ModeSwitch(mode = uiState.mode, onModeChange = viewModel::setMode)
            }

            if (!uiState.isNewRecipe && uiState.isDirtyFromBaseline) {
                DirtyBanner()
            }

            // Overrides Compose's default "scroll minimally to reveal the focused field" behavior
            // so the row being edited lands centered in the visible area above the keyboard,
            // instead of hugging whichever edge it happened to scroll in from.
            CompositionLocalProvider(LocalBringIntoViewSpec provides CenteredBringIntoViewSpec) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(uiState.lines, key = { it.key }) { line ->
                        val nameFocusRequester = remember(line.key) { FocusRequester() }
                        DisposableEffect(line.key) {
                            nameFocusRequesters[line.key] = nameFocusRequester
                            onDispose { nameFocusRequesters.remove(line.key) }
                        }
                        IngredientRow(
                            line = line,
                            canRemove = uiState.lines.size > 1,
                            nameFocusRequester = nameFocusRequester,
                            suggestions = if (uiState.activeSuggestionKey == line.key) uiState.suggestions else emptyList(),
                            onNameChange = { viewModel.onIngredientNameChange(line.key, it) },
                            onNameFocusChanged = { viewModel.onIngredientNameFocusChanged(line.key, it) },
                            onSuggestionSelected = { viewModel.onSuggestionSelected(line.key, it) },
                            onAmountChange = { viewModel.onAmountChange(line.key, it) },
                            onRemove = { viewModel.removeLine(line.key) },
                        )
                    }
                    item {
                        TextButton(onClick = viewModel::addLine) {
                            Text("+ Add ingredient")
                        }
                    }
                }
            }

            Button(
                onClick = { viewModel.save(onSaved = { onDone() }) },
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(
                    if (!uiState.isNewRecipe && uiState.isDirtyFromBaseline) {
                        "Save with these new values"
                    } else {
                        "Save"
                    },
                )
            }
        }
    }
}

/** Centers the requested rectangle in the container instead of just scrolling it minimally into view. */
@OptIn(ExperimentalFoundationApi::class)
private val CenteredBringIntoViewSpec = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        return offset - (containerSize - size) / 2f
    }
}

@Composable
private fun ModeSwitch(mode: CardMode, onModeChange: (CardMode) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = mode == CardMode.CALCULATE,
                onClick = { onModeChange(CardMode.CALCULATE) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) {
                Text("Calculate")
            }
            SegmentedButton(
                selected = mode == CardMode.EDIT_RATIOS,
                onClick = { onModeChange(CardMode.EDIT_RATIOS) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) {
                Text("Edit ratios")
            }
        }
        Text(
            text = if (mode == CardMode.CALCULATE) {
                "Changing an amount scales every other amount to match."
            } else {
                "Changing an amount only changes that ingredient."
            },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, start = 4.dp),
        )
    }
}

@Composable
private fun DirtyBanner() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Text(
            text = "Recalculated - not saved yet. Original amounts are unchanged until you save.",
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IngredientRow(
    line: CardLineState,
    canRemove: Boolean,
    nameFocusRequester: FocusRequester,
    suggestions: List<String>,
    onNameChange: (String) -> Unit,
    onNameFocusChanged: (Boolean) -> Unit,
    onSuggestionSelected: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        // The ViewModel's suggestion list is the single source of truth for whether the dropdown
        // is open - deriving it directly (instead of a remembered boolean) means retyping the
        // same prefix after a dismissal still reopens it.
        val expanded = suggestions.isNotEmpty()

        // Local TextFieldValue so picking a suggestion can explicitly place the caret at the end -
        // line.name itself is never changed except by typing here or picking a suggestion here, so
        // there's no external value to reconcile with (unlike the Amount field below).
        var nameValue by remember(line.key) {
            mutableStateOf(TextFieldValue(text = line.name, selection = TextRange(line.name.length)))
        }

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = {},
            modifier = Modifier.weight(2f),
        ) {
            OutlinedTextField(
                value = nameValue,
                onValueChange = { nameValue = it; onNameChange(it.text) },
                label = { Text("Ingredient") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryEditable, true)
                    .focusRequester(nameFocusRequester)
                    .onFocusChanged { onNameFocusChanged(it.isFocused) },
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = {}) {
                suggestions.forEach { suggestion ->
                    DropdownMenuItem(
                        text = { Text(suggestion) },
                        onClick = {
                            nameValue = TextFieldValue(text = suggestion, selection = TextRange(suggestion.length))
                            onSuggestionSelected(suggestion)
                        },
                    )
                }
            }
        }

        // Local TextFieldValue for two reasons: selecting all on focus (so the first digit typed
        // replaces the old amount, docs/TZ.md editing UX) and, unlike the name field, the text
        // *can* change from outside while unfocused - Calculate mode recalculates sibling rows.
        var amountValue by remember(line.key) {
            mutableStateOf(TextFieldValue(text = line.amountText, selection = TextRange(line.amountText.length)))
        }
        LaunchedEffect(line.amountText) {
            if (amountValue.text != line.amountText) {
                amountValue = TextFieldValue(text = line.amountText, selection = TextRange(line.amountText.length))
            }
        }
        OutlinedTextField(
            value = amountValue,
            onValueChange = { amountValue = it; onAmountChange(it.text) },
            label = { Text("Amount") },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged {
                    if (it.isFocused) {
                        amountValue = amountValue.copy(selection = TextRange(0, amountValue.text.length))
                    }
                },
        )
        IconButton(onClick = onRemove, enabled = canRemove) {
            Icon(Icons.Filled.Delete, contentDescription = "Remove ingredient")
        }
    }
}
