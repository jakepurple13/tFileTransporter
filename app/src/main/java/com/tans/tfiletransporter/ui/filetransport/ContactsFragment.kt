package com.tans.tfiletransporter.ui.filetransport

import android.os.Build
import android.os.Environment
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.result.contract.ActivityResultContracts
import com.tans.tfiletransporter.BuildConfig
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.Settings
import com.tans.tfiletransporter.databinding.ContactsLayoutBinding
import com.tans.tfiletransporter.file.VcfExporter
import com.tans.tfiletransporter.logs.AndroidLog
import com.tans.tfiletransporter.transferproto.fileexplore.FileExplore
import com.tans.tfiletransporter.transferproto.fileexplore.model.FileExploreFile
import com.tans.tfiletransporter.transferproto.fileexplore.requestSendFilesSuspend
import com.tans.tfiletransporter.ui.commomdialog.loadingDialogSuspend
import com.tans.tuiutils.fragment.BaseCoroutineStateFragment
import contacts.async.findWithContext
import contacts.core.Contacts
import contacts.core.log.AndroidLogger
import contacts.core.log.EmptyLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

class ContactsFragment : BaseCoroutineStateFragment<Unit>(Unit) {

    private val contactsApi by lazy {
        Contacts(
            context = this.requireContext(),
            logger = if (BuildConfig.DEBUG) AndroidLogger() else EmptyLogger()
        )
    }

    override val layoutId: Int = R.layout.contacts_layout

    private val onBackPressedDispatcher: OnBackPressedDispatcher
        get() = requireActivity().onBackPressedDispatcher

    private val onBackPressedCallback: OnBackPressedCallback by lazy {
        object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {

            }
        }
    }

    private val fileExplore: FileExplore by lazy {
        (requireActivity() as FileTransportActivity).fileExplore
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {}

    override fun CoroutineScope.firstLaunchInitDataCoroutine() {}

    override fun CoroutineScope.bindContentViewCoroutine(contentView: View) {
        //onBackPressedDispatcher.addCallback(this@ContactsFragment, onBackPressedCallback)
        val viewBinding = ContactsLayoutBinding.bind(contentView)
        val context = requireActivity() as FileTransportActivity

        viewBinding.contactsTextView.text = "Press the share button to share contacts"

        permissionLauncher.launch(
            arrayOf(
                android.Manifest.permission.READ_CONTACTS,
                android.Manifest.permission.WRITE_CONTACTS
            )
        )

        launch {
            context.observeFloatBtnClick()
                .collect {
                    val tab = context.currentState().selectedTabType
                    runCatching {
                        val file = File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                            "contacts.vcf"
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            if (file.exists()) {
                                file.createNewFile()
                            } else {
                                file.writeText("")
                            }
                            context.supportFragmentManager.loadingDialogSuspend {
                                file.outputStream().use {
                                    VcfExporter().exportContacts(
                                        context = requireActivity(),
                                        contactsApi = contactsApi,
                                        outputStream = it,
                                        contacts = contactsApi
                                            .rawContactsQuery()
                                            .findWithContext()
                                            .toMutableList(),
                                        showExportingToast = false,
                                        callback = {}
                                    )
                                }
                            }
                        }
                        val exploreFiles = listOf(file)
                            .map {
                                FileExploreFile(
                                    name = it.name,
                                    path = it.path,
                                    size = it.length(),
                                    lastModify = it.lastModified()
                                )
                            }
                        println(exploreFiles)
                        if (tab == FileTransportActivity.Companion.DirTabType.Contacts) {
                            launch {
                                runCatching {
                                    fileExplore.requestSendFilesSuspend(
                                        sendFiles = exploreFiles,
                                        maxConnection = Settings.transferFileMaxConnection()
                                    )
                                }.onSuccess {
                                    AndroidLog.d(TAG, "Request send files success: $it")
                                    runCatching {
                                        (requireActivity() as FileTransportActivity).sendFiles(exploreFiles)
                                    }
                                }.onFailure {
                                    AndroidLog.e(TAG, "Request send files fail: $it", it)
                                }
                                //fileTreeUI.clearSelectedFiles()
                            }
                        }
                    }.onFailure { it.printStackTrace() }
                }
        }
    }

    companion object {
        private const val TAG = "ContactsFragment"
    }
}