package com.yoti.mobile.android.keys.sampleapp

import android.app.Activity
import android.util.Log
import com.yoti.mobile.android.keys.sampleapp.KeyAction.READ
import com.yoti.mobile.android.keys.sampleapp.KeyAction.READ_CHIP_INFO
import com.yoti.mobile.android.keys.sampleapp.KeyAction.READ_FILE
import com.yoti.mobile.android.keys.sampleapp.KeyAction.READ_RESET
import com.yoti.mobile.android.keys.sampleapp.KeyAction.RESET
import com.yoti.mobile.android.keys.sampleapp.KeyAction.WRITE_FILE
import com.yoti.mobile.android.keys.sampleapp.KeyAction.WRITE_KEY
import com.yoti.mobile.android.keys.sampleapp.SampleKeyContract.Presenter
import com.yoti.mobile.android.keys.sampleapp.SampleKeyContract.View
import com.yoti.mobile.android.keys.sdk.ev2.Ev2ChipCheckerFactory
import com.yoti.mobile.android.keys.sdk.ev2.MifareDesfireEV2
import com.yoti.mobile.android.keys.sdk.exception.NfcException
import com.yoti.mobile.android.keys.sdk.interactors.TagDefaultConfigurationLoaderInteractor
import com.yoti.mobile.android.keys.sdk.interactors.TagDefaultConfigurationLoaderInteractor.TagDefaultConfigLoaderRequest
import com.yoti.mobile.android.keys.sdk.interactors.model.IKeyPayload
import com.yoti.mobile.android.keys.sdk.interactors.nfc.BaseNfcOperationInteractor.NfcOperationRequest
import com.yoti.mobile.android.keys.sdk.interactors.nfc.CompositeNfcInteractor
import com.yoti.mobile.android.keys.sdk.interactors.nfc.ReadChipHardwareIdInteractor
import com.yoti.mobile.android.keys.sdk.interactors.nfc.ResetKeyInteractor
import com.yoti.mobile.android.keys.sdk.interactors.nfc.read.ReadKeyFileInteractor
import com.yoti.mobile.android.keys.sdk.interactors.nfc.read.ReadKeyFileInteractor.ReadKeyFileRequest
import com.yoti.mobile.android.keys.sdk.interactors.nfc.read.ReadKeyPayloadInteractor
import com.yoti.mobile.android.keys.sdk.interactors.nfc.read.ReadKeyPayloadInteractor.ReadKeyPayloadRequest
import com.yoti.mobile.android.keys.sdk.interactors.nfc.write.WriteKeyFileInteractor
import com.yoti.mobile.android.keys.sdk.interactors.nfc.write.WriteKeyFileInteractor.WriteKeyFileRequest
import com.yoti.mobile.android.keys.sdk.interactors.nfc.write.WriteKeyPayloadInteractor
import com.yoti.mobile.android.keys.sdk.interactors.nfc.write.WriteKeyPayloadInteractor.WriteKeyPayloadRequest
import com.yoti.mobile.android.keys.sdk.internal.model.KeyPayloadBuilderFactory
import com.yoti.mobile.android.keys.sdk.internal.model.Session
import com.yoti.mobile.android.keys.sdk.internal.model.SessionFactory
import com.yoti.mobile.android.keys.sdk.internal.model.TagDescriptor.File
import com.yoti.mobile.android.keys.sdk.internal.model.generic.GenericAttributeType
import com.yoti.mobile.android.keys.sdk.internal.model.generic.GenericKeyData
import com.yoti.mobile.android.keys.sdk.internal.model.generic.GenericKeyPayload.GenericFile
import com.yoti.mobile.android.keys.sdk.internal.model.yoti.YotiAttributeType
import com.yoti.mobile.android.keys.sdk.internal.nfc.ChipCheckersFactory
import com.yoti.mobile.android.keys.sdk.internal.nfc.TagDispatcher
import com.yoti.mobile.android.keys.sdk.internal.util.Base64Util
import com.yoti.mobile.android.keys.sdk.internal.vendor.AbstractNFCTagDefinition
import com.yoti.mobile.android.keys.sdk.ui.NfcLinkerPresenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.ArrayList

