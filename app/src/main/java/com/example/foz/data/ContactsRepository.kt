package com.example.foz.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ContactMatch(
    val name: String,
    val phoneNumber: String
)

/**
 * Opt-in read access to device contacts so the assistant can resolve
 * names ("mom") to phone numbers for messaging/calls.
 */
class ContactsRepository(private val context: Context) {

    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    suspend fun findPhoneNumbers(query: String, limit: Int = 5): List<ContactMatch> {
        if (!hasPermission()) throw SecurityException("READ_CONTACTS not granted")
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        return withContext(Dispatchers.IO) {
            val matches = mutableListOf<ContactMatch>()
            try {
                context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                    ),
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?",
                    arrayOf("%$trimmed%"),
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
                )?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    while (cursor.moveToNext() && matches.size < limit) {
                        val name = cursor.getString(nameIndex) ?: continue
                        val number = cursor.getString(numberIndex) ?: continue
                        if (matches.none { it.name == name && it.phoneNumber == number }) {
                            matches.add(ContactMatch(name, number))
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Contact lookup failed", t)
                throw t
            }
            matches
        }
    }

    companion object {
        private const val TAG = "ContactsRepository"
    }
}
