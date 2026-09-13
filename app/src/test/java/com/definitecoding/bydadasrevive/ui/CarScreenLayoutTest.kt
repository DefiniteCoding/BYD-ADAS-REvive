package com.definitecoding.bydadasrevive.ui

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

/**
 * The car's screen, measured on it: 1920x1080 at density 240, so 1280x720dp.
 *
 * Two layout defects reached a real car before this file existed, and both were
 * invisible to the compiler: the wizard footer was measured at zero height so Back
 * and Next were never drawn, and the disclaimer's suppress checkbox was clipped off
 * the bottom of the dialog. Both are a control that exists in the tree but is not on
 * screen, which is exactly what assertIsDisplayed catches and what a reviewer reading
 * the diff does not.
 *
 * The screenshots are a by-product, written to build/outputs/roborazzi and attached
 * to the CI run. They are for looking at, not for diffing: nothing fails on a pixel
 * change, so there are no golden images to re-approve after every padding tweak.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = CAR_SCREEN)
class CarScreenLayoutTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * Without this the wizard is right to refuse: Robolectric reports the device as
     * "robolectric", the compatibility check fails, and every step is replaced by the
     * "this car is not supported" screen, which deliberately has no Next. The first
     * run of this test asserted Next on that screen and failed, correctly.
     */
    @Before
    fun presentAsTheCar() {
        ReflectionHelpers.setStaticField(Build::class.java, "BRAND", "BYD AUTO")
        ReflectionHelpers.setStaticField(Build::class.java, "MANUFACTURER", "BYD AUTO")
        ReflectionHelpers.setStaticField(Build::class.java, "DEVICE", "DiLink5.0")
        ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "BYD-AUTO")

        // The compatibility test is whether com.byd.clusterdebug exists, so the wizard
        // needs to find one before it will show a repair step at all.
        val application = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(application.packageManager).installPackage(
            PackageInfo().apply {
                packageName = CLUSTER_DEBUG
                versionName = "1.0"
                applicationInfo = ApplicationInfo().apply {
                    packageName = CLUSTER_DEBUG
                    flags = ApplicationInfo.FLAG_SYSTEM
                }
            }
        )
    }

    @Test
    fun disclaimerSuppressCheckboxIsOnScreen() {
        compose.setContent {
            ReviveTheme {
                DisclaimerDialog(onAccept = {}, onDecline = {})
            }
        }

        compose.onNode(isDialog()).captureRoboImage(OUT + "disclaimer.png")
        // Finding 14: this was off the bottom of the dialog on every build until the
        // terms box was given weight, so the notice could never be silenced.
        compose.onNodeWithText("Do not show this again").assertIsDisplayed()
    }

    @Test
    fun wizardBackAndNextAreOnScreen() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val model = ReviveViewModel(application)

        compose.setContent {
            ReviveTheme {
                WizardScreen(viewModel = model, onPickApk = {}, onClose = {})
            }
        }

        compose.onRoot().captureRoboImage(OUT + "wizard.png")
        // The footer was measured at zero height from the first wizard commit until the
        // content row stopped using fillMaxSize, so neither of these was drawn at all.
        // Next may legitimately be disabled on the landing step; disabled still draws.
        compose.onNodeWithText("Back").assertIsDisplayed()
        compose.onNodeWithText("Next").assertIsDisplayed()
    }
}

/**
 * Resource qualifiers in Android's own order: available width, available height,
 * orientation, density. 1280x720dp is 1920x1080 at density 240.
 */
private const val CAR_SCREEN = "w1280dp-h720dp-land-hdpi"

private const val OUT = "build/outputs/roborazzi/"

private const val CLUSTER_DEBUG = "com.byd.clusterdebug"
