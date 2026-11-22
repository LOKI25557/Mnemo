package com.mnemo.connectors.contacts

import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.mnemo.core.model.DataSourceType
import com.mnemo.core.model.MemoryItem
import com.mnemo.core.model.connector.ContactsConnector

/**
 * Concrete Android Contacts Provider connector.
 * Implemented by Dadu (Android System Connectors Lead).
 */
class AndroidContactsConnector(
    private val context: Context
) : ContactsConnector {

    override suspend fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    override suspend fun searchContacts(query: String): List<MemoryItem> {
        if (!hasPermission() || query.isBlank()) {
            return emptyList()
        }

        val results = mutableListOf<MemoryItem>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$query%")

        try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC LIMIT 20"
            )

            cursor?.use {
                val idIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (it.moveToNext()) {
                    val id = if (idIdx >= 0) it.getString(idIdx) else ""
                    val name = if (nameIdx >= 0) it.getString(nameIdx) else "Unknown"
                    val number = if (numIdx >= 0) it.getString(numIdx) else ""

                    results.add(
                        MemoryItem(
                            id = "contact_$id",
                            sourceType = DataSourceType.CONTACT,
                            title = name,
                            content = "Phone: $number",
                            personName = name,
                            sourceUri = "content://contacts/people/$id"
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            // Permission revoked concurrently
            return emptyList()
        } catch (e: Exception) {
            return emptyList()
        }

        return results
    }

    override suspend fun getContactById(id: String): MemoryItem? {
        if (!hasPermission()) return null
        return searchContacts(id).firstOrNull()
    }
}
