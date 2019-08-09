package com.yoti.mobile.android.keys.sampleapp

import com.yoti.mobile.android.keys.sdk.internal.model.generic.GenericDataFile
import com.yoti.mobile.android.keys.sdk.internal.model.generic.GenericKeyData
import com.yoti.mobile.android.keys.sdk.ui.NfcLinkerContract

class SampleKeyContract {

    interface Presenter : NfcLinkerContract.Presenter {
        fun onActionChanged(action: KeyAction)
    }

    interface View : NfcLinkerContract.View {

        fun showToastMessage(message:String)

        fun showKeyData(tagPayload: GenericKeyData)

        fun showKeyFileData(file: GenericDataFile)

        fun getUserInput():String?
    }
}
