package com.draco.nfcwriter

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

class MainActivity : AppCompatActivity() {
    private val requestCodeSelectFile = 1

    private lateinit var nfc: Nfc
    private lateinit var select: Button
    private lateinit var mime: EditText
    private lateinit var compression: CheckBox
    private lateinit var flash: Button

    private var pendingBytes = byteArrayOf()
    private val maxSize = 1024 * 32
    private lateinit var readyToFlashDialog: AlertDialog

    private fun loadFileFromUri(uri: Uri) {
        val inputStream = contentResolver.openInputStream(uri)

        if (inputStream == null) {
            Toast.makeText(this, "Could not read file.", Toast.LENGTH_SHORT).show()
            return
        }

        val tempBuffer = ByteArray(maxSize)
        val bytesRead = inputStream.read(tempBuffer, 0, maxSize)
        pendingBytes = tempBuffer.copyOf(bytesRead)

        inputStream.close()
    }

    private fun promptSelectFile() {
        val intent = Intent()
                .setType("*/*")
                .setAction(Intent.ACTION_GET_CONTENT)

        val chooserIntent = Intent.createChooser(intent, "Select file")
        startActivityForResult(chooserIntent, requestCodeSelectFile)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != Activity.RESULT_OK)
            return

        if (requestCode == requestCodeSelectFile &&
                data != null &&
                data.data != null) {
            loadFileFromUri(data.data!!)

            return
        }
    }

    override fun onNewIntent(thisIntent: Intent?) {
        super.onNewIntent(thisIntent)

        if (thisIntent == null)
            return

        if (readyToFlashDialog.isShowing) {
            val exception = nfc.writeBytes(thisIntent, pendingBytes, mime.text.toString(), compression.isChecked)
            readyToFlashDialog.dismiss()

            if (exception != null)
                Toast.makeText(this, exception.message, Toast.LENGTH_SHORT).show()
            else
                Toast.makeText(this, "Wrote successfully!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        nfc = Nfc(this)

        select = findViewById(R.id.select)
        mime = findViewById(R.id.mime)
        compression = findViewById(R.id.compression)
        flash = findViewById(R.id.flash)

        select.setOnClickListener {
            when (nfc.supportState()) {
                Nfc.State.SUPPORTED_ON -> promptSelectFile()
                Nfc.State.SUPPORTED_OFF ->
                    Toast.makeText(this, "Enable NFC to use this feature.", Toast.LENGTH_SHORT).show()
                Nfc.State.UNSUPPORTED ->
                    Toast.makeText(this, "Your device does not support NFC.", Toast.LENGTH_SHORT).show()
            }
        }

        flash.setOnClickListener {
            if (pendingBytes.isEmpty()) {
                Toast.makeText(this, "No bytes to write.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (mime.text.toString().isBlank()) {
                Toast.makeText(this, "Mime must not be blank.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            readyToFlashDialog.show()
        }

        readyToFlashDialog = AlertDialog.Builder(this)
                .setTitle("Ready to Flash")
                .setMessage("Please scan an NFC tag to flash your file. Ensure that NFC is enabled.")
                .setPositiveButton("Cancel", null)
                .create()
    }

    override fun onResume() {
        super.onResume()
        nfc.enableForegroundIntent(this)
    }

    override fun onPause() {
        super.onPause()
        nfc.disableForegroundIntent(this)
    }
}