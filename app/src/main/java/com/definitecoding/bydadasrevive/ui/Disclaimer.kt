package com.definitecoding.bydadasrevive.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** The name that appears in the liability wording. Swap for a legal entity if you form one. */
const val DEVELOPER_NAME = "DefiniteCoding"

const val DISCLAIMER_VERSION = 1

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

    AlertDialog(
        onDismissRequest = { /* Deliberately not dismissible: it needs an answer. */ },
        title = { Text("Read this first") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(DISCLAIMER_TEXT, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.heightIn(min = 12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Checkbox(checked = suppress, onCheckedChange = { suppress = it })
                    Spacer(Modifier.width(4.dp))
                    Text("Do not show this again", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAccept(suppress) },
                colors = ButtonDefaults.buttonColors(containerColor = Ok, contentColor = Color.Black),
            ) {
                Text("I accept")
            }
        },
        dismissButton = { TextButton(onClick = onDecline) { Text("Decline and close") } },
    )
}
