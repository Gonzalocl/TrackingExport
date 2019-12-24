package com.gonzalocl.trackingexport.ui

import android.content.Intent
import android.icu.util.GregorianCalendar
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.gonzalocl.trackingexport.R
import com.gonzalocl.trackingexport.app.TrackingExport
import java.io.*
import java.util.*
import java.util.stream.Collectors
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {


    private val EARTH_EQUATORIAL_CIRCUMFERENCE: Double = 40075017.0
    private val EARTH_MERIDIONAL_CIRCUMFERENCE: Double = 40007860.0

    private val tracksPath = "Tracks"
    private val tracksExportPath = "TracksExport"
    private val documentsPath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) Environment.DIRECTORY_DOCUMENTS else "Documents"
    private var placemarkTemplate = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<EditText>(R.id.track_title).addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                TrackingExport.currentTrackTitle = s.toString()
            }

        })

        val placemarkTemplateInputStream = resources.openRawResource(R.raw.template_placemark)
        val placemarkTemplateInputStreamReader = InputStreamReader(placemarkTemplateInputStream)
        val placemarkTemplateBufferedReader = BufferedReader(placemarkTemplateInputStreamReader)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            placemarkTemplate = placemarkTemplateBufferedReader.lines().collect(Collectors.joining("\n"))
        } else {
//            TODO("VERSION.SDK_INT < N")
            Toast.makeText(this, "NOT IMPLEMENTED", Toast.LENGTH_LONG).show()
            return
        }

        placemarkTemplateInputStream.close()
        placemarkTemplateInputStreamReader.close()
        placemarkTemplateBufferedReader.close()

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

        val trackTitle = TrackingExport.currentTrackTitle
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

        /*
        * global title
        * global description
        *
        * track title
        * track description
        * tracks coordinates
        *
        * timestamp placemarks
        *
        * carGoing description
        * carGoing coordinates
        *
        * carReturn description
        * carReturn coordinates
        */

        val trackingCoordinates = StringBuffer()
        val timestampPlacemarks = StringBuffer()
        val carGoingCoordinates = StringBuffer()
        val carReturnCoordinates = StringBuffer()


        val interval = TrackingExport.timeInterval
        val filter = TrackingExport.filterAccuracy
        val threshold = TrackingExport.filterAccuracyThreshold


        val trackingFileReader = FileReader(tracking)
        val trackingBufferedReader = BufferedReader(trackingFileReader)


        trackingBufferedReader.readLine()
        var row = trackingBufferedReader.readLine()
        var rowSplit = row.split(",")
        val trackStartTime = rowSplit[4].toLong()
        var nextPlacemark = trackStartTime + interval
        var totalDistance: Double = 0.0


        var latitude: Double = 0.0
        var longitude: Double = 0.0
        var timestamp: Long = 0

        var lastLatitude: Double = 0.0
        var lastLongitude: Double = 0.0
        var lastTimestamp: Long = 0

        var ok = false
        while (row != null && !ok) {
            rowSplit = row.split(",")
            if (!(filter && rowSplit[3].toDouble() > threshold)) {
                lastLatitude = rowSplit[0].toDouble()
                lastLongitude = rowSplit[1].toDouble()
                lastTimestamp = rowSplit[4].toLong()
                ok = true
            }
            row = trackingBufferedReader.readLine()
        }

        if (!ok) {
            Toast.makeText(this, "All points filtered stopping", Toast.LENGTH_LONG).show()
            trackingFileReader.close()
            trackingBufferedReader.close()
            exportedFile.delete()
            return
        }

        // copy coordinates
        trackingCoordinates.append("$lastLongitude,$lastLatitude,0\n")

        // set first placemark
        timestampPlacemarks.append(getPlacemarkString(lastLongitude, lastLatitude, trackStartTime, 0) + "\n")

        while (row != null) {
            rowSplit = row.split(",")
            timestamp = rowSplit[4].toLong()
            if (!(filter && rowSplit[3].toDouble() > threshold)) {
                latitude = rowSplit[0].toDouble()
                longitude = rowSplit[1].toDouble()

                // copy coordinates
                trackingCoordinates.append("$longitude,$latitude,0\n")

                // compute distance
                totalDistance += computeDistance(lastLongitude, lastLatitude, longitude, latitude)

                // set placemark
                if (timestamp > nextPlacemark) {
                    timestampPlacemarks.append(getPlacemarkString(longitude, latitude, timestamp, timestamp-trackStartTime) + "\n")
                    nextPlacemark += interval
                }

                // check timestamp order

                lastLatitude = latitude
                lastLongitude = longitude
                lastTimestamp = timestamp
            }
            row = trackingBufferedReader.readLine()
        }

        trackingFileReader.close()
        trackingBufferedReader.close()

        // set last placemark
        timestampPlacemarks.append(getPlacemarkString(longitude, latitude, timestamp, timestamp-trackStartTime) + "\n")











        // global title
        val trackDateSplit = trackDate.split("-")
        val globalTitle = "${trackDateSplit[2]}/${trackDateSplit[1]}/${trackDateSplit[0][2]}${trackDateSplit[0][3]} $trackTitle"






        val exportedFilePrintWriter = PrintWriter(exportedFile)
        exportedFilePrintWriter.printf(templateString,
            globalTitle,
            "",
            trackTitle,
            "",
            trackingCoordinates,
            timestampPlacemarks,
            "",
            carGoingCoordinates,
            "",
            carReturnCoordinates)

        exportedFilePrintWriter.close()

        if (TrackingExport.filterAccuracy) {
            Toast.makeText(this, "FILTERED: ${TrackingExport.filterAccuracyThreshold}", Toast.LENGTH_LONG).show()
        }

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

    private fun getPlacemarkString(longitude: Double, latitude: Double, absoluteTime: Long, relativeTime: Long): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {

            val absoluteCalendar = GregorianCalendar.getInstance()
            absoluteCalendar.time = Date(absoluteTime)

            val absoluteHours = absoluteCalendar.get(Calendar.HOUR_OF_DAY)
            val absoluteMinutes = absoluteCalendar.get(Calendar.MINUTE).toString().padStart(2, '0')

            val relativeHours: Long = relativeTime/1000/60/60
            val relativeMinutes = (relativeTime/1000/60 % 60).toString().padStart(2, '0')

            val name = "$absoluteHours:$absoluteMinutes / $relativeHours:$relativeMinutes"
            val description = "Tiempo absoluto: $absoluteHours:$absoluteMinutes<br>Tiempo relativo: $relativeHours:$relativeMinutes"
            val coordinates = "$longitude,$latitude,0"

            return String.format(placemarkTemplate, name, description, coordinates)
        } else {
//            TODO("VERSION.SDK_INT < N")
            Toast.makeText(this, "NOT IMPLEMENTED", Toast.LENGTH_LONG).show()
            return ""
        }
    }

    private fun computeDistance(lastLongitude: Double, lastLatitude: Double, longitude: Double, latitude: Double): Double {
        return sqrt(
            ((latitude - lastLatitude) * (EARTH_EQUATORIAL_CIRCUMFERENCE / 360.0)).pow(2.0) +
                    ((longitude - lastLongitude) * (cos(((latitude + lastLatitude) / 2.0) * Math.PI / 180.0)) *
                            (EARTH_MERIDIONAL_CIRCUMFERENCE / 360.0)).pow(2.0)
        )
    }

}
