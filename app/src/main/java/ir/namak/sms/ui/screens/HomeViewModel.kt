package ir.namak.sms.ui.screens

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.namak.sms.core.persian.PersianTextFormatter
import ir.namak.sms.core.sms.DefaultSmsHelper
import ir.namak.sms.core.sms.DualSimManager
import ir.namak.sms.core.sms.SimCardInfo
import ir.namak.sms.data.local.*
import ir.namak.sms.data.telephony.ContactInfo
import ir.namak.sms.data.telephony.TelephonyDataSource
import ir.namak.sms.domain.classifier.SmartSmsClassifier
import ir.namak.sms.domain.model.SmsMessageUi
import ir.namak.sms.domain.model.SmsTab
import ir.namak.sms.domain.model.SmsThreadUi
import ir.namak.sms.receivers.AlarmReminderReceiver
import ir.namak.sms.ui.theme.AppFontChoice
import ir.namak.sms.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isDefaultSmsApp: Boolean = false,
    val threads: List<SmsThreadUi> = emptyList(),
    val messagesByThread: Map<Long, List<SmsMessageUi>> = emptyMap(),
    val contacts: List<ContactInfo> = emptyList(),
    val activeSims: List<SimCardInfo> = emptyList(),
    val orderedTabs: List<SmsTab> = SmsTab.entries,
    val defaultTab: SmsTab = SmsTab.ALL,
    val selectedTab: SmsTab = SmsTab.ALL,
    val searchQuery: String = "",
    val showArchived: Boolean = false,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val seedColor: Color = Color(0xFF0D5C63),
    val fontChoice: AppFontChoice = AppFontChoice.DEFAULT,
    val textScale: Float = 1.0f,
    val errorBanner: String? = null,
    val infoSnack: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val defaultSmsHelper: DefaultSmsHelper,
    private val telephony: TelephonyDataSource,
    private val dualSimManager: DualSimManager,
    private val classifier: SmartSmsClassifier,
    private val threadMetaDao: ThreadMetaDao,
    private val starredDao: StarredMessageDao,
    private val ruleDao: RuleDao,
    private val scheduledDao: ScheduledSmsDao,
    private val reminderDao: ReminderDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(isDefaultSmsApp = defaultSmsHelper.checkIsDefault()))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val rulesFlow: StateFlow<List<ClassificationRuleEntity>> =
        ruleDao.observeRules().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val scheduledFlow: StateFlow<List<ScheduledSmsEntity>> =
        scheduledDao.observeScheduled().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            defaultSmsHelper.isDefaultSmsApp.collect { isDefault ->
                _uiState.update { it.copy(isDefaultSmsApp = isDefault) }
            }
        }
        refreshAll()
    }

    fun onResumeCheckDefaultSms() {
        defaultSmsHelper.refreshState()
        refreshAll()
    }

    fun refreshAll() {
        viewModelScope.launch {
            val contactsMap = telephony.loadContactsMap()
            val rawSms = telephony.queryAllSms()
            val sims = dualSimManager.getActiveSims()
            val metaMap = threadMetaDao.observeAllMeta().first().associateBy { it.threadId }
            val starredSet = starredDao.observeStarredIds().first().toSet()
            val rules = ruleDao.getEnabledRules()

            val grouped = rawSms.groupBy { it.threadId }
            val threadList = mutableListOf<SmsThreadUi>()
            val msgMap = mutableMapOf<Long, List<SmsMessageUi>>()

            for ((threadId, rows) in grouped) {
                val sortedAsc = rows.sortedBy { it.date }
                val latest = sortedAsc.last()
                val normAddr = PersianTextFormatter.normalizePhoneNumber(latest.address)
                val contact = contactsMap[normAddr]
                val meta = metaMap[threadId]
                val isGroup = rows.map { it.address }.distinct().size > 1 || latest.address.contains(",")

                val threadClass = classifier.classify(
                    address = latest.address,
                    body = latest.body,
                    isKnownContact = contact != null,
                    isGroupThread = isGroup,
                    meta = meta,
                    rules = rules
                )

                val mappedMsgs = sortedAsc.map { r ->
                    SmsMessageUi(
                        id = r.id,
                        threadId = r.threadId,
                        address = r.address,
                        body = r.body,
                        timestamp = r.date,
                        type = r.type,
                        subId = r.subId,
                        isStarred = r.id in starredSet,
                        classification = classifier.classify(
                            address = r.address,
                            body = r.body,
                            isKnownContact = contact != null,
                            isGroupThread = isGroup,
                            meta = meta,
                            rules = rules
                        )
                    )
                }
                msgMap[threadId] = mappedMsgs

                threadList.add(
                    SmsThreadUi(
                        threadId = threadId,
                        address = latest.address,
                        contactName = contact?.name,
                        contactPhotoUri = contact?.photoUri,
                        snippet = latest.body,
                        timestamp = latest.date,
                        unreadCount = rows.count { it.read == 0 },
                        isPinned = meta?.isPinned == true,
                        isArchived = meta?.isArchived == true,
                        isMuted = meta?.isMuted == true,
                        isBlocked = meta?.isBlocked == true,
                        preferredSimSubId = meta?.preferredSimSubId,
                        classification = threadClass
                    )
                )
            }

            val sortedThreads = threadList.sortedWith(
                compareByDescending<SmsThreadUi> { it.isPinned }.thenByDescending { it.timestamp }
            )

            _uiState.update {
                it.copy(
                    threads = sortedThreads,
                    messagesByThread = msgMap,
                    contacts = contactsMap.values.distinctBy { c -> c.phone }.sortedBy { c -> c.name },
                    activeSims = sims
                )
            }
        }
    }

    fun selectTab(tab: SmsTab) = _uiState.update { it.copy(selectedTab = tab) }
    fun updateSearch(query: String) = _uiState.update { it.copy(searchQuery = query) }
    fun toggleShowArchived() = _uiState.update { it.copy(showArchived = !it.showArchived) }

    fun setThemeMode(mode: AppThemeMode) = _uiState.update { it.copy(themeMode = mode) }
    fun setSeedColor(color: Color) = _uiState.update { it.copy(seedColor = color) }
    fun setFontChoice(choice: AppFontChoice) = _uiState.update { it.copy(fontChoice = choice) }
    fun setTextScale(scale: Float) = _uiState.update { it.copy(textScale = scale.coerceIn(0.85f, 1.35f)) }

    fun reorderTab(fromIndex: Int, toIndex: Int) {
        val list = _uiState.value.orderedTabs.toMutableList()
        if (fromIndex in list.indices && toIndex in list.indices) {
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            _uiState.update { it.copy(orderedTabs = list) }
        }
    }

    fun setDefaultTab(tab: SmsTab) {
        _uiState.update { it.copy(defaultTab = tab, selectedTab = tab) }
    }

    fun sendSms(address: String, body: String, subId: Int?, threadId: Long? = null) {
        viewModelScope.launch {
            if (threadId != null && subId != null) {
                val existing = threadMetaDao.getMeta(threadId) ?: ThreadMetaEntity(threadId, address)
                threadMetaDao.upsert(existing.copy(preferredSimSubId = subId))
            }
            val result = telephony.sendSms(address, body, subId, _uiState.value.isDefaultSmsApp)
            result.onSuccess {
                _uiState.update { it.copy(infoSnack = "پیامک ارسال شد.") }
                refreshAll()
            }.onFailure { err ->
                _uiState.update { it.copy(errorBanner = err.message ?: "خطا در ارسال پیامک") }
            }
        }
    }

    fun markThreadRead(threadId: Long) {
        viewModelScope.launch {
            telephony.markThreadAsRead(threadId)
            refreshAll()
        }
    }

    fun toggleStarMessage(messageId: Long, threadId: Long, currentlyStarred: Boolean) {
        viewModelScope.launch {
            if (currentlyStarred) starredDao.unstar(messageId)
            else starredDao.star(StarredMessageEntity(messageId, threadId))
            refreshAll()
        }
    }

    fun deleteMessages(ids: List<Long>) {
        viewModelScope.launch {
            telephony.deleteMessages(ids)
            refreshAll()
        }
    }

    fun updateThreadMeta(
        threadId: Long,
        address: String,
        pin: Boolean? = null,
        archive: Boolean? = null,
        mute: Boolean? = null,
        spam: Boolean? = null,
        block: Boolean? = null,
        manualCategory: SmsTab? = null
    ) {
        viewModelScope.launch {
            val cur = threadMetaDao.getMeta(threadId) ?: ThreadMetaEntity(threadId, address)
            threadMetaDao.upsert(
                cur.copy(
                    isPinned = pin ?: cur.isPinned,
                    isArchived = archive ?: cur.isArchived,
                    isMuted = mute ?: cur.isMuted,
                    isSpam = spam ?: cur.isSpam,
                    isBlocked = block ?: cur.isBlocked,
                    manualCategory = manualCategory?.key ?: cur.manualCategory
                )
            )
            refreshAll()
        }
    }

    fun bulkArchiveThreads(threadIds: List<Long>, archive: Boolean = true) {
        viewModelScope.launch {
            threadIds.forEach { tid ->
                val cur = threadMetaDao.getMeta(tid) ?: ThreadMetaEntity(tid, "")
                threadMetaDao.upsert(cur.copy(isArchived = archive))
            }
            refreshAll()
        }
    }

    fun bulkDeleteThreads(threadIds: List<Long>) {
        viewModelScope.launch {
            telephony.deleteThreads(threadIds)
            refreshAll()
        }
    }

    fun saveRule(rule: ClassificationRuleEntity) {
        viewModelScope.launch {
            ruleDao.upsertRule(rule)
            refreshAll()
        }
    }

    fun deleteRule(rule: ClassificationRuleEntity) {
        viewModelScope.launch {
            ruleDao.deleteRule(rule)
            refreshAll()
        }
    }

    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.canScheduleExactAlarms()
        } else true
    }

    fun scheduleSms(address: String, body: String, subId: Int?, triggerAtMillis: Long, userConfirmed: Boolean) {
        if (!userConfirmed) return
        viewModelScope.launch {
            val id = scheduledDao.insert(
                ScheduledSmsEntity(
                    address = address,
                    body = body,
                    subscriptionId = subId,
                    scheduledTimeMillis = triggerAtMillis,
                    userConfirmed = true
                )
            )
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, AlarmReminderReceiver::class.java).apply {
                action = AlarmReminderReceiver.ACTION_SCHEDULED_SMS
                putExtra("address", address)
                putExtra("body", body)
                putExtra("user_confirmed", true)
            }
            val pi = PendingIntent.getBroadcast(
                context,
                id.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
            _uiState.update { it.copy(infoSnack = "پیامک با تقویم شمسی زمان‌بندی شد.") }
        }
    }

    fun cancelScheduledSms(item: ScheduledSmsEntity) {
        viewModelScope.launch {
            scheduledDao.delete(item)
            _uiState.update { it.copy(infoSnack = "ارسال زمان‌بندی‌شده لغو شد.") }
        }
    }

    fun setReminder(threadId: Long, messageId: Long?, address: String, preview: String, triggerAtMillis: Long) {
        viewModelScope.launch {
            val id = reminderDao.insert(
                MessageReminderEntity(
                    threadId = threadId,
                    messageId = messageId,
                    address = address,
                    previewText = preview,
                    remindAtMillis = triggerAtMillis
                )
            )
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, AlarmReminderReceiver::class.java).apply {
                action = AlarmReminderReceiver.ACTION_REMINDER
                putExtra("thread_id", threadId)
                putExtra("address", address)
                putExtra("preview", preview)
            }
            val pi = PendingIntent.getBroadcast(
                context,
                (id + 70000).toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
            _uiState.update { it.copy(infoSnack = "یادآوری پاسخ تنظیم شد.") }
        }
    }
}
