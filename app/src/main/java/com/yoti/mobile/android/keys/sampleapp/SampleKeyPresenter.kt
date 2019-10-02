package com.yoti.mobile.android.keys.sampleapp

import android.app.Activity
import android.graphics.Bitmap.CompressFormat.PNG
import android.graphics.BitmapFactory
import android.util.Log
import com.yoti.mobile.android.keys.sampleapp.KeyAction.MULTIPLE_WRITE_SAME_FILE
import com.yoti.mobile.android.keys.sampleapp.KeyAction.READ
import com.yoti.mobile.android.keys.sampleapp.KeyAction.READ_CHIP_INFO
import com.yoti.mobile.android.keys.sampleapp.KeyAction.READ_FILE
import com.yoti.mobile.android.keys.sampleapp.KeyAction.READ_RESET
import com.yoti.mobile.android.keys.sampleapp.KeyAction.RESET
import com.yoti.mobile.android.keys.sampleapp.KeyAction.WRITE_FILE
import com.yoti.mobile.android.keys.sampleapp.KeyAction.WRITE_KEY
import com.yoti.mobile.android.keys.sampleapp.KeyAction.WRITE_TOO_BIG
import com.yoti.mobile.android.keys.sdk.ev2.CHIP_UNIQUE_NAME
import com.yoti.mobile.android.keys.sdk.ev2.checker.Ev2ChipCheckerFactory
import com.yoti.mobile.android.keys.sdk.exception.NfcException
import com.yoti.mobile.android.keys.sdk.exception.NfcException.Reason.UNEXPECTED_ERROR
import com.yoti.mobile.android.keys.sdk.interactors.TagDefaultConfigurationLoaderInteractor
import com.yoti.mobile.android.keys.sdk.interactors.TagDefaultConfigurationLoaderInteractor.TagDefaultConfigLoaderRequest
import com.yoti.mobile.android.keys.sdk.interactors.nfc.BaseNfcOperationInteractor.NfcOperationRequest
import com.yoti.mobile.android.keys.sdk.interactors.nfc.CompositeNfcInteractor
import com.yoti.mobile.android.keys.sdk.interactors.nfc.read.ReadKeyFileInteractor
import com.yoti.mobile.android.keys.sdk.interactors.nfc.read.ReadKeyFileInteractor.ReadKeyFileRequest
import com.yoti.mobile.android.keys.sdk.interactors.nfc.read.ReadKeyFileInteractor.ReadKeyFileResponse
import com.yoti.mobile.android.keys.sdk.interactors.nfc.read.ReadKeyPayloadInteractor
import com.yoti.mobile.android.keys.sdk.interactors.nfc.read.ReadKeyPayloadInteractor.ReadKeyPayloadRequest
import com.yoti.mobile.android.keys.sdk.interactors.nfc.read.ReadKeyPayloadInteractor.ReadKeyPayloadResponse
import com.yoti.mobile.android.keys.sdk.interactors.nfc.util.ReadChipInfoInteractor
import com.yoti.mobile.android.keys.sdk.interactors.nfc.util.ResetKeyInteractor
import com.yoti.mobile.android.keys.sdk.interactors.nfc.write.WriteKeyFileInteractor
import com.yoti.mobile.android.keys.sdk.interactors.nfc.write.WriteKeyFileInteractor.WriteKeyFileRequest
import com.yoti.mobile.android.keys.sdk.interactors.nfc.write.WriteKeyPayloadInteractor
import com.yoti.mobile.android.keys.sdk.interactors.nfc.write.WriteKeyPayloadInteractor.WriteKeyPayloadRequest
import com.yoti.mobile.android.keys.sdk.internal.model.Session
import com.yoti.mobile.android.keys.sdk.internal.model.SessionFactory
import com.yoti.mobile.android.keys.sdk.internal.model.TagDescriptor.File
import com.yoti.mobile.android.keys.sdk.internal.model.nfc.AbstractNFCTagDefinition
import com.yoti.mobile.android.keys.sdk.internal.model.nfc.ChipCheckersFactory
import com.yoti.mobile.android.keys.sdk.internal.model.nfc.TagDispatcher
import com.yoti.mobile.android.keys.sdk.internal.model.payloads.IKeyPayload
import com.yoti.mobile.android.keys.sdk.internal.model.payloads.KeyPayloadBuilderFactory
import com.yoti.mobile.android.keys.sdk.internal.model.payloads.generic.GenericAttributeType
import com.yoti.mobile.android.keys.sdk.internal.model.payloads.generic.GenericAttributeType.STRING
import com.yoti.mobile.android.keys.sdk.internal.model.payloads.generic.GenericKeyData
import com.yoti.mobile.android.keys.sdk.internal.model.payloads.generic.GenericKeyPayload.GenericFile
import com.yoti.mobile.android.keys.sdk.internal.model.payloads.yoti.YotiAttributeType
import com.yoti.mobile.android.keys.sdk.ui.NfcLinkerPresenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.ArrayList

