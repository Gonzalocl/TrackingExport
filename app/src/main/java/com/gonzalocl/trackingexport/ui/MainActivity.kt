package com.gonzalocl.trackingexport.ui

import android.content.Intent
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.EditText
import android.widget.Toast
import com.gonzalocl.trackingexport.R
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.PrintWriter
import java.util.stream.Collectors

class MainActivity : AppCompatActivity() {

    private val tracksPath = "Tracks"
    private val tracksExportPath = "TracksExport"
    private val documentsPath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) Environment.DIRECTORY_DOCUMENTS else "Documents"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    fun clickSettings(view: View) {
        val settingsIntent = Intent(this, SettingsActivity::class.java)
        startActivity(settingsIntent)
    }

    fun clickAuto(view: View) {

        val tracksDir = File(Environment.getExternalStoragePublicDirectory(documentsPath), tracksPath)

        val trackFiles = tracksDir.listFiles()

        val carGoing = trackFiles[trackFiles.size-6]
        val tracking = trackFiles[trackFiles.size-4]
        val carReturn = trackFiles[trackFiles.size-2]

        val trackTitle = findViewById<EditText>(R.id.track_title).text.toString()
        val trackDate = carGoing.name.split("_")[0]

        val templateInputStream = resources.openRawResource(R.raw.export_file_template)
        val templateInputStreamReader = InputStreamReader(templateInputStream)
        val templateBufferedReader = BufferedReader(templateInputStreamReader)

        val templateString = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            templateBufferedReader.lines().collect(Collectors.joining("\n"))
        } else {
//            TODO("VERSION.SDK_INT < N")
            Toast.makeText(this, "NOT IMPLEMENTED", Toast.LENGTH_LONG).show()
            return
        }

        templateInputStream.close()
        templateInputStreamReader.close()
        templateBufferedReader.close()

        val tracksExportDir = File(Environment.getExternalStoragePublicDirectory(documentsPath), tracksExportPath)
        tracksExportDir.mkdirs()

        val exportedFile = File(tracksExportDir, "${trackDate}_${trackTitle}.kml")
        if (exportedFile.exists() || exportedFile.isDirectory) {
            Toast.makeText(this, "FILE EXISTS", Toast.LENGTH_LONG).show()
            return
        }

        if (!exportedFile.createNewFile()) {
            Toast.makeText(this, "FAILED TO CREATE FILE", Toast.LENGTH_LONG).show()
            return
        }

        val exportedFilePrintWriter = PrintWriter(exportedFile)
        exportedFilePrintWriter.printf(templateString)

        exportedFilePrintWriter.close()

        Toast.makeText(this, "Completed", Toast.LENGTH_LONG).show()

/*        val shareTrack = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, exportedFile.toUri().toString())
//            putExtra(Intent.EXTRA_STREAM, Uri.parse(exportedFile.toUri().toString()))
//            putExtra(Intent.EXTRA_STREAM, Uri.parse("content://"+exportedFile.absolutePath))
            type = "application/vnd.google-earth.kml+xml"
        }
        startActivity(Intent.createChooser(shareTrack, trackTitle))*/

    }

}
