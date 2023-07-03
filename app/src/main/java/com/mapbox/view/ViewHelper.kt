package com.mapbox.view

import android.content.Context
import android.graphics.Color
import android.widget.Toast
import com.mapbox.Commons
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.easeTo
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PolygonAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createCircleAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createPolygonAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createPolylineAnnotationManager
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.navigation.MainActivity
import com.mapbox.navigation.base.formatter.DistanceFormatterOptions
import com.mapbox.navigation.core.formatter.MapboxDistanceFormatter
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.databinding.ActivityMainBinding
import com.mapbox.navigation.dropin.R
import com.mapbox.navigation.dropin.actionbutton.ActionButtonDescription
import com.mapbox.navigation.dropin.map.MapViewBinder
import com.mapbox.navigation.dropin.map.MapViewObserver
import com.mapbox.navigation.ui.base.view.MapboxExtendableButton
import com.mapbox.navigation.ui.maneuver.api.MapboxManeuverApi
import com.mapbox.navigation.ui.maneuver.model.ManeuverExitOptions
import com.mapbox.navigation.ui.maneuver.model.ManeuverPrimaryOptions
import com.mapbox.navigation.ui.maneuver.model.ManeuverSecondaryOptions
import com.mapbox.navigation.ui.maneuver.model.ManeuverSubOptions
import com.mapbox.navigation.ui.maneuver.model.ManeuverViewOptions
import com.mapbox.navigation.ui.maneuver.model.MapboxExitProperties
import com.mapbox.navigation.ui.maneuver.view.MapboxManeuverView
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject


class ViewHelper(binding: ActivityMainBinding, val appContext: Context) : MapViewObserver() {

    var mapView: MapView? = null
    val poiLayout = binding.poiLayout
    val navigationView = binding.navigationView

    var polygonAnnotationManager : PolygonAnnotationManager? = null
    var pointAnnotationManager : PointAnnotationManager? = null
    var polylineAnnotationManager : PolylineAnnotationManager? = null
    var circleAnnotationManager : CircleAnnotationManager? = null

    init {
        navigationView.registerMapObserver(this)
        customize()
    }

    override fun onAttached(mapView: MapView) {
        super.onAttached(mapView)
        this@ViewHelper.mapView = mapView
        this@ViewHelper.polygonAnnotationManager = mapView.annotations.createPolygonAnnotationManager(null)
        this@ViewHelper.pointAnnotationManager = mapView.annotations.createPointAnnotationManager(null)
        this@ViewHelper.polylineAnnotationManager = mapView.annotations.createPolylineAnnotationManager(null)
        this@ViewHelper.circleAnnotationManager = mapView.annotations.createCircleAnnotationManager(null)
    }

    override fun onDetached(mapView: MapView) {
        super.onDetached(mapView)
        this@ViewHelper.mapView = null
    }

    private fun customize(){
        customizeNavigationView()
        addObservers()
    }

    lateinit var main : MainActivity
    private fun addObservers(){
        val distanceFormatter = DistanceFormatterOptions.Builder(appContext).build()
        val maneuverApi = MapboxManeuverApi(MapboxDistanceFormatter(distanceFormatter))
        val routeProgressObserver =
            RouteProgressObserver { routeProgress ->
                val maneuvers = maneuverApi.getManeuvers(routeProgress)
                val maneuverView = main.findViewById<MapboxManeuverView>(R.id.maneuverView)
                if(maneuverView != null) {
                    if (maneuvers.value?.get(0)?.primary.toString().contains("中継点")) {
                        maneuverView.updateManeuverViewOptions(getManeuverViewOptionsSpecial())
                    }else{
                        maneuverView.updateManeuverViewOptions(getManeuverViewOptionsRegular())
                    }
                }
            }
        MapboxNavigationApp.current()?.registerRouteProgressObserver(routeProgressObserver)
    }

