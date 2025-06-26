package com.tans.tfiletransporter.file

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import contacts.core.Contacts
import contacts.core.entities.RawContact
import contacts.core.util.photoBytes
import ezvcard.Ezvcard
import ezvcard.VCard
import ezvcard.VCardVersion
import ezvcard.parameter.ImageType
import ezvcard.parameter.TelephoneType
import ezvcard.property.Categories
import ezvcard.property.Email
import ezvcard.property.FormattedName
import ezvcard.property.Photo
import java.io.OutputStream

class VcfExporter {
    enum class ExportResult {
        EXPORT_FAIL, EXPORT_OK, EXPORT_PARTIAL
    }

    private var contactsExported = 0
    private var contactsFailed = 0

    @RequiresApi(Build.VERSION_CODES.P)
    fun exportContacts(
        context: Context,
        contactsApi: Contacts,
        outputStream: OutputStream?,
        contacts: MutableList<RawContact>,
        showExportingToast: Boolean,
        version: VCardVersion = VCardVersion.V4_0,
        callback: (result: ExportResult) -> Unit,
    ) {
        try {
            if (outputStream == null) {
                return
            }

            val cards = ArrayList<VCard>()
            for (contact in contacts) {
                val card = VCard()

                /*val formattedName = arrayOf(
                    contact.prefix,
                    contact.firstName,
                    contact.middleName,
                    contact.surname,
                    contact.suffix
                )
                    .filter { it.isNotEmpty() }
                    .joinToString(separator = " ")*/
                card.formattedName = FormattedName(contact.displayNamePrimary)

                /*StructuredName().apply {
                    prefixes.add(contact.prefix)
                    given = contact.firstName
                    additionalNames.add(contact.middleName)
                    family = contact.surname
                    suffixes.add(contact.suffix)
                    card.structuredName = this
                }*/

                /*if (contact.nickname.isNotEmpty()) {
                    card.setNickname(contact.nickname)
                }*/

                contact.phones.forEach {
                    card.addTelephoneNumber(it.number, TelephoneType.TEXTPHONE)
                }

                contact.emails.forEach {
                    val email = Email(it.address)
                    email.parameters.addType("Home")
                    card.addEmail(email)
                }

                try {
                    val photo = Photo(contact.photoBytes(contactsApi), ImageType.JPEG)
                    card.addPhoto(photo)
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                if (contact.groupMemberships.isNotEmpty()) {
                    val groupList = Categories()
                    contact.groupMemberships.forEach {
                        groupList.values.add(it.primaryValue)
                    }

                    card.categories = groupList
                }

                cards.add(card)
                contactsExported++
            }

            Ezvcard.write(cards).version(version).go(outputStream)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        callback(
            when {
                contactsExported == 0 -> ExportResult.EXPORT_FAIL
                contactsFailed > 0 -> ExportResult.EXPORT_PARTIAL
                else -> ExportResult.EXPORT_OK
            }
        )
    }

}