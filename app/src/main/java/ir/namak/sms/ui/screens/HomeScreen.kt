package ir.namak.sms.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.namak.sms.core.persian.JalaliCalendar
import ir.namak.sms.core.persian.PersianTextFormatter
import ir.namak.sms.domain.model.SmsTab
import ir.namak.sms.domain.model.SmsThreadUi
import ir.namak.sms.ui.components.ContactAvatar
import ir.namak.sms.ui.components.GlassCard

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onRequestDefaultSms: () -> Unit,
    onSelectTab: (SmsTab) -> Unit,
    onSearchChange: (String) -> Unit,
    onOpenThread: (Long, String) -> Unit,
    onNewMessage: () -> Unit,
    onOpenRulesAndSchedule: () -> Unit,
    onOpenSettings: () -> Unit,
    onBulkDelete: (List<Long>) -> Unit,
    onBulkArchive: (List<Long>) -> Unit,
    onToggleArchiveView: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var selectedThreadIds by remember { mutableStateOf(setOf<Long>()) }
    val inSelectionMode = selectedThreadIds.isNotEmpty()

    val filteredThreads = remember(state.threads, state.selectedTab, state.searchQuery, state.showArchived) {
        state.threads.filter { t ->
            val archiveMatch = if (state.showArchived) t.isArchived else !t.isArchived
            val tabMatch = when (state.selectedTab) {
                SmsTab.ALL -> t.classification.category != SmsTab.SPAM
                else -> t.classification.category == state.selectedTab
            }
            val queryMatch = state.searchQuery.isBlank() ||
                    t.address.contains(state.searchQuery, ignoreCase = true) ||
                    (t.contactName?.contains(state.searchQuery, ignoreCase = true) == true) ||
                    t.snippet.contains(state.searchQuery, ignoreCase = true)
            archiveMatch && tabMatch && queryMatch
        }
    }

    Scaffold(
        topBar = {
            if (inSelectionMode) {
                TopAppBar(
                    title = { Text(PersianTextFormatter.toPersianDigits("${selectedThreadIds.size} گفتگو انتخاب شد")) },
                    navigationIcon = {
                        IconButton(onClick = { selectedThreadIds = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = "لغو انتخاب")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            onBulkArchive(selectedThreadIds.toList())
                            selectedThreadIds = emptySet()
                        }) {
                            Icon(Icons.Default.Archive, contentDescription = "آرشیو")
                        }
                        IconButton(onClick = {
                            onBulkDelete(selectedThreadIds.toList())
                            selectedThreadIds = emptySet()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "حذف")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            text = if (state.showArchived) "نامک (آرشیو)" else "نامک",
                            fontWeight = FontWeight.ExtraBold
                        )
                    },
                    actions = {
                        IconButton(onClick = onToggleArchiveView) {
                            Icon(
                                if (state.showArchived) Icons.Default.Inbox else Icons.Default.Archive,
                                contentDescription = "آرشیو"
                            )
                        }
                        IconButton(onClick = onOpenRulesAndSchedule) {
                            Icon(Icons.Default.ScheduleSend, contentDescription = "قوانین و زمان‌بندی")
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Tune, contentDescription = "تنظیمات و ظاهر")
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewMessage,
                containerColor = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Default.Edit, contentDescription = "پیام جدید", tint = Color.White)
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
                .padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(visible = !state.isDefaultSmsApp) {
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        tintColor = MaterialTheme.colorScheme.secondary,
                        alpha = 0.16f
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "نامک پیام‌رسان پیش‌فرض نیست",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    "برای ارسال، دریافت و دسته‌بندی کامل پیامک‌ها، نامک را به‌عنوان پیش‌فرض تنظیم کنید.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Button(
                                onClick = onRequestDefaultSms,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("تنظیم پیش‌فرض")
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    placeholder = { Text("جست‌وجو در نام، شماره یا متن پیامک‌ها...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "پاک کردن")
                            }
                        }
                    },
                    shape = RoundedCornerShape(18.dp),
                    singleLine = true
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.orderedTabs) { tab ->
                        FilterChip(
                            selected = state.selectedTab == tab,
                            onClick = { onSelectTab(tab) },
                            label = { Text(tab.titleFa) },
                            shape = RoundedCornerShape(14.dp)
                        )
                    }
                }

                if (filteredThreads.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "پیامکی در این بخش یافت نشد.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredThreads, key = { it.threadId }) { thread ->
                            val isSelected = thread.threadId in selectedThreadIds
                            ThreadGlassRow(
                                thread = thread,
                                isSelected = isSelected,
                                onCopyOtp = { otp ->
                                    clipboard.setText(AnnotatedString(otp))
                                },
                                modifier = Modifier.combinedClickable(
                                    onClick = {
                                        if (inSelectionMode) {
                                            selectedThreadIds = if (isSelected) selectedThreadIds - thread.threadId
                                            else selectedThreadIds + thread.threadId
                                        } else {
                                            onOpenThread(thread.threadId, thread.address)
                                        }
                                    },
                                    onLongClick = {
                                        selectedThreadIds = selectedThreadIds + thread.threadId
                                    }
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThreadGlassRow(
    thread: SmsThreadUi,
    isSelected: Boolean,
    onCopyOtp: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        tintColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        alpha = if (isSelected) 0.25f else 0.82f
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ContactAvatar(name = thread.contactName, address = thread.address)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = thread.contactName ?: PersianTextFormatter.formatPhoneLtr(thread.address),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (thread.isPinned) {
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = "سنجاق‌شده",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = JalaliCalendar.fromTimestamp(thread.timestamp).formatShortDate(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    text = PersianTextFormatter.isolateBidiText(thread.snippet),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "${thread.classification.category.titleFa}: ${thread.classification.reasonFa}",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    thread.classification.extractedOtp?.let { otp ->
                        AssistChip(
                            onClick = { onCopyOtp(otp) },
                            label = { Text("کپی کد ${PersianTextFormatter.toPersianDigits(otp)}") },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, null, Modifier.size(14.dp)) }
                        )
                    }

                    if (thread.unreadCount > 0) {
                        Badge {
                            Text(PersianTextFormatter.toPersianDigits(thread.unreadCount.toString()))
                        }
                    }
                }
            }
        }
    }
}
