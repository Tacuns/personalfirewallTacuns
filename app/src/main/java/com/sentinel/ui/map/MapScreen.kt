package com.sentinel.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sentinel.core.geo.CountryCoords
import com.sentinel.ui.components.HelpBottomSheet
import com.sentinel.ui.components.HelpContent
import com.sentinel.ui.components.HelpDialog
import com.sentinel.ui.components.HelpIcon
import com.sentinel.ui.theme.*
import com.sentinel.ui.viewmodel.MapViewModel
import com.sentinel.ui.viewmodel.ResolvedDomain
import com.tacu.nsfwzerotrust.R

// ─────────────────────────────────────────────────────────────────────────────
// Data models
// ─────────────────────────────────────────────────────────────────────────────

// count drives dot size on canvas; label kept for fallback markers only
private data class GeoMarker(val x: Float, val y: Float, val type: String, val label: String, val count: Int = 1)

// Landmass outlines stored as flat [lon0,lat0, lon1,lat1, ...] arrays.
// Converted to Mercator canvas coords at draw time: x=(lon+180)/360, y=(90-lat)/180.
// All coordinates are original approximations of public-domain geographic shapes.
private val LANDMASSES: List<FloatArray> = listOf(
    // ── North America ────────────────────────────────────────────────────────
    floatArrayOf(
        -168f,54f, -165f,62f, -162f,64f, -155f,57f, -148f,59f, -143f,59f,
        -136f,58f, -133f,55f, -130f,53f, -127f,52f, -126f,50f, -124f,49f,
        -124f,40f, -122f,37f, -120f,35f, -118f,33f, -114f,31f, -110f,23f,
        -105f,20f, -92f,16f, -90f,14f, -87f,15f, -85f,11f, -83f,10f,
        -80f,9f, -75f,10f, -62f,10f, -60f,11f, -62f,14f, -64f,18f,
        -67f,19f, -70f,19f, -73f,20f, -77f,25f, -80f,26f, -81f,31f,
        -78f,35f, -76f,37f, -75f,38f, -72f,41f, -70f,42f, -67f,44f,
        -60f,47f, -66f,50f, -70f,52f, -75f,54f, -79f,56f, -88f,58f,
        -96f,60f, -110f,60f, -120f,60f, -130f,60f, -138f,60f, -141f,61f,
        -142f,59f, -145f,56f, -148f,58f, -153f,58f, -155f,57f, -160f,55f,
        -165f,54f, -168f,54f
    ),
    // ── Greenland ────────────────────────────────────────────────────────────
    floatArrayOf(
        -45f,60f, -25f,62f, -18f,66f, -18f,72f, -25f,77f, -35f,81f,
        -50f,84f, -60f,81f, -70f,77f, -60f,70f, -55f,65f, -50f,62f, -45f,60f
    ),
    // ── South America ────────────────────────────────────────────────────────
    floatArrayOf(
        -73f,11f, -62f,11f, -60f,7f, -52f,4f, -50f,5f, -48f,2f,
        -44f,-2f, -35f,-4f, -35f,-8f, -38f,-13f, -38f,-16f, -40f,-20f,
        -42f,-23f, -46f,-24f, -48f,-28f, -52f,-33f, -53f,-36f, -57f,-38f,
        -62f,-43f, -65f,-46f, -67f,-51f, -65f,-55f, -69f,-54f, -70f,-52f,
        -68f,-47f, -65f,-44f, -63f,-42f, -66f,-35f, -68f,-30f, -70f,-25f,
        -72f,-18f, -77f,-14f, -77f,-8f, -80f,-3f, -80f,0f, -78f,2f,
        -77f,4f, -75f,8f, -73f,11f
    ),
    // ── Europe (mainland + Iberia + Scandinavia + Balkans) ───────────────────
    floatArrayOf(
        -9f,38f, -9f,44f, -8f,44f, -2f,44f, -2f,48f, 0f,51f, 3f,51f,
        5f,52f, 8f,55f, 10f,56f, 12f,56f, 15f,58f, 18f,60f, 16f,62f,
        14f,65f, 18f,68f, 25f,71f, 28f,71f, 30f,70f, 27f,65f, 30f,59f,
        27f,57f, 25f,59f, 24f,61f, 22f,62f, 20f,60f, 18f,60f, 15f,57f,
        12f,56f, 10f,57f, 7f,58f, 5f,58f, 2f,52f, 5f,51f, 8f,48f,
        12f,44f, 14f,41f, 15f,38f, 12f,38f, 15f,38f, 18f,40f, 22f,38f,
        26f,40f, 28f,42f, 32f,36f, 26f,36f, 22f,38f, 18f,40f, 15f,42f,
        14f,45f, 12f,46f, 8f,47f, 7f,44f, 1f,43f, -2f,43f, -8f,44f,
        -8f,40f, -9f,38f
    ),
    // ── Great Britain ────────────────────────────────────────────────────────
    floatArrayOf(
        -5f,50f, -6f,51f, -5f,52f, -3f,53f, -1f,54f, -2f,55f,
        -5f,57f, -5f,58f, -3f,59f, -2f,57f, 0f,54f, 1f,52f,
        1f,51f, 0f,51f, -2f,50f, -5f,50f
    ),
    // ── Ireland ──────────────────────────────────────────────────────────────
    floatArrayOf(
        -8f,52f, -10f,53f, -10f,54f, -8f,55f, -6f,54f, -6f,52f, -8f,52f
    ),
    // ── Iceland ──────────────────────────────────────────────────────────────
    floatArrayOf(
        -24f,64f, -14f,64f, -13f,65f, -16f,66f, -22f,66f, -24f,65f, -24f,64f
    ),
    // ── Africa ───────────────────────────────────────────────────────────────
    floatArrayOf(
        -5f,35f, 10f,37f, 15f,37f, 25f,31f, 32f,31f, 37f,27f,
        40f,22f, 44f,12f, 46f,11f, 50f,12f, 44f,12f, 42f,10f,
        42f,8f, 42f,5f, 40f,3f, 42f,2f, 40f,0f, 38f,-4f,
        35f,-6f, 35f,-9f, 35f,-12f, 36f,-16f, 35f,-18f, 35f,-22f,
        30f,-29f, 25f,-33f, 20f,-35f, 18f,-32f, 17f,-28f, 16f,-22f,
        14f,-12f, 12f,-6f, 10f,-2f, 9f,2f, 9f,5f, 8f,10f,
        6f,14f, 3f,18f, -2f,22f, -7f,27f, -9f,32f, -5f,35f
    ),
    // ── Madagascar ───────────────────────────────────────────────────────────
    floatArrayOf(
        44f,-12f, 46f,-13f, 50f,-16f, 50f,-22f, 47f,-25f, 44f,-23f,
        43f,-18f, 44f,-12f
    ),
    // ── Asia (main Eurasian landmass east of Europe) ─────────────────────────
    floatArrayOf(
        36f,37f, 36f,40f, 40f,43f, 45f,40f, 50f,37f, 55f,22f,
        57f,20f, 60f,25f, 66f,25f, 67f,23f, 73f,20f, 77f,8f,
        80f,10f, 84f,14f, 88f,22f, 92f,22f, 97f,22f, 100f,19f,
        103f,1f, 104f,10f, 108f,21f, 110f,19f, 118f,15f, 120f,18f,
        122f,24f, 121f,30f, 122f,35f, 126f,38f, 130f,42f, 135f,43f,
        140f,40f, 142f,47f, 143f,50f, 141f,55f, 140f,50f, 135f,48f,
        130f,45f, 125f,40f, 122f,37f, 120f,35f, 115f,27f, 110f,22f,
        105f,22f, 100f,20f, 97f,22f, 90f,28f, 85f,28f, 80f,30f,
        75f,32f, 71f,37f, 68f,35f, 65f,38f, 60f,42f, 57f,42f,
        54f,40f, 52f,33f, 50f,30f, 45f,38f, 40f,43f, 36f,40f, 36f,37f
    ),
    // ── Japan (Honshu + Kyushu + Shikoku simplified) ─────────────────────────
    floatArrayOf(
        130f,31f, 132f,34f, 131f,35f, 133f,35f, 135f,34f, 136f,35f,
        137f,35f, 139f,36f, 141f,38f, 142f,40f, 142f,43f, 141f,44f,
        139f,43f, 136f,41f, 134f,35f, 131f,34f, 130f,31f
    ),
    // ── Sri Lanka ────────────────────────────────────────────────────────────
    floatArrayOf(
        80f,10f, 82f,8f, 82f,6f, 80f,6f, 79f,8f, 80f,10f
    ),
    // ── Australia ────────────────────────────────────────────────────────────
    floatArrayOf(
        114f,-22f, 115f,-34f, 117f,-35f, 120f,-34f, 124f,-34f,
        126f,-34f, 130f,-32f, 132f,-29f, 135f,-27f, 138f,-35f,
        142f,-38f, 148f,-38f, 150f,-37f, 152f,-30f, 154f,-26f,
        154f,-22f, 150f,-17f, 144f,-14f, 140f,-17f, 138f,-18f,
        135f,-15f, 130f,-16f, 127f,-14f, 124f,-17f, 122f,-21f,
        118f,-20f, 114f,-22f
    ),
    // ── New Zealand (North + South simplified) ───────────────────────────────
    floatArrayOf(
        173f,-34f, 178f,-37f, 178f,-39f, 176f,-41f, 174f,-41f,
        173f,-39f, 173f,-34f
    ),
    floatArrayOf(
        166f,-45f, 168f,-47f, 171f,-45f, 172f,-43f, 171f,-46f,
        169f,-47f, 166f,-45f
    )
)

