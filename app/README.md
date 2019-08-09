<img src="http://enablingenterprise.org/wp-content/uploads/2016/10/Yoti_logo-300x150.png" alt="Brand logo" />

# Yoti Key #

This module contains the source code for the Yoti Key SDK.
This SDK does a lot of the heavy lifting when it comes to communicating with
a NXP Mifare DESFire EV2 or multos Chip. Those hardware specific implementations
exist in distinct artefacts that you need to import. See below in 
`How to add the sdk in your app`
On the It provides : an abstract activity, an abstract 
presenter that you may want to extends if it suits your need. There is 
also 4 use cases and a default configuration loader that you can use in 
your business logic.
All the use cases and the configuration loader can work with an `InteractorHandler`.
Or the recommended way is to use the coroutine implementation.  

# Type of payloads to read and write

There are 2 types of tag that can be read/written. One is Yoti specific and 
consist of attributes retrieved from a yoti share. The other type is a 
generic, multipurpose type of payload.
Before writing the tag, its structure and settings needs to be defined so 
that the writing process can organise the files and the various access
and encryption keys properly.

## Yoti specific 
In this case you would write a `IKeyPayload<YotiAttributeType>` which is 
implemented in `YotiKeyPayload`. You can create instances via:
```kotlin
KeyPayloadBuilderFactory().createNewKeyBuilder<YotiAttributeType>(
                        AttributeType.YOTI
                )
```
And when you read the payload from a tag you will receive `YotiKeyData`.

## Generic
For a multipurpose type of payload to read write you should write a
`IKeyPayload<YotiAttributeType>` which is implemented in 
`GenericKeyPayload`. You can create instances via:
```kotlin
KeyPayloadBuilderFactory().createNewKeyBuilder<GenericAttributeType>(
                        AttributeType.GENERIC
                )
```
And when you read the payload from a tag you will receive `GenericKeyData`.

## Describe the tag structure
This is done via `TagDefaultConfigurationLoaderInteractor`. 
This interactor can to be used with `coroutine`:
 ```kotlin
 // Clear previous configuration
private val configLoaderInteractor = TagDefaultConfigurationLoaderInteractor(context)
configLoaderInteractor.resetTagConfigurations()

val uiScope: CoroutineScope
    get() = CoroutineScope(Dispatchers.Main + Job())
        
 uiScope.launch {
    try {
        configLoaderInteractor.resetTagConfigurations()
        val files = createFilesListFromUserInput()
    
        configLoaderInteractor.run(TagDefaultConfigLoaderRequest(
            files = files,
            fileForAuthenticity = files[0],
            session = session,
            requirePinAuth = true, // if you need PN authentication
            chip = MifareDesfireEV2.CHIP_UNIQUE_NAME // the type of chip
        ))
    } catch (throwable: Throwable) {
        Log.e(TAG, "Configuration load failed", throwable)
    }
 }
 ```
 This interactor can to be used with `InteractorHandler` but this is
 DEPREACTED and should be avoided:

  ```kotlin
 // DEPRECATED, USE KOTLIN CO ROUTINE
 // Clear previous configuration
private val configLoaderInteractor = TagDefaultConfigurationLoaderInteractor(context)
configLoaderInteractor.resetTagConfigurations()
 
 // Here, you need to, somehow, generate a list of files that you want to read/write
val files = createFilesListFromUserInput()

interactorHandler.execute(configLoaderInteractor,
    // using the first file for authenticity check
    TagDefaultConfigLoaderRequest(files, files[0], session),
    
    object : InteractorCallback<ResponseValue> {
        override fun onSuccess(response: ResponseValue?) {
            // Do things when config is loaded
        }

        override fun onError(throwable: Throwable?) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Configuration load failed", throwable)
            }
            onProcessNfcException(throwable as NfcException)
        }
    }
) 
 ```
 
 It is important to note that `TagDefaultConfigLoaderRequest` received the
 customised part of the configuration. The one you should define. 
 (number of file and their type)
 - The first parameter is the list of files name and type without their 
 content that you expect to read/write.
 - The second parameter is the file that can be used to determine if a tag is
 authentic (the file presence with a non empty content will be tested).
 
 If your configuration is dynamic you might want to save your list of
 files so you can restore it for later read/write. 
 **You should call the load function for every time your tag description
 changes.**
  

