package com.mapbox.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.gson.JsonObject
import com.mapbox.Commons
import com.mapbox.geojson.BoundingBox
import com.mapbox.geojson.MultiPolygon
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.easeTo
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.*
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.plugin.gestures.OnMapLongClickListener
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
import com.mapbox.search.ui.view.CommonSearchViewConfiguration
import com.mapbox.search.ui.view.DistanceUnitType
import com.mapbox.search.ui.view.SearchResultsView
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject


class ViewHelper(private val binding: ActivityMainBinding, val appContext: Context) : MapViewObserver() {

    var mapView: MapView? = null
    //lateinit var maneuverView : MapboxManeuverView
    val poiLayout = binding.poiLayout
    private val searchView: SearchView = poiLayout.findViewById<LinearLayout>(com.mapbox.navigation.R.id.search_view_layout).findViewById(com.mapbox.navigation.R.id.poi_search)
    val searchResultsView: SearchResultsView = poiLayout.findViewById(com.mapbox.navigation.R.id.poi_search_results_view)
    val navigationView = binding.navigationView
    //val directionCriteriaButton: FloatingActionButton = navigationView.findViewById(com.mapbox.navigation.R.id.direction_criteria)
    //val directionCriteriaButtonInSearch: FloatingActionButton = poiLayout.findViewById<LinearLayout>(com.mapbox.navigation.R.id.search_buttons_layout).findViewById(com.mapbox.navigation.R.id.direction_criteria)

    private val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val bestLocationProvider = (locationManager.getBestProvider(Criteria(), true)).toString()

    var polygonAnnotationManager : PolygonAnnotationManager? = null
    var pointAnnotationManager : PointAnnotationManager? = null
    var polylineAnnotationManager : PolylineAnnotationManager? = null
    var circleAnnotationManager : CircleAnnotationManager? = null

    var searchBoundingBox: BoundingBox? = null

    private val redMarker = Commons.bitmapFromDrawableRes(
        appContext,
        com.mapbox.navigation.R.drawable.ic_baseline_place_red_32)

    lateinit var recyclerView : RecyclerView
    private lateinit var locationRecyclerViewAdapter : LocationRecyclerViewAdapter

    init {
        navigationView.registerMapObserver(this)
        searchView.queryHint = appContext.getString(com.mapbox.navigation.R.string.search_hint)
        initRecylcerView()
        customize()
        //Commons.isochrone(this, null)
    }

    /*fun toggleCriteriaButtons(boolean: Boolean){
        toggleFloatingButton(directionCriteriaButton, boolean)
        toggleFloatingButton(directionCriteriaButtonInSearch, boolean)

    }*/

    private fun toggleFloatingButton(floatingActionButton: FloatingActionButton, boolean: Boolean){
        floatingActionButton.isClickable = boolean
        floatingActionButton.isEnabled = boolean
    }

    override fun onAttached(mapView: MapView) {
        super.onAttached(mapView)
        //mapView?.getMapboxMap()?.loadStyleJson(Commons.get3DStyle(this))
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
        customizeSearchResultsView()
        addObservers()
    }

