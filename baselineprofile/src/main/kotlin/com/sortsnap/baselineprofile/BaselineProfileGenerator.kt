package com.sortsnap.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the Baseline Profile for startup and gallery scrolling.
 *
 * Run on a device or emulator (API 28+) that has granted photo access and holds some images:
 *   ./gradlew :app:generateReleaseBaselineProfile
 * The profile is written to app/src/release/generated/baselineProfiles and bundled automatically.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startupAndGalleryScroll() {
        val packageName = InstrumentationRegistry.getArguments().getString("targetAppId") ?: "com.sortsnap"
        rule.collect(packageName = packageName, includeInStartupProfile = true) {
            pressHome()
            startActivityAndWait()
            device.wait(Until.hasObject(By.res("gallery_grid")), 5_000)
            val grid = device.findObject(By.res("gallery_grid")) ?: return@collect
            grid.setGestureMargin(device.displayWidth / 5)
            repeat(3) { grid.fling(Direction.DOWN) }
            repeat(2) { grid.fling(Direction.UP) }
        }
    }
}
