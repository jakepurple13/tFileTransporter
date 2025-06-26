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
import com.tans.tfiletransporter.databinding.RemoteDirFragmentBinding
import com.tans.tfiletransporter.file.VcfExporter
import com.tans.tfiletransporter.logs.AndroidLog
import com.tans.tfiletransporter.transferproto.fileexplore.FileExplore
import com.tans.tfiletransporter.transferproto.fileexplore.model.FileExploreFile
import com.tans.tfiletransporter.transferproto.fileexplore.requestSendFilesSuspend
import com.tans.tfiletransporter.ui.FileTreeUI
import com.tans.tuiutils.fragment.BaseCoroutineStateFragment
import contacts.async.findWithContext
import contacts.core.Contacts
import contacts.core.log.AndroidLogger
import contacts.core.log.EmptyLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.LinkedBlockingDeque

class ContactsFragment : BaseCoroutineStateFragment<Unit>(Unit) {

    private val contactsApi by lazy {
        Contacts(
            context = this.requireContext(),
            logger = if (BuildConfig.DEBUG) AndroidLogger() else EmptyLogger()
        )
    }

    override val layoutId: Int = R.layout.remote_dir_fragment

    private var fileTreeUI: FileTreeUI? = null

    private val onBackPressedDispatcher: OnBackPressedDispatcher
        get() = requireActivity().onBackPressedDispatcher

    private val onBackPressedCallback: OnBackPressedCallback by lazy {
        object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                uiCoroutineScope?.launch {
                    fileTreeUI?.backPress()
                }
            }
        }
    }

    private val fileExplore: FileExplore by lazy {
        (requireActivity() as FileTransportActivity).fileExplore
    }

    private val fileTreeStateFlow by lazy {
        MutableStateFlow(FileTreeUI.Companion.FileTreeState())
    }

    private val fileTreeRecyclerViewScrollChannel: Channel<Int> by lazy {
        Channel<Int>(1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    }

    private val fileTreeFolderPositionDeque: LinkedBlockingDeque<Int> by lazy {
        LinkedBlockingDeque()
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {

    }

    override fun CoroutineScope.firstLaunchInitDataCoroutine() {}

    override fun CoroutineScope.bindContentViewCoroutine(contentView: View) {
        onBackPressedDispatcher.addCallback(this@ContactsFragment, onBackPressedCallback)
        val viewBinding = RemoteDirFragmentBinding.bind(contentView)
        val context = requireActivity() as FileTransportActivity

        permissionLauncher.launch(
            arrayOf(
                android.Manifest.permission.READ_CONTACTS,
                android.Manifest.permission.WRITE_CONTACTS
            )
        )

        viewBinding.fileTreeLayout
        /*val fileTreeUI = FileTreeUI(
            context = requireActivity(),
            viewBinding = viewBinding.fileTreeLayout,
            rootTreeUpdater = {
                val handshake = (context.currentState().connectionStatus as? FileTransportActivity.Companion.ConnectionStatus.Connected)?.handshake
                if (handshake != null) {
                    val result = runCatching {
                        fileExplore.requestScanDirSuspend(handshake.remoteFileSeparator)
                    }.onSuccess {
                        AndroidLog.d(TAG, "Request scan root dir success")
                    }.onFailure {
                        AndroidLog.e(TAG, "Request scan root dir fail: $it", it)
                    }
                    if (result.isSuccess) {
                        createRemoteRootTree(result.getOrNull()!!)
                    } else {
                        FileTree(
                            dirLeafs = emptyList(),
                            fileLeafs = emptyList(),
                            path = handshake.remoteFileSeparator,
                            parentTree = null
                        )
                    }
                } else {
                    FileTree(
                        dirLeafs = emptyList(),
                        fileLeafs = emptyList(),
                        path = File.separator,
                        parentTree = null
                    )
                }
            },
            subTreeUpdater = { parentTree, dir ->
                val result = runCatching {
                    fileExplore.requestScanDirSuspend(dir.path)
                }.onSuccess {
                    AndroidLog.d(TAG, "Request dir success")
                }.onFailure {
                    AndroidLog.e(TAG, "Request dir fail: $it", it)
                }
                result.getOrNull()?.let {
                    parentTree.newRemoteSubTree(it)
                } ?: parentTree
            },
            coroutineScope = this,
            stateFlow = fileTreeStateFlow,
            recyclerViewScrollChannel = fileTreeRecyclerViewScrollChannel,
            folderPositionDeque = fileTreeFolderPositionDeque
        )

        this@ContactsFragment.fileTreeUI = fileTreeUI

        launch {
            fileTreeUI.stateFlow()
                .map { it.fileTree }
                .distinctUntilChanged()
                .flowOn(Dispatchers.Main)
                .collect { tree ->
                    val tab = context.currentState().selectedTabType
                    onBackPressedCallback.isEnabled = !tree.isRootFileTree() && tab == FileTransportActivity.Companion.DirTabType.RemoteDir
                }
        }*/

        launch {
            context.stateFlow()
                .map { it.selectedTabType }
                .distinctUntilChanged()
                .flowOn(Dispatchers.Main)
                .collect { tab ->
                    /*val tree = fileTreeUI.currentState().fileTree
                    onBackPressedCallback.isEnabled =
                        !tree.isRootFileTree() && tab == FileTransportActivity.Companion.DirTabType.Contacts*/
                }
        }


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
                            if (file.exists()) file.createNewFile()
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
                        if (tab == FileTransportActivity.Companion.DirTabType.Contacts && exploreFiles.isNotEmpty()) {
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
        private const val TAG = "RemoteDirFragment"
    }
}