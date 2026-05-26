package com.eqm.inspection

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.eqm.inspection.data.SettingsDataStore
import com.eqm.inspection.data.api.ApiClient
import com.eqm.inspection.data.api.TokenManager
import com.eqm.inspection.ui.about.AboutScreen
import com.eqm.inspection.ui.dashboard.DashboardScreen
import com.eqm.inspection.ui.detail.DetailScreen
import com.eqm.inspection.ui.draft.DraftListScreen
import com.eqm.inspection.ui.inspection.InspectionScreen
import com.eqm.inspection.ui.login.LoginScreen
import com.eqm.inspection.ui.navigation.Routes
import com.eqm.inspection.ui.query.QueryScreen
import com.eqm.inspection.ui.settings.SettingsScreen
import com.eqm.inspection.ui.theme.VendorInspectionTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as VendorInspectionApp
        val tokenManager = app.tokenManager
        val settingsDataStore = app.settingsDataStore

        setContent {
            VendorInspectionTheme {
                Surface(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                    MainScreen(tokenManager, settingsDataStore)
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    tokenManager: TokenManager,
    settingsDataStore: SettingsDataStore
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    // 检查是否已有保存的 Token
    var startDestination by remember { mutableStateOf<String?>(null) }
    var username by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val savedToken = tokenManager.getTokenSync()
        val serverUrl = settingsDataStore.getServerUrl()
        if (serverUrl.isNotEmpty()) {
            ApiClient.updateBaseUrl(serverUrl)
        }
        if (savedToken != null) {
            ApiClient.currentToken = savedToken
            username = tokenManager.getUsernameSync() ?: ""
            role = tokenManager.getRoleSync() ?: ""

            // 验证服务器是否可达，不可达则回到登录页
            try {
                withTimeout(5000) {
                    val resp = ApiClient.apiService.getDashboard()
                    resp.code() // 确保请求完成
                }
                startDestination = Routes.DASHBOARD
            } catch (e: Exception) {
                // 服务器不可达，清除 token 回到登录页
                tokenManager.clear()
                ApiClient.currentToken = null
                username = ""
                role = ""
                startDestination = Routes.LOGIN
            }
        } else {
            startDestination = Routes.LOGIN
        }
    }

    // 等待 startDestination 确定后再渲染 NavHost
    if (startDestination == null) return

    NavHost(
        navController = navController,
        startDestination = startDestination!!,
    ) {
        // 登录
        composable(Routes.LOGIN) {
            LoginScreen(
                settingsDataStore = settingsDataStore,
                onLoginSuccess = { token, uname, urole ->
                    scope.launch {
                        tokenManager.saveToken(token, uname, urole)
                    }
                    ApiClient.currentToken = token
                    username = uname
                    role = urole
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        // 仪表板
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                role = role,
                onNavigateToInspection = if (role in listOf("vendor", "admin")) {
                    { navController.navigate(Routes.INSPECTION) }
                } else null,
                onNavigateToQuery = {
                    navController.navigate(Routes.QUERY)
                },
                onNavigateToDrafts = if (role in listOf("vendor", "admin")) {
                    { navController.navigate(Routes.DRAFTS) }
                } else null,
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                },
                onNavigateToDetail = { recordId ->
                    navController.navigate(Routes.detail(recordId))
                }
            )
        }

        // 巡检填写
        composable(
            route = Routes.INSPECTION + "?draftId={draftId}",
            arguments = listOf(
                navArgument("draftId") {
                    type = NavType.IntType
                    defaultValue = -1
                }
            )
        ) { backStackEntry ->
            val draftId = backStackEntry.arguments?.getInt("draftId") ?: -1
            InspectionScreen(
                draftId = if (draftId > 0) draftId else null,
                onBack = { navController.popBackStack() },
                onSubmitted = { recordId ->
                    navController.navigate(Routes.detail(recordId)) {
                        popUpTo(Routes.DASHBOARD)
                    }
                },
                onDraftSaved = {
                    navController.popBackStack()
                }
            )
        }

        // 查询
        composable(Routes.QUERY) {
            QueryScreen(
                onNavigateToDetail = { recordId ->
                    navController.navigate(Routes.detail(recordId))
                },
                onNavigateToReview = { recordId ->
                    navController.navigate(Routes.reviewEdit(recordId))
                },
                onBack = { navController.popBackStack() }
            )
        }

        // 详情
        composable(
            route = Routes.DETAIL,
            arguments = listOf(
                navArgument("recordId") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val recordId = backStackEntry.arguments?.getInt("recordId") ?: return@composable
            DetailScreen(
                recordId = recordId,
                role = role,
                onBack = { navController.popBackStack() },
                onNavigateToReview = { id ->
                    navController.navigate(Routes.reviewEdit(id))
                }
            )
        }

        // 审核（编辑后确认）
        composable(
            route = Routes.REVIEW_INSPECTION,
            arguments = listOf(
                navArgument("recordId") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val recordId = backStackEntry.arguments?.getInt("recordId") ?: return@composable
            InspectionScreen(
                reviewRecordId = recordId,
                onBack = { navController.popBackStack() },
                onSubmitted = { id ->
                    navController.navigate(Routes.detail(id)) {
                        popUpTo(Routes.DASHBOARD)
                    }
                },
                onDraftSaved = {
                    navController.popBackStack()
                }
            )
        }

        // 暂存管理
        composable(Routes.DRAFTS) {
            DraftListScreen(
                onNavigateToInspection = { draftId ->
                    navController.navigate(Routes.inspectionEdit(draftId))
                },
                onBack = { navController.popBackStack() }
            )
        }

        // 设置
        composable(Routes.SETTINGS) {
            SettingsScreen(
                settingsDataStore = settingsDataStore,
                username = username,
                role = role,
                onNavigateToAbout = { navController.navigate(Routes.ABOUT) },
                onLogout = {
                    scope.launch {
                        tokenManager.clear()
                    }
                    ApiClient.currentToken = null
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        // 關於
        composable(Routes.ABOUT) {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val versionName = try {
                ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "1.0"
            } catch (e: Exception) {
                "1.0"
            }
            AboutScreen(
                versionName = versionName,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
