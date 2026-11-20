package com.flashcards.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.flashcards.AppViewModel

@Composable
fun FlashcardsApp(vm: AppViewModel = viewModel()) {
    val nav = rememberNavController()
    val idArg = listOf(navArgument("id") { type = NavType.LongType })

    NavHost(navController = nav, startDestination = "decks") {
        composable("decks") {
            DeckListScreen(vm = vm, onOpenDeck = { nav.navigate("deck/$it") })
        }
        composable("deck/{id}", arguments = idArg) { entry ->
            val id = entry.arguments!!.getLong("id")
            DeckDetailScreen(
                deckId = id,
                vm = vm,
                onBack = { nav.popBackStack() },
                onScan = { nav.navigate("scan/$id") },
                onStudy = { reverse -> nav.navigate("study/$id/$reverse") },
                onQuiz = { mode, reverse -> nav.navigate("quiz/$id/${mode.name}/$reverse") },
            )
        }
        composable("scan/{id}", arguments = idArg) { entry ->
            ScanScreen(
                deckId = entry.arguments!!.getLong("id"),
                vm = vm,
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            "quiz/{id}/{mode}/{reverse}",
            arguments = idArg + listOf(
                navArgument("mode") { type = NavType.StringType },
                navArgument("reverse") { type = NavType.BoolType },
            ),
        ) { entry ->
            val args = entry.arguments!!
            QuizScreen(
                deckId = args.getLong("id"),
                mode = QuizMode.valueOf(args.getString("mode")!!),
                reverse = args.getBoolean("reverse"),
                vm = vm,
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            "study/{id}/{reverse}",
            arguments = idArg + listOf(navArgument("reverse") { type = NavType.BoolType }),
        ) { entry ->
            StudyScreen(
                deckId = entry.arguments!!.getLong("id"),
                reverse = entry.arguments!!.getBoolean("reverse"),
                vm = vm,
                onBack = { nav.popBackStack() },
            )
        }
    }
}
