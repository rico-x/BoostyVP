package ru.ardcode.boostytv.navigation

import androidx.compose.runtime.Composable
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
import androidx.navigation.navArgument
import ru.ardcode.boostytv.core.model.AuthState
import ru.ardcode.boostytv.core.ui.component.LoadingState
import ru.ardcode.boostytv.feature.blog.BlogScreen
import ru.ardcode.boostytv.feature.home.HomeScreen
import ru.ardcode.boostytv.feature.pairing.AuthLandingScreen
import ru.ardcode.boostytv.feature.pairing.QrPairingScreen
import ru.ardcode.boostytv.feature.pairing.PhoneLoginScreen
import ru.ardcode.boostytv.feature.player.PlayerScreen
import android.net.Uri

object Routes {
    /** Choice of sign-in route. */
    const val AUTH = "auth"
    const val PAIRING_QR = "auth/qr"
    const val PAIRING_PHONE = "auth/phone"
    const val HOME = "home"
    const val BLOG = "blog/{blogUrl}"
    const val PLAYER = "player/{blogUrl}/{postId}"

    fun blog(blogUrl: String) = "blog/${blogUrl.encodeArg()}"
    fun player(blogUrl: String, postId: String) =
        "player/${blogUrl.encodeArg()}/${postId.encodeArg()}"
}

/** Blog slugs are user-controlled and can contain characters that break a route. */
private fun String.encodeArg(): String = Uri.encode(this)

@Composable
fun BoostyNavHost(
    navController: NavHostController = rememberNavController(),
    viewModel: RootViewModel = hiltViewModel(),
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val signOutState by viewModel.signOutState.collectAsStateWithLifecycle()

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
                onSignOut = viewModel::signOut,
                signingOut = signOutState.inProgress,
                signOutError = signOutState.error,
                onPostClick = { post ->
                    navController.navigate(Routes.player(post.blogUrl, post.id))
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
            )
        }

        playerDestination(onClose = { navController.popBackStack() })
    }
}

// Keep the previous screen composed underneath, including its loaded pages,
// scroll offsets and focus nodes. Content is replaceable for navigation tests.
internal fun NavGraphBuilder.playerDestination(
    onClose: () -> Unit = {},
    content: @Composable (String, String) -> Unit = { blog, post ->
        PlayerScreen(blogUrl = blog, postId = post, onClose = onClose)
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
        ),
    ) { entry ->
        val blogUrl = entry.arguments?.getString("blogUrl").orEmpty()
        val postId = entry.arguments?.getString("postId").orEmpty()
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            content(blogUrl, postId)
        }
    }
}