    private fun customizeNavigationView(){
        navigationView.customizeViewOptions {
            mapStyleUriDay = "mapbox://styles/kenji-shima/clbq4qj5m000c15s1fm2ayr8r"
            mapStyleUriNight = mapStyleUriDay
        }
        val buttonsList = ArrayList<ActionButtonDescription>()
        buttonsList.add(ActionButtonDescription(
            MapboxExtendableButton(context = this.appContext).apply {
                this.setState(
                    MapboxExtendableButton.State(com.mapbox.navigation.R.drawable.baseline_not_listed_location_24)
                )
                setOnClickListener {
                    addRandomPoints()
                }
            }
        ))
        navigationView.customizeViewBinders {
            customActionButtons = buttonsList
            mapViewBinder = MapBinder()
        }

        navigationView.customizeViewStyles {
        }
    }

    private fun getManeuverViewOptionsSpecial() : ManeuverViewOptions{
        return ManeuverViewOptions
            .Builder()
            .maneuverBackgroundColor(R.color.colorError)
            .subManeuverBackgroundColor(R.color.colorPrimaryVariant)
            .upcomingManeuverBackgroundColor(R.color.colorPrimaryVariant)
            .turnIconManeuver(R.style.DropInStyleTurnIconManeuver)
            .laneGuidanceTurnIconManeuver(R.style.DropInStyleTurnIconManeuver)
            .stepDistanceTextAppearance(R.style.DropInStyleStepDistance)
            .primaryManeuverOptions(
                ManeuverPrimaryOptions
                    .Builder()
                    .textAppearance(R.style.DropInStylePrimaryManeuver)
                    .exitOptions(
                        ManeuverExitOptions
                            .Builder()
                            .textAppearance(R.style.DropInStyleExitPrimary)
                            .mutcdExitProperties(defaultMutcdProperties())
                            .viennaExitProperties(defaultViennaProperties())
                            .build()
                    )
                    .build()
            )
            .secondaryManeuverOptions(
                ManeuverSecondaryOptions
                    .Builder()
                    .textAppearance(R.style.DropInStyleSecondaryManeuver)
                    .exitOptions(
                        ManeuverExitOptions
                            .Builder()
                            .textAppearance(R.style.DropInStyleExitSecondary)
                            .mutcdExitProperties(defaultMutcdProperties())
                            .viennaExitProperties(defaultViennaProperties())
                            .build()
                    )
                    .build()
            )
            .subManeuverOptions(
                ManeuverSubOptions
                    .Builder()
                    .textAppearance(R.style.DropInStyleSubManeuver)
                    .exitOptions(
                        ManeuverExitOptions
                            .Builder()
                            .textAppearance(R.style.DropInStyleExitSub)
                            .mutcdExitProperties(defaultMutcdProperties())
                            .viennaExitProperties(defaultViennaProperties())
                            .build()
                    )
                    .build()
            )
            .build()
    }

    private fun getManeuverViewOptionsRegular() : ManeuverViewOptions{
        return ManeuverViewOptions
            .Builder()
            .maneuverBackgroundColor(R.color.colorPrimary)
            .subManeuverBackgroundColor(R.color.colorPrimaryVariant)
            .upcomingManeuverBackgroundColor(R.color.colorPrimaryVariant)
            .turnIconManeuver(R.style.DropInStyleTurnIconManeuver)
            .laneGuidanceTurnIconManeuver(R.style.DropInStyleTurnIconManeuver)
            .stepDistanceTextAppearance(R.style.DropInStyleStepDistance)
            .primaryManeuverOptions(
                ManeuverPrimaryOptions
                    .Builder()
                    .textAppearance(R.style.DropInStylePrimaryManeuver)
                    .exitOptions(
                        ManeuverExitOptions
                            .Builder()
                            .textAppearance(R.style.DropInStyleExitPrimary)
                            .mutcdExitProperties(defaultMutcdProperties())
                            .viennaExitProperties(defaultViennaProperties())
                            .build()
                    )
                    .build()
            )
            .secondaryManeuverOptions(
                ManeuverSecondaryOptions
                    .Builder()
                    .textAppearance(R.style.DropInStyleSecondaryManeuver)
                    .exitOptions(
                        ManeuverExitOptions
                            .Builder()
                            .textAppearance(R.style.DropInStyleExitSecondary)
                            .mutcdExitProperties(defaultMutcdProperties())
                            .viennaExitProperties(defaultViennaProperties())
                            .build()
                    )
                    .build()
            )
            .subManeuverOptions(
                ManeuverSubOptions
                    .Builder()
                    .textAppearance(R.style.DropInStyleSubManeuver)
                    .exitOptions(
                        ManeuverExitOptions
                            .Builder()
                            .textAppearance(R.style.DropInStyleExitSub)
                            .mutcdExitProperties(defaultMutcdProperties())
                            .viennaExitProperties(defaultViennaProperties())
                            .build()
                    )
                    .build()
            )
            .build()
    }

