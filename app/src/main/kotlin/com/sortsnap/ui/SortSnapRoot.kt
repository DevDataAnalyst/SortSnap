package com.sortsnap.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sortsnap.R
import com.sortsnap.domain.GalleryFilter
import com.sortsnap.ui.categories.CategoriesScreen
import com.sortsnap.ui.gallery.GalleryScreen
import com.sortsnap.ui.onboarding.OnboardingScreen
import com.sortsnap.ui.onboarding.PartialAccessBanner
import com.sortsnap.ui.permission.PhotoAccess
import com.sortsnap.ui.review.ReviewScreen
import com.sortsnap.ui.review.SectionScreen
import com.sortsnap.ui.review.SwipeScreen
import com.sortsnap.ui.settings.AboutScreen
import com.sortsnap.ui.settings.DebugScreen
import com.sortsnap.ui.settings.SettingsScreen
import com.sortsnap.ui.viewer.ViewerScreen

object Routes {
    const val GALLERY = "gallery"
    const val CATEGORIES = "categories"
    const val REVIEW = "review"
    const val SETTINGS = "settings"
    const val ALBUM = "album/{filter}"
    const val VIEWER = "viewer/{filter}/{index}"
    const val SECTION = "section/{section}"
    const val SWIPE = "swipe"
    const val ABOUT = "about"
    const val DEBUG = "debug"

    fun album(filter: GalleryFilter) = "album/${Uri.encode(filter.encode())}"
    fun viewer(filter: GalleryFilter, index: Int) = "viewer/${Uri.encode(filter.encode())}/$index"
    fun section(name: String) = "section/$name"
}

private data class Tab(val route: String, val label: Int, val icon: ImageVector)

private val TABS = listOf(
    Tab(Routes.GALLERY, R.string.nav_gallery, Icons.Filled.Home),
    Tab(Routes.CATEGORIES, R.string.nav_categories, Icons.AutoMirrored.Filled.List),
    Tab(Routes.REVIEW, R.string.nav_review, Icons.Filled.Delete),
    Tab(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings),
)

@Composable
fun SortSnapRoot(onAccessChanged: () -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var access by remember { mutableStateOf(PhotoAccess.current(context)) }
    var askedOnce by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        askedOnce = true
        access = PhotoAccess.current(context)
        onAccessChanged()
    }
    // Users can change access in system settings at any time: re-check on every resume.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val now = PhotoAccess.current(context)
        if (now != access) { access = now; onAccessChanged() }
    }
    val request = { launcher.launch(PhotoAccess.requestSet()) }

    if (access == PhotoAccess.NONE) {
        val primary = PhotoAccess.requestSet().first()
        val permanentlyDenied = askedOnce && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, primary)
        OnboardingScreen(
            deniedPermanently = permanentlyDenied,
            onRequestAccess = request,
            onOpenSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
        )
        return
    }
    MainScaffold(partial = access == PhotoAccess.PARTIAL, onAllowAll = request)
}

/** The four-tab bottom bar: Gallery, Categories, Review, Settings. */
@Composable
fun SortSnapBottomBar(currentRoute: String?, onNavigate: (String) -> Unit) {
    NavigationBar(Modifier.testTag("bottom_nav")) {
        TABS.forEach { tab ->
            NavigationBarItem(
                selected = currentRoute == tab.route,
                onClick = { onNavigate(tab.route) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(stringResource(tab.label)) },
                modifier = Modifier.testTag("tab_${tab.route}"),
            )
        }
    }
}

@Composable
private fun MainScaffold(partial: Boolean, onAllowAll: () -> Unit) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = route in TABS.map { it.route }
    Scaffold(
        bottomBar = {
            if (showBar) {
                SortSnapBottomBar(currentRoute = route) { target ->
                    nav.navigate(target) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.GALLERY, modifier = Modifier.padding(bottom = if (showBar) padding.calculateBottomPadding() else androidx.compose.ui.unit.Dp(0f))) {
            composable(Routes.GALLERY) {
                GalleryScreen(
                    onOpenViewer = { f, i -> nav.navigate(Routes.viewer(f, i)) },
                    banner = { if (partial) PartialAccessBanner(onAllowAll) },
                )
            }
            composable(Routes.CATEGORIES) {
                CategoriesScreen(onOpenCategory = { nav.navigate(Routes.album(GalleryFilter.Only(it))) })
            }
            composable(Routes.REVIEW) {
                ReviewScreen(onOpenSection = { nav.navigate(Routes.section(it.name)) }, onOpenSwipe = { nav.navigate(Routes.SWIPE) })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onOpenAbout = { nav.navigate(Routes.ABOUT) }, onOpenDebug = { nav.navigate(Routes.DEBUG) })
            }
            composable(Routes.ALBUM, arguments = listOf(navArgument("filter") { type = NavType.StringType })) {
                GalleryScreen(onOpenViewer = { f, i -> nav.navigate(Routes.viewer(f, i)) }, onBack = { nav.popBackStack() })
            }
            composable(
                Routes.VIEWER,
                arguments = listOf(navArgument("filter") { type = NavType.StringType }, navArgument("index") { type = NavType.StringType }),
            ) { ViewerScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.SECTION, arguments = listOf(navArgument("section") { type = NavType.StringType })) {
                SectionScreen(onBack = { nav.popBackStack() })
            }
            composable(Routes.SWIPE) { SwipeScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.ABOUT) { AboutScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.DEBUG) { DebugScreen(onBack = { nav.popBackStack() }) }
        }
    }
}
