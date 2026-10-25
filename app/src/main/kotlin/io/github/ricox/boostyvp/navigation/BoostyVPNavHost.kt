package io.github.ricox.boostyvp.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import io.github.ricox.boostyvp.core.model.AuthState
import io.github.ricox.boostyvp.core.ui.component.LoadingState
import io.github.ricox.boostyvp.core.ui.component.LocalWatchProgress
import io.github.ricox.boostyvp.core.ui.component.LocalLibraryControls
import io.github.ricox.boostyvp.feature.blog.BlogScreen
import io.github.ricox.boostyvp.feature.home.HomeScreen
import io.github.ricox.boostyvp.feature.pairing.AuthLandingScreen
import io.github.ricox.boostyvp.feature.pairing.QrPairingScreen
import io.github.ricox.boostyvp.feature.pairing.PhoneLoginScreen
import io.github.ricox.boostyvp.feature.player.PlayerScreen
import android.net.Uri
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.ricox.boostyvp.integration.ContentTarget
import io.github.ricox.boostyvp.integration.IntegrationViewModel
import io.github.ricox.boostyvp.integration.SettingsHubDialog
import io.github.ricox.boostyvp.core.ui.component.LocalCatalogControls

object Routes {
    /** Choice of sign-in route. */
    const val AUTH = "auth"
    const val PAIRING_QR = "auth/qr"
    const val PAIRING_PHONE = "auth/phone"
    const val HOME = "home"
    const val BLOG = "blog/{blogUrl}"
    const val PLAYER = "player/{blogUrl}/{postId}?fromStart={fromStart}"
    const val COMMENTS = "comments/{blogUrl}/{postId}"

    fun blog(blogUrl: String) = "blog/${blogUrl.encodeArg()}"
    fun player(blogUrl: String, postId: String, fromStart: Boolean = false) =
        "player/${blogUrl.encodeArg()}/${postId.encodeArg()}" + if (fromStart) "?fromStart=true" else ""
    fun comments(blogUrl: String, postId: String) = "comments/${blogUrl.encodeArg()}/${postId.encodeArg()}"
}

/** Blog slugs are user-controlled and can contain characters that break a route. */
private fun String.encodeArg(): String = Uri.encode(this)

