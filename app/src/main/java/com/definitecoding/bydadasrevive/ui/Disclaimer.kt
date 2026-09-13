package com.definitecoding.bydadasrevive.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** The name that appears in the liability wording. Swap for a legal entity if you form one. */
const val DEVELOPER_NAME = "DefiniteCoding"

const val DISCLAIMER_VERSION = 1

/** What the notice actually means, for someone who will not read the clauses. */
val DISCLAIMER_SUMMARY = listOf(
    "This app is not made by BYD and has nothing to do with BYD.",
    "It changes software on your car and opens a factory diagnostic screen.",
    "That can affect driver-assistance features, may void warranty, and may not be reversible.",
    "Nothing this app tells you is proof that a safety system works. Only your own checks are.",
    "If something goes wrong, that is on you, not on the developer.",
)

val DISCLAIMER_TEXT = """
BYD ADAS REvive is an unofficial, community-built tool, provided free of charge, AS IS and
WITHOUT WARRANTY OF ANY KIND, express or implied, including but not limited to the implied
warranties of merchantability, fitness for a particular purpose and non-infringement. It is
licensed under the GNU General Public License v3.0, which itself grants no warranty.

NOT AFFILIATED WITH BYD. This application is not affiliated with, authorised by, endorsed by
or supported by BYD Auto Co., Ltd. or any of its subsidiaries. All product names, trademarks
and registered trademarks are the property of their respective owners.

WHAT IT DOES. This application installs and removes an application package on your vehicle's
head unit and opens a factory diagnostic screen. These are changes to software running on a
vehicle. They may alter the behaviour of driver-assistance features, may not be reversible,
may void part or all of your vehicle warranty or service agreement, and may be restricted by
law or regulation where you live.

SAFETY. Advanced driver-assistance systems are safety-related. Nothing this application
reports is a verification that any such system is present, calibrated, functioning or safe. A
successful install means a software package was written to storage and nothing more. Never
rely on a driver-assistance feature you have not verified yourself, under safe and controlled
conditions. Carry out every step with the vehicle stationary, parked and in a safe location,
and never while driving.

YOUR RESPONSIBILITY. You use this application entirely at your own risk and on your own
initiative. You are solely responsible for every action you take with it, for deciding whether
those actions are appropriate and lawful for your vehicle, for following the steps in the
order given, and for any and all consequences that follow.

LIMITATION OF LIABILITY. To the maximum extent permitted by applicable law, $DEVELOPER_NAME,
the developer of this application, and any contributor to it shall not be liable for any
claim, damage, loss or other liability of any kind, including without limitation direct,
indirect, incidental, special, consequential, punitive or exemplary damages, loss of use, loss
of data, damage to your vehicle or any of its systems, loss of warranty or insurance coverage,
economic loss, or personal injury, arising from or in connection with this application, its
use, its misuse, or the inability to use it, whether in an action of contract, tort or
otherwise, and whether or not the developer was advised of the possibility of such damage.

By continuing you confirm that you have read and understood this notice, that you accept sole
responsibility for your use of this application, and that you agree to these terms. If you do
not agree, do not use this application.
""".trimIndent()

@Composable
fun DisclaimerDialog(
    onAccept: (suppressFuture: Boolean) -> Unit,
    onDecline: () -> Unit,
) {
    var suppress by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    // Accept stays shut until the terms have actually gone past.
    val readToEnd by remember {
        derivedStateOf { scroll.maxValue == 0 || scroll.value >= scroll.maxValue - 8 }
    }

    AlertDialog(
        onDismissRequest = { /* Deliberately not dismissible: it needs an answer. */ },
        title = { Text("Before you use this") },
        text = {
            Column {
                DISCLAIMER_SUMMARY.forEach { line ->
                    Row(Modifier.padding(bottom = 6.dp)) {
                        Text("-", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.width(8.dp))
                        Text(line, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Spacer(Modifier.heightIn(min = 10.dp))
                Text(
                    "The full terms:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // The terms box is the only part that may shrink. Material3 measures this
                // slot with weight(1f, fill = false) and clips it from the bottom, and the
                // checkbox is the last thing in the column, so at 240dpi on a 720dp-tall
                // window the checkbox fell off the end and the notice could not be silenced.
                // Weighting the box makes Compose measure every fixed sibling first and hand
                // this one what is left, so the checkbox keeps its 56dp whatever the density.
                // fill = false keeps the box at its content height on a window with room to
                // spare, and heightIn caps it where it always was.
                Column(
                    Modifier
                        .heightIn(max = 260.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(8.dp),
                        )
                        .padding(12.dp)
                        .verticalScroll(scroll)
                ) {
                    Text(DISCLAIMER_TEXT, style = MaterialTheme.typography.bodyMedium)
                }
                if (!readToEnd) {
                    Text(
                        "Scroll to the end of the terms to continue.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Warn,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                // Outside the scrolling region, so it cannot be scrolled out of sight.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Checkbox(checked = suppress, onCheckedChange = { suppress = it })
                    Spacer(Modifier.width(8.dp))
                    Text("Do not show this again", style = MaterialTheme.typography.bodyLarge)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAccept(suppress) },
                enabled = readToEnd,
                colors = ButtonDefaults.buttonColors(containerColor = Ok, contentColor = Color.Black),
                modifier = Modifier.defaultMinSize(minHeight = TAP_TARGET_HEIGHT),
            ) {
                Text("I accept")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDecline,
                modifier = Modifier.defaultMinSize(minHeight = TAP_TARGET_HEIGHT),
            ) {
                Text("Decline and close")
            }
        },
    )
}
