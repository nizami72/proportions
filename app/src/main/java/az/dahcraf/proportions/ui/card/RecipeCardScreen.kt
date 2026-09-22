package az.dahcraf.proportions.ui.card

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RecipeCardScreen(
    viewModel: RecipeCardViewModel,
    onDone: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    // Edit ratios (or a brand new, not-yet-saved card) is the full recipe editor - name, ingredient
    // names, add/remove rows. Calculate is the day-to-day "use the recipe" view: only amounts move.
    val editable = uiState.isNewRecipe || uiState.mode == CardMode.EDIT_RATIOS

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

    // Compose's default "scroll minimally to reveal the focused field" fires right as focus
    // changes, before the IME (or our own keypad below) has finished animating in - for a long
    // list that leaves the freshly-focused row positioned against the *pre-keyboard* viewport
    // height, so it can end up hidden once that settles. Track focus ourselves instead and keep
    // the row fully visible as the viewport's real, settled height keeps changing.
    var focusedLineKey by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(focusedLineKey) {
        val key = focusedLineKey ?: return@LaunchedEffect
        val index = uiState.lines.indexOfFirst { it.key == key }
        if (index < 0) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.viewportSize.height }.collect { viewportHeight ->
            val itemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
            val fullyVisible = itemInfo != null &&
                itemInfo.offset >= 0 &&
                itemInfo.offset + itemInfo.size <= viewportHeight
            if (!fullyVisible) {
                listState.scrollToItem(index)
            }
        }
    }

    // Amount fields are read-only to the system keyboard (see IngredientRow) and driven entirely
    // by the on-screen NumericKeypad instead, so their edit state has to live up here where the
    // keypad's button presses can reach whichever row is currently focused.
    val amountValues = remember { mutableStateMapOf<Long, TextFieldValue>() }
    var focusedAmountKey by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(uiState.lines) {
        val validKeys = uiState.lines.map { it.key }.toSet()
        amountValues.keys.retainAll(validKeys)
        uiState.lines.forEach { line ->
            val current = amountValues[line.key]
            if (current == null || (line.key != focusedAmountKey && current.text != line.amountText)) {
                amountValues[line.key] = TextFieldValue(line.amountText, TextRange(line.amountText.length))
            }
        }
    }
    fun updateAmount(key: Long, value: TextFieldValue) {
        amountValues[key] = value
        viewModel.onAmountChange(key, value.text)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (uiState.isNewRecipe) "New recipe" else uiState.recipeName,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!uiState.isNewRecipe) {
                        Text("Use", style = MaterialTheme.typography.labelSmall)
                        Switch(
                            checked = uiState.mode == CardMode.EDIT_RATIOS,
                            onCheckedChange = { checked ->
                                viewModel.setMode(if (checked) CardMode.EDIT_RATIOS else CardMode.CALCULATE)
                            },
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                        Text("Edit", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 8.dp))
                    }
                    FilledIconButton(
                        onClick = { viewModel.save(onSaved = { onDone() }) },
                        enabled = uiState.canSave,
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = "Save")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).imePadding()) {
            if (editable) {
                OutlinedTextField(
                    value = uiState.recipeName,
                    onValueChange = viewModel::onRecipeNameChange,
                    label = { Text("Recipe name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            if (!uiState.isNewRecipe && uiState.isDirtyFromBaseline) {
                DirtyBanner()
            }

            CompositionLocalProvider(LocalBringIntoViewSpec provides NoAutoScrollBringIntoViewSpec) {
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
                            editable = editable,
                            canRemove = uiState.lines.size > 1,
                            nameFocusRequester = nameFocusRequester,
                            suggestions = if (uiState.activeSuggestionKey == line.key) uiState.suggestions else emptyList(),
                            amountValue = amountValues[line.key]
                                ?: TextFieldValue(line.amountText, TextRange(line.amountText.length)),
                            onNameChange = { viewModel.onIngredientNameChange(line.key, it) },
                            onNameFocusChanged = { viewModel.onIngredientNameFocusChanged(line.key, it) },
                            onSuggestionSelected = { viewModel.onSuggestionSelected(line.key, it) },
                            onAmountFieldFocused = {
                                focusedLineKey = line.key
                                focusedAmountKey = line.key
                                val current = amountValues[line.key] ?: TextFieldValue(line.amountText)
                                amountValues[line.key] = current.copy(selection = TextRange(0, current.text.length))
                            },
                            onRemove = { viewModel.removeLine(line.key) },
                            onNameFieldFocused = {
                                focusedLineKey = line.key
                                focusedAmountKey = null
                            },
                        )
                    }
                    if (editable) {
                        item {
                            TextButton(onClick = viewModel::addLine) {
                                Text("+ Add ingredient")
                            }
                        }
                    }
                }
            }

            if (focusedAmountKey != null) {
                val key = focusedAmountKey!!
                NumericKeypad(
                    onDigit = { digit ->
                        val current = amountValues[key] ?: TextFieldValue("")
                        val replacing = current.selection.length == current.text.length
                        val newText = if (replacing) digit.toString() else current.text + digit
                        updateAmount(key, TextFieldValue(newText, TextRange(newText.length)))
                    },
                    onDecimal = {
                        val current = amountValues[key] ?: TextFieldValue("")
                        val replacing = current.selection.length == current.text.length
                        val base = if (replacing) "" else current.text
                        if (!base.contains('.')) {
                            val newText = "$base."
                            updateAmount(key, TextFieldValue(newText, TextRange(newText.length)))
                        }
                    },
                    onBackspace = {
                        val current = amountValues[key] ?: TextFieldValue("")
                        val newText = if (current.selection.length == current.text.length) "" else current.text.dropLast(1)
                        updateAmount(key, TextFieldValue(newText, TextRange(newText.length)))
                    },
                    onDone = {
                        focusedAmountKey = null
                        // Otherwise the field stays focused and tapping it again fires no new
                        // focus event, so the keypad would never come back for that row.
                        focusManager.clearFocus()
                    },
                )
            }
        }
    }
}

