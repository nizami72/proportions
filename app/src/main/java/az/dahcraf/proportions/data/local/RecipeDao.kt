package az.dahcraf.proportions.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {

    @Transaction
    @Query("SELECT * FROM recipes ORDER BY createdAt DESC")
    fun observeRecipesWithLines(): Flow<List<RecipeWithLines>>

    @Transaction
    @Query("SELECT * FROM recipes WHERE id = :id")
    fun observeRecipeWithLines(id: Long): Flow<RecipeWithLines?>

    @Query("SELECT COUNT(*) FROM recipes")
    suspend fun countRecipes(): Int

    @Insert
    suspend fun insertRecipe(recipe: RecipeEntity): Long

    @Update
    suspend fun updateRecipe(recipe: RecipeEntity)

    @Delete
    suspend fun deleteRecipe(recipe: RecipeEntity)

    @Insert
    suspend fun insertLines(lines: List<IngredientLineEntity>)

    @Query("DELETE FROM ingredient_lines WHERE recipeId = :recipeId")
    suspend fun deleteLinesForRecipe(recipeId: Long)
}