# Classes to use
## NFC use cases
- **`ResetKeyInteractor`**: allows you to reset the tag to its factory state
- **`ReadChipHardwareIdInteractor`**: allows you to read the chip type and UID
- **`ReadKeyPayloadInteractor`**: allows you read the full content from the 
tag and will return a `GenericKeyData` (or `YotiKeyData`) in the response
- **`ReadKeyFileInteractor`**: allows you read a single file content from the 
tag and will return a `GenericDataFile` in the response
- **`WriteKeyInteractor`**: allows you to write to a full tag via 
a `GenericKeyPayload` (or `YotiKeyPayload`) 
- **`WriteKeyFileInteractor`**: allows you to write a single file to the 
tag via a `GenericFile`
- **`TagDefaultConfigurationLoaderInteractor`**: allows you to load the 
default configuration.
- **`CompositeNfcInteractor`**: allows you to execute several NFC operation
during the "Tap" form the user.

The recommended way to use the use case is via the suspend function 
`run(NfcOperationRequest):Interactor.ResponseValue`:
 ```kotlin
val uiScope: CoroutineScope
    get() = CoroutineScope(Dispatchers.Main + Job())

val resetInteractor = ResetKeyInteractor(chipCheckerFactory.exceptionFactory)

// to be notified of the start and the end of the nfc operation
// assuming 'this' implements OnNfcOperationListener
resetInteractor.setOnNfcOperationListener(this)

// This method is from the listener set on the tag dispatcher instance
// It is invoked when a compatible NFC tag is detected. See below
override fun onYotiKeyDetected(key:AbstractNFCTagDefinition){        
    uiScope.launch {
        try {
            resetInteractor.run(NfcOperationRequest(key))
            view.showToastMessage("Chip has been reset")
        } catch (nfcException: NfcException) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Tag factory reset failed", nfcException)
            }
        }
    }
}
 ```

If you want to run multiple nfc operation in one 'tap' you will need
to use `CompositeNfcInteractor` this way:
```kotlin
private fun multiOperation(key: AbstractNFCTagDefinition<Any>) {
        val compositeNfcOperation = CompositeNfcInteractor()
        val keyPayload = createPayloadFromUserInput()

        compositeNfcOperation
                .addOperation(readInteractor, NfcOperationRequest(key))
                .addOperation(resetInteractor, NfcOperationRequest(key))
                .addOperation(writeInteractor, WriteKeyRequest(key, keyPayload))

        uiScope.launch {
            try {
                val responses = compositeNfcOperation.executeAll()
                view.showToastMessage("The key has been read and reset")
            } catch (nfcException: NfcException) {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "read/reset operation failed", nfcException)
                }
            }
        }
    }
```

If you want PIN authentication to be supported then you need to:
- Configure the tag description to require PIN authentication: 
```
TagDefaultConfigLoaderRequest(
    files = files,
    fileForAuthenticity = files[0],
    session = session,
    requirePinAuth = true,
    chip = MifareDesfireEV2.CHIP_UNIQUE_NAME
)
```

- to submit the PIN in the read and write requests:
```kotlin
NfcOperationRequest(key, pin="1234") // read interactor
WriteKeyRequest(key, keyPayload, pin="1234") // write interactor
```
- If the PIN is wrongly configure or does not match you will receive an
`NfcException` with the following `Reason`:
```
    PIN_MISMATCH // The PIN on the chip and the one submitted does not match
    PIN_NOT_SUPPORTED_WITH_EV2_AND_YOTI_PAYLOAD // Yoti Payload does allow PIN configuration
    PIN_PROVIDED_BUT_PIN_TAG_DESCRIPTOR_NOT_FOUND //PIN submitted but not configured in the tag descriptor
    PIN_NOT_PROVIDED_BUT_PIN_TAG_DESCRIPTOR_FOUND // PIN not submitted but configuration found
```

## Implementation
This section gives 2 types of implementations MVP and any other.
### MVP implementation
If you are using an MVP implementation then you might want to extend the
activity and the presenter provided.

- **`NfcLinkerActivity`**: this activity implement NFC monitoring and warn 
the user if it is disabled and offer to sent him to the settings to enable it.
In order to customised the way to message it displayed you may override 
`notifyNfcNeedsToBeEnabled`
If you want this activoty to monitor network state for you, do call
`enableNetworkStateMonitoring` before `onResume` is called. The counterpart
`disableNetworkStateMonitoring` is available as well. For this case, if
you want to customise how the network state is handled you may override
`notifyNetworkChanged` 

