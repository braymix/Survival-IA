package com.survivalwiki.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import com.survivalwiki.app.ui.screens.AnswerScreen
import com.survivalwiki.app.ui.screens.AskScreen
import com.survivalwiki.app.ui.screens.BrowseScreen
import com.survivalwiki.app.ui.screens.OnboardingScreen
import com.survivalwiki.app.ui.screens.SavedScreen
import com.survivalwiki.app.ui.screens.SettingsScreen

/**
 * Shell di navigazione. Per lo scaffold (Fase 1) tutte le schermate sono stub;
 * verranno collegate a ViewModel/retrieval nelle fasi successive.
 */
@Composable
fun SurvivalWikiApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val showBottomBar = Destination.bottomBar.any { dest ->
        currentDestination?.hierarchy?.any { it.route == dest.route } == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    Destination.bottomBar.forEach { dest ->
                        val selected =
                            currentDestination?.hierarchy?.any { it.route == dest.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(Destination.Ask.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { dest.icon?.let { Icon(it, contentDescription = dest.label) } },
                            label = { Text(dest.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            // TODO(fase 5): partire da Onboarding solo se il disclaimer non è ancora accettato.
            startDestination = Destination.Ask.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Destination.Onboarding.route) { OnboardingScreen() }
            composable(Destination.Ask.route) {
                AskScreen(onAsk = { query, category ->
                    val q = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
                    val cat = category?.id.orEmpty()
                    navController.navigate("answer?q=$q&cat=$cat")
                })
            }
            composable(
                route = "answer?q={q}&cat={cat}",
                arguments = listOf(
                    navArgument("q") { type = NavType.StringType; defaultValue = "" },
                    navArgument("cat") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { AnswerScreen() }
            composable(Destination.Browse.route) { BrowseScreen() }
            composable(Destination.Saved.route) { SavedScreen() }
            composable(Destination.Settings.route) { SettingsScreen() }
        }
    }
}
