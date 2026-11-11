package app.leeeo.gachita

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import java.io.File
import org.junit.Rule
import org.junit.Test

class RideFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performScrollTo().performClick()
    }

    private fun drive() {
        click("start")
        compose.onNodeWithTag("depart").assertIsNotEnabled()
        capture("01-checking", "phase-CHECKING")
        click("belt")
        compose.onNodeWithTag("depart").assertIsEnabled()
        click("depart")
        compose.onNodeWithTag("phase-RIDING").assertExists()
        compose.onNodeWithTag("start").assertDoesNotExist()
    }

    private fun capture(name: String, phase: String) {
        compose.onNodeWithTag(phase).performScrollTo()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val directory = File(compose.activity.getExternalFilesDir(null), "screenshots")
        check(directory.isDirectory || directory.mkdirs())
        File(directory, "$name.png").outputStream().use {
            check(image.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    @Test fun completeRideWithEscalationAndDirectConfirmation() {
        drive()
        click("eta-ten")
        click("eta-three")
        capture("02-riding", "phase-RIDING")
        click("park")
        compose.onNodeWithTag("phase-ARRIVAL").assertExists()
        compose.onNodeWithTag("confirm").assertIsNotEnabled()
        capture("03-arrival", "phase-ARRIVAL")
        click("driver-exit")
        click("advance")
        compose.onNodeWithTag("escalated").assertExists()
        capture("04-escalated", "phase-ARRIVAL")
        click("child-exit")
        compose.onNodeWithTag("confirm").assertIsEnabled()
        click("confirm")
        compose.onNodeWithTag("phase-COMPLETE").assertExists()
        compose.onNodeWithTag("escalated").assertDoesNotExist()
        capture("05-complete", "phase-COMPLETE")
    }

    @Test fun unknownSeatDoesNotEnableConfirmation() {
        drive()
        click("park")
        click("child-exit")
        click("unknown")
        compose.onNodeWithTag("confirm").assertIsNotEnabled()
    }

    @Test fun recreationRetainsUnconfirmedEscalation() {
        drive()
        click("park")
        click("advance")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("phase-ARRIVAL").assertExists()
        compose.onNodeWithTag("escalated").assertExists()
        compose.onNodeWithTag("confirm").assertIsNotEnabled()
    }
}
