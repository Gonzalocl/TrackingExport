package com.gonzalocl.trackingexport.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.icu.util.GregorianCalendar
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.Toast
import android.Manifest
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.gonzalocl.trackingexport.R
import com.gonzalocl.trackingexport.app.TrackingExport
import java.io.*
import java.text.SimpleDateFormat
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

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
            Toast.makeText(this, "NO STORAGE PERMISSION", Toast.LENGTH_LONG).show()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Toast.makeText(this, "NO STORAGE MANAGER PERMISSION", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                return
            }
        }

        val tracksDir = File(Environment.getExternalStoragePublicDirectory(documentsPath), tracksPath)

        val trackFiles = tracksDir.listFiles()
        Arrays.sort(trackFiles)

        if (trackFiles == null || trackFiles.size < 6) {
            Toast.makeText(this, "NO FILES FOUND", Toast.LENGTH_LONG).show()
            return
        }

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
        *
        * tracks coordinates original
        * carGoing coordinates original
        * carReturn coordinates original
        */

        val trackingCoordinates = StringBuffer()
        val timestampPlacemarks = StringBuffer()
        val carGoingCoordinates = StringBuffer()
        val carReturnCoordinates = StringBuffer()

        val trackingCoordinatesOriginal = StringBuffer()
        val carGoingCoordinatesOriginal = StringBuffer()
        val carReturnCoordinatesOriginal = StringBuffer()


        val interval = TrackingExport.timeInterval
        val filter = TrackingExport.filterAccuracy
        val threshold = TrackingExport.filterAccuracyThreshold


        val trackingFileReader = FileReader(tracking)
        val trackingBufferedReader = BufferedReader(trackingFileReader)


        trackingBufferedReader.readLine()
        var row = trackingBufferedReader.readLine()
        var rowSplit = row.split(",")
        val trackStartTime = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).parse(tracking.name.split(".")[0]).time
        var nextPlacemark = trackStartTime + interval
        var totalDistance = 0.0
        var filtered = 0


        var latitude = 0.0
        var longitude = 0.0
        var timestamp: Long = 0

        var lastLatitude = 0.0
        var lastLongitude = 0.0
        var lastTimestamp: Long = 0

        var ok = false
        while (row != null && !ok) {
            rowSplit = row.split(",")
            lastLatitude = rowSplit[0].toDouble()
            lastLongitude = rowSplit[1].toDouble()
            timestamp = rowSplit[4].toLong()

            // copy coordinates original
            trackingCoordinatesOriginal.append("$lastLongitude,$lastLatitude,0\n")

            // check timestamp order
            if (lastTimestamp > timestamp) {
                Toast.makeText(this, "TIMESTAMP ORDER INVERTED DETECTED", Toast.LENGTH_LONG).show()
            }

            lastTimestamp = timestamp

            if (!(filter && rowSplit[3].toDouble() > threshold)) {
                ok = true
            } else {
                filtered++
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
            latitude = rowSplit[0].toDouble()
            longitude = rowSplit[1].toDouble()
            timestamp = rowSplit[4].toLong()

            // copy coordinates original
            trackingCoordinatesOriginal.append("$longitude,$latitude,0\n")

            // check timestamp order
            if (lastTimestamp > timestamp) {
                Toast.makeText(this, "TIMESTAMP ORDER INVERTED DETECTED", Toast.LENGTH_LONG).show()
            }

            lastTimestamp = timestamp

            if (!(filter && rowSplit[3].toDouble() > threshold)) {

                // copy coordinates
                trackingCoordinates.append("$longitude,$latitude,0\n")

                // compute distance
                totalDistance += computeDistance(lastLongitude, lastLatitude, longitude, latitude)

                // set placemark
                if (timestamp > nextPlacemark) {
                    timestampPlacemarks.append(getPlacemarkString(longitude, latitude, timestamp, timestamp-trackStartTime) + "\n")
                    nextPlacemark += interval
                }

                lastLatitude = latitude
                lastLongitude = longitude
            } else {
                filtered++
            }
            row = trackingBufferedReader.readLine()
        }

        trackingFileReader.close()
        trackingBufferedReader.close()

        // set last placemark
        timestampPlacemarks.append(getPlacemarkString(longitude, latitude, timestamp, timestamp-trackStartTime) + "\n")


        // compute car tracks
        val carGoingDescription = StringBuffer()
        computeCarTrack(carGoing, carGoingDescription, carGoingCoordinates, carGoingCoordinatesOriginal)

        val carReturnDescription = StringBuffer()
        computeCarTrack(carReturn, carReturnDescription, carReturnCoordinates, carReturnCoordinatesOriginal)


        // global title
        val trackDateSplit = trackDate.split("-")
        val trackDateString = "${trackDateSplit[2]}/${trackDateSplit[1]}/${trackDateSplit[0][2]}${trackDateSplit[0][3]}"
        val globalTitle = "$trackDateString $trackTitle"

        // track description
        val trackTime = lastTimestamp - trackStartTime
        val trackHours: Long = trackTime/1000/60/60
        val trackMinutes = (trackTime/1000/60 % 60).toString().padStart(2, '0')
        val totalDistanceString = "%.2f".format(totalDistance/1000)

        val globalDescription = "$trackTitle<br>${trackHours}h${trackMinutes}min, $totalDistanceString km"
        val trackDescription = StringBuffer()
        trackDescription.append("$globalDescription<br>")
        trackDescription.append("Salida: %02d:%02d<br>".format(timestampGetHours(trackStartTime), timestampGetMinutes(trackStartTime)))
        trackDescription.append("Llegada: %02d:%02d<br>".format(timestampGetHours(lastTimestamp), timestampGetMinutes(lastTimestamp)))
        trackDescription.append("Tiempo: %d:%02d<br>".format(trackTime/1000/60/60, trackTime/1000/60 % 60))
        trackDescription.append("Distancia: %.2f km<br>".format(totalDistance/1000))
        trackDescription.append("%s\tMonte\t%02d:%02d:%02d\t%02d:%02d:%02d\t.\t%d\t.\t%s".format(
            trackDateString,
            timestampGetHours(trackStartTime),
            timestampGetMinutes(trackStartTime),
            timestampGetSeconds(trackStartTime),
            timestampGetHours(lastTimestamp),
            timestampGetMinutes(lastTimestamp),
            timestampGetSeconds(lastTimestamp),
            totalDistance.toInt(),
            trackTitle
        ))


        val exportedFilePrintWriter = PrintWriter(exportedFile)
        exportedFilePrintWriter.printf(templateString,
            globalTitle,
            globalDescription,
            trackTitle,
            trackDescription,
            trackingCoordinates,
            timestampPlacemarks,
            carGoingDescription,
            carGoingCoordinates,
            carReturnDescription,
            carReturnCoordinates,
            trackingCoordinatesOriginal,
            carGoingCoordinatesOriginal,
            carReturnCoordinatesOriginal)

        exportedFilePrintWriter.close()

        if (TrackingExport.filterAccuracy) {
            Toast.makeText(this, "FILTERED : ${TrackingExport.filterAccuracyThreshold} : $filtered", Toast.LENGTH_LONG).show()
        }

        Toast.makeText(this, "Completed", Toast.LENGTH_LONG).show()

        val shareTrackTitle = Intent().apply {
            action = Intent.ACTION_SEND
            `package` = "org.telegram.messenger"
            putExtra(Intent.EXTRA_TEXT, "${trackDateSplit[2]}/${trackDateSplit[1]}/${trackDateSplit[0][2]}${trackDateSplit[0][3]} $trackTitle\n${trackHours}h${trackMinutes}min, $totalDistanceString km")
            type = "text/plain"
        }
        startActivity(shareTrackTitle)

    }

    private fun getPlacemarkString(longitude: Double, latitude: Double, absoluteTime: Long, relativeTime: Long): String {

        val absoluteHours = timestampGetHours(absoluteTime)
        val absoluteMinutes = timestampGetMinutes(absoluteTime)

        val relativeHours: Long = relativeTime/1000/60/60
        val relativeMinutes = relativeTime/1000/60 % 60

        val name = "%02d:%02d / %d:%02d".format(absoluteHours, absoluteMinutes, relativeHours, relativeMinutes)
        val description = "Tiempo absoluto: %02d:%02d<br>Tiempo relativo: %d:%02d".format(absoluteHours, absoluteMinutes, relativeHours, relativeMinutes)
        val coordinates = "$longitude,$latitude,0"

        return placemarkTemplate.format(name, description, coordinates)

    }

    private fun computeDistance(lastLongitude: Double, lastLatitude: Double, longitude: Double, latitude: Double): Double {
        return sqrt(
            ((latitude - lastLatitude) * (EARTH_EQUATORIAL_CIRCUMFERENCE / 360.0)).pow(2.0) +
                    ((longitude - lastLongitude) * (cos(((latitude + lastLatitude) / 2.0) * Math.PI / 180.0)) *
                            (EARTH_MERIDIONAL_CIRCUMFERENCE / 360.0)).pow(2.0)
        )
    }

    private fun computeCarTrack(tracking: File, trackingDescription: StringBuffer, trackingCoordinates: StringBuffer, trackingCoordinatesOriginal: StringBuffer) {
        val filter = TrackingExport.filterAccuracy
        val threshold = TrackingExport.filterAccuracyThreshold


        val trackingFileReader = FileReader(tracking)
        val trackingBufferedReader = BufferedReader(trackingFileReader)


        trackingBufferedReader.readLine()
        var row = trackingBufferedReader.readLine()
        var rowSplit = row.split(",")
        val trackStartTime = rowSplit[4].toLong()
        var totalDistance = 0.0


        var latitude = 0.0
        var longitude = 0.0
        var timestamp: Long = 0

        var lastLatitude = 0.0
        var lastLongitude = 0.0
        var lastTimestamp: Long = 0

        var ok = false
        while (row != null && !ok) {
            rowSplit = row.split(",")
            lastLatitude = rowSplit[0].toDouble()
            lastLongitude = rowSplit[1].toDouble()
            timestamp = rowSplit[4].toLong()

            // copy coordinates original
            trackingCoordinatesOriginal.append("$lastLongitude,$lastLatitude,0\n")

            // check timestamp order
            if (lastTimestamp > timestamp) {
                Toast.makeText(this, "TIMESTAMP ORDER INVERTED DETECTED", Toast.LENGTH_LONG).show()
            }

            lastTimestamp = timestamp

            // TODO count hidden points in distance
            if (!((filter && rowSplit[3].toDouble() > threshold) || computeDistance(lastLongitude, lastLatitude, -0.99578, 37.6038) < 500)) {
                ok = true
            }
            row = trackingBufferedReader.readLine()
        }

        if (!ok) {
            Toast.makeText(this, "All car points filtered stopping", Toast.LENGTH_LONG).show()
            trackingFileReader.close()
            trackingBufferedReader.close()
            return
        }

        // copy coordinates
        trackingCoordinates.append("$lastLongitude,$lastLatitude,0\n")

        // set first placemark
//        timestampPlacemarks.append(getPlacemarkString(lastLongitude, lastLatitude, trackStartTime, 0) + "\n")

        while (row != null) {
            rowSplit = row.split(",")
            latitude = rowSplit[0].toDouble()
            longitude = rowSplit[1].toDouble()
            timestamp = rowSplit[4].toLong()

            // copy coordinates original
            trackingCoordinatesOriginal.append("$longitude,$latitude,0\n")

            // check timestamp order
            if (lastTimestamp > timestamp) {
                Toast.makeText(this, "TIMESTAMP ORDER INVERTED DETECTED", Toast.LENGTH_LONG).show()
            }

            lastTimestamp = timestamp

            // TODO count hidden points in distance
            if (!((filter && rowSplit[3].toDouble() > threshold) || computeDistance(lastLongitude, lastLatitude, -0.99578, 37.6038) < 500)) {

                // copy coordinates
                trackingCoordinates.append("$longitude,$latitude,0\n")

                // compute distance
                totalDistance += computeDistance(lastLongitude, lastLatitude, longitude, latitude)

                lastLatitude = latitude
                lastLongitude = longitude
            }
            row = trackingBufferedReader.readLine()
        }

        trackingFileReader.close()
        trackingBufferedReader.close()

        // set last placemark
//        timestampPlacemarks.append(getPlacemarkString(longitude, latitude, timestamp, timestamp-trackStartTime) + "\n")

        // track description
        val trackTime = lastTimestamp - trackStartTime

        trackingDescription.append("Salida: %02d:%02d<br>".format(timestampGetHours(trackStartTime), timestampGetMinutes(trackStartTime)))
        trackingDescription.append("Llegada: %02d:%02d<br>".format(timestampGetHours(lastTimestamp), timestampGetMinutes(lastTimestamp)))
        trackingDescription.append("Tiempo: %d:%02d<br>".format(trackTime/1000/60/60, trackTime/1000/60 % 60))
        trackingDescription.append("Distancia: %.2f km".format(totalDistance/1000))

    }

    private fun timestampGetHours(timestamp: Long): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val calendar = GregorianCalendar.getInstance()
            calendar.time = Date(timestamp)
            return calendar.get(Calendar.HOUR_OF_DAY)
        } else {
            return 0
        }
    }

    private fun timestampGetMinutes(timestamp: Long): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val calendar = GregorianCalendar.getInstance()
            calendar.time = Date(timestamp)
            return calendar.get(Calendar.MINUTE)
        } else {
            return 0
        }
    }

    private fun timestampGetSeconds(timestamp: Long): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val calendar = GregorianCalendar.getInstance()
            calendar.time = Date(timestamp)
            return calendar.get(Calendar.SECOND)
        } else {
            return 0
        }
    }

}
