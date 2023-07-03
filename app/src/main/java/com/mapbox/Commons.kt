package com.mapbox

import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.geojson.Point
import com.mapbox.navigation.R
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.extensions.applyLanguageAndVoiceUnitOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.NavigationRouterCallback
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.base.route.RouterOrigin
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.view.ViewHelper
import kotlinx.coroutines.DelicateCoroutinesApi
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

@OptIn(DelicateCoroutinesApi::class)
object Commons {
    fun getJsonFromFile(viewHelper: ViewHelper, fileName: String): String {
        lateinit var json: String
        try {
            json = viewHelper.appContext.assets.open(fileName).bufferedReader().use { it.readText() }
        } catch (ioException: IOException){
            println(ioException)
        }
        return json
    }

    private const val optimizationUrl = "https://api.mapbox.com/optimized-trips/v2"
    suspend fun makeOptimizationRequest(viewHelper: ViewHelper, optimizationFeatures: JSONArray) {
        val accessToken = viewHelper.appContext.getString(R.string.mapbox_access_token)
        val body = JSONObject(getJsonFromFile(viewHelper, "optimization-body.json"))
        val locations = body.getJSONArray("locations")
        val services = body.getJSONArray("services")
        for (i in 0 until optimizationFeatures.length()){
            val feature = optimizationFeatures.getJSONObject(i)
            val location = JSONObject()
            val comment = feature.getJSONObject("properties").getString("comment")
            location.put("name", comment)
            location.put("coordinates", feature.getJSONObject("geometry").get("coordinates"))
            locations.put(location)
            if(comment == "スタート" || comment == "エンド") continue

            val service = JSONObject()
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
                    val json = responseBody?.let { JSONObject(it) }
                    if(json?.getString("status") == "ok"){
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
                val json = responseBody?.let { JSONObject(it) }
                if(json?.has("status") == true && json.getString("status") == "processing"){
                    GlobalScope.launch {
                        delay(100)
                        processOptimizationResult(viewHelper, id)
                    }
                }else{
                    makeDirectionsRequest(viewHelper, json?.getJSONArray("routes")?.getJSONObject(0)!!
                        .getJSONArray("stops"))
                }

            } else {
                // Handle the error
                println("Request failed: ${response.code} ${response.message}")
            }
        }
    }

    private fun makeDirectionsRequest(viewHelper: ViewHelper, stops: JSONArray){
        val coordinatesList = ArrayList<Point>()
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
                .applyDefaultNavigationOptions(DirectionsCriteria.PROFILE_DRIVING)
                .applyLanguageAndVoiceUnitOptions(viewHelper.appContext)
                .waypointTargetsList(coordinatesList.toImmutableList())
                .coordinatesList(coordinatesList)
                .steps(true)
                .waypointNames(wayNames)
                .alternatives(true)
                .build(),
            callback = object : NavigationRouterCallback {
                override fun onCanceled(routeOptions: RouteOptions, routerOrigin: RouterOrigin) {
                }

                override fun onFailure(reasons: List<RouterFailure>, routeOptions: RouteOptions) {
                    println("Request failed: $reasons")
                }

                override fun onRoutesReady(
                    routes: List<NavigationRoute>,
                    routerOrigin: RouterOrigin
                ) {
                    viewHelper.navigationView.api.startRoutePreview(routes)
                }
            }
        )

    }

}