class SampleKeyPresenter(
        private val activity: Activity,
        private val view: SampleKeyContract.View,
        private val session: Session = SessionFactory.getDefaultSession(),
        chipCheckerFactory: ChipCheckersFactory = Ev2ChipCheckerFactory(activity, session)
) :
        NfcLinkerPresenter(view, TagDispatcher(activity, chipCheckerFactory)),
        SampleKeyContract.Presenter {

    private val resetInteractor = ResetKeyInteractor(chipCheckerFactory.exceptionFactory)
    private val readChipInfoInteractor: ReadChipInfoInteractor =
            ReadChipInfoInteractor(
                    chipCheckerFactory.exceptionFactory
            )
    private val readKeyInteractor: ReadKeyPayloadInteractor = ReadKeyPayloadInteractor(
            activity,
            session,
            chipCheckerFactory.exceptionFactory
    )
    private val writeInteractor: WriteKeyPayloadInteractor =
            WriteKeyPayloadInteractor(
                    session,
                    chipCheckerFactory.exceptionFactory
            )

    private val readFileInteractor: ReadKeyFileInteractor =
            ReadKeyFileInteractor(
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

    private val uiScope = CoroutineScope(Dispatchers.Main)
    private val version = File.Builder()
            .name(YotiAttributeType.VERSION.name)
            .shouldBeEncryptedIndividually(true)
            .genericAttributeType(STRING)
            .build()

    init {
        resetInteractor.setOnNfcOperationListener(this)
        readChipInfoInteractor.setOnNfcOperationListener(this)
        readKeyInteractor.setOnNfcOperationListener(this)
        writeInteractor.setOnNfcOperationListener(this)
    }

    override fun onActionChanged(action: KeyAction) {
        currentAction = action
    }

    override fun onProcessYotiKey(key: AbstractNFCTagDefinition) {

        when (currentAction) {
            READ_CHIP_INFO -> loadConfig { readChipInfo(key) }
            READ -> loadConfig { read(key) }
            RESET -> loadConfig { reset(key) }
            WRITE_KEY -> loadConfig { write(key) }
            READ_RESET -> loadConfig { multiOperation(key) }
            WRITE_TOO_BIG -> loadConfig { write(key, true) }
            READ_FILE -> loadConfig { readFile(key) }
            WRITE_FILE -> loadConfig { writeFile(key) }
            MULTIPLE_WRITE_SAME_FILE -> loadConfig { multiWriteSameFile(key) }
            null -> view.showToastMessage("Select an action before")
        }
    }

    override fun onYotiKeyNotEmpty() {
        view.showToastMessage("Key Not empty")
    }

    private fun readChipInfo(key: AbstractNFCTagDefinition) {
        uiScope.launch {
            try {
                val response = readChipInfoInteractor.run(NfcOperationRequest(key))
                val msg = "Chip info: $response"

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
                val response = readKeyInteractor.run(ReadKeyPayloadRequest(key, pin = "1234"))
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

    private fun write(key: AbstractNFCTagDefinition, forceTooBigPayload: Boolean = false) {
        uiScope.launch {
            try {
                val keyPayload = createPayloadFromUserInput(forceTooBigPayload)
                writeInteractor.run(WriteKeyPayloadRequest(key, keyPayload, pin = "1234"))
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

    private fun readFile(key: AbstractNFCTagDefinition) {
        uiScope.launch {
            try {
                val response =
                        readFileInteractor.run(ReadKeyFileRequest(key, version, pin = "1234"))
                val yotiKeyData = response.fileData
                if (BuildConfig.DEBUG) {
                    Log.i(TAG, "Read tag operation completed: \n$yotiKeyData")
                }

                view.showKeyFileData(yotiKeyData)
            } catch (nfcException: NfcException) {

                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Read tag operation failed", nfcException)
                }
                onProcessNfcException(nfcException)
            }
        }
    }

    private fun writeFile(key: AbstractNFCTagDefinition) {
        uiScope.launch {
            try {
                val file =
                        GenericFile(version.name, STRING, "New Version A bit longer".toByteArray())
                writeFileInteractor.run(WriteKeyFileRequest(key, file, pin = "1234"))
                if (BuildConfig.DEBUG) {
                    Log.i(TAG, "Write operation completed")
                }

                view.showToastMessage("The file has been written")
            } catch (nfcException: NfcException) {

                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Write operation failed", nfcException)
                }
                onProcessNfcException(nfcException)
            }
        }
    }

    private fun multiOperation(key: AbstractNFCTagDefinition) {
        uiScope.launch {
            try {
                val compositeNfcOperation = CompositeNfcInteractor()
                val keyPayload = createPayloadFromUserInput()

                compositeNfcOperation
                        .addOperation(resetInteractor, NfcOperationRequest(key))
                        .addOperation(
                                writeInteractor,
                                WriteKeyPayloadRequest(key, keyPayload)
                        ).addOperation(readKeyInteractor, NfcOperationRequest(key))

                val response =
                        compositeNfcOperation.executeAll("1234").last() as ReadKeyPayloadResponse
                if (BuildConfig.DEBUG) {
                    Log.i(TAG, "reset, write, read operation completed")
                }

                view.showToastMessage("The key has been reset, written and read")

                view.showKeyData(response.keyData as GenericKeyData)
            } catch (nfcException: NfcException) {

                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "reset, write, read operation failed", nfcException)
                }
                onProcessNfcException(nfcException)
            }
        }
    }

    private fun multiWriteSameFile(key: AbstractNFCTagDefinition) {
        uiScope.launch {
            try {
                val compositeNfcOperation = CompositeNfcInteractor()

                val file = GenericFile(
                        version.name,
                        version.genericAttributeType!!,
                        "New Version A bit longer".toByteArray()
                )

                compositeNfcOperation
                        .addOperation(writeFileInteractor, WriteKeyFileRequest(key, file))
                        .addOperation(writeFileInteractor, WriteKeyFileRequest(key, file))
                        .addOperation(writeFileInteractor, WriteKeyFileRequest(key, file))
                        .addOperation(writeFileInteractor, WriteKeyFileRequest(key, file))
                        .addOperation(writeFileInteractor, WriteKeyFileRequest(key, file))
                        .addOperation(readFileInteractor, ReadKeyFileRequest(key, version))

                val response =
                        compositeNfcOperation.executeAll("1234").last() as ReadKeyFileResponse
                if (BuildConfig.DEBUG) {
                    Log.i(TAG, "Multiple write operations completed")
                }

                view.showToastMessage("The key has been read and written multiple times")
                view.showKeyFileData(response.fileData)
            } catch (nfcException: NfcException) {

                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Multiple write operations failed", nfcException)
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

                configLoaderInteractor.run(
                        TagDefaultConfigLoaderRequest(
                                files = files,
                                fileForAuthenticity = files[0],
                                session = session,
                                requirePinAuth = true,
                                chip = CHIP_UNIQUE_NAME
                        )
                )
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
        val version = File.Builder()
                .name(YotiAttributeType.VERSION.name)
                .shouldBeEncryptedIndividually(true)
                .genericAttributeType(STRING)
                .build()


        files.add(version)

        return files
    }

    private fun createFilesListFromUserInput(): ArrayList<File> {
        val files = getDefaultFilesList()

        processUserInput { name, _ ->
            val file =
                    File.Builder()
                            .name(name)
                            .genericAttributeType(STRING)
                            .build()
            files.add(file)
            Log.d(TAG, "File list from user input is: $files")
        }

        return files
    }

    private suspend fun createPayloadFromUserInput(
            makeItToBig: Boolean = false
    ): IKeyPayload = withContext(Dispatchers.IO) {
        val files = getDefaultFilesList()

        val genericPayloadBuilder =
                KeyPayloadBuilderFactory().createGenericPayloadBuilder()

        genericPayloadBuilder.addFileDataToKeyPayload(files[0], "1.0".toByteArray())

        processUserInput { name, value ->
            val file =
                    File.Builder()
                            .name(name)
                            .genericAttributeType(STRING)
                            .build()
            genericPayloadBuilder.addFileDataToKeyPayload(file, value.toByteArray())

            Log.d(TAG, "Adding file to payload: $file")
        }

        if (makeItToBig) {
            val pictureData = loadBitmap()
            for (i in 1..5) {
                val file =
                        File.Builder()
                                .name("PictureStream_$i")
                                .genericAttributeType(GenericAttributeType.PNG)
                                .build()

                genericPayloadBuilder.addFileDataToKeyPayload(file, pictureData.toByteArray())
            }
        }

        Log.d(TAG, "Generic payload is:$genericPayloadBuilder")

        return@withContext genericPayloadBuilder.build()
    }

    private suspend fun loadBitmap(): ByteArrayOutputStream = withContext(Dispatchers.IO) {
        val image = BitmapFactory.decodeResource(activity.resources, R.drawable.selfie);
        val stream = ByteArrayOutputStream()
        image.compress(PNG, 100, stream)
        return@withContext stream
    }

    fun cancelOperations() {
        uiScope.cancel()
    }

    override fun onProcessNfcException(exception: NfcException?) {
        super.onProcessNfcException(exception)

        view.showExceptionReason(exception?.reason ?: UNEXPECTED_ERROR)
    }
}

private const val TAG = "SampleKeyPresenter"