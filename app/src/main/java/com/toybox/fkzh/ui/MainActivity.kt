package com.toybox.fkzh.ui

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.aayushatharva.brotli4j.Brotli4jLoader
import com.aayushatharva.brotli4j.decoder.Decoder
import com.aayushatharva.brotli4j.decoder.DecoderJNI
import com.aayushatharva.brotli4j.decoder.DirectDecompress
import com.aayushatharva.brotli4j.encoder.Encoder
import com.toybox.fkzh.databinding.ActivityMainBinding

class MainActivity: AppCompatActivity(), View.OnClickListener {

    private val viewBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onClick(v: View?) {
        Brotli4jLoader.ensureAvailability()

        // Compress data and get output in byte array
        val compressed: ByteArray? = Encoder.compress("Meow".toByteArray())

        // Decompress data and get output in DirectDecompress
        val directDecompress: DirectDecompress =
            Decoder.decompress(compressed) // or DirectDecompress.decompress(compressed);

        if (directDecompress.resultStatus === DecoderJNI.Status.DONE) {
            Toast.makeText(this, "Decompression Successful: " + String(directDecompress.getDecompressedData()), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Some Error Occurred While Decompressing", Toast.LENGTH_SHORT)
        }
    }
}