    private fun defaultMutcdProperties() = MapboxExitProperties.PropertiesMutcd(
        exitBackground = R.drawable.mapbox_dropin_exit_board_background,
        fallbackDrawable = R.drawable.mapbox_dropin_ic_exit_arrow_right_mutcd,
        exitRightDrawable = R.drawable.mapbox_dropin_ic_exit_arrow_right_mutcd,
        exitLeftDrawable = R.drawable.mapbox_dropin_ic_exit_arrow_left_mutcd
    )
    private fun defaultViennaProperties() = MapboxExitProperties.PropertiesVienna(
        exitBackground = R.drawable.mapbox_dropin_exit_board_background,
        fallbackDrawable = R.drawable.mapbox_dropin_ic_exit_arrow_right_vienna,
        exitRightDrawable = R.drawable.mapbox_dropin_ic_exit_arrow_right_vienna,
        exitLeftDrawable = R.drawable.mapbox_dropin_ic_exit_arrow_left_vienna
    )

    internal class MapBinder : MapViewBinder(){
        override val shouldLoadMapStyle: Boolean = true
        override fun getMapView(context: Context): MapView {
            val mapView = MapView(context).apply {
                compass.enabled = false
            }
            return mapView
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun addRandomPoints(){
        circleAnnotationManager?.deleteAll()
        val optimizationFeatures: JSONArray = JSONObject(Commons.getJsonFromFile(this, "optimization-points-base.geojson")).getJSONArray("features")
        val randomJson = Commons.getJsonFromFile(this, "random-points.geojson")
        val features = JSONObject(randomJson).getJSONArray("features")
        val locationOptionsList = arrayListOf<CircleAnnotationOptions>()
        val pointList = arrayListOf<Point>()
        for (i in 0 until features.length()) {
            val element = features.getJSONObject(i)
            val properties = element.getJSONObject("properties")
            properties.put("type", "waypoint")
            properties.put("comment", "中継点$i")
            optimizationFeatures.put(element)

            val coordinates = element.getJSONObject("geometry").getJSONArray("coordinates")
            val point = Point.fromLngLat(coordinates.getDouble(0), coordinates.getDouble(1))
            pointList.add(point)
            val options = CircleAnnotationOptions()
                .withCircleColor(Color.RED)
                .withCircleRadius(6.0)
                .withGeometry(point)
            locationOptionsList.add(options)
            if(i > 400) break
        }
        circleAnnotationManager?.create(locationOptionsList)
        cameraToFitPoints(pointList)

        val message = optimizationFeatures.length().toString()+"の点からルート計算開始。処理に数十秒掛かります。"
        Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()

        GlobalScope.launch {
            Commons.makeOptimizationRequest(this@ViewHelper, optimizationFeatures)
        }
    }

    private fun cameraToFitPoints(list : List<Point>){
        if(list.isEmpty()) return
        val camera = mapView?.getMapboxMap()?.cameraForCoordinates(list) as CameraOptions
        mapView?.getMapboxMap()?.easeTo(camera,
            MapAnimationOptions.Builder().duration(1000).build()
        )
    }

}