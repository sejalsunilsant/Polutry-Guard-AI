package com.poultryguard.ai.ui.admin

import android.annotation.SuppressLint
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import com.poultryguard.ai.BuildConfig
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.layout.layout
import com.google.gson.Gson
import com.poultryguard.ai.data.model.FarmerProfile
import com.poultryguard.ai.ui.theme.*

// Data class to represent coordinates
data class MapCoordinate(
    val lat: Double,
    val lng: Double
)

// Data class to represent delivery routes
data class DeliveryRoute(
    val id: String,
    val farmName: String,
    val farmerName: String,
    val lat: Double,
    val lng: Double,
    val distanceKm: Double,
    val etaMinutes: Int,
    val status: String,
    val cargo: String
)

// Helper to determine coordinates from string location names
fun getCoordinatesForLocation(location: String, id: String): MapCoordinate {
    val loc = location.lowercase().trim()
    return when {
        loc.contains("pune") -> MapCoordinate(18.5204, 73.8567)
        loc.contains("mumbai") -> MapCoordinate(19.0760, 72.8777)
        loc.contains("bangalore") || loc.contains("bengaluru") -> MapCoordinate(12.9716, 77.5946)
        loc.contains("delhi") -> MapCoordinate(28.6139, 77.2090)
        loc.contains("dhaka") -> MapCoordinate(23.8103, 90.4125)
        loc.contains("gazipur") -> MapCoordinate(23.9999, 90.4203)
        loc.contains("savar") -> MapCoordinate(23.8583, 90.2667)
        loc.contains("kolkata") -> MapCoordinate(22.5726, 88.3639)
        loc.contains("chennai") -> MapCoordinate(13.0827, 80.2707)
        loc.contains("hyderabad") -> MapCoordinate(17.3850, 78.4867)
        else -> {
            // Generate a deterministic coordinate cluster around Pune based on the ID hash
            val hash = id.hashCode()
            val latOffset = (Math.abs(hash % 100)) / 1200.0
            val lngOffset = (Math.abs((hash / 100) % 100)) / 1200.0
            // Shift slightly to spread out markers
            MapCoordinate(18.5204 + latOffset - 0.04, 73.8567 + lngOffset - 0.04)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AdminMapView(
    farmers: List<FarmerProfile>,
    modifier: Modifier = Modifier
) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    // Map filters state
    var showFarms by remember { mutableStateOf(true) }
    var showRiskZones by remember { mutableStateOf(true) }
    var showOutbreakZones by remember { mutableStateOf(true) }
    var showRoutes by remember { mutableStateOf(true) }

    // Prepare JSON data for Leaflet
    val gson = remember { Gson() }
    
    val farmersWithCoords = remember(farmers) {
        farmers.map { f ->
            val coords = getCoordinatesForLocation(f.farmLocation, f.id)
            // Dynamically attach latitude/longitude fields for JS consumption
            object {
                val id = f.id
                val name = f.name
                val farmName = f.farmName
                val farmLocation = f.farmLocation
                val chickAgeDays = f.chickAgeDays
                val mortalitiesCount = f.mortalitiesCount
                val openDiseaseAlertsCount = f.openDiseaseAlertsCount
                val isOnline = f.isOnline
                val deviceId = f.deviceId
                val lat = coords.lat
                val lng = coords.lng
            }
        }
    }

    val deliveryRoutes = remember(farmersWithCoords) {
        farmersWithCoords.take(4).mapIndexed { index, f ->
            // Calculate distance from central hub Pune (18.5204, 73.8567)
            val latDiff = f.lat - 18.5204
            val lngDiff = f.lng - 73.8567
            val dist = Math.sqrt(latDiff * latDiff + lngDiff * lngDiff) * 111.0
            val distanceKm = Math.round(dist * 10.0) / 10.0
            val eta = (distanceKm * 2.2 + 15).toInt()
            
            val status = when (index % 4) {
                0 -> "In Transit"
                1 -> "Dispatched"
                2 -> "Preparing"
                else -> "Delivered"
            }
            
            val cargo = when (index % 3) {
                0 -> "Avian Influenza Vaccines (1,500 doses)"
                1 -> "Biosafety Ammonia neutralizer spray"
                else -> "Emergency Broad-Spectrum Antibiotics & Feeder Kits"
            }
            
            DeliveryRoute(
                id = "PG-DEL-${1045 + index}",
                farmName = f.farmName,
                farmerName = f.name,
                lat = f.lat,
                lng = f.lng,
                distanceKm = if (distanceKm > 0.0) distanceKm else 7.2,
                status = status,
                cargo = cargo,
                etaMinutes = eta
            )
        }
    }

    val farmersJson = remember(farmersWithCoords) { gson.toJson(farmersWithCoords) }
    val routesJson = remember(deliveryRoutes) { gson.toJson(deliveryRoutes) }

    // HTML source code for the Leaflet.js WebView map
    val mapHtml = remember(farmersJson, routesJson) {
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
            <style>
                body, html { margin: 0; padding: 0; width: 100%; height: 100%; overflow: hidden; background-color: #FAF5EC; position: relative; }
                #map { width: 100%; height: 100%; }
                #pac-input {
                    background-color: #fff;
                    font-family: system-ui, -apple-system, sans-serif;
                    font-size: 14px;
                    font-weight: 300;
                    margin-left: 12px;
                    padding: 0 11px 0 13px;
                    text-overflow: ellipsis;
                    width: 220px;
                    height: 38px;
                    border: 1px solid #ECE2D7;
                    border-radius: 8px;
                    box-shadow: 0 2px 6px rgba(66, 47, 36, 0.15);
                    outline: none;
                    color: #422F24;
                    margin-top: 10px;
                    position: absolute;
                    top: 10px;
                    left: 10px;
                    z-index: 1000;
                }
                #pac-input:focus {
                    border-color: #9E704F;
                }
                .custom-popup {
                    background: #FFFFFF;
                    color: #422F24;
                    font-family: system-ui, -apple-system, sans-serif;
                    padding: 4px;
                    width: 220px;
                }
                .popup-title {
                    font-weight: bold;
                    font-size: 14px;
                    color: #9E704F;
                    margin-bottom: 6px;
                    border-bottom: 1.5px solid #ECE2D7;
                    padding-bottom: 4px;
                }
                .popup-row {
                    margin: 5px 0;
                    font-size: 12px;
                    line-height: 1.4;
                }
                .popup-row strong {
                    color: #422F24;
                }
                .badge {
                    display: inline-block;
                    padding: 2px 6px;
                    border-radius: 4px;
                    font-weight: bold;
                    font-size: 9px;
                }
                .badge-online { background-color: rgba(76, 175, 80, 0.12); color: #4CAF50; }
                .badge-offline { background-color: rgba(134, 114, 101, 0.12); color: #867265; }
                .badge-alert { background-color: rgba(194, 74, 63, 0.12); color: #C24A3F; }
                .nav-btn {
                    display: block;
                    margin-top: 8px;
                    padding: 6px 12px;
                    background-color: #9E704F;
                    color: #FFFFFF !important;
                    text-decoration: none;
                    border-radius: 6px;
                    font-weight: bold;
                    font-size: 11px;
                    text-align: center;
                    transition: background-color 0.2s;
                }
                .nav-btn:hover {
                    background-color: #855C3F;
                }
                .leaflet-popup-content-wrapper {
                    background: #FFFFFF;
                    color: #422F24;
                    border-radius: 8px;
                    border: 1px solid #ECE2D7;
                    box-shadow: 0 3px 14px rgba(66, 47, 36, 0.18);
                }
                .leaflet-popup-content {
                    margin: 12px;
                }
                .leaflet-tile-container {
                    filter: sepia(0.15) hue-rotate(10deg) contrast(0.95) brightness(1.02);
                }
            </style>
            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
        </head>
        <body>
            <input id="pac-input" class="controls" type="text" placeholder="Search farm locations..." />
            <div id="map"></div>
            <script>
                var map;
                var farmMarkers = [];
                var riskCircles = [];
                var outbreakCircles = [];
                var routeLines = [];

                function initMap() {
                    map = L.map('map', {
                        zoomControl: false
                    }).setView([18.5204, 73.8567], 11);

                    L.control.zoom({
                        position: 'topright'
                    }).addTo(map);

                    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                        maxZoom: 19,
                        attribution: '&copy; <a href="http://www.openstreetmap.org/copyright">OpenStreetMap</a>'
                    }).addTo(map);

                    var input = document.getElementById('pac-input');
                    input.addEventListener('keypress', function(e) {
                        if (e.key === 'Enter') {
                            var query = input.value;
                            if (query) {
                                fetch('https://nominatim.openstreetmap.org/search?format=json&q=' + encodeURIComponent(query))
                                    .then(function(res) { return res.json(); })
                                    .then(function(data) {
                                        if (data && data.length > 0) {
                                            var first = data[0];
                                            map.setView([parseFloat(first.lat), parseFloat(first.lon)], 14);
                                        }
                                    })
                                    .catch(function(err) { console.error(err); });
                            }
                        }
                    });

                    var hubMarker = L.circleMarker([18.5204, 73.8567], {
                        radius: 9,
                        fillColor: '#9E704F',
                        color: '#FFFFFF',
                        weight: 3,
                        fillOpacity: 1
                    }).addTo(map);

                    hubMarker.bindPopup('<div class="custom-popup"><div class="popup-title">Rapid Response Veterinary Hub</div><div class="popup-row">Central logistics office for dispatching support, feed neutralizers, and mobile vets.</div><a href="https://www.google.com/maps/dir/?api=1&destination=18.5204,73.8567" target="_blank" class="nav-btn">Navigate Here</a></div>');

                    var farmers = $farmersJson;
                    var routes = $routesJson;
                    var boundsPoints = [];
                    boundsPoints.push([18.5204, 73.8567]);

                    farmers.forEach(function(f) {
                        var pos = [f.lat, f.lng];
                        boundsPoints.push(pos);

                        var hasAlerts = f.openDiseaseAlertsCount > 0;
                        var mort = f.mortalitiesCount;

                        var color = "#9E704F";
                        if (hasAlerts) color = "#C24A3F";
                        else if (!f.isOnline) color = "#867265";

                        var marker = L.circleMarker(pos, {
                            radius: 7,
                            fillColor: color,
                            color: '#FFFFFF',
                            weight: 2.5,
                            fillOpacity: 1
                        }).addTo(map);

                        var popupHtml = '<div class="custom-popup">' +
                            '<div class="popup-title">' + f.farmName + '</div>' +
                            '<div class="popup-row"><strong>Farmer:</strong> ' + f.name + '</div>' +
                            '<div class="popup-row"><strong>Location:</strong> ' + f.farmLocation + '</div>' +
                            '<div class="popup-row"><strong>Chick Age:</strong> ' + f.chickAgeDays + ' days</div>' +
                            '<div class="popup-row"><strong>Mortalities:</strong> ' + mort + ' birds</div>' +
                            '<div class="popup-row"><strong>Status:</strong> ' + 
                                (f.isOnline ? '<span class="badge badge-online">Online</span>' : '<span class="badge badge-offline">Offline</span>') +
                                (hasAlerts ? ' <span class="badge badge-alert">Alerts: ' + f.openDiseaseAlertsCount + '</span>' : '') +
                            '</div>' +
                            '<a href="https://www.google.com/maps/dir/?api=1&destination=' + f.lat + ',' + f.lng + '" target="_blank" class="nav-btn">Navigate</a>' +
                            '</div>';

                        marker.bindPopup(popupHtml);
                        farmMarkers.push(marker);

                        if (hasAlerts || mort >= 4) {
                            var riskCircle = L.circle(pos, {
                                color: "#E08244",
                                fillColor: "#E08244",
                                fillOpacity: 0.12,
                                weight: 1.5,
                                radius: 5000
                            }).addTo(map);
                            riskCircle.bindPopup('<strong>Risk Zone: ' + f.farmName + '</strong><br/>Secondary advisory warning buffer.');
                            riskCircles.push(riskCircle);
                        }

                        if (mort >= 10 || (hasAlerts && mort >= 7)) {
                            var outbreakCore = L.circle(pos, {
                                color: "#C24A3F",
                                fillColor: "#C24A3F",
                                fillOpacity: 0.22,
                                weight: 2,
                                radius: 2500
                            }).addTo(map);
                            outbreakCore.bindPopup('<strong>CRITICAL OUTBREAK SECTOR</strong><br/>Quarantine restrictions active. No unregulated transport allowed.');
                            outbreakCircles.push(outbreakCore);

                            var containmentOuter = L.circle(pos, {
                                color: "#C24A3F",
                                fillOpacity: 0,
                                weight: 1,
                                radius: 9000,
                                interactive: false
                            }).addTo(map);
                            outbreakCircles.push(containmentOuter);
                        }
                    });

                    routes.forEach(function(r) {
                        var latlngs = [
                            [18.5204, 73.8567],
                            [r.lat, r.lng]
                        ];
                        
                        var routeLine = L.polyline(latlngs, {
                            color: r.status === "In Transit" ? "#E08244" : "#866046",
                            opacity: 0.75,
                            weight: 3.5
                        }).addTo(map);

                        var routePopupHtml = '<strong>Delivery ' + r.id + '</strong><br/>' +
                            '<strong>Cargo:</strong> ' + r.cargo + '<br/>' +
                            '<strong>Status:</strong> ' + r.status + '<br/>' +
                            '<strong>Distance:</strong> ' + r.distanceKm + ' km<br/>' +
                            '<strong>ETA:</strong> ' + r.etaMinutes + ' min<br/>' +
                            '<a href="https://www.google.com/maps/dir/?api=1&destination=' + r.lat + ',' + r.lng + '" target="_blank" class="nav-btn">Navigate</a>';

                        routeLine.bindPopup(routePopupHtml);
                        routeLines.push(routeLine);
                    });

                    if (boundsPoints.length > 0) {
                        map.fitBounds(L.latLngBounds(boundsPoints), { padding: [30, 30] });
                    }
                }

                function setLayerVisible(layerName, visible) {
                    if (layerName === "farms") {
                        farmMarkers.forEach(function(m) { if (visible) { map.addLayer(m); } else { map.removeLayer(m); } });
                    } else if (layerName === "risk") {
                        riskCircles.forEach(function(c) { if (visible) { map.addLayer(c); } else { map.removeLayer(c); } });
                    } else if (layerName === "outbreak") {
                        outbreakCircles.forEach(function(c) { if (visible) { map.addLayer(c); } else { map.removeLayer(c); } });
                    } else if (layerName === "routes") {
                        routeLines.forEach(function(l) { if (visible) { map.addLayer(l); } else { map.removeLayer(l); } });
                    }
                }

                function centerOn(lat, lng) {
                    if (map) {
                        map.setView([lat, lng], 14);
                    }
                }

                window.onload = function() {
                    initMap();
                };
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        // Split Layout: 55% Map View, 45% Logistics Info list
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.3f)
                .border(BorderStroke(1.dp, DividerColor))
        ) {
            // WebView displaying interactive Leaflet Map
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                if (url != null && (url.startsWith("https://www.google.com/maps") || url.startsWith("https://maps.google.com") || url.contains("google.com/maps"))) {
                                    try {
                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                        context.startActivity(intent)
                                        return true
                                    } catch (e: Exception) {
                                        // Fallback
                                    }
                                }
                                return false
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                                val url = request?.url?.toString()
                                if (url != null && (url.startsWith("https://www.google.com/maps") || url.startsWith("https://maps.google.com") || url.contains("google.com/maps"))) {
                                    try {
                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                        context.startActivity(intent)
                                        return true
                                    } catch (e: Exception) {
                                        // Fallback
                                    }
                                }
                                return false
                            }
                        }
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        webViewInstance = this
                        loadDataWithBaseURL("https://appassets.androidplatform.net", mapHtml, "text/html", "UTF-8", null)
                    }
                },
                update = { webView ->
                    // Make sure dataset changes reload Web data if necessary
                }
            )

            // Dynamic filter toggle panel floating in the top-right corner
            Card(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .width(180.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface.copy(alpha = 0.95f)),
                border = BorderStroke(1.dp, DividerColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "MAP LAYERS",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = TextMedium,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    
                    // Farms Toggle
                    LayerToggleRow("Farm Markers", showFarms, Color(0xFF9E704F)) { checked ->
                        showFarms = checked
                        webViewInstance?.evaluateJavascript("setLayerVisible('farms', $checked);", null)
                    }
                    
                    // Risk Zones Toggle
                    LayerToggleRow("Risk Zones", showRiskZones, AlertOrange) { checked ->
                        showRiskZones = checked
                        webViewInstance?.evaluateJavascript("setLayerVisible('risk', $checked);", null)
                    }
                    
                    // Outbreak/Quarantine Toggle
                    LayerToggleRow("Containment", showOutbreakZones, AlertRed) { checked ->
                        showOutbreakZones = checked
                        webViewInstance?.evaluateJavascript("setLayerVisible('outbreak', $checked);", null)
                    }
                    
                    // Routes Toggle
                    LayerToggleRow("Delivery Routes", showRoutes, Color(0xFF866046)) { checked ->
                        showRoutes = checked
                        webViewInstance?.evaluateJavascript("setLayerVisible('routes', $checked);", null)
                    }
                }
            }
        }

        // Logistics/Dispatch Information Bottom Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.0f)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = BorderStroke(1.dp, DividerColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header of Logistics Panel
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocalShipping,
                            contentDescription = "Logistics",
                            tint = GreenPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Vaccine & Medicine Logistics",
                            style = Typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(GreenLight)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${deliveryRoutes.size} Active",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = GreenDark
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(10.dp))
                
                // List of Active Deliveries
                if (deliveryRoutes.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No active logistical routes dispatching today.",
                            color = TextMedium,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(deliveryRoutes) { route ->
                            DeliveryRouteCard(
                                route = route,
                                onLocateClick = {
                                    // Evaluate JavaScript to pan and zoom on map to specific coordinates
                                    webViewInstance?.evaluateJavascript(
                                        "centerOn(${route.lat}, ${route.lng});",
                                        null
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LayerToggleRow(
    label: String,
    checked: Boolean,
    color: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            // Visual color marker
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = TextDark
            )
        }
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = GreenPrimary,
                checkmarkColor = Color.White
            ),
            modifier = Modifier.scale(0.85f)
        )
    }
}

