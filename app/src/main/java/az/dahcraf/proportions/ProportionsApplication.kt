package az.dahcraf.proportions

import android.app.Application
import az.dahcraf.proportions.data.RecipeRepository
import az.dahcraf.proportions.data.local.AppDatabase

class ProportionsApplication : Application() {
    val repository: RecipeRepository by lazy { RecipeRepository(AppDatabase.getInstance(this)) }
}
