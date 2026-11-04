package nl.jeroen.massqueue.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import nl.jeroen.massqueue.tr

/**
 * Welkomstscherm bij de allereerste start: dit toestel verplicht een naam geven.
 * Met die naam verschijnt het als speler in Music Assistant ("telefoon als speler" gaat vanzelf aan).
 * Er is geen terug- of overslaanknop; doorgaan kan pas met een niet-lege naam.
 */
@Composable
fun DeviceNameScreen(
    suggestedName: String,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    val valid = name.isNotBlank()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Outlined.PhoneAndroid,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(24.dp))
            Text(
                tr("Welkom bij SpinFlow", "Welcome to SpinFlow"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                tr(
                    "Geef dit toestel een naam. Met die naam verschijnt het als speler in Music Assistant, zodat je ook muziek op dit toestel zelf kunt afspelen.",
                    "Give this device a name. It will show up under that name as a player in Music Assistant, so you can also play music on this device itself."
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(tr("Naam van dit toestel", "Name of this device")) },
                placeholder = { Text(suggestedName) },
                supportingText = {
                    Text(tr("Bijvoorbeeld \"Mijn telefoon\" of \"Tablet keuken\"", "For example \"My phone\" or \"Kitchen tablet\""))
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Badge, contentDescription = null) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { if (valid) onConfirm(name.trim()) }),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { onConfirm(name.trim()) },
                enabled = valid,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(tr("Verder", "Continue"))
            }
        }
    }
}
