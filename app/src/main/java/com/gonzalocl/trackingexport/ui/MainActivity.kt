package com.gonzalocl.trackingexport.ui

import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.EditText
import com.gonzalocl.trackingexport.R
import java.io.File

class MainActivity : AppCompatActivity() {

    private val tracksPath = "Tracks"
    private val documentsPath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) Environment.DIRECTORY_DOCUMENTS else "Documents"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    fun clickAuto(view: View) {

        val tracksDir = File(Environment.getExternalStoragePublicDirectory(documentsPath), tracksPath)

        val trackFiles = tracksDir.listFiles()

        val carGoing = trackFiles[trackFiles.size-6]
        val tracking = trackFiles[trackFiles.size-4]
        val carReturn = trackFiles[trackFiles.size-2]

        val trackTitle = findViewById<EditText>(R.id.track_title).text.toString()

    }

}