    lateinit var main : MainActivity
    fun addObservers(){
         /*val sessionObserver =
             NavigationSessionStateObserver { navigationSession ->

                 if(navigationSession is NavigationSessionState.FreeDrive) {
                     toggleCriteriaButtons(true)
                 }else{
                     toggleCriteriaButtons(false)
                 }
             }

        MapboxNavigationApp.current()?.registerNavigationSessionStateObserver(sessionObserver)*/
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
                    //Commons.requestRouteFromPoints(this@ViewHelper)
                    //Commons.requestRouteFromLine(this@ViewHelper)
                    //Commons.requestRouteByOptimization(this@ViewHelper)
                    addRandomPoints()
                }
            }
        ))
        navigationView.customizeViewBinders {
            customActionButtons = buttonsList
            mapViewBinder = MapBinder(this@ViewHelper)
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

    internal class MapBinder(val viewHelper: ViewHelper) : MapViewBinder(){
        private val setDestinationListener = OnMapLongClickListener { point ->
            Commons.setDestinationHistory(point)
            false
        }

        override val shouldLoadMapStyle: Boolean = true

        override fun getMapView(context: Context): MapView {
            val mapView = MapView(context).apply {
                compass.enabled = false
            }
            /*mapView.getMapboxMap()?.loadStyleJson(Commons.get3DStyle(viewHelper))
            mapView.gestures?.addOnMapLongClickListener(setDestinationListener)*/
            return mapView
        }
    }

    private fun customizeSearchResultsView(){
        searchResultsView.apply {
            initialize(
                SearchResultsView.Configuration(CommonSearchViewConfiguration(DistanceUnitType.METRIC))
            )
        }
    }

    private fun addRandomPoints(){
        circleAnnotationManager?.deleteAll()
        val optimizationFeatures: JSONArray = JSONObject(Commons.getJsonFromFile(this, "optimization-points-base.geojson")).getJSONArray("features")
        val randomJson = Commons.getJsonFromFile(this, "random-points.geojson")
        val features = JSONObject(randomJson).getJSONArray("features")
        var locationOptionsList = arrayListOf<CircleAnnotationOptions>()
        var pointList = arrayListOf<Point>()
        for (i in 0 until features.length()) {
            val element = features.getJSONObject(i)
            val properties = element.getJSONObject("properties")
            properties.put("type", "waypoint")
            properties.put("comment", "中継点$i")
            optimizationFeatures.put(element)

            val coordinates = element.getJSONObject("geometry").getJSONArray("coordinates")
            var point = Point.fromLngLat(coordinates.getDouble(0), coordinates.getDouble(1))
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
        Toast.makeText(appContext, "$message", Toast.LENGTH_SHORT).show()

        GlobalScope.launch {
            Commons.makeOptimizationRequest(this@ViewHelper, optimizationFeatures)
        }
    }

    @SuppressLint("MissingPermission")
    fun getLastKnownLocation(): Location? {
        return locationManager.getLastKnownLocation(bestLocationProvider)
    }

    fun setDefaultBoundingBox(coords: DoubleArray){
        searchBoundingBox = BoundingBox.fromLngLats(coords[0], coords[1], coords[2], coords[3])
    }

    fun removeAllSearchedLocations(){
        pointAnnotationManager?.deleteAll()
        previousLocationIndex = null
        previousFocusedAnnotation = null
        pointAnnotationManager?.deleteAll()
        locationOptionsList.clear()
        locationAnnotationList.clear()
        locationRecyclerViewAdapter.notifyDataSetChanged()
        recyclerView.removeAllViewsInLayout()
    }

    fun initRecylcerView(){
        recyclerView = binding.rvOnTopOfMap
        recyclerView.layoutManager = (LinearLayoutManager(appContext, RecyclerView.HORIZONTAL, true))
        recyclerView.itemAnimator = DefaultItemAnimator()
        locationRecyclerViewAdapter = LocationRecyclerViewAdapter(this)
        recyclerView.adapter = locationRecyclerViewAdapter
        val snapHelper = LinearSnapHelper()
        snapHelper.attachToRecyclerView(recyclerView)
        recyclerView.addOnScrollListener(RecyclerViewScrollListener(this))
    }

    internal class RecyclerViewScrollListener(val viewHelper: ViewHelper) : RecyclerView.OnScrollListener(){
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)
            val layoutManager = recyclerView.layoutManager as LinearLayoutManager
            val position = layoutManager.findFirstVisibleItemPosition()
            if(viewHelper.previousLocationIndex == position) return

            viewHelper.removePreviousSelectedLocation()
            viewHelper.replaceRegularLocationWithFocusedLocation(position)
            viewHelper.cameraToFocusedLocation(position)
        }
    }

    var locationOptionsList = arrayListOf<PointAnnotationOptions>()
    var locationAnnotationList = arrayListOf<PointAnnotation>()

    var previousLocationIndex : Int? = null
    var previousFocusedAnnotation : PointAnnotation? = null

    fun removePreviousSelectedLocation(){
        previousFocusedAnnotation?.let {
            pointAnnotationManager?.delete(it)
        }
        previousLocationIndex?.let {locationAnnotationList[it] =
            pointAnnotationManager?.create( locationOptionsList.get(it))!!
        }
    }

    fun replaceRegularLocationWithFocusedLocation(position: Int){
        pointAnnotationManager?.delete(locationAnnotationList[position])
        val annotationOption = locationOptionsList[position]
        val option = PointAnnotationOptions()
            .withGeometry(annotationOption.getGeometry()!!)
            .withIconImage(
                redMarker!!
            )
            .withData(annotationOption.getData()!!)

        previousFocusedAnnotation = pointAnnotationManager!!.create(option)
        previousLocationIndex = position
    }

    fun cameraToFocusedLocation(position: Int){
        val camera = CameraOptions.Builder()
            .center(locationOptionsList[position].getPoint())
            .build()
        mapView?.getMapboxMap()?.easeTo(camera,
            MapAnimationOptions.Builder().duration(1000).build()
        )
    }

    fun cameraToFitPolygons(multiPolygon: MultiPolygon){
        val camera = mapView?.getMapboxMap()?.cameraForGeometry(multiPolygon) as CameraOptions
        //setBoundingBox(multiPolygon)
        mapView?.getMapboxMap()?.easeTo(camera,
            MapAnimationOptions.Builder().duration(1000).build()
        )
    }

    fun cameraToFitPoints(list : List<Point>){
        if(list.isEmpty()) return
        val camera = mapView?.getMapboxMap()?.cameraForCoordinates(list) as CameraOptions
        mapView?.getMapboxMap()?.easeTo(camera,
            MapAnimationOptions.Builder().duration(1000).build()
        )
    }

    internal class LocationRecyclerViewAdapter(val viewHelper: ViewHelper) : RecyclerView.Adapter<LocationViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LocationViewHolder {
            val itemView = LayoutInflater.from(parent.context).inflate(com.mapbox.navigation.R.layout.rv_on_top_of_map_card, parent,false)
            return LocationViewHolder(itemView, viewHelper)
        }

        override fun onBindViewHolder(holder: LocationViewHolder, position: Int) {
            val annotationOption = viewHelper.locationOptionsList[position]
            val item = annotationOption.getData() as JsonObject
            var category = item.get("category").toString().replace("\"", "")
            if(category.contains(">")) {
                category = category.substring(category.lastIndexOf(">")+1)
                category = "($category)"
            }else{
                category = ""
            }
            holder.index = position
            holder.name.text =  "$category ${item.get("name").toString().replace("\"", "")}"
            holder.address.text = item.get("address").toString().replace("\"", "")
            holder.distance.text = "${item.get("distance").toString().replace("\"", "")} m"
        }

        override fun getItemCount(): Int {
            return viewHelper.locationOptionsList.size
        }
    }

   // internal class LocationViewHolder(view: View,  var viewHelper: ViewHelper) : RecyclerView.ViewHolder(view), View.OnClickListener {
   internal class LocationViewHolder(view: View,  var viewHelper: ViewHelper) : RecyclerView.ViewHolder(view) {
       var index : Int? = null
       var name: TextView = view.findViewById(com.mapbox.navigation.R.id.location_name)
       var address: TextView = view.findViewById(com.mapbox.navigation.R.id.location_address)
       var distance: TextView = view.findViewById(com.mapbox.navigation.R.id.location_distance)
       var route : MapboxExtendableButton = view.findViewById(com.mapbox.navigation.R.id.location_route)
       init{
           route.setState(MapboxExtendableButton.State(com.mapbox.navigation.dropin.R.drawable.mapbox_ic_route))
           route.setOnClickListener(object: View.OnClickListener{
               override fun onClick(v: View?) {
                   val options = viewHelper.locationOptionsList[index!!]
                   options.getPoint()?.let { Commons.requestRoutes(it,viewHelper) }
                   viewHelper.removeAllSearchedLocations()
               }

           })
       }
    }
}