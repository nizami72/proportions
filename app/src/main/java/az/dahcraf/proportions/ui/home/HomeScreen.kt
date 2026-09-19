package az.dahcraf.proportions.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import az.dahcraf.proportions.data.local.RecipeWithLines

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onAddRecipe: () -> Unit,
    onOpenRecipe: (Long) -> Unit,
    onOpenAbout: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Proportions") },
                actions = {
                    IconButton(onClick = onOpenAbout) {
                        Icon(Icons.Filled.Info, contentDescription = "About")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddRecipe) {
                Icon(Icons.Filled.Add, contentDescription = "Add recipe")
            }
        },
    ) { padding ->
        if (uiState.recipes.isEmpty() && !uiState.isLoading) {
            EmptyState(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 8.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    end = 8.dp,
                    bottom = 88.dp,
                ),
            ) {
                items(uiState.recipes, key = { it.recipe.id }) { recipeWithLines ->
                    RecipeRow(
                        recipeWithLines = recipeWithLines,
                        onClick = { onOpenRecipe(recipeWithLines.recipe.id) },
                        onDelete = { viewModel.deleteRecipe(recipeWithLines.recipe) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RecipeRow(
    recipeWithLines: RecipeWithLines,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        ListItem(
            headlineContent = { Text(recipeWithLines.recipe.name) },
            supportingContent = { Text("${recipeWithLines.lines.size} ingredients") },
            trailingContent = {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete recipe")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "No recipes yet",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Tap + to save your first ingredient ratio.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
