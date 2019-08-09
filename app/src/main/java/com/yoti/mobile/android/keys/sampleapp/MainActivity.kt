package com.yoti.mobile.android.keys.sampleapp

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.yoti.mobile.android.keys.sdk.internal.model.generic.GenericDataFile
import com.yoti.mobile.android.keys.sdk.internal.model.generic.GenericKeyData
import com.yoti.mobile.android.keys.sdk.ui.NfcLinkerActivity
import com.yoti.mobile.android.keys.sdk.ui.NfcLinkerPresenter

class MainActivity : NfcLinkerActivity(),
        SampleKeyContract.View {
    private lateinit var localPresenter: SampleKeyPresenter

    override fun onCreate(savedInstanceState: Bundle?) {
        localPresenter = SampleKeyPresenter(this, this)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val spinner = findViewById<Spinner>(R.id.action)
        ArrayAdapter.createFromResource(
                this,
                R.array.key_actions_array,
                android.R.layout.simple_spinner_item
        ).also { adapter ->
            spinner.adapter = adapter
        }

        spinner.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
            }

            override fun onItemSelected(
                    parent: AdapterView<*>?, view: View?, position: Int, id: Long
            ) {
                val selection = parent?.getItemAtPosition(position)
                KeyAction.values().forEach {
                    if (getString(it.resId) == selection) {
                        localPresenter.onActionChanged(it)
                    }
                }
            }
        }

        findViewById<Button>(R.id.clean).setOnClickListener {
            findViewById<TextView>(R.id.writeContent).text = ""
            findViewById<TextView>(R.id.status).text = ""
            findViewById<TextView>(R.id.readContent).text = ""
        }
    }

    override fun getPresenter(): NfcLinkerPresenter {
        return localPresenter
    }

    override fun getActivityLayoutResId(): Int {
        return R.layout.activity_main
    }

    override fun getToolbarLayoutResId(): Int {
        return NO_TOOLBAR
    }

    override fun getUserInput(): String? {
        val textInput = findViewById<EditText>(R.id.writeContent)
        return textInput?.text?.toString()
    }

    override fun showToastMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        findViewById<TextView>(R.id.status).text = message
    }

    override fun showKeyFileData(file: GenericDataFile) {
        findViewById<TextView>(R.id.readContent).text = file.toString()
    }

    override fun showKeyData(tagPayload: GenericKeyData) {
        findViewById<TextView>(R.id.readContent).text = tagPayload.toString()
    }

    override fun notifyNfcOperationInProgress() {
        findViewById<TextView>(R.id.status).text = "Nfc operation in progress"
    }

    override fun notifyNfcOperationFinished() {
        findViewById<TextView>(R.id.status).text = "Nfc operation over"
    }

    override fun notifyNfcError(messageResId: Int) {
        findViewById<TextView>(R.id.status).text = getString(messageResId)
    }
}

enum class KeyAction(val resId: Int) {
    READ_CHIP_INFO(R.string.action_read_info),
    READ(R.string.action_read),
    RESET(R.string.action_reset),
    WRITE_KEY(R.string.action_write),
    READ_RESET(R.string.action_read_reset),
    READ_FILE(R.string.action_read_file),
    WRITE_FILE(R.string.action_write_file);
}

