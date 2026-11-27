package ir.namak.sms.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.namak.sms.core.persian.PersianTextFormatter
import ir.namak.sms.core.sms.SimCardInfo
import ir.namak.sms.data.telephony.ContactInfo
import ir.namak.sms.ui.components.ContactAvatar
import ir.namak.sms.ui.components.GlassCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewMessageScreen(
    initialBody: String = "",
    contacts: List<ContactInfo>,
    activeSims: List<SimCardInfo>,
    onBack: () -> Unit,
    onSendAndOpen: (address: String, body: String, subId: Int?) -> Unit
) {
    var recipient by remember { mutableStateOf("") }
    var messageBody by remember { mutableStateOf(initialBody) }
    var selectedSubId by remember(activeSims) { mutableStateOf(activeSims.firstOrNull()?.subscriptionId) }

    val filteredContacts = remember(contacts, recipient) {
        if (recipient.isBlank()) contacts.take(25)
        else contacts.filter {
            it.name.contains(recipient, ignoreCase = true) || it.phone.contains(recipient)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("پیام جدید") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = recipient,
                onValueChange = { recipient = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("شماره گیرنده یا نام مخاطب") },
                shape = RoundedCornerShape(18.dp),
                singleLine = true
            )

            if (activeSims.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    activeSims.forEach { sim ->
                        FilterChip(
                            selected = selectedSubId == sim.subscriptionId,
                            onClick = { selectedSubId = sim.subscriptionId },
                            label = { Text("سیم ${PersianTextFormatter.toPersianDigits(sim.slotIndex.toString())}: ${sim.displayName}") }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = messageBody,
                onValueChange = { messageBody = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("متن پیامک") },
                shape = RoundedCornerShape(18.dp),
                minLines = 3
            )

            Button(
                onClick = {
                    if (recipient.isNotBlank() && messageBody.isNotBlank()) {
                        onSendAndOpen(recipient.trim(), messageBody, selectedSubId)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, null)
                Spacer(Modifier.width(8.dp))
                Text("ارسال پیامک")
            }

            Text("مخاطبین دستگاه", style = MaterialTheme.typography.titleMedium)

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filteredContacts) { contact ->
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { recipient = contact.phone }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ContactAvatar(name = contact.name, address = contact.phone, size = 40.dp)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(contact.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    PersianTextFormatter.formatPhoneLtr(contact.phone),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