// Extension to scale Composable components easily
private fun Modifier.scale(scale: Float): Modifier = this.layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) {
        placeable.placeWithLayer(0, 0) {
            scaleX = scale
            scaleY = scale
        }
    }
}

@Composable
fun DeliveryRouteCard(
    route: DeliveryRoute,
    onLocateClick: () -> Unit
) {
    val statusColor = when (route.status) {
        "In Transit" -> AlertOrange
        "Dispatched" -> GreenPrimary
        "Preparing" -> TextMedium
        else -> GreenDark
    }
    
    val statusBg = when (route.status) {
        "In Transit" -> AlertOrange.copy(alpha = 0.1f)
        "Dispatched" -> GreenPrimary.copy(alpha = 0.1f)
        "Preparing" -> TextMedium.copy(alpha = 0.1f)
        else -> GreenLight
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AppBackground.copy(alpha = 0.5f)),
        border = BorderStroke(1.dp, DividerColor)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = route.id,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TextDark
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(statusBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = route.status,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }
                }
                
                // Map Action Buttons (Locate and Navigate)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Locate on Map Text/Button
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onLocateClick() }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "Locate",
                            tint = GreenPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Locate",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = GreenPrimary
                        )
                    }

                    // Navigate Button (Using Google Maps Direction URL)
                    val context = androidx.compose.ui.platform.LocalContext.current
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                val url = "https://www.google.com/maps/dir/?api=1&destination=${route.lat},${route.lng}"
                                try {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    // Fallback if maps intent fails
                                }
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Navigation,
                            contentDescription = "Navigate",
                            tint = GreenPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Navigate",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = GreenPrimary
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(6.dp))
            
            Text(
                text = "Target Farm: ${route.farmName} (${route.farmerName})",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark
            )
            Text(
                text = "Cargo: ${route.cargo}",
                fontSize = 12.sp,
                color = TextMedium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = DividerColor)
            Spacer(modifier = Modifier.height(6.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Timeline,
                            contentDescription = "Distance",
                            tint = TextMedium,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${route.distanceKm} km",
                            fontSize = 11.sp,
                            color = TextDark,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = "ETA",
                            tint = TextMedium,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "ETA: ${route.etaMinutes} min",
                            fontSize = 11.sp,
                            color = TextDark,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
