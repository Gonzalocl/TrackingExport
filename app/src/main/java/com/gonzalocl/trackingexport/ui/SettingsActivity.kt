package com.gonzalocl.trackingexport.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity
import com.gonzalocl.trackingexport.R
import com.gonzalocl.trackingexport.app.TrackingExport

class SettingsActivity: AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<EditText>(R.id.time_interval).setText("%d".format(TrackingExport.timeInterval/60/1000))
        findViewById<EditText>(R.id.filter_accuracy_threshold).setText("${TrackingExport.filterAccuracyThreshold}")
        findViewById<Switch>(R.id.filter_accuracy).isChecked = TrackingExport.filterAccuracy

        findViewById<EditText>(R.id.time_interval).addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s.toString().equals(""))
                    TrackingExport.timeInterval = 0
                else
                    TrackingExport.timeInterval = s.toString().toLong()*60*1000
            }
        })

        findViewById<EditText>(R.id.filter_accuracy_threshold).addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s.toString().equals(""))
                    TrackingExport.filterAccuracyThreshold = 0
                else
                    TrackingExport.filterAccuracyThreshold = s.toString().toLong()
            }
        })

        findViewById<Switch>(R.id.filter_accuracy).setOnCheckedChangeListener(object : CompoundButton.OnCheckedChangeListener {
            override fun onCheckedChanged(buttonView: CompoundButton?, isChecked: Boolean) {
                TrackingExport.filterAccuracy = isChecked
            }
        })



    }


}