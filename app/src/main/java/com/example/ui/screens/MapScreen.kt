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
    val gpsData by viewModel.gpsData.collectAsState()

    var activeTab by remember { mutableStateOf("map") } // "map" or "stations"
    var showMapKeysMenu by remember { mutableStateOf(false) }
    var selectedMapLayer by remember { mutableStateOf("streets") }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    val primaryTrain = selectedTrain ?: activeTrains.firstOrNull()
    // When there is no live train, the map remains centered on the selected reference station.
    val trainLat = primaryTrain?.latitude ?: selectedStation.latitude
    val trainLon = primaryTrain?.longitude ?: selectedStation.longitude
    val trainSpeed = primaryTrain?.speedKmh?.toInt()?.toString() ?: "غير متوفر"
    val trainEta = primaryTrain?.etaToWaitingStationMinutes?.toString() ?: "غير متوفر"
    val trainDistKm = primaryTrain?.distanceToWaitingStationKm?.let { "%.1f".format(it) } ?: "غير متوفر"

    // Push only real live coordinates to the Leaflet layer; never create a fallback train.
    LaunchedEffect(trainLat, trainLon, primaryTrain?.trainNumber, trainSpeed, trainEta, primaryTrain != null) {
        val script = if (primaryTrain != null) {
            """
                if (window.updateTrainPosition) {
                    window.updateTrainPosition($trainLat, $trainLon, '$trainSpeed', '$trainEta', '${primaryTrain.trainNumber}', '$trainDistKm');
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
        // TOP 4 ACTION CARDS (Exactly matching the screenshot layout)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Card 1: Activate Steam Train Whistle (Emergency sound)
            TopActionCard(
                title = "تفعيل صافرة القطار",
                icon = Icons.Default.VolumeUp,
                iconColor = Color(0xFFF87171),
                onClick = { viewModel.triggerSelectedSound() },
                modifier = Modifier.weight(1f)
            )

            // Card 2: Voice Announcement / Horn
            TopActionCard(
                title = "تشغيل صوت التنبيه",
                icon = Icons.Default.Campaign,
                iconColor = Color(0xFFA78BFA),
                onClick = { viewModel.triggerSelectedSound() },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Card 3: Refresh GPS Location
            TopActionCard(
                title = "تحديث الموقع الآن",
                icon = Icons.Default.Refresh,
                iconColor = Color(0xFF34D399),
                onClick = {
                    viewModel.refreshGpsLocation()
                    webViewInstance?.evaluateJavascript("if(window.centerOnTrain) window.centerOnTrain();", null)
                },
                modifier = Modifier.weight(1f)
            )

            // Card 4: Call Station Operations
            TopActionCard(
                title = "اتصال بمحطة القطار",
                icon = Icons.Default.Call,
                iconColor = Color(0xFF60A5FA),
                onClick = {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:021743333"))
                    try { context.startActivity(intent) } catch (_: Exception) {}
                },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // TAB BAR (Matching screenshot pills)
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
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF020617))
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
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.cacheMode = WebSettings.LOAD_DEFAULT
                                settings.loadWithOverviewMode = true
                                settings.useWideViewPort = true
                                webViewClient = object : WebViewClient() {
                                    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                                        return true
                                    }
                                }

                            val stationsJson = selectedSuburb.stations.joinToString(",") { st ->
                                """{name: '${st.name}', code: '${st.code}', lat: ${st.latitude}, lng: ${st.longitude}}"""
                            }

                            val html = """
                            <!DOCTYPE html>
                            <html>
                            <head>
                                <meta charset="utf-8" />
                                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
                                <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
                                <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
                                <style>
                                    html, body, #map { height: 100%; width: 100%; margin: 0; padding: 0; background: #e5e5e5; }
                                    .train-pin-container {
                                        display: flex;
                                        flex-direction: column;
                                        align-items: center;
                                    }
                                    .train-badge {
                                        background: #059669;
                                        color: white;
                                        font-family: sans-serif;
                                        font-size: 11px;
                                        font-weight: bold;
                                        padding: 4px 10px;
                                        border-radius: 12px;
                                        box-shadow: 0 3px 8px rgba(0,0,0,0.5);
                                        white-space: nowrap;
                                        margin-bottom: 2px;
                                        border: 2px solid white;
                                        text-align: center;
                                    }
                                    .train-badge-sub {
                                        font-size: 9px;
                                        color: #D1FAE5;
                                        font-weight: normal;
                                    }
                                    .train-icon-pin {
                                        background: #059669;
                                        width: 32px;
                                        height: 32px;
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
                                    .radar-pulse {
                                        position: absolute;
                                        width: 50px;
                                        height: 50px;
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
                                <script>
                                    var stations = [$stationsJson];
                                    var initialLat = $trainLat;
                                    var initialLon = $trainLon;

                                    var map = L.map('map', {
                                        zoomControl: false,
                                        attributionControl: false
                                    }).setView([initialLat, initialLon], 14);

                                    // Real OpenStreetMap Tile Layer with Algeria streets & landmarks
                                    var osmLayer = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
                                        maxZoom: 19
                                    }).addTo(map);

                                    // Draw Railway Track Polyline
                                    var trackPoints = stations.map(function(s) { return [s.lat, s.lng]; });
                                    var trackLine = L.polyline(trackPoints, {
                                        color: '#0284C7',
                                        weight: 5,
                                        opacity: 0.85,
                                        dashArray: '8, 8'
                                    }).addTo(map);

                                    // Add Station Markers
                                    stations.forEach(function(s) {
                                        var stationIcon = L.divIcon({
                                            className: 'station-icon',
                                            html: '<div style="background:#1E293B;color:white;padding:2px 6px;border-radius:6px;font-size:10px;border:1px solid #38BDF8;font-weight:bold;white-space:nowrap;transform:translate(-50%,-100%);">🚉 ' + s.name + '</div><div style="width:8px;height:8px;background:#38BDF8;border-radius:50%;border:2px solid white;position:absolute;top:-4px;left:-4px;"></div>',
                                            iconSize: [0, 0]
                                        });
                                        L.marker([s.lat, s.lng], { icon: stationIcon }).addTo(map);
                                    });

                                                                        // A train marker exists only when the API supplied a real live position.
                                    var trainMarker = null;
                                    var hasLiveTrain = ${primaryTrain != null};
                                    if (hasLiveTrain) {
                                        var trainIcon = L.divIcon({
                                            className: 'custom-train-marker',
                                            html: '<div class="train-pin-container">' +
                                                  '  <div class="radar-pulse"></div>' +
                                                  '  <div class="train-badge" id="train-label">🚆 ${primaryTrain?.trainNumber ?: ""}<br><span class="train-badge-sub" id="train-sub">${trainSpeed} كم/سا • ETA: ${trainEta} د</span></div>' +
                                                  '  <div class="train-icon-pin"><span>🚆</span></div>' +
                                                  '</div>',
                                            iconSize: [110, 80],
                                            iconAnchor: [55, 60]
                                        });
                                        trainMarker = L.marker([initialLat, initialLon], { icon: trainIcon }).addTo(map);
                                    }


                                    window.updateTrainPosition = function(lat, lng, speed, eta, trainName, distKm) {
                                        if (trainMarker) {
                                            trainMarker.setLatLng([lat, lng]);
                                            var label = document.getElementById('train-label');
                                            if (label) {
                                                label.innerHTML = '🚆 ' + trainName + '<br><span class="train-badge-sub">' + speed + ' كم/سا • وصول: ' + eta + ' د (' + distKm + ' كم)</span>';
                                            }
                                        }
                                    };

                                    window.removeTrainMarker = function() {
                                        if (trainMarker) {
                                            map.removeLayer(trainMarker);
                                            trainMarker = null;
                                        }
                                    };
                                    window.centerOnTrain = function() {
                                        if (trainMarker) {
                                            map.panTo(trainMarker.getLatLng(), { animate: true });
                                        }
                                    };

                                    window.zoomInMap = function() { map.zoomIn(); };
                                    window.zoomOutMap = function() { map.zoomOut(); };
                                    window.toggleLayer = function(layerType) {
                                        // Tile layer switcher
                                    };
                                </script>
                            </body>
                            </html>
                            """.trimIndent()

                            loadDataWithBaseURL("https://openstreetmap.org", html, "text/html", "UTF-8", null)
                            webViewInstance = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // FLOATING MAP CONTROLS (Right side - exactly matching the screenshot)
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top Green Target Location Button
                    Surface(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .clickable {
                                webViewInstance?.evaluateJavascript("if(window.centerOnTrain) window.centerOnTrain();", null)
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
                                webViewInstance?.evaluateJavascript("if(window.centerOnTrain) window.centerOnTrain();", null)
                            },
                        color = Color(0xDD1E293B),
                        shadowElevation = 4.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Fullscreen, contentDescription = "ملء الشاشة", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }

                    // Recenter / Layer Switch Button
                    Surface(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .clickable {
                                showMapKeysMenu = true
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
                            onClick = {
                                selectedMapLayer = "streets"
                                showMapKeysMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("إظهار كل محطات السكة الحديدية") },
                            onClick = {
                                showMapKeysMenu = false
                                webViewInstance?.evaluateJavascript("if(window.centerOnTrain) window.centerOnTrain();", null)
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

                // BOTTOM-LEFT: GPS Coordinate Pill (From screenshot: "GPS: 36.7538, 3.0588")
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(bottom = 10.dp, start = 10.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xEE0F172A)
                ) {
                    Text(
                        text = if (primaryTrain != null) "موقع القطار: ${String.format("%.4f", trainLat)}, ${String.format("%.4f", trainLon)}" else "مركز المحطة: ${String.format("%.4f", selectedStation.latitude)}, ${String.format("%.4f", selectedStation.longitude)}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
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
