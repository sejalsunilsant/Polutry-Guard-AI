package com.poultryguard.ai.ui.admin

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.poultryguard.ai.data.model.FarmerProfile
import com.poultryguard.ai.ui.theme.*
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

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

// Data class to represent a farmer marker on the map
data class FarmerMapMarker(
    val id: String,
    val name: String,
    val farmName: String,
    val farmLocation: String,
    val chickAgeDays: Int,
    val mortalitiesCount: Int,
    val openDiseaseAlertsCount: Int,
    val isOnline: Boolean,
    val deviceId: String,
    val lat: Double,
    val lng: Double
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

// Helper function to check network status
private fun isNetworkAvailable(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    if (connectivityManager != null) {
        val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        if (capabilities != null) {
            return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                   capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                   capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        }
    }
    return false
}

// Helper to create a circle bitmap for map marker icons
private fun createCircleBitmap(color: Int, radius: Int, strokeColor: Int = android.graphics.Color.WHITE, strokeWidth: Float = 4f): Bitmap {
    val size = (radius * 2 + strokeWidth * 2).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Stroke
    paint.color = strokeColor
    paint.style = Paint.Style.FILL
    canvas.drawCircle(size / 2f, size / 2f, radius + strokeWidth / 2f, paint)

    // Fill
    paint.color = color
    canvas.drawCircle(size / 2f, size / 2f, radius.toFloat(), paint)

    return bitmap
}

// Generate circle GeoJSON polygon approximation for zones
private fun generateCirclePolygonPoints(center: Point, radiusMeters: Double, steps: Int = 64): List<Point> {
    val points = mutableListOf<Point>()
    val earthRadius = 6371000.0 // meters
    val lat = Math.toRadians(center.latitude())
    val lng = Math.toRadians(center.longitude())

    for (i in 0..steps) {
        val angle = Math.toRadians((360.0 / steps) * i)
        val pointLat = Math.asin(
            Math.sin(lat) * Math.cos(radiusMeters / earthRadius) +
            Math.cos(lat) * Math.sin(radiusMeters / earthRadius) * Math.cos(angle)
        )
        val pointLng = lng + Math.atan2(
            Math.sin(angle) * Math.sin(radiusMeters / earthRadius) * Math.cos(lat),
            Math.cos(radiusMeters / earthRadius) - Math.sin(lat) * Math.sin(pointLat)
        )
        points.add(Point.fromLngLat(Math.toDegrees(pointLng), Math.toDegrees(pointLat)))
    }
    return points
}

/**
 * Helper to populate all map data layers onto a loaded MapLibre style.
 */
private fun populateMapLayers(
    mapLibreMap: MapLibreMap,
    style: Style,
    farmersWithCoords: List<FarmerMapMarker>,
    deliveryRoutes: List<DeliveryRoute>,
    showFarms: Boolean,
    showRiskZones: Boolean,
    showOutbreakZones: Boolean,
    showRoutes: Boolean
) {
    // Remove old sources and layers if they exist (for re-population)
    val layerIds = listOf(
        "hub-marker-layer", "farm-markers-layer",
        "risk-zones-layer", "containment-core-layer", "containment-outer-layer",
        "routes-layer"
    )
    val sourceIds = listOf(
        "hub-source", "farm-markers-source",
        "risk-zones-source", "containment-core-source", "containment-outer-source",
        "routes-source"
    )
    layerIds.forEach { id -> style.getLayer(id)?.let { style.removeLayer(it) } }
    sourceIds.forEach { id -> style.getSource(id)?.let { style.removeSource(it) } }

    // ---- 1. HQ Marker (Pune) ----
    val hubPoint = Point.fromLngLat(73.8567, 18.5204)
    val hubFeature = Feature.fromGeometry(hubPoint)
    hubFeature.addStringProperty("title", "Rapid Response Hub")
    hubFeature.addStringProperty("snippet", "Pune Central Headquarters")
    val hubSource = GeoJsonSource("hub-source", FeatureCollection.fromFeatures(listOf(hubFeature)))
    style.addSource(hubSource)

    // Add hub icon
    val hubBitmap = createCircleBitmap(android.graphics.Color.parseColor("#3B82F6"), 14, android.graphics.Color.WHITE, 5f)
    style.addImage("hub-icon", hubBitmap)

    val hubLayer = SymbolLayer("hub-marker-layer", "hub-source")
        .withProperties(
            PropertyFactory.iconImage("hub-icon"),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.textField("HQ: PUNE HUB"),
            PropertyFactory.textOffset(arrayOf(0f, 1.8f)),
            PropertyFactory.textSize(11f),
            PropertyFactory.textColor(android.graphics.Color.parseColor("#3B82F6")),
            PropertyFactory.textHaloColor(android.graphics.Color.WHITE),
            PropertyFactory.textHaloWidth(1.5f)
        )
    style.addLayer(hubLayer)

    // ---- 2. Farm Markers ----
    val farmFeatures = farmersWithCoords.map { f ->
        val feature = Feature.fromGeometry(Point.fromLngLat(f.lng, f.lat))
        feature.addStringProperty("id", f.id)
        feature.addStringProperty("name", f.name)
        feature.addStringProperty("farmName", f.farmName)
        feature.addNumberProperty("mortalities", f.mortalitiesCount)
        feature.addNumberProperty("alerts", f.openDiseaseAlertsCount)
        feature.addBooleanProperty("isOnline", f.isOnline)

        val iconKey = when {
            f.openDiseaseAlertsCount > 0 -> "farm-alert"
            f.isOnline -> "farm-online"
            else -> "farm-offline"
        }
        feature.addStringProperty("icon", iconKey)
        feature
    }

    // Add farm marker icons
    style.addImage("farm-alert", createCircleBitmap(android.graphics.Color.parseColor("#EF4444"), 10, android.graphics.Color.WHITE, 3f))
    style.addImage("farm-online", createCircleBitmap(android.graphics.Color.parseColor("#10B981"), 10, android.graphics.Color.WHITE, 3f))
    style.addImage("farm-offline", createCircleBitmap(android.graphics.Color.parseColor("#64748B"), 10, android.graphics.Color.WHITE, 3f))

    val farmSource = GeoJsonSource("farm-markers-source", FeatureCollection.fromFeatures(farmFeatures))
    style.addSource(farmSource)

    val farmLayer = SymbolLayer("farm-markers-layer", "farm-markers-source")
        .withProperties(
            PropertyFactory.iconImage("{icon}"),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.textField("{farmName}"),
            PropertyFactory.textOffset(arrayOf(0f, 1.5f)),
            PropertyFactory.textSize(10f),
            PropertyFactory.textColor(android.graphics.Color.parseColor("#422F24")),
            PropertyFactory.textHaloColor(android.graphics.Color.WHITE),
            PropertyFactory.textHaloWidth(1f),
            PropertyFactory.textOptional(true)
        )
    farmLayer.setProperties(PropertyFactory.visibility(if (showFarms) org.maplibre.android.style.layers.Property.VISIBLE else org.maplibre.android.style.layers.Property.NONE))
    style.addLayer(farmLayer)

    // ---- 3. Risk Zones (orange, 5km radius) ----
    val riskFeatures = farmersWithCoords
        .filter { it.mortalitiesCount > 5 }
        .map { f ->
            val circlePoints = generateCirclePolygonPoints(Point.fromLngLat(f.lng, f.lat), 5000.0)
            Feature.fromGeometry(
                org.maplibre.geojson.Polygon.fromLngLats(listOf(circlePoints))
            )
        }
    if (riskFeatures.isNotEmpty()) {
        val riskSource = GeoJsonSource("risk-zones-source", FeatureCollection.fromFeatures(riskFeatures))
        style.addSource(riskSource)
        val riskLayer = org.maplibre.android.style.layers.FillLayer("risk-zones-layer", "risk-zones-source")
            .withProperties(
                PropertyFactory.fillColor(android.graphics.Color.parseColor("#F59E0B")),
                PropertyFactory.fillOpacity(0.12f)
            )
        riskLayer.setProperties(PropertyFactory.visibility(if (showRiskZones) org.maplibre.android.style.layers.Property.VISIBLE else org.maplibre.android.style.layers.Property.NONE))
        style.addLayer(riskLayer)
    }

    // ---- 4. Containment Zones (red, 2.5km core + 9km outer) ----
    val containmentCoreFarms = farmersWithCoords.filter { it.openDiseaseAlertsCount > 0 }
    if (containmentCoreFarms.isNotEmpty()) {
        val coreFeatures = containmentCoreFarms.map { f ->
            Feature.fromGeometry(
                org.maplibre.geojson.Polygon.fromLngLats(
                    listOf(generateCirclePolygonPoints(Point.fromLngLat(f.lng, f.lat), 2500.0))
                )
            )
        }
        val coreSource = GeoJsonSource("containment-core-source", FeatureCollection.fromFeatures(coreFeatures))
        style.addSource(coreSource)
        val coreLayer = org.maplibre.android.style.layers.FillLayer("containment-core-layer", "containment-core-source")
            .withProperties(
                PropertyFactory.fillColor(android.graphics.Color.parseColor("#EF4444")),
                PropertyFactory.fillOpacity(0.18f)
            )
        coreLayer.setProperties(PropertyFactory.visibility(if (showOutbreakZones) org.maplibre.android.style.layers.Property.VISIBLE else org.maplibre.android.style.layers.Property.NONE))
        style.addLayer(coreLayer)

        val outerFeatures = containmentCoreFarms.map { f ->
            Feature.fromGeometry(
                org.maplibre.geojson.Polygon.fromLngLats(
                    listOf(generateCirclePolygonPoints(Point.fromLngLat(f.lng, f.lat), 9000.0))
                )
            )
        }
        val outerSource = GeoJsonSource("containment-outer-source", FeatureCollection.fromFeatures(outerFeatures))
        style.addSource(outerSource)
        val outerLayer = org.maplibre.android.style.layers.FillLayer("containment-outer-layer", "containment-outer-source")
            .withProperties(
                PropertyFactory.fillColor(android.graphics.Color.parseColor("#EF4444")),
                PropertyFactory.fillOpacity(0.06f)
            )
        outerLayer.setProperties(PropertyFactory.visibility(if (showOutbreakZones) org.maplibre.android.style.layers.Property.VISIBLE else org.maplibre.android.style.layers.Property.NONE))
        style.addLayer(outerLayer)
    }

    // ---- 5. Delivery Routes (polylines from HQ to farm) ----
    val routeFeatures = deliveryRoutes.map { route ->
        val linePoints = listOf(
            Point.fromLngLat(73.8567, 18.5204),
            Point.fromLngLat(route.lng, route.lat)
        )
        val feature = Feature.fromGeometry(LineString.fromLngLats(linePoints))
        feature.addStringProperty("status", route.status)
        feature
    }
    if (routeFeatures.isNotEmpty()) {
        val routeSource = GeoJsonSource("routes-source", FeatureCollection.fromFeatures(routeFeatures))
        style.addSource(routeSource)
        val routeLayer = LineLayer("routes-layer", "routes-source")
            .withProperties(
                PropertyFactory.lineColor(android.graphics.Color.parseColor("#866046")),
                PropertyFactory.lineWidth(3.5f),
                PropertyFactory.lineOpacity(0.75f)
            )
        routeLayer.setProperties(PropertyFactory.visibility(if (showRoutes) org.maplibre.android.style.layers.Property.VISIBLE else org.maplibre.android.style.layers.Property.NONE))
        style.addLayer(routeLayer)
    }

    // Fit bounds to show all markers
    val boundsBuilder = LatLngBounds.Builder()
    boundsBuilder.include(LatLng(18.5204, 73.8567)) // HQ
    farmersWithCoords.forEach { f -> boundsBuilder.include(LatLng(f.lat, f.lng)) }
    try {
        val bounds = boundsBuilder.build()
        mapLibreMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 60), 1000)
    } catch (e: Exception) {
        // Not enough points, just center on HQ
        mapLibreMap.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder().target(LatLng(18.5204, 73.8567)).zoom(11.0).build()
            ), 1000
        )
    }
}

