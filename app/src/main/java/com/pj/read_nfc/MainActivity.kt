package com.pj.read_nfc

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.*
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pj.read_nfc.ui.theme.Read_NFCTheme
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.IOException
import java.nio.charset.Charset

class MainActivity : ComponentActivity() {

    // NFC fields to intercept NFC TAG
    var tag :Tag? = null
    private lateinit var nfcAdapter: NfcAdapter
    private lateinit var nfcPendingIntent: PendingIntent
    private lateinit var nfcIntentFiltersArray: Array<IntentFilter>
    private lateinit var nfcTechListsArray: Array<Array<String>>
    private var NFC_Received_Value = MutableStateFlow("")
    private var writeNFCMessage = MutableStateFlow(false)
    private var NFCMessageWasWritten = MutableStateFlow(false)
    private var newNFCTextToWrite1 = ""
    private var newNFCTextToWrite2 = ""
    // End NFC fields

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        Log.d("NFC", "NFC supported " + (nfcAdapter != null).toString())
        Log.d("NFC", "NFC enabled " + (nfcAdapter?.isEnabled).toString())

        // Intercept intent
        val intent = Intent(this, javaClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        nfcPendingIntent = PendingIntent.getActivity(this, 0, intent, 0)

        //Indicate what type of intents we want to intercept
        val ndef = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
            try {
                addDataType("*/*")    /* Handles all MIME based dispatches.
                                 You should specify only the ones that you need. */
            } catch (e: IntentFilter.MalformedMimeTypeException) {
                throw RuntimeException("fail", e)
            }
        }

        nfcIntentFiltersArray = arrayOf(ndef)
        nfcTechListsArray = arrayOf(arrayOf<String>(NfcF::class.java.name))



