package com.mapbox

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.api.isochrone.IsochroneCriteria
import com.mapbox.api.isochrone.MapboxIsochrone
import com.mapbox.api.optimization.v1.MapboxOptimization
import com.mapbox.api.optimization.v1.models.OptimizationResponse
import com.mapbox.geojson.*
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions
import com.mapbox.navigation.R
import com.mapbox.navigation.base.ExperimentalPreviewMapboxNavigationAPI
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.extensions.applyLanguageAndVoiceUnitOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.NavigationRouterCallback
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.base.route.RouterOrigin
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.search.ServiceProvider
import com.mapbox.search.common.CompletionCallback
import com.mapbox.turf.TurfMeasurement
import com.mapbox.view.ViewHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.internal.toImmutableList
import okio.IOException
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

object Commons {
    private var lastDestination : Point? = null

    fun setDestinationHistory(destination: Point?){
        this.lastDestination = destination
    }

    fun getDestinationHistory(): Point? {
        return this.lastDestination
    }

    fun get3DStyle(viewHelper: ViewHelper): String {
        lateinit var json: String
        try {
            json = viewHelper.appContext.assets.open("dynamic-ja.json").bufferedReader().use { it.readText() }
        } catch (ioException: IOException){
            println(ioException)
        }
        return json
    }

    fun getJsonFromFile(viewHelper: ViewHelper, fileName: String): String {
        lateinit var json: String
        try {
            json = viewHelper.appContext.assets.open(fileName).bufferedReader().use { it.readText() }
        } catch (ioException: IOException){
            println(ioException)
        }
        return json
    }

    fun updateDirectionCriteria(viewHelper: ViewHelper){
        viewHelper.mapView?.getMapboxMap()?.loadStyleJson(Commons.get3DStyle(viewHelper))
        changeDirectionsCriteria(viewHelper)
        /*viewHelper.directionCriteriaButton.setImageDrawable(
            ContextCompat.getDrawable(viewHelper.appContext, getDirectionsCriteriaIcon()))
        viewHelper.directionCriteriaButtonInSearch.setImageDrawable(
            ContextCompat.getDrawable(viewHelper.appContext, getDirectionsCriteriaIcon()))*/
        getDestinationHistory()?.let { requestRoutes(it, viewHelper) }
    }

    fun requestRoutes(destination: Point, viewHelper: ViewHelper) {
        val currentLocation = viewHelper.getLastKnownLocation()
        val origin = Point.fromLngLat(currentLocation?.longitude!!, currentLocation?.latitude!!)
        MapboxNavigationApp.current()!!.requestRoutes(
            routeOptions = RouteOptions
                .builder()
                .applyDefaultNavigationOptions(dcList[dcIndex])
                .applyLanguageAndVoiceUnitOptions(viewHelper.appContext)
                .coordinatesList(listOf(origin, destination))
                .alternatives(true)
                .build(),
            callback = object : NavigationRouterCallback {
                override fun onCanceled(routeOptions: RouteOptions, routerOrigin: RouterOrigin) {
                    setDestinationHistory(null)
                }

                override fun onFailure(reasons: List<RouterFailure>, routeOptions: RouteOptions) {
                    setDestinationHistory(null)
                }

                override fun onRoutesReady(
                    routes: List<NavigationRoute>,
                    routerOrigin: RouterOrigin
                ) {
                    viewHelper.navigationView.api.startRoutePreview(routes)
                    setDestinationHistory(destination)
                }
            }
        )
    }

    fun requestRouteFromPoints(viewHelper: ViewHelper){
        val points = getJsonFromFile(viewHelper, "fixed_route.geojson")
        val json = JSONObject(points)
        val features = json.getJSONArray("features")
        var waylist = ArrayList<Point>()
        var wayNames = ""
        for (i in 0 until features.length()) {
            val element = features.getJSONObject(i)
            val geometry = element.getString("geometry")
            val point = Point.fromJson(geometry)
            val properties = element.getJSONObject("properties")
            val comment = properties.getString("comment")

            waylist.add(point)

            if(wayNames != ""){
                wayNames += ";"
            }
            wayNames += comment
        }

        MapboxNavigationApp.current()!!.requestRoutes(
            routeOptions = RouteOptions
                .builder()
                .applyDefaultNavigationOptions(dcList[dcIndex])
                .applyLanguageAndVoiceUnitOptions(viewHelper.appContext)
                .waypointTargetsList(waylist.toImmutableList())
                .coordinatesList(waylist)
                .steps(true)
                .waypointNames(wayNames)
                .alternatives(true)
                .build(),
            callback = object : NavigationRouterCallback {
                override fun onCanceled(routeOptions: RouteOptions, routerOrigin: RouterOrigin) {
                    setDestinationHistory(null)
                }

                override fun onFailure(reasons: List<RouterFailure>, routeOptions: RouteOptions) {
                    println(reasons)
                }

                override fun onRoutesReady(
                    routes: List<NavigationRoute>,
                    routerOrigin: RouterOrigin
                ) {
                    viewHelper.navigationView.api.startRoutePreview(routes)
                    //setDestinationHistory(destination)
                }
            }
        )
    }

