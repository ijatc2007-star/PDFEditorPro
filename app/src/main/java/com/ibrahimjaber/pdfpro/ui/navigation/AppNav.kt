package com.ibrahimjaber.pdfpro.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ibrahimjaber.pdfpro.PdfProApp
import com.ibrahimjaber.pdfpro.ui.screens.HomeScreen
import com.ibrahimjaber.pdfpro.ui.screens.PageOrganizerScreen
import com.ibrahimjaber.pdfpro.ui.screens.ProToolWorkspaceScreen
import com.ibrahimjaber.pdfpro.ui.screens.SettingsAboutScreen
import com.ibrahimjaber.pdfpro.ui.screens.ToolsHubScreen
import com.ibrahimjaber.pdfpro.ui.screens.ViewerEditorScreen

object Routes {
    const val LIBRARY = "library"
    const val TOOLS = "tools"
    const val SETTINGS = "settings"
    const val VIEWER = "viewer/{path}"
    const val ORGANIZER = "organizer/{path}"
    const val TOOL_WORKSPACE = "tool/{toolId}"

    fun viewer(path: String): String = "viewer/${Uri.encode(path)}"
    fun organizer(path: String): String = "organizer/${Uri.encode(path)}"
    fun toolWorkspace(toolId: String): String = "tool/$toolId"
}

@Composable
fun PdfProNavHost(
    app: PdfProApp,
    pendingOpenPath: String?,
    onConsumedOpenPath: () -> Unit
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    LaunchedEffect(pendingOpenPath) {
        if (!pendingOpenPath.isNullOrBlank()) {
            navController.navigate(Routes.viewer(pendingOpenPath))
            onConsumedOpenPath()
        }
    }

    val showBottomBar = currentRoute in listOf(Routes.LIBRARY, Routes.TOOLS, Routes.SETTINGS)

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == Routes.LIBRARY,
                        onClick = {
                            navController.navigate(Routes.LIBRARY) {
                                popUpTo(Routes.LIBRARY) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(Icons.Default.Folder, contentDescription = "Library") },
                        label = { Text("Library") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.TOOLS,
                        onClick = {
                            navController.navigate(Routes.TOOLS) {
                                popUpTo(Routes.LIBRARY)
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(Icons.Default.Build, contentDescription = "Pro Tools") },
                        label = { Text("Pro Tools") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.SETTINGS,
                        onClick = {
                            navController.navigate(Routes.SETTINGS) {
                                popUpTo(Routes.LIBRARY)
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.LIBRARY,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.LIBRARY) {
                HomeScreen(
                    app = app,
                    onOpenPdf = { path -> navController.navigate(Routes.viewer(path)) },
                    onOrganizePdf = { path -> navController.navigate(Routes.organizer(path)) },
                    onOpenTool = { toolId -> navController.navigate(Routes.toolWorkspace(toolId)) }
                )
            }

            composable(Routes.TOOLS) {
                ToolsHubScreen(
                    onSelectTool = { toolId -> navController.navigate(Routes.toolWorkspace(toolId)) }
                )
            }

            composable(Routes.SETTINGS) {
                SettingsAboutScreen(app = app)
            }

            composable(
                route = Routes.VIEWER,
                arguments = listOf(navArgument("path") { type = NavType.StringType })
            ) { entry ->
                val decodedPath = Uri.decode(entry.arguments?.getString("path").orEmpty())
                ViewerEditorScreen(
                    app = app,
                    filePath = decodedPath,
                    onBack = { navController.popBackStack() },
                    onOpenOrganizer = { p -> navController.navigate(Routes.organizer(p)) }
                )
            }

            composable(
                route = Routes.ORGANIZER,
                arguments = listOf(navArgument("path") { type = NavType.StringType })
            ) { entry ->
                val decodedPath = Uri.decode(entry.arguments?.getString("path").orEmpty())
                PageOrganizerScreen(
                    app = app,
                    filePath = decodedPath,
                    onBack = { navController.popBackStack() },
                    onOpenSavedPdf = { savedPath ->
                        navController.navigate(Routes.viewer(savedPath)) {
                            popUpTo(Routes.LIBRARY)
                        }
                    }
                )
            }

            composable(
                route = Routes.TOOL_WORKSPACE,
                arguments = listOf(navArgument("toolId") { type = NavType.StringType })
            ) { entry ->
                val toolId = entry.arguments?.getString("toolId").orEmpty()
                ProToolWorkspaceScreen(
                    app = app,
                    toolId = toolId,
                    onBack = { navController.popBackStack() },
                    onOpenPdf = { p -> navController.navigate(Routes.viewer(p)) },
                    onOpenOrganizer = { p -> navController.navigate(Routes.organizer(p)) }
                )
            }
        }
    }
}