        setContent {
            Read_NFCTheme {
                var nfcTextToWrite1 by remember { mutableStateOf("")}
                var nfcTextToWrite2 by remember { mutableStateOf("")}
                val nfc_Received_Value by NFC_Received_Value.collectAsState()
                val writeNFCMessageStatus by writeNFCMessage.collectAsState()
                val nfcMessageWasWrittenStatus by NFCMessageWasWritten.collectAsState()

                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Greeting("NFC: $nfc_Received_Value")
                        Spacer(modifier = Modifier.height(16.dp))
                        TextField(value = nfcTextToWrite1 , onValueChange = { nfcTextToWrite1 = it}, label = {Text("Message 1")})
                        TextField(value = nfcTextToWrite2 , onValueChange = { nfcTextToWrite2 = it}, label = {Text("Message 2")})
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            if(intent != null) {
                                newNFCTextToWrite1=nfcTextToWrite1
                                newNFCTextToWrite2=nfcTextToWrite2
                                writeNFCMessage.value = true
                            }
                        }) {
                            Text("Write NFC TAG")
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if(writeNFCMessageStatus){
                            Text(
                                color = Color.Red,
                                text = "Please tap NFC label with your Phone to write the message!")
                        }
                        if(nfcMessageWasWrittenStatus){
                            Text(
                                color = Color.Green,
                                text = "Message was written, remove NFC and tap again to read it!")
                        }
                    }


                }
            }
        }
    }
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            if(writeNFCMessage.value){
                createNFCMessage(arrayOf(newNFCTextToWrite1,newNFCTextToWrite2), intent)
                writeNFCMessage.value=false
                NFCMessageWasWritten.value=true
            }
            else {
                val messages = processIntent(intent)
                messages[0]?.let{
                    NFC_Received_Value.value = "\nValue 1: " + it
                }

                messages[1]?.let{
                    NFC_Received_Value.value += "\nValue 2: " + it
                }

                NFCMessageWasWritten.value=false
            }
        }
    }

    public override fun onPause() {
        super.onPause()
        nfcAdapter.disableForegroundDispatch(this)
    }

    public override fun onResume() {
        super.onResume()
        nfcAdapter.enableForegroundDispatch(this, nfcPendingIntent, nfcIntentFiltersArray, nfcTechListsArray)
    }

    private fun processIntent(checkIntent: Intent):MutableList<String> {
        return retrieveNFCMessage(checkIntent)
//        val test = "new intent received"
//
//        // Check if intent has the action of a discovered NFC tag
//        // with NDEF formatted contents
//        if (checkIntent.action == NfcAdapter.ACTION_NDEF_DISCOVERED) {
//            tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
//            // Retrieve the raw NDEF message from the tag
//            val rawMessages =
//                checkIntent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
//
//            var ndefMsg = rawMessages?.get(0) as NdefMessage
//            var ndefRecord = ndefMsg.records[0]
//
//            if (ndefRecord.toUri() != null) {
//                // Use Android functionality to convert payload to URI
//                Log.d("NFC", "URI detected " + ndefRecord.toUri().toString())
//                return ndefRecord.toUri().toString()
//            } else {
//                // Other NFC Tags
//                Log.d("NFC", "Payload " + ndefRecord.payload.contentToString())
//                return ndefRecord.payload.contentToString()
//            }
//        }
//        return ""
    }

    fun retrieveNFCMessage(intent: Intent?): MutableList<String> {
        var messages:MutableList<String> = mutableListOf()
        intent?.let {
            if (NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action) {
                val nDefMessages = getNDefMessages(intent)
                nDefMessages[0].records?.let {
                    it.forEach {
                        it?.payload.let {
                            it?.let {
                                val message = String(it).drop(3)
                                messages.add(message)
                                //return String(it).drop(3)
                            }
                        }
                    }
                }
            }
        }
        return messages
    }
    private fun getNDefMessages(intent: Intent): Array<NdefMessage> {

        val rawMessage = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
        rawMessage?.let {
            return rawMessage.map {
                it as NdefMessage
            }.toTypedArray()
        }
        // Unknown tag type
        val empty = byteArrayOf()
        val record = NdefRecord(NdefRecord.TNF_UNKNOWN, empty, empty, empty)
        val msg = NdefMessage(arrayOf(record))
        return arrayOf(msg)
    }


    fun readTag(tag: Tag?): String? {

        return MifareUltralight.get(tag)?.use { mifare ->
            mifare.connect()
            val payload = mifare.readPages(4)
            String(payload, Charset.forName("US-ASCII"))
        }
    }

    fun writeTag(intent: Intent, tagText: String) {
        intent?.let {
            val tag = it.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
            MifareUltralight.get(tag)?.use { ultralight ->
                ultralight.connect()
                Charset.forName("US-ASCII").also { usAscii ->
                    ultralight.writePage(4, "abcd".toByteArray(usAscii))
                    ultralight.writePage(5, "efgh".toByteArray(usAscii))
                    ultralight.writePage(6, "ijkl".toByteArray(usAscii))
                    ultralight.writePage(7, "mnop".toByteArray(usAscii))
                }
            }
        }
    }
    fun createNdefTextRecord(payload: String, locale: Locale, encodeInUtf8: Boolean): NdefRecord {
        val langBytes = locale.language.toByteArray(Charset.forName("US-ASCII"))
        val utfEncoding = if (encodeInUtf8) Charset.forName("UTF-8") else Charset.forName("UTF-16")
        val textBytes = payload.toByteArray(utfEncoding)
        val utfBit: Int = if (encodeInUtf8) 0 else 1 shl 7
        val status = (utfBit + langBytes.size).toChar()
        val data = ByteArray(1 + langBytes.size + textBytes.size)
        data[0] = status.toByte()
        System.arraycopy(langBytes, 0, data, 1, langBytes.size)
        System.arraycopy(textBytes, 0, data, 1 + langBytes.size, textBytes.size)
        return NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT, ByteArray(0), data)
    }

    fun createNFCMessage(payload: Array<String>, intent: Intent?): Boolean {

        val nfcRecords:MutableList<NdefRecord> = arrayListOf()
        payload.forEach { message->
            val nfcRecord = NdefRecord.createTextRecord("",message)
            nfcRecords.add(nfcRecord)
        }
        val pathPrefix = "pj.com:read_nfc"

        val nfcMessage = NdefMessage(nfcRecords.toTypedArray())
        intent?.let {
            val tag = it.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
            return writeMessageToTag(nfcMessage, tag)
        }
        return false
    }

    private fun writeMessageToTag(nfcMessage: NdefMessage, tag: Tag?): Boolean {

        try {
            val nDefTag = Ndef.get(tag)

            nDefTag?.let {
                it.connect()
                if (it.maxSize < nfcMessage.toByteArray().size) {
                    //Message to large to write to NFC tag
                    return false
                }
                if (it.isWritable) {
                    it.writeNdefMessage(nfcMessage)
                    it.close()
                    //Message is written to tag
                    return true
                } else {
                    //NFC tag is read-only
                    return false
                }
            }

            val nDefFormatableTag = NdefFormatable.get(tag)

            nDefFormatableTag?.let {
                try {
                    it.connect()
                    it.format(nfcMessage)
                    it.close()
                    //The data is written to the tag
                    return true
                } catch (e: IOException) {
                    //Failed to format tag
                    return false
                }
            }
            //NDEF is not supported
            return false

        } catch (e: Exception) {
            //Write operation has failed
        }
        return false
    }
}

@Composable
fun Greeting(name: String) {
    Text(text = "$name")
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    Read_NFCTheme {
        Greeting("Android")
    }
}