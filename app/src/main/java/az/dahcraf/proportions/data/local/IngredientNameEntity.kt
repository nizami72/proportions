package az.dahcraf.proportions.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ingredient_names")
data class IngredientNameEntity(
    @PrimaryKey val name: String,
    val useCount: Int = 1,
)
