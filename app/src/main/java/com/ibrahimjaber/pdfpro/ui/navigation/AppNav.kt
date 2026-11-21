package com.ibrahimjaber.pdfpro.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
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
import com.ibrahimjaber.pdfpro.data.LocalStrings
import com.ibrahimjaber.pdfpro.ui.screens.AiAssistantScreen
import com.ibrahimjaber.pdfpro.ui.screens.CamScannerScreen
import com.ibrahimjaber.pdfpro.ui.screens.ConverterScreen
import com.ibrahimjaber.pdfpro.ui.screens.DigitalSignatureScreen
import com.ibrahimjaber.pdfpro.ui.screens.FormFillerScreen
import com.ibrahimjaber.pdfpro.ui.screens.HomeScreen
import com.ibrahimjaber.pdfpro.ui.screens.LiveCameraTranslateScreen
import com.ibrahimjaber.pdfpro.ui.screens.OcrStudioScreen
import com.ibrahimjaber.pdfpro.ui.screens.PageOrganizerScreen
import com.ibrahimjaber.pdfpro.ui.screens.ProToolWorkspaceScreen
import com.ibrahimjaber.pdfpro.ui.screens.SettingsAboutScreen
import com.ibrahimjaber.pdfpro.ui.screens.ToolsHubScreen
import com.ibrahimjaber.pdfpro.ui.screens.ViewerEditorScreen

object Routes {
    const val LIBRARY = "library"
    const val CONVERTER = "converter"
    const val AI_STUDIO = "ai_studio"
    const val AI_STUDIO_FILE = "ai_studio/{path}"
    const val CAM_SCANNER = "cam_scanner"
    const val CAMERA_TRANSLATE = "camera_translate"
    const val OCR_STUDIO = "ocr_studio?path={path}"
    const val FORM_FILLER = "form_filler?path={path}"
    const val DIGITAL_SIGN = "digital_sign"
    const val TOOLS = "tools"
    const val SETTINGS = "settings"
    const val VIEWER = "viewer/{path}?page={page}"
    const val ORGANIZER = "organizer/{path}"
    const val TOOL_WORKSPACE = "tool/{toolId}"

    fun viewer(path: String, page: Int = -1): String = "viewer/${Uri.encode(path)}?page=$page"
    fun aiStudioFor(path: String): String = "ai_studio/${Uri.encode(path)}"
    fun ocrFor(path: String = ""): String = "ocr_studio?path=${Uri.encode(path)}"
    fun formFor(path: String = ""): String = "form_filler?path=${Uri.encode(path)}"
    fun organizer(path: String): String = "organizer/${Uri.encode(path)}"
    fun toolWorkspace(toolId: String): String = "tool/$toolId"
}

