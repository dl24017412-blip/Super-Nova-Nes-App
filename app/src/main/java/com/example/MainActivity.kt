package com.example

import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.jni.NativeBridge
import com.example.model.SnesButton
import com.example.ui.emulator.EmulatorScreen
import com.example.ui.emulator.EmulatorViewModel
import com.example.ui.library.LibraryScreen
import com.example.ui.library.LibraryViewModel
import com.example.ui.navigation.Screen
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SettingsViewModel
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.SuperNovaSnesTheme

class MainActivity : ComponentActivity() {
    private val libraryViewModel: LibraryViewModel by viewModels()
    private val emulatorViewModel: EmulatorViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    private var hardwareButtonMask = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            SuperNovaSnesTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    SuperNovaApp(
                        libraryViewModel = libraryViewModel,
                        emulatorViewModel = emulatorViewModel,
                        settingsViewModel = settingsViewModel
                    )
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if ((event.source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
            (event.source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK ||
            (event.source and InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD
        ) {
            val button = mapKeyCodeToSnesButton(event.keyCode)
            if (button != null) {
                val isDown = event.action == KeyEvent.ACTION_DOWN
                hardwareButtonMask = if (isDown) {
                    hardwareButtonMask or button.mask
                } else {
                    hardwareButtonMask and button.mask.inv()
                }
                emulatorViewModel.setRawButtonMask(hardwareButtonMask)
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if ((event.source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
            val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
            val stickX = event.getAxisValue(MotionEvent.AXIS_X)
            val stickY = event.getAxisValue(MotionEvent.AXIS_Y)

            val left = hatX < -0.5f || stickX < -0.5f
            val right = hatX > 0.5f || stickX > 0.5f
            val up = hatY < -0.5f || stickY < -0.5f
            val down = hatY > 0.5f || stickY > 0.5f

            updateDpadMask(SnesButton.LEFT, left)
            updateDpadMask(SnesButton.RIGHT, right)
            updateDpadMask(SnesButton.UP, up)
            updateDpadMask(SnesButton.DOWN, down)

            emulatorViewModel.setRawButtonMask(hardwareButtonMask)
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    private fun updateDpadMask(button: SnesButton, pressed: Boolean) {
        hardwareButtonMask = if (pressed) {
            hardwareButtonMask or button.mask
        } else {
            hardwareButtonMask and button.mask.inv()
        }
    }

    private fun mapKeyCodeToSnesButton(keyCode: Int): SnesButton? {
        return when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> SnesButton.B
            KeyEvent.KEYCODE_BUTTON_B -> SnesButton.A
            KeyEvent.KEYCODE_BUTTON_X -> SnesButton.Y
            KeyEvent.KEYCODE_BUTTON_Y -> SnesButton.X
            KeyEvent.KEYCODE_BUTTON_L1 -> SnesButton.L
            KeyEvent.KEYCODE_BUTTON_R1 -> SnesButton.R
            KeyEvent.KEYCODE_BUTTON_START -> SnesButton.START
            KeyEvent.KEYCODE_BUTTON_SELECT -> SnesButton.SELECT
            KeyEvent.KEYCODE_DPAD_UP -> SnesButton.UP
            KeyEvent.KEYCODE_DPAD_DOWN -> SnesButton.DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> SnesButton.LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> SnesButton.RIGHT
            else -> null
        }
    }
}

@Composable
fun SuperNovaApp(
    libraryViewModel: LibraryViewModel,
    emulatorViewModel: EmulatorViewModel,
    settingsViewModel: SettingsViewModel
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Library.route
    ) {
        composable(Screen.Library.route) {
            LibraryScreen(
                viewModel = libraryViewModel,
                onRomSelected = { romId ->
                    navController.navigate(Screen.Emulator.createRoute(romId))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(
            route = Screen.Emulator.route,
            arguments = listOf(
                navArgument("romId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val romId = backStackEntry.arguments?.getLong("romId") ?: 0L
            EmulatorScreen(
                romId = romId,
                viewModel = emulatorViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
