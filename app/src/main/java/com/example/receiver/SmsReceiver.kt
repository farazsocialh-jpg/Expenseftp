package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.TransactionEntity
import com.example.util.SmsParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    private val TAG = "SmsReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.provider.Telephony.SMS_RECEIVED") return

        val bundle = intent.extras ?: return
        try {
            val pdus = bundle.get("pdus") as? Array<*> ?: return
            val format = bundle.getString("format")

            // Correctly obtain the PendingResult once outside of PDU loop
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context)
                    val repository = db.transactionDao()
                    val settingsDao = db.appSettingDao()

                    // Check the user-selected sender from db setting
                    val selectedSender = settingsDao.getSettingByKeyImmediate("selected_sms_sender")?.value ?: "Cb SMS"

                    for (pdu in pdus) {
                        val pduBytes = pdu as? ByteArray ?: continue
                        val sms = SmsMessage.createFromPdu(pduBytes, format)
                        val sender = sms.originatingAddress ?: ""
                        val body = sms.messageBody ?: ""

                        Log.d(TAG, "Received SMS from '$sender' with body: '$body'")

                        // Match selected sender using robust comparison (stripping hyphens and non-alphanumeric)
                        if (SmsParser.isSameSender(sender, selectedSender)) {
                            val parsed = SmsParser.parseMessage(sender, body)
                            if (parsed != null && parsed.amount > 0.0) {
                                // Real automatic parse and insert into database
                                repository.insertTransaction(
                                    TransactionEntity(
                                        amount = parsed.amount,
                                        type = parsed.type,
                                        category = parsed.category,
                                        tag = parsed.tag,
                                        description = "SMS Alert: ${parsed.description}",
                                        sender = sender,
                                        account = parsed.account,
                                        timestamp = sms.timestampMillis
                                    )
                                )
                                Log.i(TAG, "Automatically saved SMS transaction of ${parsed.amount} classified as ${parsed.type}")
                            }
                        } else {
                            Log.d(TAG, "SMS sender '$sender' did not match selected sender '$selectedSender'")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing received SMS", e)
                } finally {
                    pendingResult.finish()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing SMS intent in BroadCastReceiver", e)
        }
    }
}
