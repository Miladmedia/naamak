package ir.namak.sms.ui.screens

import android.provider.Telephony
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import ir.namak.sms.core.persian.JalaliCalendar
import ir.namak.sms.core.persian.PersianTextFormatter
import ir.namak.sms.core.sms.SimCardInfo
import ir.namak.sms.domain.model.SmsMessageUi
import ir.namak.sms.domain.model.SmsTab
import ir.namak.sms.domain.model.SmsThreadUi
import ir.namak.sms.ui.components.GlassCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    threadId: Long,
    address: String,
    threadUi: SmsThreadUi?,
    messages: List<SmsMessageUi>,
    activeSims: List<SimCardInfo>,
    onBack: () -> Unit,
    onSend: (String, String, Int?, Long) -> Unit,
    onDeleteMessages: (List<Long>) -> Unit,
    onToggleStar: (Long, Long, Boolean) -> Unit,
    onUpdateThreadMeta: (pin: Boolean?, archive: Boolean?, mute: Boolean?, spam: Boolean?, block: Boolean?, category: SmsTab?) -> Unit,
    onOpenKhaterahBazi: (SmsMessageUi) -> Unit,
    onSetReminder: (SmsMessageUi, Long) -> Unit,
    onForwardMessage: (String) -> Unit
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var inputText by remember { mutableStateOf("") }
    var selectedSubId by remember(threadUi?.preferredSimSubId, activeSims) {
        mutableStateOf(threadUi?.preferredSimSubId ?: activeSims.firstOrNull()?.subscriptionId)
    }
    var showSimPickerDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showInChatSearch by remember { mutableStateOf(false) }
    var inChatQuery by remember { mutableStateOf("") }
    var selectedMsgIds by remember { mutableStateOf(setOf<Long>()) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(messages.lastIndex)
        }
    }

    val matchingIndices = remember(messages, inChatQuery) {
        if (inChatQuery.isBlank()) emptyList()
        else messages.mapIndexedNotNull { index, msg ->
            if (msg.body.contains(inChatQuery, ignoreCase = true)) index else null
        }
    }
    var searchCursor by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            if (selectedMsgIds.isNotEmpty()) {
                TopAppBar(
                    title = { Text(PersianTextFormatter.toPersianDigits("${selectedMsgIds.size} پیام")) },
                    navigationIcon = {
                        IconButton(onClick = { selectedMsgIds = emptySet() }) {
                            Icon(Icons.Default.Close, "لغو")
                        }
                    },
                    actions = {
                        val singleSelected = messages.find { it.id == selectedMsgIds.firstOrNull() }
                        if (selectedMsgIds.size == 1 && singleSelected != null) {
                            IconButton(onClick = {
                                onOpenKhaterahBazi(singleSelected)
                                selectedMsgIds = emptySet()
                            }) {
                                Icon(Icons.Default.AutoAwesome, "خاطره بازی (کارت‌پستال)")
                            }
                            IconButton(onClick = {
                                clipboard.setText(AnnotatedString(singleSelected.body))
                                selectedMsgIds = emptySet()
                            }) {
                                Icon(Icons.Default.ContentCopy, "کپی")
                            }
                            IconButton(onClick = {
                                onForwardMessage(singleSelected.body)
                                selectedMsgIds = emptySet()
                            }) {
                                Icon(Icons.Default.Forward, "انتقال")
                            }
                            IconButton(onClick = {
                                onSetReminder(singleSelected, System.currentTimeMillis() + 3600_000L)
                                selectedMsgIds = emptySet()
                            }) {
                                Icon(Icons.Default.AlarmAdd, "یادآوری پاسخ")
                            }
                        }
                        IconButton(onClick = {
                            onDeleteMessages(selectedMsgIds.toList())
                            selectedMsgIds = emptySet()
                        }) {
                            Icon(Icons.Default.Delete, "حذف")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = threadUi?.contactName ?: PersianTextFormatter.formatPhoneLtr(address),
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (threadUi?.contactName != null) {
                                Text(
                                    text = PersianTextFormatter.formatPhoneLtr(address),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showInChatSearch = !showInChatSearch }) {
                            Icon(Icons.Default.Search, "جست‌وجو در گفتگو")
                        }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, "گزینه‌ها")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("رفتن به اولین پیام") },
                                onClick = {
                                    showMenu = false
                                    if (messages.isNotEmpty()) coroutineScope.launch { listState.animateScrollToItem(0) }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("رفتن به آخرین پیام") },
                                onClick = {
                                    showMenu = false
                                    if (messages.isNotEmpty()) coroutineScope.launch { listState.animateScrollToItem(messages.lastIndex) }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (threadUi?.isPinned == true) "برداشتن سنجاق" else "سنجاق‌کردن گفتگو") },
                                onClick = {
                                    showMenu = false
                                    onUpdateThreadMeta(!(threadUi?.isPinned ?: false), null, null, null, null, null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (threadUi?.isMuted == true) "باصدا کردن اعلان" else "بی‌صدا کردن اعلان") },
                                onClick = {
                                    showMenu = false
                                    onUpdateThreadMeta(null, null, !(threadUi?.isMuted ?: false), null, null, null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (threadUi?.isBlocked == true) "رفع مسدودی شماره" else "مسدود کردن و انتقال به اسپم") },
                                onClick = {
                                    showMenu = false
                                    val target = !(threadUi?.isBlocked ?: false)
                                    onUpdateThreadMeta(null, null, null, target, target, if (target) SmsTab.SPAM else SmsTab.UNKNOWN)
                                }
                            )
                        }
                    }
                )
            }
        },
        bottomBar = {
            Surface(tonalElevation = 4.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (activeSims.size > 1) {
                        val activeSim = activeSims.find { it.subscriptionId == selectedSubId } ?: activeSims.first()
                        AssistChip(
                            onClick = { showSimPickerDialog = true },
                            label = { Text("سیم ${PersianTextFormatter.toPersianDigits(activeSim.slotIndex.toString())}") }
                        )
                        Spacer(Modifier.width(6.dp))
                    }

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("متن پیامک را بنویسید...") },
                        shape = RoundedCornerShape(22.dp),
                        maxLines = 4
                    )

                    Spacer(Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                            .combinedClickable(
                                onClick = {
                                    if (inputText.isNotBlank()) {
                                        onSend(address, inputText, selectedSubId, threadId)
                                        inputText = ""
                                    }
                                },
                                onLongClick = {
                                    if (activeSims.isNotEmpty()) showSimPickerDialog = true
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "ارسال پیامک",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (showInChatSearch) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inChatQuery,
                        onValueChange = {
                            inChatQuery = it
                            searchCursor = 0
                        },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("جست‌وجو در پیام‌های این گفتگو...") },
                        singleLine = true
                    )
                    if (matchingIndices.isNotEmpty()) {
                        Text(
                            PersianTextFormatter.toPersianDigits(" ${searchCursor + 1}/${matchingIndices.size} "),
                            style = MaterialTheme.typography.labelMedium
                        )
                        IconButton(onClick = {
                            searchCursor = (searchCursor + 1) % matchingIndices.size
                            coroutineScope.launch { listState.animateScrollToItem(matchingIndices[searchCursor]) }
                        }) {
                            Icon(Icons.Default.KeyboardArrowDown, "بعدی")
                        }
                    }
                }
            }

            threadUi?.let { t ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "دسته: ${t.classification.category.titleFa} (${t.classification.reasonFa})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    var catMenu by remember { mutableStateOf(false) }
                    TextButton(onClick = { catMenu = true }) {
                        Text("اصلاح دسته")
                    }
                    DropdownMenu(expanded = catMenu, onDismissRequest = { catMenu = false }) {
                        SmsTab.entries.filter { it != SmsTab.ALL }.forEach { tab ->
                            DropdownMenuItem(
                                text = { Text(tab.titleFa) },
                                onClick = {
                                    catMenu = false
                                    onUpdateThreadMeta(null, null, null, tab == SmsTab.SPAM, null, tab)
                                }
                            )
                        }
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    val isOutgoing = msg.type != Telephony.Sms.MESSAGE_TYPE_INBOX
                    val isFailed = msg.type == Telephony.Sms.MESSAGE_TYPE_FAILED
                    val isSelected = msg.id in selectedMsgIds

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isOutgoing) Arrangement.Start else Arrangement.End
                    ) {
                        GlassCard(
                            modifier = Modifier
                                .widthIn(max = 310.dp)
                                .combinedClickable(
                                    onClick = {
                                        if (selectedMsgIds.isNotEmpty()) {
                                            selectedMsgIds = if (isSelected) selectedMsgIds - msg.id else selectedMsgIds + msg.id
                                        }
                                    },
                                    onLongClick = { selectedMsgIds = selectedMsgIds + msg.id }
                                ),
                            tintColor = when {
                                isSelected -> MaterialTheme.colorScheme.secondary
                                isFailed -> MaterialTheme.colorScheme.error
                                isOutgoing -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.surface
                            },
                            alpha = if (isOutgoing || isFailed) 0.22f else 0.88f
                        ) {
                            Text(
                                text = PersianTextFormatter.isolateBidiText(msg.body),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = JalaliCalendar.fromTimestamp(msg.timestamp).formatFull(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                IconButton(
                                    onClick = { onToggleStar(msg.id, msg.threadId, msg.isStarred) },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        if (msg.isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = "نشان‌دار",
                                        tint = if (msg.isStarred) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (isFailed) {
                                TextButton(onClick = { onSend(address, msg.body, selectedSubId, threadId) }) {
                                    Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("ارسال ناموفق - تلاش دوباره")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSimPickerDialog) {
        AlertDialog(
            onDismissRequest = { showSimPickerDialog = false },
            title = { Text("انتخاب سیم‌کارت ارسال") },
            text = {
                Column {
                    activeSims.forEach { sim ->
                        TextButton(
                            onClick = {
                                selectedSubId = sim.subscriptionId
                                showSimPickerDialog = false
                                if (inputText.isNotBlank()) {
                                    onSend(address, inputText, sim.subscriptionId, threadId)
                                    inputText = ""
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("سیم‌کارت ${PersianTextFormatter.toPersianDigits(sim.slotIndex.toString())} - ${sim.displayName} (${sim.carrierName})")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSimPickerDialog = false }) { Text("بستن") }
            }
        )
    }
}
