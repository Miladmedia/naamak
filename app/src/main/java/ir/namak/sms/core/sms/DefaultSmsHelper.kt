package ir.namak.sms.core.sms

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultSmsHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _isDefaultSmsApp = MutableStateFlow(checkIsDefault())
    val isDefaultSmsApp: StateFlow<Boolean> = _isDefaultSmsApp.asStateFlow()

    fun checkIsDefault(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(RoleManager::class.java)
                roleManager?.isRoleHeld(RoleManager.ROLE_SMS) == true ||
                        Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
            } else {
                Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
            }
        } catch (_: Exception) {
            false
        }
    }

    fun refreshState() {
        _isDefaultSmsApp.value = checkIsDefault()
    }

    fun createRequestDefaultSmsIntent(): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_SMS) && !roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
            } else {
                null
            }
        } else {
            Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
            }
        }
    }
}
