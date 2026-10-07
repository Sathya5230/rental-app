package com.rentnest.app.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.rentnest.app.domain.SmsGateway
import com.rentnest.app.domain.format.PhoneNumbers
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AndroidSmsGateway @Inject constructor(@ApplicationContext private val context: Context) : SmsGateway {
    override fun canSendDirectly(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_MESSAGING) &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    override fun send(phone: String, message: String): Boolean {
        val to = PhoneNumbers.e164(phone) ?: return false
        if (!canSendDirectly()) return false
        return try {
            val sms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.getSystemService(SmsManager::class.java)
            else @Suppress("DEPRECATION") SmsManager.getDefault()
            sms.sendMultipartTextMessage(to, null, sms.divideMessage(message), null, null)
            true
        } catch (e: Exception) {
            Log.w("AndroidSmsGateway", "SMS to $to failed", e)
            false
        }
    }
}
