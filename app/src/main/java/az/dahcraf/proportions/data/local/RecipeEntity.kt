package az.dahcraf.proportions.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val photoUri: String? = null,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
