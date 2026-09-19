package az.dahcraf.proportions.data

import androidx.room.withTransaction
import az.dahcraf.proportions.data.local.AppDatabase
import az.dahcraf.proportions.data.local.IngredientLineEntity
import az.dahcraf.proportions.data.local.RecipeEntity
import az.dahcraf.proportions.data.local.RecipeWithLines
import kotlinx.coroutines.flow.Flow

data class IngredientLineInput(val name: String, val amount: Double)

class RecipeRepository(private val db: AppDatabase) {

    private val recipeDao = db.recipeDao()
    private val ingredientNameDao = db.ingredientNameDao()

    fun observeRecipes(): Flow<List<RecipeWithLines>> = recipeDao.observeRecipesWithLines()

    fun observeRecipe(id: Long): Flow<RecipeWithLines?> = recipeDao.observeRecipeWithLines(id)

    suspend fun countRecipes(): Int = recipeDao.countRecipes()

    suspend fun suggestIngredientNames(prefix: String): List<String> =
        if (prefix.isBlank()) emptyList() else ingredientNameDao.suggest(prefix)

    /**
     * Persists the whole card in one go: existing lines are replaced with [lines] (the card has
     * no stable per-row identity worth tracking - simplest correct approach for a handful of rows).
     */
    suspend fun saveRecipe(recipeId: Long?, name: String, lines: List<IngredientLineInput>): Long =
        db.withTransaction {
            val id = if (recipeId == null) {
                recipeDao.insertRecipe(RecipeEntity(name = name))
            } else {
                recipeDao.updateRecipe(RecipeEntity(id = recipeId, name = name))
                recipeDao.deleteLinesForRecipe(recipeId)
                recipeId
            }
            recipeDao.insertLines(
                lines.mapIndexed { index, line ->
                    IngredientLineEntity(recipeId = id, name = line.name, baselineAmount = line.amount, position = index)
                },
            )
            lines.forEach { ingredientNameDao.recordUse(it.name) }
            id
        }

    suspend fun deleteRecipe(recipe: RecipeEntity) = recipeDao.deleteRecipe(recipe)
}
