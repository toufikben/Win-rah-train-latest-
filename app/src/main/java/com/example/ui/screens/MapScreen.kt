package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.Station
import com.example.viewmodel.TrainViewModel

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MapScreen(viewModel: TrainViewModel) {
    val context = LocalContext.current
    val suburbs by viewModel.suburbs.collectAsState()
    val selectedSuburb by viewModel.selectedSuburb.collectAsState()
    val selectedStation by viewModel.selectedStation.collectAsState()
    val activeTrains by viewModel.activeTrains.collectAsState()
    val selectedTrain by viewModel.selectedTrain.collectAsState()
    val monitorBinding by viewModel.monitorBinding.collectAsState()
    val gpsData by viewModel.gpsData.collectAsState()
    val trackGeometry by viewModel.trackGeometry.collectAsState()
    val trackGeometryLoading by viewModel.trackGeometryLoading.collectAsState()
    val trackGeometryError by viewModel.trackGeometryError.collectAsState()

    LaunchedEffect(selectedSuburb.id) {
        viewModel.refreshTrackGeometry(selectedSuburb)
    }

    var activeTab by remember { mutableStateOf("map") } // "map" or "stations"
    var showMapKeysMenu by remember { mutableStateOf(false) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var mapReady by remember { mutableStateOf(false) }
    var isFollowingTrain by remember { mutableStateOf(false) }

    val boundTrain = monitorBinding?.let { binding ->
        activeTrains.firstOrNull { it.id == binding.trainId }
    }
    // During an active broadcast session, never substitute an unrelated nearby train.
    val primaryTrain = boundTrain ?: if (monitorBinding == null) (selectedTrain ?: activeTrains.firstOrNull()) else null
    val ownBroadcastLocation = monitorBinding != null && gpsData.isGpsActive &&
        gpsData.latitude != 0.0 && gpsData.longitude != 0.0
    val displayLatitude = primaryTrain?.latitude ?: if (ownBroadcastLocation) gpsData.latitude else null
    val displayLongitude = primaryTrain?.longitude ?: if (ownBroadcastLocation) gpsData.longitude else null
    val initialMapLatitude = displayLatitude ?: selectedStation.latitude
    val initialMapLongitude = displayLongitude ?: selectedStation.longitude
    val trainSpeed = primaryTrain?.speedKmh?.toInt()?.toString()
        ?: if (ownBroadcastLocation) gpsData.speedKmh.toInt().toString() else "غير متوفر"
    val trainEta = primaryTrain?.etaToWaitingStationMinutes?.toString() ?: "غير متوفر"
    val trainDistKm = primaryTrain?.distanceToWaitingStationKm?.let { "%.1f".format(it) } ?: "غير متوفر"
    val trainStateKey = when {
        primaryTrain?.status?.contains("EMERGENCY", ignoreCase = true) == true -> "emergency"
        primaryTrain?.status?.contains("DELAY", ignoreCase = true) == true -> "delay"
        primaryTrain?.speedKmh != null && primaryTrain.speedKmh <= 1f -> "stopped"
        else -> "normal"
    }
    val trainStatusText = when {
        primaryTrain == null -> "لا يوجد قطار مباشر حاليًا"
        primaryTrain.status?.contains("EMERGENCY", ignoreCase = true) == true -> "حالة طارئة"
        primaryTrain.status?.contains("DELAY", ignoreCase = true) == true -> "يوجد تأخير"
        primaryTrain.speedKmh != null && primaryTrain.speedKmh <= 1f -> "متوقف مؤقتًا"
        else -> "يعمل بشكل طبيعي"
    }
    val trainStatusColor = when {
        primaryTrain == null -> Color(0xFF94A3B8)
        primaryTrain.status?.contains("EMERGENCY", ignoreCase = true) == true -> Color(0xFFF87171)
        primaryTrain.status?.contains("DELAY", ignoreCase = true) == true -> Color(0xFFFBBF24)
        primaryTrain.speedKmh != null && primaryTrain.speedKmh <= 1f -> Color(0xFFFBBF24)
        else -> Color(0xFF34D399)
    }
    val serverTrackPointsJson = trackGeometry?.coordinates
        ?.mapNotNull { coordinate ->
            if (coordinate.size >= 2) "[${coordinate[1]}, ${coordinate[0]}]" else null
        }
        ?.joinToString(prefix = "[", postfix = "]")
        ?: "[]"
    val mapSourceLabel = when (trackGeometry?.sourceKind) {
        "OSM_REVIEWED" -> "المسار: هندسة OSM مراجعة"
        "REFERENCE_NETWORK_DERIVED" -> "المسار: تقريبي بين المحطات، ليس محور سكة ممسوحًا"
        null -> "المسار: ترتيب محطات تقريبي (لا توجد هندسة منشورة)"
        else -> "المسار: مصدر غير معروف"
    }

    // Push only real live coordinates to the Leaflet layer; never create a fallback train.
    LaunchedEffect(mapReady, displayLatitude, displayLongitude, primaryTrain?.trainNumber, ownBroadcastLocation, trainSpeed, trainEta, trainStateKey) {
        val script = if (displayLatitude != null && displayLongitude != null) {
            val displayTrainNumber = primaryTrain?.trainNumber ?: "قطاري المحدد"
            """
                if (window.updateTrainPosition) {
                    window.updateTrainPosition($displayLatitude, $displayLongitude, '$trainSpeed', '$trainEta', '$displayTrainNumber', '$trainDistKm', '$trainStateKey');
                }
            """.trimIndent()
        } else {
            """
                if (window.removeTrainMarker) window.removeTrainMarker();
            """.trimIndent()
        }
        webViewInstance?.evaluateJavascript(script, null)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)) // Dark background like the user's screenshot
            .padding(top = 8.dp, start = 12.dp, end = 12.dp, bottom = 4.dp)
    ) {
        // Compact map header: keeps the map as the primary surface.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "خريطة قطارات ${selectedSuburb.name}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                                            text = when {
                            boundTrain != null -> "قطارك المحدد ظاهر على الخريطة"
                            ownBroadcastLocation -> "موقع بثك الحقيقي ظاهر على الخريطة"
                            primaryTrain != null -> "قطار مباشر متاح"
                            else -> "بانتظار بيانات قطار مباشرة"
                        },
                        color = if (primaryTrain != null || ownBroadcastLocation) Color(0xFF34D399) else Color(0xFF94A3B8),

                    style = MaterialTheme.typography.labelSmall
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(onClick = {
                    viewModel.refreshGpsLocation()
                    webViewInstance?.evaluateJavascript("if(window.centerOnTrain) window.centerOnTrain();", null)
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = "تحديث الموقع", tint = Color(0xFF34D399))
                }
                IconButton(onClick = { viewModel.triggerSelectedSound() }) {
                    Icon(Icons.Default.Campaign, contentDescription = "تنبيه القطار", tint = Color(0xFFA78BFA))
                }
                IconButton(onClick = {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:021743333"))
                    try { context.startActivity(intent) } catch (_: Exception) {}
                }) {
                    Icon(Icons.Default.Call, contentDescription = "الاتصال بالمحطة", tint = Color(0xFF60A5FA))
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // TAB BAR (compact navigation for map and stations)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(25.dp),
            color = Color(0xFF1E293B)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Stations tab
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (activeTab == "stations") Color(0xFF059669) else Color.Transparent,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { activeTab = "stations" }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = null,
                            tint = if (activeTab == "stations") Color.White else Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "محطات الضاحية (${selectedSuburb.stations.size})",
                            color = if (activeTab == "stations") Color.White else Color(0xFF94A3B8),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Map tab
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (activeTab == "map") Color(0xFF059669) else Color.Transparent,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { activeTab = "map" }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Map,
                            contentDescription = null,
                            tint = if (activeTab == "map") Color.White else Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "الخريطة والموقع",
                            color = if (activeTab == "map") Color.White else Color(0xFF94A3B8),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Line Quick Selector
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(suburbs) { suburb ->
                val isSelected = suburb.id == selectedSuburb.id
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) Color(0xFF0284C7) else Color(0xFF1E293B),
                    modifier = Modifier.clickable {
                        viewModel.selectSuburb(suburb)
                        webViewInstance?.reload()
                    }
                ) {
                    Text(
                        text = suburb.name,
                        color = if (isSelected) Color.White else Color(0xFF94A3B8),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // MAIN CONTAINER: OpenStreetMap or Stations List
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF020617)
        ) {
            if (activeTab == "stations") {
                // STATIONS TAB VIEW
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    Text(
                        text = "محطات خط ${selectedSuburb.name}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    androidx.compose.foundation.lazy.LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(selectedSuburb.stations) { station ->
                            val isSelectedStation = station.id == selectedStation.id
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelectedStation) Color(0xFF059669) else Color(0xFF1E293B),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.selectStation(station)
                                        activeTab = "map"
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "${station.order}.",
                                            color = if (isSelectedStation) Color.White else Color(0xFF38BDF8),
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = station.name,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Text(
                                                text = "رمز المحطة: ${station.code}",
                                                color = if (isSelectedStation) Color(0xFFD1FAE5) else Color(0xFF94A3B8),
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelectedStation) Color(0xFF065F46) else Color(0xFF0F172A)
                                    ) {
                                        Text(
                                            text = if (isSelectedStation) "محطة الانتظار المختارة" else "تحديد المحطة",
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // MAP TAB VIEW
                Box(modifier = Modifier.fillMaxSize()) {
                    // Real OpenStreetMap HTML with Leaflet CSS/JS
                    androidx.compose.runtime.key(
                        selectedSuburb.id,
                        trackGeometry?.sourceKind,
                        trackGeometry?.coordinates?.hashCode()
                    ) {
                        AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                // Keep hardware acceleration for reliable WebView map rendering.
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                clearCache(true)
                                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                                settings.loadWithOverviewMode = true
                                settings.useWideViewPort = true
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        mapReady = true
                                        view?.evaluateJavascript(
                                            "(typeof L !== 'undefined') + ' | ' + document.getElementById('map').clientWidth + 'x' + document.getElementById('map').clientHeight"
                                        ) { result ->
                                            android.util.Log.w("WinRahMap", "PROBE: $result")
                                        }
                                    }

                                    override fun onReceivedError(
                                        view: WebView?,
                                        request: android.webkit.WebResourceRequest?,
                                        error: android.webkit.WebResourceError?
                                    ) {
                                        android.util.Log.w("WinRahMap", "resource error ${request?.url}: ${error?.description}")
                                    }

                                    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                                        android.util.Log.e("WinRahMap", "WebView renderer stopped")
                                        mapReady = false
                                        return true
                                    }
                                }
                                webChromeClient = object : android.webkit.WebChromeClient() {
                                    override fun onConsoleMessage(message: android.webkit.ConsoleMessage?): Boolean {
                                        android.util.Log.w("WinRahMap", "console ${message?.message()} @${message?.lineNumber()}")
                                        return true
                                    }
                                }

                            mapReady = false
                            val stationsJson = org.json.JSONArray().apply {
                                selectedSuburb.stations.forEach { st ->
                                    put(org.json.JSONObject().apply {
                                        put("name", st.name)
                                        put("code", st.code)
                                        put("order", st.order)
                                        put("lat", st.latitude)
                                        put("lng", st.longitude)
                                    })
                                }
                            }.toString()
                            val leafletCss = ctx.assets.open("leaflet/leaflet.css")
                                .bufferedReader().use { it.readText() }
                                .replace("url(images/", "url(leaflet/images/")
                            val leafletJs = ctx.assets.open("leaflet/leaflet.js")
                                .bufferedReader().use { it.readText() }

                            val html = """
                            <!DOCTYPE html>
                            <html>
                            <head>
                                <meta charset="utf-8" />
                                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
                                <style>$leafletCss</style>
                                <style>
                                    html, body { height: 100%; width: 100%; margin: 0; padding: 0; background: #e5e5e5; }
                                    #map { height: 100%; min-height: 320px; width: 100%; background: #e5e5e5; }
                                    .train-pin-container {
                                        display: flex;
                                        flex-direction: column;
                                        align-items: center;
                                    }
                                    .train-badge {
                                        background: #059669;
                                        color: white;
                                        font-family: sans-serif;
                                        font-size: 10px;
                                        font-weight: bold;
                                        padding: 3px 7px;
                                        max-width: 150px;
                                        overflow: hidden;
                                        text-overflow: ellipsis;
                                        border-radius: 12px;
                                        box-shadow: 0 3px 8px rgba(0,0,0,0.5);
                                        white-space: nowrap;
                                        margin-bottom: 2px;
                                        border: 2px solid white;
                                        text-align: center;
                                    }
                                    .train-badge-sub {
                                        font-size: 8px;
                                        color: #D1FAE5;
                                        font-weight: normal;
                                    }
                                    .train-icon-pin {
                                        background: #059669;
                                        width: 28px;
                                        height: 28px;
                                        border-radius: 50% 50% 50% 0;
                                        transform: rotate(-45deg);
                                        display: flex;
                                        align-items: center;
                                        justify-content: center;
                                        border: 2px solid white;
                                        box-shadow: 0 4px 10px rgba(0,0,0,0.4);
                                    }
                                    .train-icon-pin span {
                                        transform: rotate(45deg);
                                        font-size: 16px;
                                    }
                                    .train-state-delay .train-badge, .train-state-delay .train-icon-pin { background: #D97706; }
                                    .train-state-stopped .train-badge, .train-state-stopped .train-icon-pin { background: #64748B; }
                                    .train-state-emergency .train-badge, .train-state-emergency .train-icon-pin { background: #DC2626; }
                                    .train-state-delay .radar-pulse { background: rgba(217, 119, 6, 0.35); }
                                    .train-state-stopped .radar-pulse { background: rgba(100, 116, 139, 0.28); animation: none; }
                                    .train-state-emergency .radar-pulse { background: rgba(220, 38, 38, 0.42); }
                                    .station-icon { background: transparent; border: 0; }
                                    .station-dot {
                                        width: 12px;
                                        height: 12px;
                                        border-radius: 50%;
                                        background: #38BDF8;
                                        border: 2px solid #FFFFFF;
                                        box-shadow: 0 2px 5px rgba(0,0,0,0.45);
                                    }
                                    .station-dot-selected {
                                        width: 16px;
                                        height: 16px;
                                        margin: -2px;
                                        background: #10B981;
                                        box-shadow: 0 0 0 4px rgba(16,185,129,0.22), 0 2px 6px rgba(0,0,0,0.55);
                                    }
                                    .station-label {
                                        display: none;
                                        position: absolute;
                                        left: 50%;
                                        bottom: 14px;
                                        transform: translateX(-50%);
                                        max-width: 130px;
                                        padding: 3px 6px;
                                        color: #FFFFFF;
                                        background: rgba(15,23,42,0.92);
                                        border: 1px solid #38BDF8;
                                        border-radius: 7px;
                                        font: 700 9px sans-serif;
                                        white-space: nowrap;
                                        overflow: hidden;
                                        text-overflow: ellipsis;
                                        pointer-events: none;
                                    }
                                    .station-label-selected { display: block; border-color: #10B981; }
                                    .radar-pulse {
                                        position: absolute;
                                        width: 42px;
                                        height: 42px;
                                        background: rgba(5, 150, 105, 0.35);
                                        border-radius: 50%;
                                        top: 10px;
                                        animation: pulse 1.8s infinite;
                                        pointer-events: none;
                                    }
                                    @keyframes pulse {
                                        0% { transform: scale(0.6); opacity: 1; }
                                        100% { transform: scale(1.6); opacity: 0; }
                                    }
                                </style>
                            </head>
                            <body>
                                <div id="map"></div>
                                <script>$leafletJs</script>
                                <script>
                                    function showMapStatus(message) { console.warn(message); }
                                    var followTrain = false;
                                    var lastTrainPosition = null;
                                    function setFollowTrain(enabled) {
                                        followTrain = !!enabled;
                                        if (followTrain && typeof window.centerOnTrain === 'function') window.centerOnTrain();
                                    }
                                    window.onerror = function(message, source, line, column) {
                                        showMapStatus('خطأ JavaScript: ' + message + ' @' + line);
                                        return true;
                                    };
                                    if (typeof L === 'undefined') {
                                        showMapStatus('فشل تحميل مكتبة الخريطة المدمجة');
                                    } else {
                                    var stations = $stationsJson;
                                    var selectedStationCode = '${selectedStation.code}';
                                    var initialLat = $initialMapLatitude;
                                    var initialLon = $initialMapLongitude;
                                    var serverTrackPoints = $serverTrackPointsJson;

                                    var map = L.map('map', {
                                        zoomControl: false,
                                        attributionControl: false
                                    }).setView([initialLat, initialLon], 14);
                                    setTimeout(function() { map.invalidateSize({ animate: false }); }, 250);

                                    // Real OpenStreetMap Tile Layer with Algeria streets & landmarks
                                    var osmLayer = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
                                        maxZoom: 19,
                                        attribution: '&copy; OpenStreetMap contributors'
                                    }).addTo(map).on('tileerror', function() {
                                        showMapStatus('تعذر تحميل خلفية الخريطة — المحطات والقطار يعملان');
                                    });

                                    // Use server GeoJSON when it matches the canonical line; otherwise draw the local station-order fallback.
                                    var trackPoints = serverTrackPoints.length >= 2
                                        ? serverTrackPoints
                                        : stations.map(function(s) { return [s.lat, s.lng]; });
                                    var trackLine = L.polyline(trackPoints, {
                                        color: '#0284C7',
                                        weight: 5,
                                        opacity: 0.85,
                                        dashArray: '8, 8'
                                    }).addTo(map);

                                    // Station markers use a stable dot; labels are progressive to avoid clutter.
                                    var stationMarkers = [];
                                    stations.forEach(function(s) {
                                        var isSelected = s.code === selectedStationCode;
                                        var stationIcon = L.divIcon({
                                            className: 'station-icon',
                                            html: '<div class="station-label ' + (isSelected ? 'station-label-selected' : '') + '">🚉 ' + escapeHtml(s.name) + '</div>' +
                                                  '<div class="' + (isSelected ? 'station-dot station-dot-selected' : 'station-dot') + '"></div>',
                                            iconSize: [16, 16],
                                            iconAnchor: [8, 8]
                                        });
                                        var marker = L.marker([s.lat, s.lng], { icon: stationIcon, keyboard: false }).addTo(map);
                                        stationMarkers.push({ marker: marker, selected: isSelected });
                                    });
                                    function updateStationLabels() {
                                        var zoom = map.getZoom();
                                        stationMarkers.forEach(function(item) {
                                            var label = item.marker.getElement()?.querySelector('.station-label');
                                            if (!label) return;
                                            label.style.display = item.selected || zoom >= 13 ? 'block' : 'none';
                                        });
                                    }
                                    map.on('zoomend', updateStationLabels);
                                    map.on('moveend', updateStationLabels);
                                    updateStationLabels();

                                    // The marker is created lazily so a train can appear after the WebView is ready.
                                    var trainMarker = null;
                                    var lastTrainState = 'normal';
                                    function escapeHtml(value) {
                                        return String(value == null ? '' : value).replace(/[&<>\"']/g, function(ch) {
                                            return ({'&':'&amp;','<':'&lt;','>':'&gt;','\"':'&quot;',"'":'&#39;'})[ch];
                                        });
                                    }
                                    function createTrainIcon(trainName, speed, eta, distKm, state) {
                                        var safeState = ['normal', 'delay', 'stopped', 'emergency'].indexOf(state) >= 0 ? state : 'normal';
                                        var badge = escapeHtml(trainName);
                                        var speedText = escapeHtml(speed);
                                        var etaText = escapeHtml(eta);
                                        var distText = escapeHtml(distKm);
                                        return L.divIcon({
                                            className: 'custom-train-marker',
                                            html: '<div class="train-pin-container train-state-' + safeState + '">' +
                                                  '  <div class="radar-pulse"></div>' +
                                                  '  <div class="train-badge" id="train-label">🚆 ' + badge + '<br><span class="train-badge-sub">' + speedText + ' كم/سا • وصول: ' + etaText + ' د (' + distText + ' كم)</span></div>' +
                                                  '  <div class="train-icon-pin" id="train-icon-pin"><span>🚆</span></div>' +
                                                  '</div>',
                                            iconSize: [90, 70],
                                            iconAnchor: [45, 52]
                                        });
                                    }
                                    function ensureTrainMarker(lat, lng, trainName, speed, eta, distKm, state) {
                                        if (!trainMarker) {
                                            trainMarker = L.marker([lat, lng], { icon: createTrainIcon(trainName, speed, eta, distKm, state) }).addTo(map);
                                            lastTrainPosition = L.latLng(lat, lng);
                                        }
                                    }
                                    function applyTrainState(state) {
                                        var safeState = ['normal', 'delay', 'stopped', 'emergency'].indexOf(state) >= 0 ? state : 'normal';
                                        var container = document.querySelector('.train-pin-container');
                                        if (container) container.className = 'train-pin-container train-state-' + safeState;
                                        lastTrainState = safeState;
                                    }

                                    if (${primaryTrain != null}) {
                                        ensureTrainMarker(initialLat, initialLon, '${primaryTrain?.trainNumber ?: ""}', '${trainSpeed}', '${trainEta}', '${trainDistKm}', '${trainStateKey}');
                                    }

                                    window.updateTrainPosition = function(lat, lng, speed, eta, trainName, distKm, state) {
                                        ensureTrainMarker(lat, lng, trainName, speed, eta, distKm, state);
                                        applyTrainState(state);
                                        var start = lastTrainPosition || trainMarker.getLatLng();
                                        var end = L.latLng(lat, lng);
                                        var distance = map.distance(start, end);
                                        var duration = Math.max(450, Math.min(1400, distance * 35));
                                        var startedAt = performance.now();
                                        var bearing = Math.atan2(end.lng - start.lng, end.lat - start.lat) * 180 / Math.PI;
                                        if (bearing < 0) bearing += 360;
                                        var iconPin = document.getElementById('train-icon-pin');
                                        if (iconPin && distance > 0.5) iconPin.style.transform = 'rotate(' + (bearing - 45) + 'deg)';
                                        function animate(now) {
                                            var progress = Math.min(1, (now - startedAt) / duration);
                                            var eased = progress < 0.5 ? 2 * progress * progress : 1 - Math.pow(-2 * progress + 2, 2) / 2;
                                            var current = L.latLng(
                                                start.lat + (end.lat - start.lat) * eased,
                                                start.lng + (end.lng - start.lng) * eased
                                            );
                                            trainMarker.setLatLng(current);
                                            if (followTrain) map.panTo(current, { animate: false });
                                            if (progress < 1) window.requestAnimationFrame(animate);
                                        }
                                        window.cancelAnimationFrame(window.trainAnimationFrame || 0);
                                        window.trainAnimationFrame = window.requestAnimationFrame(animate);
                                        lastTrainPosition = end;
                                        var label = document.getElementById('train-label');
                                        if (label) {
                                            label.innerHTML = '🚆 ' + escapeHtml(trainName) + '<br><span class="train-badge-sub">' + escapeHtml(speed) + ' كم/سا • وصول: ' + escapeHtml(eta) + ' د (' + escapeHtml(distKm) + ' كم)</span>';
                                        }
                                    };

                                    window.removeTrainMarker = function() {
                                        if (trainMarker) {
                                            map.removeLayer(trainMarker);
                                            trainMarker = null;
                                        }
                                    };
                                    window.setFollowTrain = function(enabled) { setFollowTrain(enabled); };
                                    window.centerOnTrain = function() {
                                        if (trainMarker) {
                                            var trainLatLng = trainMarker.getLatLng();
                                            var targetZoom = Math.max(map.getZoom(), 15);
                                            map.setView(trainLatLng, targetZoom, { animate: true });
                                        } else if (trackPoints.length >= 2) {
                                            map.fitBounds(trackLine.getBounds(), { padding: [24, 24], maxZoom: 14, animate: true });
                                        } else if (stations.length > 0) {
                                            map.setView([stations[0].lat, stations[0].lng], Math.max(map.getZoom(), 13), { animate: true });
                                        }
                                    };
                                    window.fitTrack = function() {
                                        followTrain = false;
                                        if (trackPoints.length >= 2) {
                                            map.fitBounds(trackLine.getBounds(), { padding: [24, 24], maxZoom: 13, animate: true });
                                        } else if (stations.length > 0) {
                                            var stationBounds = L.latLngBounds(stations.map(function(s) { return [s.lat, s.lng]; }));
                                            map.fitBounds(stationBounds, { padding: [24, 24], maxZoom: 13, animate: true });
                                        }
                                    };

                                    setTimeout(function() {
                                        map.invalidateSize({ animate: false });
                                        if (trackPoints.length >= 2) {
                                            map.fitBounds(trackLine.getBounds(), { padding: [24, 24] });
                                        }
                                    }, 450);
                                    window.zoomInMap = function() { map.zoomIn(); }
                                    window.zoomOutMap = function() { map.zoomOut(); };
                                    }
                                </script>
                            </body>
                            </html>
                            """.trimIndent()

                            loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null)
                            webViewInstance = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                        )
                    }

                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xCC020617)
                    ) {
                        Text(
                            text = if (trackGeometryLoading) "جاري تحميل هندسة الخريطة..." else if (trackGeometryError != null) "$mapSourceLabel\nتعذر الاتصال بالخادم" else mapSourceLabel,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }

                // FLOATING MAP CONTROLS (Right side - exactly matching the screenshot)

                Column(
                    modifier = Modifier
                            .align(Alignment.CenterEnd)
                        .padding(end = 10.dp, bottom = 172.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Track selected train button
                    Surface(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .clickable {
                                isFollowingTrain = !isFollowingTrain
                                webViewInstance?.evaluateJavascript("(function(){if(window.setFollowTrain){window.setFollowTrain($isFollowingTrain);}else{console.warn('خريطة التتبع غير جاهزة');}})();", null)
                            },
                        color = if (isFollowingTrain) Color(0xFF2563EB) else Color(0xFF059669),
                        shadowElevation = 6.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.MyLocation,
                                contentDescription = if (isFollowingTrain) "إيقاف تتبع القطار" else "تتبع القطار",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // General map / overview button
                    Surface(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .clickable {
                                isFollowingTrain = false
                                webViewInstance?.evaluateJavascript("(function(){if(window.setFollowTrain)window.setFollowTrain(false);if(window.fitTrack)window.fitTrack();else console.warn('خريطة المسار غير جاهزة');})();", null)
                            },
                        color = Color(0xDD1E293B),
                        shadowElevation = 4.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Map, contentDescription = "العودة إلى الخريطة العامة", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }

                    // Top Green Target Location Button
                    Surface(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .clickable {
                                webViewInstance?.evaluateJavascript("(function(){if(window.centerOnTrain)window.centerOnTrain();else console.warn('علامة القطار غير جاهزة');})();", null)
                            },
                        color = Color(0xFF059669),
                        shadowElevation = 6.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.MyLocation,
                                contentDescription = "تحديد موقع القطار",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Zoom In Button (+)
                    Surface(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .clickable {
                                webViewInstance?.evaluateJavascript("if(window.zoomInMap) window.zoomInMap();", null)
                            },
                        color = Color(0xDD1E293B),
                        shadowElevation = 4.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Add, contentDescription = "تكبير", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }

                    // Zoom Out Button (-)
                    Surface(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .clickable {
                                webViewInstance?.evaluateJavascript("if(window.zoomOutMap) window.zoomOutMap();", null)
                            },
                        color = Color(0xDD1E293B),
                        shadowElevation = 4.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Remove, contentDescription = "تصغير", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }

                    // Fullscreen Button
                    Surface(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .clickable {
                                webViewInstance?.evaluateJavascript("if(window.fitTrack) window.fitTrack();", null)
                            },
                        color = Color(0xDD1E293B),
                        shadowElevation = 4.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Fullscreen, contentDescription = "عرض كامل للمسار", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }

                    // Recenter / Layer Switch Button
                    Surface(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .clickable {
                                viewModel.refreshTrackGeometry(selectedSuburb)
                                showMapKeysMenu = false
                            },
                        color = Color(0xDD1E293B),
                        shadowElevation = 4.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Refresh, contentDescription = "تحديث الطبقات", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // TOP-LEFT: "مفاتيح الخريطة 🗺️" Dropdown Button (From screenshot)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 10.dp, start = 10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xDD1E293B),
                        modifier = Modifier.clickable { showMapKeysMenu = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF34D399))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "مفاتيح الخريطة 🗺️",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showMapKeysMenu,
                        onDismissRequest = { showMapKeysMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("خريطة الشوارع (OpenStreetMap)") },
                            onClick = { showMapKeysMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("إظهار كل محطات السكة الحديدية") },
                            onClick = {
                                showMapKeysMenu = false
                                webViewInstance?.evaluateJavascript("if(window.fitTrack) window.fitTrack();", null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("إطلاق صافرة وتنبيه القطار 🔊") },
                            onClick = {
                                viewModel.triggerSelectedSound()
                                showMapKeysMenu = false
                            }
                        )
                    }
                }

                // Bottom train information sheet: one source of truth for the selected train.
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xEC0F172A),
                    shadowElevation = 8.dp
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = primaryTrain?.trainNumber ?: "مراقبة الخط",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (primaryTrain?.destinationName != null) "نحو ${primaryTrain.destinationName}" else "محطة الانتظار: ${selectedStation.name}",
                                    color = Color(0xFFCBD5E1),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = trainStatusColor.copy(alpha = 0.18f)
                            ) {
                                Text(
                                    text = trainStatusText,
                                    color = trainStatusColor,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TrainInfoMetric("الوصول", if (primaryTrain?.etaToWaitingStationMinutes != null) "${trainEta} د" else "—")
                            TrainInfoMetric("السرعة", if (primaryTrain?.speedKmh != null) "${trainSpeed} كم/س" else "—")
                            TrainInfoMetric("المسافة", if (primaryTrain?.distanceToWaitingStationKm != null) "${trainDistKm} كم" else "—")
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "المصدر: بيانات مباشرة • ${mapSourceLabel.substringBefore("\\n")}",
                                color = Color(0xFF94A3B8),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                modifier = Modifier.clickable {
                                    if (primaryTrain != null) {
                                        isFollowingTrain = !isFollowingTrain
                                        webViewInstance?.evaluateJavascript("(function(){if(window.setFollowTrain)window.setFollowTrain($isFollowingTrain);else console.warn('خريطة التتبع غير جاهزة');})();", null)
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isFollowingTrain) Color(0xFF2563EB) else Color(0xFF059669)
                            ) {
                                Text(
                                    text = if (isFollowingTrain) "إيقاف التتبع" else "تتبع القطار",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun TrainInfoMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            color = Color(0xFF94A3B8),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
fun TopActionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF1E293B), // Matching the dark container cards in screenshot
        modifier = modifier
            .height(58.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = title,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