- **`NfcLinkerPresenter`**: Manages NFC related error and initialisation 
errors as well as the dispatch of processing of the intent then
`onProcessYotiKey(AbstractNFCTagDefinition)` will be invoked. This 
is the method tha gives you access to the tag for read and write operation.
This also management of progress when an NFC operation starts or when one is over. 

### Any type of implementation
In this part we will detail more of the various class that work together
to enable NFC capabilities.

#### `TagDispatcher`
This class is the first one that deals with NFC. Its methods must be wired
to some of your `Activity` lifecycle.
First you need to call `TagDispatcher.enableForegroundDispatch` so that
your activity can be receive NFC intent. Typically wired to
`Activity.onResume`. To stop receiving those intents call 
`TagDispatcher.disableForegroundDispatch`, typically wired to
`Activity.onPause`.
Then in oder to process incoming intent wire  `Activity.onNewIntent` 
to `TagDispatcher.onNewIntent`. 

In order to be notified when a compatible NFC tag is detected (or if an 
error occur) you need to set a listener on your tag dispatcher instance
via `TagDispatcher.setOnKeyInteractionListener(OnYotiKeyInteractionListener)`
#### `ChipCheckerFactory`
Now when intent is processed by the TagDispatcher, it will try to 
determine what type of chip it from the list of supported chip that 
it's aware of. This is determined via an instance of `ChipCheckerFactory` 
passed in the constructor of `TagDispatcher`. Each hardware implementation 
dependency (EV2 or MULTOS) provides its own factory: `Ev2ChipCheckerFactory` 
and `MultosChipCheckerFactory`. If you need to support both you can create
your own subclass of `ChipCheckerFactory` and delegate the implementation 
of the methods to an instance of ev2 and multos implementation.

# How to add the sdk in your app

Your app needs to register your application ID on the Tap Linx portal: https://www.mifare.net/devcenter 
to be allowed to run the Tap Linx SDK. Then you would use the key the following way.
You need to add these lines in your build.gradle:
```groovy
android {

    // Tap Linx SDK uses deprecated http API
    useLibrary 'org.apache.http.legacy'
    
    // Tap Linx SDK key needs to be define as well. if you use product flavour
    // and each flavour has a different application id you will need a key
    // per application ID

    productFlavors {
        dev {
            applicationIdSuffix ".debug"
            resValue "string", "TAPLINX_SDK_KEY", '"aaaaaaaaaa"'
        }
        staging {
            applicationIdSuffix ".staging"
            resValue "string", "TAPLINX_SDK_KEY", '"bbbbbbbbbb"'
        }
        production {
            resValue "string", "TAPLINX_SDK_KEY", '"cccccccccc"'
        }
    }
}

dependencies {
    
    implementation("com.yoti.mobile.android.keys:key-sdk-common:1.2.0")
    // if you need ev2 chip support
    implementation("com.yoti.mobile.android.keys:key-sdk-ev2:1.2.0")
    // if you need multos chip support
    implementation("com.yoti.mobile.android.keys:key-sdk-multos:1.2.0")
}
```
Make sure you have added the internal yoti nexus server in the list of
repositories in your root build.gradle:
```groovy
allprojects {
    repositories {
        //... other repo
        maven { url releaseRepository }
        maven {
          url nxpRepositoryUrl
          credentials {
            username nxpRepositoryUserName
            password nxpRepositoryPassword
          }
        }
    }
}
```
with these values in your properties files:
```
releaseRepository=https://nexus.internal.yoti.com/repository/maven-releases
nxpRepositoryUrl=http://maven.taplinx.nxp.com/nexus/content/repositories/taplinx/
nxpRepositoryUserName=sdkuser
nxpRepositoryPassword=taplinx
```

Then you need to reference the string resources created `TAPLINX_SDK_KEY`
from you manifest:

```xml
<application>
    <meta-data
        android:name="com.yoti.mobile.android.keys.SDK_KEY"
        android:value="@string/TAPLINX_SDK_KEY"/>
        
    <uses-library android:name="org.apache.http.legacy" android:required="false"/>
</application>
```

The taplinxx SDK requires firebase analytics. For that reason you need to
configure your app. Instructions there https://firebase.google.com/docs/analytics/android/start
You can find an example in this sample app.

### Copyright

Copyright 2018 Yoti ®