@Composable
fun PdfProNavHost(
    app: PdfProApp,
    pendingOpenPath: String?,
    onConsumedOpenPath: () -> Unit
) {
    val s = LocalStrings.current
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    LaunchedEffect(pendingOpenPath) {
        if (!pendingOpenPath.isNullOrBlank()) {
            navController.navigate(Routes.viewer(pendingOpenPath))
            onConsumedOpenPath()
        }
    }

    val showBottomBar = currentRoute in listOf(
        Routes.LIBRARY,
        Routes.CONVERTER,
        Routes.AI_STUDIO,
        Routes.TOOLS,
        Routes.SETTINGS
    )

    fun routeToTool(toolId: String) {
        when (toolId) {
            "converter" -> navController.navigate(Routes.CONVERTER)
            "ai_studio" -> navController.navigate(Routes.AI_STUDIO)
            "cam_scanner" -> navController.navigate(Routes.CAM_SCANNER)
            "camera_translate" -> navController.navigate(Routes.CAMERA_TRANSLATE)
            "ocr_studio" -> navController.navigate(Routes.ocrFor())
            "form_filler" -> navController.navigate(Routes.formFor())
            "digital_sign" -> navController.navigate(Routes.DIGITAL_SIGN)
            else -> navController.navigate(Routes.toolWorkspace(toolId))
        }
    }

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
                        icon = { Icon(Icons.Default.Folder, contentDescription = s.navLibrary) },
                        label = { Text(s.navLibrary) }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.CONVERTER,
                        onClick = {
                            navController.navigate(Routes.CONVERTER) {
                                popUpTo(Routes.LIBRARY)
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(Icons.Default.SwapHoriz, contentDescription = s.navConverter) },
                        label = { Text(s.navConverter) }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.AI_STUDIO,
                        onClick = {
                            navController.navigate(Routes.AI_STUDIO) {
                                popUpTo(Routes.LIBRARY)
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "AI") },
                        label = { Text(if (s.isArabic) "ذكاء AI" else "AI Studio") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.TOOLS,
                        onClick = {
                            navController.navigate(Routes.TOOLS) {
                                popUpTo(Routes.LIBRARY)
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(Icons.Default.Build, contentDescription = s.navProTools) },
                        label = { Text(s.navProTools) }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.SETTINGS,
                        onClick = {
                            navController.navigate(Routes.SETTINGS) {
                                popUpTo(Routes.LIBRARY)
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(Icons.Default.Settings, contentDescription = s.navSettings) },
                        label = { Text(s.navSettings) }
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
                    onOpenTool = { routeToTool(it) }
                )
            }

            composable(Routes.CONVERTER) {
                ConverterScreen(
                    app = app,
                    onOpenPdf = { path -> navController.navigate(Routes.viewer(path)) }
                )
            }

            composable(Routes.AI_STUDIO) {
                AiAssistantScreen(
                    app = app,
                    initialFilePath = null,
                    onBack = null,
                    onOpenPdfAtPage = { path, pageIdx -> navController.navigate(Routes.viewer(path, pageIdx)) }
                )
            }

            composable(
                route = Routes.AI_STUDIO_FILE,
                arguments = listOf(navArgument("path") { type = NavType.StringType })
            ) { entry ->
                val decodedPath = Uri.decode(entry.arguments?.getString("path").orEmpty())
                AiAssistantScreen(
                    app = app,
                    initialFilePath = decodedPath,
                    onBack = { navController.popBackStack() },
                    onOpenPdfAtPage = { path, pageIdx -> navController.navigate(Routes.viewer(path, pageIdx)) }
                )
            }

            composable(
                route = Routes.OCR_STUDIO,
                arguments = listOf(navArgument("path") { type = NavType.StringType; defaultValue = "" })
            ) { entry ->
                val decoded = Uri.decode(entry.arguments?.getString("path").orEmpty())
                OcrStudioScreen(
                    app = app,
                    initialPath = decoded.ifBlank { null },
                    onBack = { navController.popBackStack() },
                    onOpenPdf = { path -> navController.navigate(Routes.viewer(path)) }
                )
            }

            composable(
                route = Routes.FORM_FILLER,
                arguments = listOf(navArgument("path") { type = NavType.StringType; defaultValue = "" })
            ) { entry ->
                val decoded = Uri.decode(entry.arguments?.getString("path").orEmpty())
                FormFillerScreen(
                    app = app,
                    initialPath = decoded.ifBlank { null },
                    onBack = { navController.popBackStack() },
                    onOpenPdf = { path -> navController.navigate(Routes.viewer(path)) }
                )
            }

            composable(Routes.DIGITAL_SIGN) {
                DigitalSignatureScreen(
                    app = app,
                    onBack = { navController.popBackStack() },
                    onOpenPdf = { path -> navController.navigate(Routes.viewer(path)) }
                )
            }

            composable(Routes.CAM_SCANNER) {
                CamScannerScreen(
                    app = app,
                    onBack = { navController.popBackStack() },
                    onOpenPdf = { path -> navController.navigate(Routes.viewer(path)) }
                )
            }

            composable(Routes.CAMERA_TRANSLATE) {
                LiveCameraTranslateScreen(
                    app = app,
                    onBack = { navController.popBackStack() },
                    onOpenPdf = { path -> navController.navigate(Routes.viewer(path)) }
                )
            }

            composable(Routes.TOOLS) {
                ToolsHubScreen(onSelectTool = { routeToTool(it) })
            }

            composable(Routes.SETTINGS) {
                SettingsAboutScreen(app = app)
            }

            composable(
                route = Routes.VIEWER,
                arguments = listOf(
                    navArgument("path") { type = NavType.StringType },
                    navArgument("page") { type = NavType.IntType; defaultValue = -1 }
                )
            ) { entry ->
                val decodedPath = Uri.decode(entry.arguments?.getString("path").orEmpty())
                val pageArg = entry.arguments?.getInt("page") ?: -1
                ViewerEditorScreen(
                    app = app,
                    filePath = decodedPath,
                    initialPageIndex = pageArg,
                    onBack = { navController.popBackStack() },
                    onOpenOrganizer = { p -> navController.navigate(Routes.organizer(p)) },
                    onOpenAiStudio = { p -> navController.navigate(Routes.aiStudioFor(p)) },
                    onOpenFormFiller = { p -> navController.navigate(Routes.formFor(p)) },
                    onOpenOcr = { p -> navController.navigate(Routes.ocrFor(p)) },
                    onSwitchDocument = { p ->
                        navController.navigate(Routes.viewer(p)) {
                            popUpTo(Routes.LIBRARY)
                        }
                    }
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
