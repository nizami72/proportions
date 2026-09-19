package az.dahcraf.proportions.data.local

import androidx.room.Dao
import androidx.room.Query

@Dao
interface IngredientNameDao {

    @Query("SELECT name FROM ingredient_names WHERE name LIKE :prefix || '%' ORDER BY useCount DESC LIMIT :limit")
    suspend fun suggest(prefix: String, limit: Int = 10): List<String>

    @Query(
        """
        INSERT INTO ingredient_names(name, useCount) VALUES (:name, 1)
        ON CONFLICT(name) DO UPDATE SET useCount = useCount + 1
        """,
    )
    suspend fun recordUse(name: String)
}