@Composable
fun BoostyVPNavHost(
    navController: NavHostController = rememberNavController(),
    viewModel: RootViewModel = hiltViewModel(),
    watchProgress: WatchProgressViewModel = hiltViewModel(),
    personalLibrary: PersonalLibraryViewModel = hiltViewModel(),
    integration: IntegrationViewModel = hiltViewModel(),
    externalTarget: ContentTarget? = null,
    onExternalHandled: () -> Unit = {},
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val watched by watchProgress.byPost.collectAsStateWithLifecycle()
    val library by personalLibrary.controls.collectAsStateWithLifecycle()
    val signOutState by viewModel.signOutState.collectAsStateWithLifecycle()
    val catalog by integration.controls.collectAsStateWithLifecycle()
    var settings by remember { mutableStateOf(false) }
    LaunchedEffect(externalTarget) { if (externalTarget != null) settings = false }

    when (authState) {
        AuthState.Unknown -> {
            LoadingState()
            return
        }

        AuthState.SignedOut -> {
            // Bounce back to pairing from wherever the user was when the session
            // died -- a refresh failure mid-session must not leave them on a
            // screen that can only show errors.
            LaunchedEffect(Unit) {
                navController.navigate(Routes.AUTH) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }

        is AuthState.SignedIn -> Unit
    }

    CompositionLocalProvider(LocalWatchProgress provides watched, LocalLibraryControls provides library,
        LocalCatalogControls provides catalog,
        io.github.ricox.boostyvp.core.ui.component.LocalPostComments provides { post ->
            navController.navigate(Routes.comments(post.blogUrl, post.id)) { launchSingleTop = true }
        },
        io.github.ricox.boostyvp.core.ui.component.LocalPostResolver provides integration::resolvePost) {
        NavHost(
            navController = navController,
            startDestination = if (authState is AuthState.SignedIn) Routes.HOME else Routes.AUTH,
        ) {
            // Both sign-in routes converge here: clear the whole auth
            // subgraph so Back from Home never lands on a sign-in screen for an
            // account that is already signed in.
            val toHome: () -> Unit = {
                navController.navigate(Routes.HOME) {
                    popUpTo(Routes.AUTH) { inclusive = true }
                }
            }

            composable(Routes.AUTH) {
                AuthLandingScreen(
                    onChoosePhonePairing = { navController.navigate(Routes.PAIRING_QR) },
                    onChooseOnDevice = { navController.navigate(Routes.PAIRING_PHONE) },
                )
            }

            composable(Routes.PAIRING_QR) {
                QrPairingScreen(onPaired = toHome, onBack = { navController.popBackStack() })
            }

            composable(Routes.PAIRING_PHONE) {
                PhoneLoginScreen(onSignedIn = toHome, onBack = { navController.popBackStack() })
            }

            composable(Routes.HOME) {
                HomeScreen(
                    onUnifiedSettings = { settings = true },
                    onSignOut = viewModel::signOut,
                    signingOut = signOutState.inProgress,
                    signOutError = signOutState.error,
                    onPostClick = { post ->
                        navController.navigate(Routes.player(post.blogUrl, post.id))
                    },
                    onPostFromStart = { post ->
                        navController.navigate(Routes.player(post.blogUrl, post.id, fromStart = true))
                    },
                    onBlogClick = { blogUrl ->
                        navController.navigate(Routes.blog(blogUrl))
                    },
                )
            }

            composable(
                route = Routes.BLOG,
                arguments = listOf(navArgument("blogUrl") { type = NavType.StringType }),
            ) { entry ->
                val blogUrl = entry.arguments?.getString("blogUrl").orEmpty()
                BlogScreen(
                    blogUrl = blogUrl,
                    onBack = { navController.popBackStack() },
                    onPostClick = { post ->
                        navController.navigate(Routes.player(post.blogUrl, post.id))
                    },
                    onPostFromStart = { post ->
                        navController.navigate(Routes.player(post.blogUrl, post.id, fromStart = true))
                    },
                )
            }

            playerDestination(onClose = { navController.popBackStack() })
            commentsDestination(onClose = { navController.popBackStack() })
        }
        HandleContentTarget(navController, authState is AuthState.SignedIn, externalTarget, onExternalHandled)
        if (settings && authState is AuthState.SignedIn) SettingsHubDialog(integration) { settings = false }
    }
}

internal fun NavGraphBuilder.commentsDestination(
    onClose: () -> Unit = {},
    content: @Composable (String, String) -> Unit = { blog, post ->
        io.github.ricox.boostyvp.feature.player.VideoCommentsScreen(blog, post, onClose)
    },
) {
    dialog(Routes.COMMENTS,
        arguments = listOf(navArgument("blogUrl") { type = NavType.StringType },
            navArgument("postId") { type = NavType.StringType }),
        dialogProperties = DialogProperties(usePlatformDefaultWidth = false,
            dismissOnClickOutside = false, decorFitsSystemWindows = false)) { entry ->
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            content(entry.arguments?.getString("blogUrl").orEmpty(), entry.arguments?.getString("postId").orEmpty())
        }
    }
}

/** Wait for sign-in to finish its own Home navigation; otherwise it can pop the newly opened video. */
@Composable
internal fun HandleContentTarget(nav: NavHostController, signedIn: Boolean, target: ContentTarget?, handled: () -> Unit) {
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    LaunchedEffect(signedIn, target, route) {
        if (signedIn && target != null && route != null && !route.startsWith(Routes.AUTH)) {
            nav.navigate(Routes.player(target.blog, target.post)) { launchSingleTop = true }
            handled()
        }
    }
}

// Keep the previous screen composed underneath, including its loaded pages,
// scroll offsets and focus nodes. Content is replaceable for navigation tests.
internal fun NavGraphBuilder.playerDestination(
    onClose: () -> Unit = {},
    content: @Composable (String, String, Boolean) -> Unit = { blog, post, fromStart ->
        PlayerScreen(blogUrl = blog, postId = post, onClose = onClose, startFromBeginning = fromStart)
    },
) {
    dialog(
        route = Routes.PLAYER,
        dialogProperties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false,
        ),
        arguments = listOf(
            navArgument("blogUrl") { type = NavType.StringType },
            navArgument("postId") { type = NavType.StringType },
            navArgument("fromStart") { type = NavType.BoolType; defaultValue = false },
        ),
    ) { entry ->
        val blogUrl = entry.arguments?.getString("blogUrl").orEmpty()
        val postId = entry.arguments?.getString("postId").orEmpty()
        val fromStart = entry.arguments?.getBoolean("fromStart") ?: false
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            content(blogUrl, postId, fromStart)
        }
    }
}
