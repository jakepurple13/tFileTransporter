package com.tans.tfiletransporter.file

import contacts.async.commitInChunksWithContext
import contacts.core.Contacts
import contacts.core.entities.NewPhone
import contacts.core.entities.NewRawContact
import contacts.core.entities.PhoneEntity
import contacts.core.util.PhotoData
import contacts.core.util.addPhone
import contacts.core.util.setName
import contacts.core.util.setPhoto
import contacts.permissions.insertWithPermission
import ezvcard.Ezvcard
import java.io.File

class VcfImporter {
    enum class ImportResult {
        IMPORT_FAIL, IMPORT_OK, IMPORT_PARTIAL
    }

    private var contactsImported = 0
    private var contactsFailed = 0

    suspend fun importContacts(
        contacts: Contacts,
        path: File,
        //targetContactSource: String
    ): ImportResult {
        println("Here!!! $path")
        try {
            val inputStream = path.readText()

            println(inputStream)

            val ezContacts = Ezvcard.parse(inputStream).all()
            val c = ezContacts.map { ezContact ->
                println(ezContact)
                println(ezContact.telephoneNumbers)
                NewRawContact().apply {
                    setName {
                        displayName = ezContact.formattedName?.value
                    }

                    ezContact.photos.firstOrNull()?.data?.let { setPhoto(PhotoData.from(it)) }

                    ezContact.telephoneNumbers.forEach {
                        addPhone(
                            NewPhone(
                                type = PhoneEntity.Type.MOBILE,
                                number = it.text,
                                label = it.types.firstOrNull()?.value
                            )
                        )
                    }

                    /*ezContact.emails.forEach {
                        addEmail {
                            primaryValue = it?.value
                        }
                    }*/
                }

                /*val structuredName = ezContact.structuredName
                val prefix = structuredName?.prefixes?.firstOrNull() ?: ""
                val firstName = structuredName?.given ?: ""
                val middleName = structuredName?.additionalNames?.firstOrNull() ?: ""
                val surname = structuredName?.family ?: ""
                val suffix = structuredName?.suffixes?.firstOrNull() ?: ""
                val nickname = ezContact.nickname?.values?.firstOrNull() ?: ""
                var photoUri = ""

                val phoneNumbers = ArrayList<PhoneNumber>()
                ezContact.telephoneNumbers.forEach {
                    val number = it.text
                    val type = getPhoneNumberTypeId(
                        type = it.types.firstOrNull()?.value ?: MOBILE,
                        subtype = it.types.getOrNull(1)?.value
                    )
                    val label = if (type == Phone.TYPE_CUSTOM) {
                        it.types.firstOrNull()?.value ?: ""
                    } else {
                        ""
                    }

                    val preferred = getPreferredValue(it.types.lastOrNull()?.value) == 1
                    phoneNumbers.add(
                        PhoneNumber(
                            value = number,
                            type = type,
                            label = label,
                            normalizedNumber = number.normalizePhoneNumber(),
                            isPrimary = preferred
                        )
                    )
                }

                val emails = ArrayList<Email>()
                ezContact.emails.forEach {
                    val email = it.value
                    val type = getEmailTypeId(it.types.firstOrNull()?.value ?: HOME)
                    val label = if (type == CommonDataKinds.Email.TYPE_CUSTOM) {
                        it.types.firstOrNull()?.value ?: ""
                    } else {
                        ""
                    }

                    if (email.isNotEmpty()) {
                        emails.add(Email(email, type, label))
                    }
                }

                val addresses = ArrayList<Address>()
                ezContact.addresses.forEach {
                    var address = it.streetAddress ?: ""
                    val type = getAddressTypeId(it.types.firstOrNull()?.value ?: HOME)
                    val label = if (type == StructuredPostal.TYPE_CUSTOM) {
                        it.types.firstOrNull()?.value ?: ""
                    } else {
                        ""
                    }
                    val country = it.country ?: ""
                    val region = it.region ?: ""
                    val city = it.locality ?: ""
                    val postcode = it.postalCode ?: ""
                    val pobox = it.poBox ?: ""
                    val street = it.streetAddress ?: ""
                    val neighborhood = it.extendedAddress ?: ""

                    if (it.locality?.isNotEmpty() == true) {
                        address += " ${it.locality} "
                    }

                    if (it.region?.isNotEmpty() == true) {
                        if (address.isNotEmpty()) {
                            address = "${address.trim()}, "
                        }
                        address += "${it.region} "
                    }

                    if (it.postalCode?.isNotEmpty() == true) {
                        address += "${it.postalCode} "
                    }

                    if (it.country?.isNotEmpty() == true) {
                        address += "${it.country} "
                    }

                    address = address.trim()
                    if (address.isNotEmpty() == true) {
                        addresses.add(
                            Address(
                                value = address,
                                type = type,
                                label = label,
                                country = country,
                                region = region,
                                city = city,
                                postcode = postcode,
                                pobox = pobox,
                                street = street,
                                neighborhood = neighborhood
                            )
                        )
                    }
                }

                val events = ArrayList<Event>()
                ezContact.anniversaries.forEach { anniversary ->
                    val event = if (anniversary.date != null) {
                        Event(
                            formatDateToDayCode(anniversary.date),
                            CommonDataKinds.Event.TYPE_ANNIVERSARY
                        )
                    } else {
                        Event(
                            formatPartialDateToDayCode(anniversary.partialDate),
                            CommonDataKinds.Event.TYPE_ANNIVERSARY
                        )
                    }
                    events.add(event)
                }

                ezContact.birthdays.forEach { birthday ->
                    val event = if (birthday.date != null) {
                        Event(
                            formatDateToDayCode(birthday.date),
                            CommonDataKinds.Event.TYPE_BIRTHDAY
                        )
                    } else {
                        Event(
                            formatPartialDateToDayCode(birthday.partialDate),
                            CommonDataKinds.Event.TYPE_BIRTHDAY
                        )
                    }
                    events.add(event)
                }

                val starred = 0
                val contactId = 0
                val notes = ezContact.notes.firstOrNull()?.value ?: ""
                val groups = getContactGroups(ezContact)
                val company = ezContact.organization?.values?.firstOrNull() ?: ""
                val jobPosition = ezContact.titles?.firstOrNull()?.value ?: ""
                val organization = Organization(company, jobPosition)
                val websites = ezContact.urls.map { it.value } as ArrayList<String>
                val photoData = ezContact.photos.firstOrNull()?.data
                val photo = if (photoData != null) {
                    BitmapFactory.decodeByteArray(photoData, 0, photoData.size)
                } else {
                    null
                }

                val thumbnailUri = savePhoto(photoData)
                if (thumbnailUri.isNotEmpty()) {
                    photoUri = thumbnailUri
                }

                val ringtone = null

                val IMs = ArrayList<IM>()
                ezContact.impps.forEach {
                    val typeString = it.uri.scheme
                    val value = URLDecoder.decode(
                        it.uri.toString().substring(it.uri.scheme.length + 1),
                        "UTF-8"
                    )
                    val type = when {
                        it.isAim -> Im.PROTOCOL_AIM
                        it.isYahoo -> Im.PROTOCOL_YAHOO
                        it.isMsn -> Im.PROTOCOL_MSN
                        it.isIcq -> Im.PROTOCOL_ICQ
                        it.isSkype -> Im.PROTOCOL_SKYPE
                        typeString == HANGOUTS -> Im.PROTOCOL_GOOGLE_TALK
                        typeString == QQ -> Im.PROTOCOL_QQ
                        typeString == JABBER -> Im.PROTOCOL_JABBER
                        else -> Im.PROTOCOL_CUSTOM
                    }

                    val label = if (type == Im.PROTOCOL_CUSTOM) URLDecoder.decode(
                        typeString,
                        "UTF-8"
                    ) else ""
                    val IM = IM(value, type, label)
                    IMs.add(IM)
                }

                val contact = Contact(
                    0,
                    prefix,
                    firstName,
                    middleName,
                    surname,
                    suffix,
                    nickname,
                    photoUri,
                    phoneNumbers,
                    emails,
                    addresses,
                    events,
                    targetContactSource,
                    starred,
                    contactId,
                    thumbnailUri,
                    photo,
                    notes,
                    groups,
                    organization,
                    websites,
                    IMs,
                    DEFAULT_MIMETYPE,
                    ringtone
                )

                // if there is no N and ORG fields at the given contact, only FN, treat it as an organization
                if (contact.getNameToDisplay()
                        .isEmpty() && contact.organization.isEmpty() && ezContact.formattedName?.value?.isNotEmpty() == true
                ) {
                    contact.organization.company = ezContact.formattedName.value
                    contact.mimetype = CommonDataKinds.Organization.CONTENT_ITEM_TYPE
                }

                if (contact.isABusinessContact()) {
                    contact.mimetype = CommonDataKinds.Organization.CONTENT_ITEM_TYPE
                }*/


                /*if (ContactsHelper(activity).insertContact(contact)) {
                    contactsImported++
                }*/
            }
            println("Importing!")

            c.forEach {
                println(it)
            }

            val result = contacts
                .insertWithPermission()
                .allowBlanks(true)
                .rawContacts(c)
                .commitInChunksWithContext()

            println(result)
        } catch (e: Exception) {
            e.printStackTrace()
            contactsFailed++
        }

        return when {
            contactsImported == 0 -> ImportResult.IMPORT_FAIL
            contactsFailed > 0 -> ImportResult.IMPORT_PARTIAL
            else -> ImportResult.IMPORT_OK
        }
    }
}