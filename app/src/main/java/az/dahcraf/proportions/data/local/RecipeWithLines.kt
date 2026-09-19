package az.dahcraf.proportions.data.local

import androidx.room.Embedded
import androidx.room.Relation

data class RecipeWithLines(
    @Embedded val recipe: RecipeEntity,
    @Relation(parentColumn = "id", entityColumn = "recipeId")
    val lines: List<IngredientLineEntity>,
)
