package com.mapbox.poi

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import com.google.gson.JsonObject
import com.mapbox.Commons
import com.mapbox.geojson.Point
import com.mapbox.maps.plugin.annotation.generated.*

import com.mapbox.navigation.R
import com.mapbox.search.*
import com.mapbox.search.common.IsoCountryCode
import com.mapbox.search.common.IsoLanguageCode
import com.mapbox.search.offline.OfflineResponseInfo
import com.mapbox.search.offline.OfflineSearchEngine
import com.mapbox.search.offline.OfflineSearchEngineSettings
import com.mapbox.search.offline.OfflineSearchResult
import com.mapbox.search.record.HistoryRecord
import com.mapbox.search.result.SearchResult
import com.mapbox.search.result.SearchSuggestion
import com.mapbox.search.ui.adapter.engines.SearchEngineUiAdapter
import com.mapbox.view.ViewHelper


class POISearcher(val viewHelper: ViewHelper) : SearchEngineUiAdapter.SearchListener,
    SearchView.OnQueryTextListener, SearchSelectionCallback, SearchCallback {

    private val searchEngine = SearchEngine.createSearchEngineWithBuiltInDataProviders(
        //private val searchEngine = SearchEngine.createSearchEngine(
        //apiType = ApiType.GEOCODING,
        apiType = ApiType.SBS,
        settings = SearchEngineSettings(viewHelper.appContext.getString(R.string.mapbox_access_token))
    )
    private val offlineSearchEngine = OfflineSearchEngine.create(
        OfflineSearchEngineSettings(viewHelper.appContext.getString(R.string.mapbox_access_token))
    )
    private val searchEngineUiAdapter = SearchEngineUiAdapter(
        view = viewHelper.searchResultsView,
        searchEngine = searchEngine,
        offlineSearchEngine = offlineSearchEngine,
    )

    init {
        searchEngineUiAdapter.addSearchListener(this)
        viewHelper.poiLayout.findViewById<SearchView>(R.id.poi_search).setOnQueryTextListener(this)
    }

    override fun onSuggestionsShown(
        suggestions: List<SearchSuggestion>,
        responseInfo: ResponseInfo
    ) {
        // Nothing to do
    }

    override fun onCategoryResultsShown(
        suggestion: SearchSuggestion,
        results: List<SearchResult>,
        responseInfo: ResponseInfo
    ) {

    }

    override fun onOfflineSearchResultsShown(
        results: List<OfflineSearchResult>,
        responseInfo: OfflineResponseInfo
    ) {
        closeSearchView()
    }

    override fun onSuggestionSelected(searchSuggestion: SearchSuggestion): Boolean {
        this.searchEngine.select(searchSuggestion, this)
        return false
    }

    override fun onSearchResultSelected(searchResult: SearchResult, responseInfo: ResponseInfo) {
        closeSearchView()
        //searchPlaceView.open(SearchPlace.createFromSearchResult(searchResult, responseInfo))
        ///mapMarkersManager.showMarker(searchResult.coordinate)
    }

    override fun onOfflineSearchResultSelected(
        searchResult: OfflineSearchResult,
        responseInfo: OfflineResponseInfo
    ) {
        closeSearchView()
        //searchPlaceView.open(SearchPlace.createFromOfflineSearchResult(searchResult))
        //mapMarkersManager.showMarker(searchResult.coordinate)
    }

    override fun onError(e: Exception) {
        val message = viewHelper.appContext.getString(R.string.error_occurred)
        Toast.makeText(viewHelper.appContext, "$message: $e", Toast.LENGTH_LONG).show()
    }

    override fun onHistoryItemClick(historyRecord: HistoryRecord) {
        Commons.requestRoutes(historyRecord.coordinate, viewHelper)
        closeSearchView()
    }

    override fun onPopulateQueryClick(suggestion: SearchSuggestion, responseInfo: ResponseInfo) {
        /*if (::searchView.isInitialized) {
            searchView.setQuery(suggestion.name, true)
        }*/
    }

    override fun onFeedbackItemClick(responseInfo: ResponseInfo) {
        // Not implemented
    }

    private fun closeSearchView() {
        viewHelper.poiLayout.apply {
            isVisible = false
        }
    }

    override fun onSuggestions(suggestions: List<SearchSuggestion>, responseInfo: ResponseInfo) {

    }

    override fun onResult(
        suggestion: SearchSuggestion,
        result: SearchResult,
        responseInfo: ResponseInfo
    ) {
        Commons.requestRoutes(result.coordinate, viewHelper)
    }

    override fun onCategoryResult(
        suggestion: SearchSuggestion,
        results: List<SearchResult>,
        responseInfo: ResponseInfo
    ) {
        //Log.i("SearchApiExample", "Category search results: $results")
    }

    override fun onQueryTextSubmit(query: String): Boolean {
        return false
    }

    private val types = arrayListOf(QueryType.POI)

    @SuppressLint("MissingPermission")
    override fun onQueryTextChange(newText: String): Boolean {

        //val location = viewHelper.getLastKnownLocation()
        //val point = Point.fromLngLat(location?.longitude!!, location.latitude)
        val point = viewHelper.mapView?.getMapboxMap()?.cameraState?.center

        searchEngineUiAdapter.search(
            newText,
            SearchOptions(
                countries = listOf(IsoCountryCode(viewHelper.appContext.getString(R.string.country_code))),
                languages = listOf(IsoLanguageCode(viewHelper.appContext.getString(R.string.language_code))),
                origin = point,
                proximity = point,
                //boundingBox = viewHelper.searchBoundingBox,
                limit = 10,
                types = this.types,
                fuzzyMatch = true
            )
        )
        return false
    }

    override fun onResults(results: List<SearchResult>, responseInfo: ResponseInfo) {
        Toast.makeText(viewHelper.appContext, "${results.size} ${viewHelper.appContext.getString(R.string.results)}", Toast.LENGTH_SHORT).show()
        val fixer = CoordinateFixer()
        val pointList = arrayListOf<Point>()
        Commons.bitmapFromDrawableRes(
            viewHelper.appContext,
            R.drawable.ic_baseline_place_blue_32
        )?.let {
            var index = 0
            results.forEach { r ->
                pointList.add(Point.fromLngLat(r.coordinate.longitude(),r.coordinate.latitude()))
                val fixedCoordinates = fixer.fix(r.coordinate)
                val data = JsonObject()
                data.addProperty("name", r.name)
                data.addProperty("address", r.fullAddress)
                data.addProperty("category", r.categories?.get(r.categories?.size?.minus(1) ?: 0))
                data.addProperty("distance", String.format("%.2f", r.distanceMeters))
                data.addProperty("index", index)
                val option = PointAnnotationOptions()
                    .withGeometry(fixedCoordinates)
                    .withIconImage(it)
                    .withData(data)
                index++
                viewHelper.locationOptionsList.add(option)
            }

            viewHelper.locationAnnotationList = (viewHelper.pointAnnotationManager?.create(viewHelper.locationOptionsList) as ArrayList<PointAnnotation>?)!!
            viewHelper.pointAnnotationManager?.addClickListener(OnPointAnnotationClickListener { annotation ->
                val data = annotation.getData() as JsonObject
                viewHelper.recyclerView.layoutManager?.scrollToPosition(data.get("index").asInt)
                true
            })

            viewHelper.cameraToFitPoints(pointList)
        }

    }

    fun searchCategoryPoi(viewHelper: ViewHelper, category: String) {
        viewHelper.removeAllSearchedLocations()
        viewHelper.navigationView.api.startFreeDrive()

        val searchEngine = SearchEngine.createSearchEngine(
            apiType = ApiType.SBS,
            settings = SearchEngineSettings(viewHelper.appContext.getString(R.string.mapbox_access_token))
        )
        //val location = viewHelper.getLastKnownLocation()
        //val point = Point.fromLngLat(location?.longitude!!, location.latitude)
        val point = viewHelper.mapView?.getMapboxMap()?.cameraState?.center
        searchEngine.search(
            category,
            CategorySearchOptions(
                countries = listOf(IsoCountryCode(viewHelper.appContext.getString(R.string.country_code))),
                languages = listOf(IsoLanguageCode(viewHelper.appContext.getString(R.string.language_code))),
                origin = point,
                proximity = point,
                //boundingBox = viewHelper.searchBoundingBox,
                limit = 100,
                fuzzyMatch = false
            ),
            this
        )

    }

    internal class CoordinateFixer(){
        private val OFFSET = 1e-5
        var fixerMap = HashMap<String, Int>()

        fun fix(point: Point) : Point{
            val key = "${point.longitude()}_${point.latitude()}"
            var count = 0
            if(fixerMap.containsKey(key)) count = fixerMap[key]!!
            count++
            fixerMap.put(key,count)

            val fix = (count-1) * OFFSET

            return Point.fromLngLat(point.longitude() + fix, point.latitude() + fix)
        }
    }

    /*private class MapMarkersManager(mapView: MapView) {

        private val mapboxMap = mapView.getMapboxMap()
        private val circleAnnotationManager =
            mapView.annotations.createCircleAnnotationManager(null)
        private val markers = mutableMapOf<Long, Point>()

        fun clearMarkers() {
            markers.clear()
            circleAnnotationManager.deleteAll()
        }

        fun showMarker(coordinate: Point) {
            clearMarkers()

            val circleAnnotationOptions: CircleAnnotationOptions = CircleAnnotationOptions()
                .withPoint(coordinate)
                .withCircleRadius(8.0)
                .withCircleColor("#ee4e8b")
                .withCircleStrokeWidth(2.0)
                .withCircleStrokeColor("#ffffff")

            val annotation = circleAnnotationManager.create(circleAnnotationOptions)
            markers[annotation.id] = coordinate

            CameraOptions.Builder()
                .center(coordinate)
                //.padding(MARKERS_INSETS_OPEN_CARD)
                .zoom(14.0)
                .build().also {
                    mapboxMap.setCamera(it)
                }
        }
    }*/

}