class SampleKeyPresenter(
        activity: Activity,
        private val view: View,
        private val session: Session = SessionFactory.getDefaultSession(),
        chipCheckerFactory: ChipCheckersFactory = Ev2ChipCheckerFactory(activity, session)
) :
        NfcLinkerPresenter(view, TagDispatcher(activity, chipCheckerFactory)),
        Presenter {

    private val resetInteractor = ResetKeyInteractor(chipCheckerFactory.exceptionFactory)
    private val readChipInfoInteractor: ReadChipHardwareIdInteractor =
            ReadChipHardwareIdInteractor(
                    chipCheckerFactory.exceptionFactory
            )
    private val readInteractor: ReadKeyPayloadInteractor =
            ReadKeyPayloadInteractor(
                    activity,
                    session,
                    chipCheckerFactory.exceptionFactory
            )

    private val readFileInteractor: ReadKeyFileInteractor =
            ReadKeyFileInteractor(
                    session,
                    chipCheckerFactory.exceptionFactory
            )
    private val writeInteractor: WriteKeyPayloadInteractor =
            WriteKeyPayloadInteractor(
                    session,
                    chipCheckerFactory.exceptionFactory
            )

    private val writeFileInteractor: WriteKeyFileInteractor =
            WriteKeyFileInteractor(
                    session,
                    chipCheckerFactory.exceptionFactory
            )
    private val configLoaderInteractor = TagDefaultConfigurationLoaderInteractor(activity)

    private var currentAction: KeyAction? = null

    private val uiScope: CoroutineScope
        get() = CoroutineScope(Dispatchers.Main + Job())

    val version = File.builder()
            .name(YotiAttributeType.VERSION.name)
            .shouldBeEncryptedIndividually(true)
            .genericAttributeType(GenericAttributeType.STRING)
            .build()

    init {
        resetInteractor.setOnNfcOperationListener(this)
        readChipInfoInteractor.setOnNfcOperationListener(this)
        readInteractor.setOnNfcOperationListener(this)
        writeInteractor.setOnNfcOperationListener(this)
    }

    override fun onActionChanged(action: KeyAction) {
        currentAction = action
    }

    override fun onProcessYotiKey(key: AbstractNFCTagDefinition) {
        if (currentAction == null) {
            view.showToastMessage("Select an action before")
            return
        }

        when (currentAction) {
            READ_CHIP_INFO -> loadConfig { readChipInfo(key) }
            READ -> loadConfig { read(key) }
            RESET -> loadConfig { reset(key) }
            WRITE_KEY -> loadConfig { write(key) }
            READ_RESET -> loadConfig { multiOperation(key) }
            READ_FILE -> loadConfig { readFile(key) }
            WRITE_FILE -> loadConfig { writeFile(key) }
        }
    }

    override fun onYotiKeyNotEmpty() {
        view.showToastMessage("Key Not empty")
    }

    private fun readChipInfo(key: AbstractNFCTagDefinition) {
        uiScope.launch {
            try {
                val response = readChipInfoInteractor.run(NfcOperationRequest(key))
                val msg = "Chip info ${response.chipType} : ${Base64Util().encode(
                        response.chipHardwareId
                )}"

                if (BuildConfig.DEBUG) {
                    Log.i(TAG, msg)
                }

                view.showToastMessage(msg)
            } catch (nfcException: NfcException) {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Read chip info failed", nfcException)
                }

                onProcessNfcException(nfcException)
            }
        }
    }

    private fun reset(key: AbstractNFCTagDefinition) {
        uiScope.launch {
            try {
                resetInteractor.run(NfcOperationRequest(key))
                if (BuildConfig.DEBUG) {
                    Log.i(TAG, "Tag factory reset completed")
                }
                view.showToastMessage("Chip has been reset")
            } catch (nfcException: NfcException) {

                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Tag factory reset failed", nfcException)
                }
                onProcessNfcException(nfcException)
            }
        }
    }

    private fun read(key: AbstractNFCTagDefinition) {
        uiScope.launch {
            try {
                val response = readInteractor.run(ReadKeyPayloadRequest(key, pin="1234"))
                val yotiKeyData = response.keyData as GenericKeyData
                if (BuildConfig.DEBUG) {
                    Log.i(TAG, "Read tag operation completed: \n$yotiKeyData")
                }

                view.showKeyData(yotiKeyData)
            } catch (nfcException: NfcException) {

                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Read tag operation failed", nfcException)
                }
                onProcessNfcException(nfcException)
            }
        }
    }

    private fun readFile(key: AbstractNFCTagDefinition) {
        uiScope.launch {
            try {
                val response = readFileInteractor.run(ReadKeyFileRequest(version, key, pin="1234"))
                if (BuildConfig.DEBUG) {
                    Log.i(TAG, "Read file operation completed: \n$response.fileData")
                }

                view.showKeyFileData(response.fileData)
            } catch (nfcException: NfcException) {

                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Read file operation failed", nfcException)
                }
                onProcessNfcException(nfcException)
            }
        }
    }

    private fun write(key: AbstractNFCTagDefinition) {
        uiScope.launch {
            try {
                val keyPayload = createPayloadFromUserInput()
                writeInteractor.run(WriteKeyPayloadRequest(key, keyPayload, pin="1234"))
                if (BuildConfig.DEBUG) {
                    Log.i(TAG, "Write operation completed")
                }

                view.showToastMessage("The key has been written")
            } catch (nfcException: NfcException) {

                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Write operation failed", nfcException)
                }
                onProcessNfcException(nfcException)
            }
        }
    }

    private fun writeFile(key: AbstractNFCTagDefinition) {
        uiScope.launch {
            try {
                val file = GenericFile(version.name, version.genericAttributeType, "myVersion".toByteArray())
                writeFileInteractor.run(WriteKeyFileRequest(key, file, pin="1234"))
                if (BuildConfig.DEBUG) {
                    Log.i(TAG, "Write operation completed")
                }

                view.showToastMessage("The key has been written")
            } catch (nfcException: NfcException) {

                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Write operation failed", nfcException)
                }
                onProcessNfcException(nfcException)
            }
        }
    }

    private fun multiOperation(key: AbstractNFCTagDefinition) {
        val compositeNfcOperation = CompositeNfcInteractor()
        val keyPayload = createPayloadFromUserInput()

        compositeNfcOperation
                .addOperation(readInteractor, ReadKeyPayloadRequest(key))
                .addOperation(resetInteractor, NfcOperationRequest(key))
                .addOperation(writeInteractor, WriteKeyPayloadRequest(key, keyPayload))

        uiScope.launch {
            try {
                val response = compositeNfcOperation.executeAll()
                if (BuildConfig.DEBUG) {
                    Log.i(TAG, "read/reset operation completed")
                }

                view.showToastMessage("The key has been read and reset")
            } catch (nfcException: NfcException) {

                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "read/reset operation failed", nfcException)
                }
                onProcessNfcException(nfcException)
            }
        }
    }

    private fun loadConfig(postLoading: () -> Unit) {
        uiScope.launch {
            try {
                configLoaderInteractor.resetTagConfigurations()
                val files = createFilesListFromUserInput()

                configLoaderInteractor.run(TagDefaultConfigLoaderRequest(
                        files = files,
                        fileForAuthenticity = files[0],
                        session = session,
                        requirePinAuth = true,
                        chip = MifareDesfireEV2.CHIP_UNIQUE_NAME
                ))
                postLoading()
            } catch (throwable: Throwable) {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Configuration load failed", throwable)
                }

                if (throwable is NfcException) {
                    onProcessNfcException(throwable)
                }
            }
        }
    }

    private fun processUserInput(processor: (name: String, value: String) -> Unit) {
        val userInput = view.getUserInput()
        Log.d(TAG, "user input is:<$userInput>")

        if (userInput.isNullOrEmpty()) return

        // expected structure : name1:value1\nname2:value2
        userInput.split("\n").forEach { nameAndValue ->
            val array = nameAndValue.split(":")
            val name = array.first()
            val value = array.last()

            processor(name, value)
        }
    }

    private fun getDefaultFilesList(): ArrayList<File> {
        val files = ArrayList<File>()

        files.add(version)

        return files
    }

    private fun createFilesListFromUserInput(): ArrayList<File> {
        val files = getDefaultFilesList()

        processUserInput { name, _ ->
            val file =
                    File.builder()
                            .name(name)
                            .genericAttributeType(GenericAttributeType.STRING)
                            .build()
            files.add(file)
            Log.d(TAG, "File list from user input is: $files")
        }

        return files
    }

    private fun createPayloadFromUserInput(): IKeyPayload {
        val files = getDefaultFilesList()

        val genericPayloadBuilder =
                KeyPayloadBuilderFactory().createGenericPayloadBuilder()

        genericPayloadBuilder.addAttributeDataToKeyPayload(files[0], "1.0".toByteArray())

        processUserInput { name, value ->
            val file =
                    File.builder()
                            .name(name)
                            .genericAttributeType(GenericAttributeType.STRING)
                            .build()
            genericPayloadBuilder.addAttributeDataToKeyPayload(file, value.toByteArray())

            Log.d(TAG, "Adding file to payload: $file")
        }

        Log.d(TAG, "Generic payload is:$genericPayloadBuilder")

        return genericPayloadBuilder.build()
    }
}

private const val TAG = "SampleKeyPresenter"