// OSM raster tile style JSON used by MapLibre for rendering actual map tiles
private val OSM_RASTER_STYLE = """
{
    "version": 8,
    "name": "OSM Raster",
    "sources": {
        "osm-raster": {
            "type": "raster",
            "tiles": [
                "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
            ],
            "tileSize": 256,
            "attribution": "&copy; <a href='https://www.openstreetmap.org/copyright'>OpenStreetMap</a> contributors"
        }
    },
    "layers": [
        {
            "id": "osm-raster-layer",
            "type": "raster",
            "source": "osm-raster",
            "minzoom": 0,
            "maxzoom": 19
        }
    ]
}
""".trimIndent()

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AdminMapView(
    farmers: List<FarmerProfile>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Initialize MapLibre
    remember { MapLibre.getInstance(context) }

    // Map instance reference
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

    // Loading and offline states
    var isLoading by remember { mutableStateOf(true) }
    var isError by remember { mutableStateOf(false) }
    var isOffline by remember { mutableStateOf(!isNetworkAvailable(context)) }
    var selectedFarmerId by remember { mutableStateOf<String?>(null) }

    // Map filters state
    var showFarms by remember { mutableStateOf(true) }
    var showRiskZones by remember { mutableStateOf(true) }
    var showOutbreakZones by remember { mutableStateOf(true) }
    var showRoutes by remember { mutableStateOf(true) }

    val farmersWithCoords = remember(farmers) {
        farmers.map { f ->
            val coords = getCoordinatesForLocation(f.farmLocation, f.id)
            FarmerMapMarker(
                id = f.id,
                name = f.name,
                farmName = f.farmName,
                farmLocation = f.farmLocation,
                chickAgeDays = f.chickAgeDays,
                mortalitiesCount = f.mortalitiesCount,
                openDiseaseAlertsCount = f.openDiseaseAlertsCount,
                isOnline = f.isOnline,
                deviceId = f.deviceId,
                lat = f.latitude ?: coords.lat,
                lng = f.longitude ?: coords.lng
            )
        }
    }

    val deliveryRoutes = remember(farmersWithCoords, selectedFarmerId) {
        val targetFarmers = if (selectedFarmerId != null) {
            farmersWithCoords.filter { it.id == selectedFarmerId }
        } else {
            farmersWithCoords.take(4)
        }
        targetFarmers.mapIndexed { index, f ->
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

    // Lifecycle management for MapView
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapViewRef) {
        val observer = LifecycleEventObserver { _, event ->
            val mv = mapViewRef ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_START -> mv.onStart()
                Lifecycle.Event.ON_RESUME -> mv.onResume()
                Lifecycle.Event.ON_PAUSE -> mv.onPause()
                Lifecycle.Event.ON_STOP -> mv.onStop()
                Lifecycle.Event.ON_DESTROY -> mv.onDestroy()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }

    // Re-populate map data when filter states or data changes
    LaunchedEffect(showFarms, showRiskZones, showOutbreakZones, showRoutes, farmersWithCoords, deliveryRoutes) {
        val map = mapLibreMap ?: return@LaunchedEffect
        val style = map.style ?: return@LaunchedEffect
        populateMapLayers(map, style, farmersWithCoords, deliveryRoutes, showFarms, showRiskZones, showOutbreakZones, showRoutes)
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
                .background(AppBackground)
        ) {
            if (!isOffline && !isError) {
                // MapLibre MapView
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        MapView(ctx).apply {
                            onCreate(null)
                            getMapAsync { map ->
                                mapLibreMap = map
                                mapViewRef = this

                                // Set camera to Pune
                                map.cameraPosition = CameraPosition.Builder()
                                    .target(LatLng(18.5204, 73.8567))
                                    .zoom(11.0)
                                    .build()

                                // Load OSM raster style
                                map.setStyle(Style.Builder().fromJson(OSM_RASTER_STYLE)) { style ->
                                    isLoading = false
                                    // Populate layers
                                    populateMapLayers(
                                        map, style,
                                        farmersWithCoords, deliveryRoutes,
                                        showFarms, showRiskZones, showOutbreakZones, showRoutes
                                    )

                                    // Handle marker clicks
                                    map.addOnMapClickListener { latLng ->
                                        val screenPoint = map.projection.toScreenLocation(latLng)
                                        val features = map.queryRenderedFeatures(screenPoint, "farm-markers-layer")
                                        if (features.isNotEmpty()) {
                                            val clickedId = features[0].getStringProperty("id")
                                            selectedFarmerId = clickedId
                                        } else {
                                            selectedFarmerId = null
                                        }
                                        true
                                    }
                                }
                            }
                            onStart()
                            onResume()
                        }
                    }
                )
            }

            // Loading overlay state
            if (isLoading && !isOffline && !isError) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AppBackground.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = GreenPrimary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Initializing MapLibre & loading tiles...",
                            style = Typography.bodyMedium,
                            color = TextDark,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Offline state overlay
            if (isOffline) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AppBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.WifiOff,
                            contentDescription = "Offline",
                            tint = AlertOrange,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Internet Connection",
                            style = Typography.titleMedium,
                            color = TextDark,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "An active internet connection is required to fetch map tiles from OpenStreetMap.",
                            style = Typography.bodyMedium,
                            color = TextMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = {
                                isOffline = !isNetworkAvailable(context)
                                if (!isOffline) {
                                    isError = false
                                    isLoading = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Retry Connection", color = Color.White)
                        }
                    }
                }
            }

            // General resource loading error overlay
            if (isError && !isOffline) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AppBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Error",
                            tint = AlertRed,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Failed to load map data",
                            style = Typography.titleMedium,
                            color = TextDark,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Check your connection and configuration, then try again.",
                            style = Typography.bodyMedium,
                            color = TextMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = {
                                isOffline = !isNetworkAvailable(context)
                                if (!isOffline) {
                                    isError = false
                                    isLoading = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Retry Loading", color = Color.White)
                        }
                    }
                }
            }

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
                    }

                    // Risk Zones Toggle
                    LayerToggleRow("Risk Zones", showRiskZones, AlertOrange) { checked ->
                        showRiskZones = checked
                    }

                    // Outbreak/Quarantine Toggle
                    LayerToggleRow("Containment", showOutbreakZones, AlertRed) { checked ->
                        showOutbreakZones = checked
                    }

                    // Routes Toggle
                    LayerToggleRow("Delivery Routes", showRoutes, Color(0xFF866046)) { checked ->
                        showRoutes = checked
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

                var dropdownExpanded by remember { mutableStateOf(false) }
                val selectedFarmerName = farmersWithCoords.find { it.id == selectedFarmerId }?.name ?: "All Farmers (Auto-routing)"

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    OutlinedButton(
                        onClick = { dropdownExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextDark),
                        border = BorderStroke(1.dp, DividerColor),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = "Filter",
                                    tint = GreenPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Route Target: $selectedFarmerName",
                                    style = Typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Dropdown",
                                tint = TextMedium
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        DropdownMenuItem(
                            text = { Text("All Farmers (Auto-routing)") },
                            onClick = {
                                selectedFarmerId = null
                                dropdownExpanded = false
                            }
                        )
                        farmersWithCoords.forEach { f ->
                            DropdownMenuItem(
                                text = { Text(f.name + " (" + f.farmName + ")") },
                                onClick = {
                                    selectedFarmerId = f.id
                                    dropdownExpanded = false
                                    // Animate camera to selected farm
                                    mapLibreMap?.animateCamera(
                                        CameraUpdateFactory.newCameraPosition(
                                            CameraPosition.Builder()
                                                .target(LatLng(f.lat, f.lng))
                                                .zoom(14.0)
                                                .build()
                                        ),
                                        800
                                    )
                                }
                            )
                        }
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
                                    mapLibreMap?.animateCamera(
                                        CameraUpdateFactory.newCameraPosition(
                                            CameraPosition.Builder()
                                                .target(LatLng(route.lat, route.lng))
                                                .zoom(14.0)
                                                .build()
                                        ),
                                        800
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