// ─────────────────────────────────────────────────────────────────────────────
// Screen root
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MapScreen(
    mapViewModel: MapViewModel = viewModel()
) {
    val domains by mapViewModel.domains.collectAsState()
    var showGeoMapHelp     by remember { mutableStateOf(false) }
    var showTopDomainsHelp by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        MapHeader()

        Spacer(Modifier.height(20.dp))

        MapSectionHeader(stringResource(R.string.section_geo_threat_map), onHelpClick = { showGeoMapHelp = true })
        Spacer(Modifier.height(8.dp))
        GeoMapCard(domains = domains)
        if (showGeoMapHelp) {
            HelpBottomSheet(content = geoMapHelp(), onDismiss = { showGeoMapHelp = false })
        }

        Spacer(Modifier.height(20.dp))

        MapSectionHeader(stringResource(R.string.section_top_blocked_domains), onHelpClick = { showTopDomainsHelp = true })
        Spacer(Modifier.height(8.dp))
        TopBlockedDomainsCard(domains = domains)
        if (showTopDomainsHelp) {
            HelpDialog(content = topBlockedDomainsHelp(), onDismiss = { showTopDomainsHelp = false })
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun geoMapHelp() = HelpContent(
    title = stringResource(R.string.geo_map_help_title),
    whatItIs = stringResource(R.string.geo_map_help_what_it_is),
    whatItDoes = stringResource(R.string.geo_map_help_what_it_does),
    why = stringResource(R.string.geo_map_help_why),
    benefit = stringResource(R.string.geo_map_help_benefit),
    bestPractices = listOf(
        stringResource(R.string.geo_map_help_practice_1)
    )
)

@Composable
private fun topBlockedDomainsHelp() = HelpContent(
    title = stringResource(R.string.top_blocked_help_title),
    whatItIs = stringResource(R.string.top_blocked_help_what_it_is),
    whatItDoes = stringResource(R.string.top_blocked_help_what_it_does),
    why = stringResource(R.string.top_blocked_help_why),
    benefit = stringResource(R.string.top_blocked_help_benefit),
    bestPractices = emptyList()
)

// ─────────────────────────────────────────────────────────────────────────────
// Header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MapHeader() {
    Row(
        modifier          = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Brush.linearGradient(listOf(CyanPrimary, BlueAccent))),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = Icons.Default.Public,
                contentDescription = null,
                tint               = Color.White,
                modifier           = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text          = stringResource(R.string.map_header_title),
                style         = MaterialTheme.typography.labelLarge,
                fontWeight    = FontWeight.Bold,
                color         = TextPrimary,
                letterSpacing = 0.5.sp
            )
            Text(
                text  = stringResource(R.string.map_header_subtitle),
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MapSectionHeader(title: String, onHelpClick: (() -> Unit)? = null) {
    Row(
        modifier          = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(CyanPrimary)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text          = title,
            style         = MaterialTheme.typography.labelSmall,
            color         = TextSecondary,
            fontWeight    = FontWeight.SemiBold,
            letterSpacing = 1.5.sp,
            modifier      = Modifier.weight(1f)
        )
        if (onHelpClick != null) {
            HelpIcon(onClick = onHelpClick)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Geo Map Card — world map canvas
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GeoMapCard(domains: List<ResolvedDomain>) {
    // Real markers only: allowed websites grouped by the country of their server.
    // Nothing is drawn that did not happen — no data means an honest empty message.
    val markers = remember(domains) {
        domains
            .filter { it.type == "ALLOWED" && it.countryCode != null }
            .groupBy { it.countryCode!! to it.type }
            .mapNotNull { (key, entries) ->
                val (code, type) = key
                val (x, y) = CountryCoords.get(code) ?: return@mapNotNull null
                GeoMarker(x, y, type, code, entries.sumOf { it.count })
            }
    }
    val noneToday = domains.none { it.type == "ALLOWED" }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(1.dp, BgBorder, RoundedCornerShape(12.dp))
    ) {
        Column {
            Box(contentAlignment = Alignment.Center) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                ) {
                    drawOceanBackground()
                    drawGridLines()
                    drawContinents()
                    drawArcFlows(markers)
                    drawDeviceNode()
                    markers.forEach { drawGeoMarker(it) }
                }
                if (noneToday) {
                    Text(
                        text  = stringResource(R.string.map_empty_today),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(BgCard.copy(alpha = 0.85f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            HorizontalDivider(color = BgBorder, thickness = 0.5.dp)
            MapLegend()
        }
    }
}

private fun DrawScope.drawOceanBackground() {
    drawRect(color = Color(0xFF020913))   // deep ocean navy
}

// Convert geographic lon/lat to canvas pixel offset using Mercator projection
private fun lonLatToOffset(lon: Float, lat: Float, w: Float, h: Float): Offset {
    val x = (lon + 180f) / 360f * w
    val y = (90f - lat) / 180f * h
    return Offset(x, y)
}

private fun DrawScope.drawGridLines() {
    val w = size.width
    val h = size.height
    for (i in 1..8) {
        val y = h / 9f * i
        val alpha = if (i == 4 || i == 5) 0.14f else 0.05f
        drawLine(
            color       = CyanPrimary.copy(alpha = alpha),
            start       = Offset(0f, y),
            end         = Offset(w, y),
            strokeWidth = if (i == 4 || i == 5) 1.0f else 0.6f
        )
    }
    for (i in 1..10) {
        val x = w / 11f * i
        val alpha = if (i == 5 || i == 6) 0.10f else 0.05f
        drawLine(
            color       = CyanPrimary.copy(alpha = alpha),
            start       = Offset(x, 0f),
            end         = Offset(x, h),
            strokeWidth = if (i == 5 || i == 6) 1.0f else 0.6f
        )
    }
}

private fun DrawScope.drawContinents() {
    val w = size.width
    val h = size.height
    // Land slightly lighter than ocean so continents read clearly on dark bg
    val landFill   = Color(0xFF0B2540)
    val landStroke = Color(0xFF1C5A8A).copy(alpha = 0.55f)

    LANDMASSES.forEach { pts ->
        if (pts.size < 4) return@forEach
        val path = Path().apply {
            val first = lonLatToOffset(pts[0], pts[1], w, h)
            moveTo(first.x, first.y)
            var i = 2
            while (i + 1 < pts.size) {
                val p = lonLatToOffset(pts[i], pts[i + 1], w, h)
                lineTo(p.x, p.y)
                i += 2
            }
            close()
        }
        drawPath(path, color = landFill)
        drawPath(path, color = landStroke, style = Stroke(width = 0.9f))
    }
}

private fun DrawScope.drawArcFlows(markers: List<GeoMarker>) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    markers.forEach { m ->
        val sx     = m.x * size.width
        val sy     = m.y * size.height
        val color  = if (m.type == "BLOCKED") RedCritical else CyanPrimary
        val ctrlX  = (sx + cx) / 2f
        val ctrlY  = ((sy + cy) / 2f) - size.height * 0.14f
        val path   = Path().apply {
            moveTo(sx, sy)
            quadraticBezierTo(ctrlX, ctrlY, cx, cy)
        }
        drawPath(
            path  = path,
            color = color.copy(alpha = 0.20f),
            style = Stroke(width = 1.0f, cap = StrokeCap.Round)
        )
    }
}

private fun DrawScope.drawDeviceNode() {
    val center = Offset(size.width / 2f, size.height / 2f)
    drawCircle(CyanPrimary.copy(alpha = 0.07f), radius = 26f, center = center)
    drawCircle(CyanPrimary.copy(alpha = 0.16f), radius = 14f, center = center)
    drawCircle(CyanPrimary.copy(alpha = 0.80f), radius =  6f, center = center)
}

private fun DrawScope.drawGeoMarker(marker: GeoMarker) {
    val cx    = marker.x * size.width
    val cy    = marker.y * size.height
    val color = if (marker.type == "BLOCKED") RedCritical else CyanPrimary
    // Inner dot grows from 3f (count=1) to 8f (count≥200); glow rings scale proportionally
    val inner = 3f + (marker.count.coerceAtMost(200).toFloat() / 200f) * 5f
    drawCircle(color.copy(alpha = 0.08f), radius = inner * 4.5f, center = Offset(cx, cy))
    drawCircle(color.copy(alpha = 0.22f), radius = inner * 2.5f, center = Offset(cx, cy))
    drawCircle(color,                     radius = inner,         center = Offset(cx, cy))
}

@Composable
private fun MapLegend() {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        LegendDot(color = CyanPrimary, label = stringResource(R.string.legend_allowed_traffic))
        Spacer(Modifier.weight(1f))
        LegendDot(color = CyanPrimary.copy(alpha = 0.80f), label = stringResource(R.string.legend_your_device), size = 6)
    }
}

@Composable
private fun LegendDot(color: Color, label: String, size: Int = 8) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(size.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top Blocked Domains Card — real data, with VPN-off empty state
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TopBlockedDomainsCard(domains: List<ResolvedDomain>) {
    val blocked = domains.filter { it.type == "BLOCKED" }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(1.dp, BgBorder, RoundedCornerShape(12.dp))
    ) {
        if (blocked.isEmpty()) {
            DomainsEmptyState()
        } else {
            Column {
                blocked.forEachIndexed { i, entry ->
                    DomainRow(rank = i + 1, entry = entry)
                    if (i < blocked.lastIndex) {
                        HorizontalDivider(
                            modifier  = Modifier.padding(horizontal = 14.dp),
                            color     = BgBorder.copy(alpha = 0.5f),
                            thickness = 0.5.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DomainsEmptyState() {
    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF062028)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = Icons.Default.WifiOff,
                contentDescription = null,
                tint               = TextMuted,
                modifier           = Modifier.size(20.dp)
            )
        }
        Text(
            text       = stringResource(R.string.map_domains_empty_title),
            style      = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color      = TextSecondary
        )
        Text(
            text  = stringResource(R.string.map_domains_empty_subtitle),
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
        )
    }
}

@Composable
private fun DomainRow(rank: Int, entry: ResolvedDomain) {
    // Blocked websites never reached a server, so there is no address or country to show.
    val ipText  = stringResource(R.string.map_blocked_no_server)
    val ipColor = TextMuted

    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(BgBorder.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text       = rank.toString(),
                fontSize   = 10.sp,
                fontWeight = FontWeight.Bold,
                color      = TextMuted
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = entry.domain,
                style      = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color      = TextPrimary,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
            Text(
                text  = ipText,
                style = MaterialTheme.typography.labelSmall,
                color = ipColor
            )
        }

        entry.countryCode?.let { code ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(CyanPrimary.copy(alpha = 0.10f))
                    .border(0.5.dp, CyanPrimary.copy(alpha = 0.30f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    text       = code,
                    fontSize   = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color      = CyanPrimary
                )
            }
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(3.dp))
                .background(RedCritical.copy(alpha = 0.15f))
                .border(0.5.dp, RedCritical.copy(alpha = 0.35f), RoundedCornerShape(3.dp))
                .padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
            Text(
                text       = fmtCount(entry.count),
                fontSize   = 10.sp,
                fontWeight = FontWeight.Bold,
                color      = RedCritical
            )
        }
    }
}

private fun fmtCount(n: Int): String =
    if (n >= 1_000) "%,d".format(n) else n.toString()

