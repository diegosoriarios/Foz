package com.example.foz.data

import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import com.example.foz.model.ContactInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ContactRepository(private val context: Context) {

    suspend fun searchContacts(query: String, limit: Int = 20): List<ContactInfo> {
        if (query.isBlank()) return emptyList()
        return withContext(Dispatchers.IO) {
            val results = mutableListOf<ContactInfo>()
            var cursor: Cursor? = null
            try {
                val projection = arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                )
                val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} LIKE ?"
                val selectionArgs = arrayOf("%$query%")

                cursor = context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    "${ContactsContract.CommonDataKinds.Phone.STARRED} DESC, ${ContactsContract.CommonDataKinds.Phone.SORT_KEY_PRIMARY} ASC"
                )

                cursor?.use { c ->
                    val nameIndex = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
                    val numberIndex = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val seen = mutableSetOf<String>()
                    while (c.moveToNext() && results.size < limit) {
                        val name = c.getString(nameIndex) ?: continue
                        val number = c.getString(numberIndex) ?: continue
                        val key = "$name|$number"
                        if (seen.add(key)) {
                            results.add(ContactInfo(name = name, phoneNumber = number))
                        }
                    }
                }
            } catch (_: SecurityException) {
                // READ_CONTACTS not granted
            } catch (_: Exception) {
            } finally {
                cursor?.close()
            }
            results
        }
    }
}
