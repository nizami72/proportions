package az.dahcraf.proportions.ui.card

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeCardScreen(
    viewModel: RecipeCardViewModel,
    onDone: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val canSave = uiState.recipeName.isNotBlank() &&
        uiState.lines.any { it.name.isNotBlank() && parseAmount(it.amountText) != null }

    val listState = rememberLazyListState()
    LaunchedEffect(uiState.scrollToLineKey) {
        if (uiState.scrollToLineKey != null) {
            // The new row is always appended last, right before the "+ Add ingredient" item -
            // scrolling to that trailing item brings both into view in one motion.
            listState.animateScrollToItem(uiState.lines.size)
            viewModel.onScrolledToLine()
        }
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

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(uiState.lines, key = { it.key }) { line ->
                    IngredientRow(
                        line = line,
                        canRemove = uiState.lines.size > 1,
                        onNameChange = { viewModel.onIngredientNameChange(line.key, it) },
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

            Button(
                onClick = { viewModel.save(onSaved = { onDone() }) },
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(
                    if (uiState.isDirtyFromBaseline) {
                        "Save with these new values"
                    } else {
                        "Save"
                    },
                )
            }
        }
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

@Composable
private fun IngredientRow(
    line: CardLineState,
    canRemove: Boolean,
    onNameChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = line.name,
            onValueChange = onNameChange,
            label = { Text("Ingredient") },
            singleLine = true,
            modifier = Modifier.weight(2f),
        )
        OutlinedTextField(
            value = line.amountText,
            onValueChange = onAmountChange,
            label = { Text("Amount") },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRemove, enabled = canRemove) {
            Icon(Icons.Filled.Delete, contentDescription = "Remove ingredient")
        }
    }
}
