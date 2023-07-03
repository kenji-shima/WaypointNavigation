package com.mapbox.navigation

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.isVisible
import com.mapbox.navigation.databinding.ActivityMainBinding
import com.mapbox.view.ViewHelper


class MainActivity : AppCompatActivity() {

    private lateinit var viewHelper: ViewHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkGPSPermission()
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        viewHelper = ViewHelper(binding, this)
        viewHelper.poiLayout.apply {
            isVisible = false
        }
        binding.navigationView.api.routeReplayEnabled(true)
        viewHelper.main = this
    }

    private fun checkGPSPermission() {
        if (ActivityCompat.checkSelfPermission(
                this@MainActivity,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this@MainActivity,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                )
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                    1
                )
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                    1
                )
            }
        }
    }

    fun backToMap(view: View){
        viewHelper.poiLayout.apply {
            isVisible = false
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return false
    }

    override fun onSupportNavigateUp(): Boolean {
        return true
    }
}