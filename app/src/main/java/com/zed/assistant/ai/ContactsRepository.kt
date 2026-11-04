package com.zed.assistant.ai

import android.content.Context
import android.provider.ContactsContract
import android.util.Log

data class ContactMatch(
  val contactId: Long,
  val name: String,
  val number: String,
  val label: String?
)

/** Read-only contact lookup used by the callContact tool. */
class ContactsRepository(context: Context) {

  companion object {
    private const val TAG = "ZedContacts"
  }

  private val appContext = context.applicationContext
  private val resolver = appContext.contentResolver

  fun searchByName(query: String): List<ContactMatch> {
    val name = query.trim()
    if (name.length < 2) return emptyList()

    val projection = arrayOf(
      ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
      ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
      ContactsContract.CommonDataKinds.Phone.NUMBER,
      ContactsContract.CommonDataKinds.Phone.TYPE
    )

    val matches = mutableListOf<ContactMatch>()
    try {
      resolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        projection,
        "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
        arrayOf("%$name%"),
        "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
      )?.use { cursor ->
        val idIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
        val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val numberIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val typeIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE)

        val seen = mutableSetOf<String>()
        while (cursor.moveToNext()) {
          val displayName = cursor.getString(nameIndex)?.trim().orEmpty()
          val number = cursor.getString(numberIndex)?.trim().orEmpty()
          if (displayName.isBlank() || number.isBlank()) continue
          val key = "${cursor.getLong(idIndex)}|${number.filter { it.isDigit() }}"
          if (!seen.add(key)) continue
          matches.add(
            ContactMatch(
              contactId = cursor.getLong(idIndex),
              name = displayName,
              number = number,
              label = ContactsContract.CommonDataKinds.Phone
                .getTypeLabel(appContext.resources, cursor.getInt(typeIndex), null)
                .toString()
            )
          )
        }
      }
    } catch (error: SecurityException) {
      Log.e(TAG, "Contacts permission denied while searching", error)
      throw error
    } catch (error: Exception) {
      Log.e(TAG, "Contact search failed", error)
    }

    Log.d(TAG, "Contact search '$name' returned ${matches.size} number(s)")
    return matches
  }
}
