package az.dahcraf.proportions.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import az.dahcraf.proportions.data.RecipeRepository
import az.dahcraf.proportions.ui.about.AboutScreen
import az.dahcraf.proportions.ui.card.RecipeCardScreen
import az.dahcraf.proportions.ui.card.RecipeCardViewModel
import az.dahcraf.proportions.ui.home.HomeScreen
import az.dahcraf.proportions.ui.home.HomeViewModel

private const val ROUTE_HOME = "home"
private const val ROUTE_ABOUT = "about"
private const val ROUTE_CARD_BASE = "card"
private const val ARG_RECIPE_ID = "recipeId"
private const val NO_RECIPE_ID = -1L

@Composable
fun ProportionsApp(repository: RecipeRepository) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = ROUTE_HOME) {
        composable(ROUTE_HOME) {
            HomeScreen(
                viewModel = viewModel(factory = HomeViewModel.factory(repository)),
                onAddRecipe = { navController.navigate("$ROUTE_CARD_BASE?$ARG_RECIPE_ID=$NO_RECIPE_ID") },
                onOpenRecipe = { id -> navController.navigate("$ROUTE_CARD_BASE?$ARG_RECIPE_ID=$id") },
                onOpenAbout = { navController.navigate(ROUTE_ABOUT) },
            )
        }
        composable(
            route = "$ROUTE_CARD_BASE?$ARG_RECIPE_ID={$ARG_RECIPE_ID}",
            arguments = listOf(
                navArgument(ARG_RECIPE_ID) {
                    type = NavType.LongType
                    defaultValue = NO_RECIPE_ID
                },
            ),
        ) { backStackEntry ->
            val recipeId = backStackEntry.arguments?.getLong(ARG_RECIPE_ID) ?: NO_RECIPE_ID
            RecipeCardScreen(
                viewModel = viewModel(
                    key = "card_$recipeId",
                    factory = RecipeCardViewModel.factory(
                        repository = repository,
                        recipeId = recipeId.takeIf { it != NO_RECIPE_ID },
                    ),
                ),
                onDone = { navController.popBackStack() },
            )
        }
        composable(ROUTE_ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }
}
