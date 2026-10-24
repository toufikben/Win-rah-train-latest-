package com.example.feature.map

import android.app.Application
import android.graphics.Color as AColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewModelScope
import com.example.WinRahApplication
import com.example.data.Line
import com.example.data.Station
import com.example.data.remote.dto.LiveTrainDto
import com.example.design.LineColors
import com.example.design.LiveDot
import com.example.design.Wr
import com.example.design.WrType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

data class MapState(val lines: List<Line> = emptyList(), val stations: List<Station> = emptyList(), val trains: List<LiveTrainDto> = emptyList())

class MapViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as WinRahApplication).container
    val state = MutableStateFlow(MapState())

    init {
        viewModelScope.launch {
            while (isActive) {
                runCatching {
                    c.network.loadReference()
                    state.value = MapState(c.network.lines.value.filter { it.active }, c.network.stations.value, c.network.liveTrains())
                }
                delay(10_000)
            }
        }
    }

    fun station(id: String) = c.network.station(id)
}

/** Free OpenStreetMap vector style; no key, no tracking. */
private const val STYLE_LIGHT = "https://tiles.openfreemap.org/styles/positron"
private const val STYLE_DARK = "https://tiles.openfreemap.org/styles/dark"

@Composable
fun MapScreen(vm: MapViewModel, onOpenStation: (String) -> Unit) {
    val s by vm.state.collectAsState()
    val context = LocalContext.current
    val dark = Wr.colors.isDark
    val mapRef = remember { mutableStateOf<MapLibreMap?>(null) }
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs); mapView.onDestroy() }
    }
    Box(Modifier.fillMaxSize()) {
        AndroidView({ mapView }, Modifier.fillMaxSize()) { v ->
            if (mapRef.value == null) v.getMapAsync { map ->
                map.cameraPosition = CameraPosition.Builder().target(LatLng(36.70, 3.10)).zoom(9.6).build()
                map.uiSettings.isRotateGesturesEnabled = false
                map.setStyle(Style.Builder().fromUri(if (dark) STYLE_DARK else STYLE_LIGHT)) { style ->
                    setup(style)
                    update(style, vm.state.value)
                }
                map.addOnMapClickListener { p ->
                    val pt = map.projection.toScreenLocation(p)
                    map.queryRenderedFeatures(pt, "wr-stations").firstOrNull()?.getStringProperty("id")?.let { onOpenStation(it); true } ?: false
                }
                mapRef.value = map
            }
        }
        LaunchedEffect(s, mapRef.value) {
            mapRef.value?.getStyle { style -> update(style, s) }
        }
        val live = s.trains.count { it.truth == "live" }
        Row(
            Modifier.statusBarsPadding().padding(16.dp).align(Alignment.TopStart).clip(RoundedCornerShape(16.dp))
                .background(Wr.colors.card).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (live > 0) LiveDot()
            Text(if (s.trains.isEmpty()) "لا قطارات مشاركة الآن" else "${s.trains.size} قطار على الخريطة", style = WrType.small, color = Wr.colors.ink)
        }
    }
}

private fun setup(style: Style) {
    style.addSource(GeoJsonSource("wr-lines"))
    style.addSource(GeoJsonSource("wr-stations-src"))
    style.addSource(GeoJsonSource("wr-trains"))
    style.addLayer(LineLayer("wr-line", "wr-lines").withProperties(
        PropertyFactory.lineColor(Expression.get("color")), PropertyFactory.lineWidth(4f), PropertyFactory.lineOpacity(0.9f)))
    style.addLayer(CircleLayer("wr-stations", "wr-stations-src").withProperties(
        PropertyFactory.circleRadius(4.5f), PropertyFactory.circleColor(AColor.WHITE),
        PropertyFactory.circleStrokeColor(AColor.parseColor("#0F1A17")), PropertyFactory.circleStrokeWidth(2f)))
    style.addLayer(SymbolLayer("wr-station-names", "wr-stations-src").withProperties(
        PropertyFactory.textField(Expression.get("name")), PropertyFactory.textSize(11f), PropertyFactory.textOffset(arrayOf(0f, 1.2f)),
        PropertyFactory.textHaloColor(AColor.WHITE), PropertyFactory.textHaloWidth(1.5f), PropertyFactory.textOptional(true))
.apply { minZoom = 11f })
    style.addLayer(CircleLayer("wr-train-halo", "wr-trains").withProperties(
        PropertyFactory.circleRadius(14f), PropertyFactory.circleColor(Expression.get("color")), PropertyFactory.circleOpacity(0.25f)))
    style.addLayer(CircleLayer("wr-train", "wr-trains").withProperties(
        PropertyFactory.circleRadius(8f), PropertyFactory.circleColor(Expression.get("color")),
        PropertyFactory.circleStrokeColor(AColor.WHITE), PropertyFactory.circleStrokeWidth(2.5f)))
    style.addLayer(SymbolLayer("wr-train-label", "wr-trains").withProperties(
        PropertyFactory.textField(Expression.get("label")), PropertyFactory.textSize(12f), PropertyFactory.textOffset(arrayOf(0f, -1.6f)),
        PropertyFactory.textHaloColor(AColor.WHITE), PropertyFactory.textHaloWidth(1.5f), PropertyFactory.textAllowOverlap(true)))
}

private fun update(style: Style, s: MapState) {
    val byId = s.stations.associateBy { it.id }
    val lineFeatures = JSONArray()
    s.lines.forEach { l ->
        val coords = JSONArray()
        l.stations.sortedBy { it.chainageM }.mapNotNull { byId[it.id] }.forEach { coords.put(JSONArray().put(it.longitude).put(it.latitude)) }
        if (coords.length() >= 2) lineFeatures.put(feature(JSONObject().put("type", "LineString").put("coordinates", coords),
            JSONObject().put("color", hex(LineColors.forCode(l.code).toArgb()))))
    }
    val stationFeatures = JSONArray()
    s.stations.forEach { st ->
        stationFeatures.put(feature(point(st.longitude, st.latitude), JSONObject().put("id", st.id).put("name", st.nameAr)))
    }
    val trainFeatures = JSONArray()
    s.trains.forEach { t ->
        val p = t.estimatedPosition ?: t.lastObservedPosition ?: return@forEach
        trainFeatures.put(feature(point(p.longitude, p.latitude), JSONObject()
            .put("color", if (t.truth == "live") "#3DDC97" else "#E8A317")
            .put("label", t.trainNumber ?: "")))
    }
    (style.getSource("wr-lines") as? GeoJsonSource)?.setGeoJson(collection(lineFeatures))
    (style.getSource("wr-stations-src") as? GeoJsonSource)?.setGeoJson(collection(stationFeatures))
    (style.getSource("wr-trains") as? GeoJsonSource)?.setGeoJson(collection(trainFeatures))
}

private fun hex(argb: Int) = String.format("#%06X", argb and 0xFFFFFF)
private fun point(lon: Double, lat: Double) = JSONObject().put("type", "Point").put("coordinates", JSONArray().put(lon).put(lat))
private fun feature(geometry: JSONObject, props: JSONObject) = JSONObject().put("type", "Feature").put("geometry", geometry).put("properties", props)
private fun collection(features: JSONArray) = JSONObject().put("type", "FeatureCollection").put("features", features).toString()