    val optimizationUrl = "https://api.mapbox.com/optimized-trips/v2"
    suspend fun makeOptimizationRequest(viewHelper: ViewHelper, optimizationFeatures: JSONArray) {
        val accessToken = viewHelper.appContext.getString(R.string.mapbox_access_token)
        var body = JSONObject(getJsonFromFile(viewHelper, "optimization-body.json"))
        var locations = body.getJSONArray("locations")
        var services = body.getJSONArray("services")
        for (i in 0 until optimizationFeatures.length()){
            var feature = optimizationFeatures.getJSONObject(i)
            var location = JSONObject()
            var comment = feature.getJSONObject("properties").getString("comment")
            location.put("name", comment)
            location.put("coordinates", feature.getJSONObject("geometry").get("coordinates"))
            locations.put(location)
            if(comment == "スタート" || comment == "エンド") continue

            var service = JSONObject()
            service.put("name", comment)
            service.put("location", comment)
            services.put(service)
        }

        val url = "$optimizationUrl?access_token=$accessToken&destination=last"
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            .build()
        withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    // Process the response
                    val json = JSONObject(responseBody)
                    if(json.getString("status") == "ok"){
                        processOptimizationResult(viewHelper, json.getString("id"))
                    }
                } else {
                    // Handle the error
                    println("Request failed: ${response.code} ${response.message}")
                }
            }
        }
    }

    private fun processOptimizationResult(viewHelper: ViewHelper, id: String){
        val accessToken = viewHelper.appContext.getString(R.string.mapbox_access_token)
        val url = "$optimizationUrl/$id?access_token=$accessToken"
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(url)
            .build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                val json = JSONObject(responseBody)
                if(json.has("status") && json.getString("status") == "processing"){
                    GlobalScope.launch {
                        delay(100)
                        processOptimizationResult(viewHelper, id)
                    }
                }else{
                    makeDirectionsRequest(viewHelper, json.getJSONArray("routes").getJSONObject(0).getJSONArray("stops"))
                }

            } else {
                // Handle the error
                println("Request failed: ${response.code} ${response.message}")
            }
        }
    }

    private fun makeDirectionsRequest(viewHelper: ViewHelper, stops: JSONArray){
        var coordinatesList = ArrayList<Point>()
        var wayNames = ""
        for(i in 0 until stops.length()){
            val elem = stops.getJSONObject(i)
            val locationMetadata = elem.getJSONObject("location_metadata")
            val snapped = locationMetadata.getJSONArray("snapped_coordinate")
            val point = Point.fromLngLat(snapped.getDouble(0), snapped.getDouble(1))
            val location = elem.getString("location")
            coordinatesList.add(point)
            if(wayNames != "") wayNames+=";"
            wayNames+=location
        }

        MapboxNavigationApp.current()!!.requestRoutes(
            routeOptions = RouteOptions
                .builder()
                .applyDefaultNavigationOptions(dcList[dcIndex])
                .applyLanguageAndVoiceUnitOptions(viewHelper.appContext)
                .waypointTargetsList(coordinatesList.toImmutableList())
                .coordinatesList(coordinatesList)
                .steps(true)
                .waypointNames(wayNames)
                .alternatives(true)
                .build(),
            callback = object : NavigationRouterCallback {
                override fun onCanceled(routeOptions: RouteOptions, routerOrigin: RouterOrigin) {
                    setDestinationHistory(null)
                }

                override fun onFailure(reasons: List<RouterFailure>, routeOptions: RouteOptions) {
                    var list = reasons.get(0).message.split(",")
                    list.forEach { l ->
                        println("@@$l")
                    }

                }

                override fun onRoutesReady(
                    routes: List<NavigationRoute>,
                    routerOrigin: RouterOrigin
                ) {
                    viewHelper.navigationView.api.startRoutePreview(routes)
                    //setDestinationHistory(destination)
                }
            }
        )

    }

    fun requestRouteByOptimization(viewHelper: ViewHelper) {
        val points = getJsonFromFile(viewHelper, "fixed_route.geojson")
        val json = JSONObject(points)
        val features = json.getJSONArray("features")
        lateinit var origin: Point
        lateinit var destination: Point
        var waylist = ArrayList<Point>()
        var coordList = ArrayList<Pair<Double, Double>>()
        var wayNames = ""
        for (i in 0 until features.length()) {
            val element = features.getJSONObject(i)
            val geometry = element.getString("geometry")
            val point = Point.fromJson(geometry)
            val properties = element.getJSONObject("properties")
            val comment = properties.getString("comment")

            /*if(comment.trim() == "スタート"){
                origin = point
            }else if(comment.trim() == "エンド"){
                destination = point
            }else{*/
            waylist.add(point)
            val coordinates = element.getJSONObject("geometry").getJSONArray("coordinates")
            val pair = Pair(coordinates.getDouble(0), coordinates.getDouble(1))
            coordList.add(pair)
            //}

            if (wayNames != "") {
                wayNames += ";"
            }
            wayNames += comment
        }
        waylist.removeLast()

        var reorderList = ArrayList<Point>()
        reorderList.add(waylist.get(5))
        reorderList.add(waylist.get(6))
        reorderList.add(waylist.get(7))
        reorderList.add(waylist.get(8))
        reorderList.add(waylist.get(9))
        reorderList.add(waylist.get(10))
        reorderList.add(waylist.get(11))
        reorderList.add(waylist.get(0))
        reorderList.add(waylist.get(1))
        reorderList.add(waylist.get(2))
        reorderList.add(waylist.get(3))
        reorderList.add(waylist.get(4))
        coordList.removeLast()

        GlobalScope.launch {

            /*makeOptimizationRequest(
                viewHelper.appContext.getString(R.string.mapbox_access_token),
                coordList
            )*/
    }
        return

        val optimizedClient = MapboxOptimization.builder()
            .coordinates(reorderList)
            .steps(true)
            .profile(DirectionsCriteria.PROFILE_DRIVING)
            .accessToken(viewHelper.appContext.getString(R.string.mapbox_access_token))
            .build()

        optimizedClient?.enqueueCall(object : Callback<OptimizationResponse> {
            override fun onResponse(call: Call<OptimizationResponse>, response: Response<OptimizationResponse>) {
                if (!response.isSuccessful) {
                    println("@@@@@@@Unsuccessful"+response)
                    return
                } else {
                    if (response.body()!!.trips()!!.isEmpty()) {
                        println("@@@@@Empty")
                        return
                    }
                }

                val optimizedRoute = response.body()!!.trips()!![0]
                println("@@@@@@"+ optimizedRoute)
                var list = optimizedRoute.toString().split(",")
                list.forEach { l ->
                    println("9999@@@@@$l")
                }

                MapboxNavigationApp.current()!!.requestRoutes(
                    optimizedRoute.routeOptions()!!,
                    callback = object : NavigationRouterCallback {
                        override fun onCanceled(routeOptions: RouteOptions, routerOrigin: RouterOrigin) {
                            setDestinationHistory(null)
                        }

                        override fun onFailure(reasons: List<RouterFailure>, routeOptions: RouteOptions) {
                            var list = reasons.get(0).message.split(",")
                            list.forEach { l ->
                                println("@@@@@$l${reasons.size}")
                            }
                        }

                        override fun onRoutesReady(
                            routes: List<NavigationRoute>,
                            routerOrigin: RouterOrigin
                        ) {
                            viewHelper.navigationView.api.startRoutePreview(routes)
                            //setDestinationHistory(destination)
                        }
                    }
                )

                //viewHelper.navigationView.api.startRoutePreview(listOf(optimizedRoute.toNavigationRoute(RouterOrigin.Offboard)))

            }

            override fun onFailure(call: Call<OptimizationResponse>, throwable: Throwable) {
                println("@@@Error: " + throwable.message)
            }
        })


    }

    fun requestRouteFromLine(viewHelper: ViewHelper){
        val points = getJsonFromFile(viewHelper, "fixed_route_line.geojson")
        val json = JSONObject(points)
        val features = json.getJSONArray("features")
        var origin = Point.fromLngLat( 139.76602,35.68206)
        var destination = Point.fromLngLat(139.76578,35.682100000000005)
        var waylist = ArrayList<Point>()
        var wayNames = ""
        lateinit var geometry : String
        for (i in 0 until features.length()) {
            val element = features.getJSONObject(i)
            geometry = element.getString("geometry")
            /*val coordinates = geometry.getJSONArray("coordinates")
            for (j in 0 until coordinates.length()) {
                val coord = coordinates.getString(j)
                val coordArray = JSONArray(coord)
                val point = Point.fromLngLat(coordArray.getDouble(0), coordArray.getDouble(1))
                waylist.add(point)
                if(wayNames != ""){
                    wayNames += ";"
                }
                wayNames += "通過点$j"
                if(j>20) break
            }*/
            val properties = element.getJSONObject("properties")
            val comment = properties.getString("comment")


        }

        //var routeOptions = RouteOptions.fromJson(geometry)
        println("@@@"+geometry)

        MapboxNavigationApp.current()!!.requestRoutes(
            routeOptions = RouteOptions
                .builder()
                .applyDefaultNavigationOptions(dcList[dcIndex])
                .applyLanguageAndVoiceUnitOptions(viewHelper.appContext)
                //.waypointTargetsList(waylist.toImmutableList())
                .coordinatesList(listOf(origin,destination))
                .steps(true)
                //.geometries(geometry)
                //.waypointNames(wayNames)
                .alternatives(true)
                .build(),
            callback = object : NavigationRouterCallback {
                override fun onCanceled(routeOptions: RouteOptions, routerOrigin: RouterOrigin) {
                    setDestinationHistory(null)
                }

                override fun onFailure(reasons: List<RouterFailure>, routeOptions: RouteOptions) {
                    var list = reasons.get(0).message.split(",")
                    list.forEach { l ->
                        println("@@@@@"+l)
                    }

                }

                override fun onRoutesReady(
                    routes: List<NavigationRoute>,
                    routerOrigin: RouterOrigin
                ) {
                    viewHelper.navigationView.api.startRoutePreview(routes)
                    //setDestinationHistory(destination)
                }
            }
        )
    }

    fun clearHistory(viewHelper: ViewHelper){
        ServiceProvider.INSTANCE.historyDataProvider().clear(object: CompletionCallback<Unit> {
            override fun onComplete(result: Unit) {
                val message = viewHelper.appContext.getString(R.string.history_removed)
                Toast.makeText(viewHelper.appContext, "$message", Toast.LENGTH_SHORT).show()
            }
            override fun onError(e: Exception) {
                val message = viewHelper.appContext.getString(R.string.error_occurred)
                Toast.makeText(viewHelper.appContext, "$message: $e", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private val dcList = arrayListOf(DirectionsCriteria.PROFILE_DRIVING, DirectionsCriteria.PROFILE_CYCLING, DirectionsCriteria.PROFILE_WALKING)
    private var dcIndex = 0
    private fun changeDirectionsCriteria(viewHelper: ViewHelper){
        if(dcIndex >= dcList.size-1) dcIndex = 0 else dcIndex++
        val next = dcList[dcIndex]

        viewHelper.navigationView.setRouteOptionsInterceptor { defaultBuilder ->
            defaultBuilder.layers(null)
                .applyDefaultNavigationOptions(next)
        }
    }

    private fun getDirectionsCriteriaIcon(): Int {
        return if (DirectionsCriteria.PROFILE_DRIVING == dcList[dcIndex]){
            R.drawable.ic_baseline_directions_car_24
        }else if (DirectionsCriteria.PROFILE_CYCLING == dcList[dcIndex]){
            R.drawable.ic_baseline_directions_bike_24
        }else{
            R.drawable.ic_baseline_directions_walk_24
        }
    }

    private fun getIsochroneProfile() : String{
        return if (DirectionsCriteria.PROFILE_DRIVING == dcList[dcIndex]){
            IsochroneCriteria.PROFILE_DRIVING
        }else if (DirectionsCriteria.PROFILE_CYCLING == dcList[dcIndex]){
            IsochroneCriteria.PROFILE_CYCLING
        }else{
            IsochroneCriteria.PROFILE_WALKING
        }
    }

    private val minsList = arrayListOf(5,10,30)

    @OptIn(ExperimentalPreviewMapboxNavigationAPI::class)
    fun isochrone(viewHelper: ViewHelper, callback: Unit?){
        //viewHelper.toggleCriteriaButtons(false)
        viewHelper.polylineAnnotationManager?.deleteAll()

        val location = viewHelper.getLastKnownLocation()
        if(location == null) return
        val point = Point.fromLngLat(location?.getLongitude()!!, location?.getLatitude()!!)

        val mapboxIsochroneRequest = MapboxIsochrone.builder()
            .accessToken(viewHelper.appContext.getString(R.string.mapbox_access_token))
            .profile(getIsochroneProfile())
            .addContoursMinutes(minsList[0], minsList[1], minsList[2])
            .polygons(false)
            .addContoursColors("800080","0000FF","00FFFF")
            .generalize(2f)
            .denoise(.4f)
            .coordinates(point)
            .build()

        mapboxIsochroneRequest.enqueueCall(object : Callback<FeatureCollection> {
            override fun onResponse(call: Call<FeatureCollection>, response: Response<FeatureCollection>) {
                //val pointsList : ArrayList<List<Point>> = arrayListOf()
                //val polygonList = arrayListOf<Polygon>()
                //val optionBuilder = PolygonAnnotationOptions()
                val optionBuilder = PolylineAnnotationOptions
                response.body()?.features()?.forEach { f ->
                    //val polygon = f.geometry() as Polygon
                    val option = optionBuilder.fromFeature(f)
                    option?.lineColor = f.properties()?.get("fillColor")?.asString
                    option?.lineWidth = 2.0
                    option?.lineOpacity = 0.6
                    //val option = optionBuilder.withFillOutlineColor(f.properties()?.get("fillColor")?.asString!!)
                    //val option = optionBuilder.withGeometry(polygon)
                    //option.fillColor = f.properties()?.get("fillColor")?.asString
                    ////option.fillOpacity = 0.2
                    //optionsList.add(option)
                    //polygonList.add(polygon)
                    viewHelper.polylineAnnotationManager?.create(option!!)
                    //viewHelper.polygonAnnotationManager?.create(option)
                    /*val bbox = TurfMeasurement.bbox(f.geometry() as LineString)
                    val points = listOf<Point>(Point.fromLngLat(bbox[0],bbox[1]), Point.fromLngLat(bbox[2], bbox[3]))
                    pointsList.add(points)*/

                }
                viewHelper.setDefaultBoundingBox(TurfMeasurement.bbox(response.body()))
                //viewHelper.polygonAnnotationManager?.create(optionsList)

                //getDirectionsPolygon()?.let { polygonList.add(it) }
                //val multiPolygon = MultiPolygon.fromPolygons(polygonList)
                /*if(mins == minsList[minsList.size-1]){
                    viewHelper.cameraToFitPolygons(multiPolygon)
                }*/

                //viewHelper.toggleCriteriaButtons(true)

            }
            override fun onFailure(call: Call<FeatureCollection>, t: Throwable) {
                Toast.makeText(viewHelper.appContext, "Error happened: $t", Toast.LENGTH_SHORT).show()
                //viewHelper.toggleCriteriaButtons(true)
            }
        })
    }

    fun addAnnotationToMap(point: Point, viewHelper: ViewHelper) {
// Create an instance of the Annotation API and get the PointAnnotationManager.
        bitmapFromDrawableRes(
            viewHelper.appContext,
            //com.mapbox.navigation.dropin.R.drawable.mapbox_ic_destination_marker
        R.drawable.ic_baseline_restaurant_24
        )?.let {
// Set options for the resulting symbol layer.
            val pointAnnotationOptions: PointAnnotationOptions = PointAnnotationOptions()
// Define a geographic coordinate.
                .withPoint(point)
// Specify the bitmap you assigned to the point annotation
// The bitmap will be added to map style automatically.
                .withIconImage(it)
// Add the resulting pointAnnotation to the map.
            viewHelper.pointAnnotationManager?.create(pointAnnotationOptions)
        }
    }

    fun bitmapFromDrawableRes(context: Context, @DrawableRes resourceId: Int) =
        convertDrawableToBitmap(AppCompatResources.getDrawable(context, resourceId))



    private fun convertDrawableToBitmap(sourceDrawable: Drawable?): Bitmap? {

        if (sourceDrawable == null) {
            return null
        }
        return if (sourceDrawable is BitmapDrawable) {
            sourceDrawable.bitmap
        } else {
// copying drawable object to not manipulate on the same reference
            val constantState = sourceDrawable.constantState ?: return null
            val drawable = constantState.newDrawable().mutate()
            val bitmap: Bitmap = Bitmap.createBitmap(
                drawable.intrinsicWidth, drawable.intrinsicHeight,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        }
    }
}