/**
 * Disables Compose's built-in "scroll to reveal the focused field" for the ingredient list -
 * the manual re-centering effect above (keyed off the viewport's real, settled height) replaces
 * it entirely, so the two don't fight over the same scroll position.
 */
@OptIn(ExperimentalFoundationApi::class)
private val NoAutoScrollBringIntoViewSpec = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
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
    editable: Boolean,
    canRemove: Boolean,
    nameFocusRequester: FocusRequester,
    suggestions: List<String>,
    amountValue: TextFieldValue,
    onNameChange: (String) -> Unit,
    onNameFocusChanged: (Boolean) -> Unit,
    onSuggestionSelected: (String) -> Unit,
    onAmountFieldFocused: () -> Unit,
    onRemove: () -> Unit,
    onNameFieldFocused: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (editable) {
            // The ViewModel's suggestion list is the single source of truth for whether the
            // dropdown is open - deriving it directly (instead of a remembered boolean) means
            // retyping the same prefix after a dismissal still reopens it.
            val expanded = suggestions.isNotEmpty()

            // Local TextFieldValue so picking a suggestion can explicitly place the caret at the
            // end - line.name is never changed except by typing or picking a suggestion here.
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
                        .onFocusChanged {
                            onNameFocusChanged(it.isFocused)
                            if (it.isFocused) onNameFieldFocused()
                        },
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
        } else {
            Text(
                text = line.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(2f),
            )
        }

        // readOnly so the system keyboard never appears - all input for this field goes through
        // the NumericKeypad docked at the bottom of the screen instead (see RecipeCardScreen).
        OutlinedTextField(
            value = amountValue,
            onValueChange = {},
            readOnly = true,
            label = { Text("Amount") },
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { if (it.isFocused) onAmountFieldFocused() },
        )
        if (editable) {
            IconButton(onClick = onRemove, enabled = canRemove) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove ingredient")
            }
        }
    }
}

@Composable
private fun NumericKeypad(
    onDigit: (Char) -> Unit,
    onDecimal: () -> Unit,
    onBackspace: () -> Unit,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf(listOf('1', '2', '3'), listOf('4', '5', '6'), listOf('7', '8', '9')).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { digit ->
                    KeypadKey(text = digit.toString(), onClick = { onDigit(digit) }, modifier = Modifier.weight(1f))
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KeypadKey(text = ".", onClick = onDecimal, modifier = Modifier.weight(1f))
            KeypadKey(text = "0", onClick = { onDigit('0') }, modifier = Modifier.weight(1f))
            KeypadKey(text = "⌫", onClick = onBackspace, modifier = Modifier.weight(1f))
        }
        TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("Done")
        }
    }
}

@Composable
private fun KeypadKey(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier.height(52.dp)) {
        Text(text, style = MaterialTheme.typography.titleLarge)
    }
}
