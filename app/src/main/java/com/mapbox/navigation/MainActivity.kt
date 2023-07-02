package com.mapbox.navigation

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.isVisible
import com.mapbox.Commons
import com.mapbox.navigation.databinding.ActivityMainBinding
import com.mapbox.poi.POISearcher
import com.mapbox.view.ViewHelper


class MainActivity : AppCompatActivity() {

    private lateinit var viewHelper: ViewHelper
    private lateinit var poiSearcher: POISearcher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkGPSPermission()
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        viewHelper = ViewHelper(binding, this)
        poiSearcher = POISearcher(viewHelper)
        viewHelper.poiLayout.apply {
            isVisible = false
        }
        binding.navigationView.api.routeReplayEnabled(true)
        viewHelper.main = this
        //viewHelper.maneuverView = findViewById<MapboxManeuverView>(R.id.maneuverView)
        //viewHelper.addObservers()
        //viewHelper.maneuverView.updateManeuverViewOptions()


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

    fun updateDirectionCriteria(view: View){
        //viewHelper.toggleCriteriaButtons(false)
        Commons.updateDirectionCriteria(viewHelper)
        Commons.isochrone(viewHelper, null)
    }

    /*fun updateMins(view: View){
        viewHelper.toggleCriteriaButtons(false)
        //Commons.updateMins(viewHelper)
        //Commons.isochroneSearch(viewHelper)
    }*/

    fun clearHistory(view: View){
        Commons.clearHistory(viewHelper)
    }

    fun searchRestaurant(view: View){
        categorySearch(viewHelper.appContext.getString(com.mapbox.navigation.R.string.restaurant), view)
    }

    fun searchCafe(view: View){
        categorySearch(viewHelper.appContext.getString(com.mapbox.navigation.R.string.cafe), view)
    }

    fun searchConvenience(view: View){
        categorySearch(viewHelper.appContext.getString(com.mapbox.navigation.R.string.convenience), view)
    }

    private fun categorySearch(searchString: String, view: View){
        Commons.isochrone(viewHelper, poiSearcher.searchCategoryPoi(viewHelper, searchString))
        backToMap(view)
    }

    fun backToMap(view: View){
        viewHelper.poiLayout.apply {
            isVisible = false
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        Commons.setDestinationHistory(null)
        viewHelper.removeAllSearchedLocations()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        //menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        /*return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }*/
        return false
    }

    override fun onSupportNavigateUp(): Boolean {
        /*val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()*/
        return true
    }
}