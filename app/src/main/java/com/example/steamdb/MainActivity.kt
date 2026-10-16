package com.example.steamdb

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sin

val SdbBg = Color(0xFF161920)
val SdbHeader = Color(0xFF1D212A)
val SdbPanel = Color(0xFF212630)
val SdbRowAlt = Color(0xFF1A1E26)
val SdbBorder = Color(0xFF2E3543)
val SdbBlue = Color(0xFF66C0F4)
val SdbBrightGreen = Color(0xFF00E676)
val SdbSaleBadgeBg = Color(0xFF4C6B22)
val SdbSaleBadgeText = Color(0xFFBEEE11)
val SdbRed = Color(0xFFE53935)
val SdbOrange = Color(0xFFFFB300)
val SdbTextMain = Color(0xFFE2E6ED)
val SdbTextMuted = Color(0xFF8A94A6)

private val SteamDbDarkColors = darkColorScheme(
    primary = SdbBlue,
    secondary = SdbBrightGreen,
    tertiary = SdbSaleBadgeText,
    background = SdbBg,
    surface = SdbHeader,
    surfaceVariant = SdbPanel,
    onPrimary = Color(0xFF0A0E14),
    onBackground = SdbTextMain,
    onSurface = SdbTextMain,
    onSurfaceVariant = SdbTextMuted
)

@Composable
fun SteamSmartThumb(
    appId: Int,
    primaryUrl: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val urls = remember(appId, primaryUrl) {
        buildList {
            if (primaryUrl.isNotBlank() && primaryUrl.startsWith("http")) {
                add(primaryUrl.replace("http://", "https://"))
            }
            if (appId > 0) {
                add("https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/$appId/header.jpg")
                add("https://cdn.akamai.steamstatic.com/steam/apps/$appId/header.jpg")
                add("https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/$appId/capsule_231x87.jpg")
                add("https://cdn.cloudflare.steamstatic.com/steam/apps/$appId/capsule_184x69.jpg")
            }
        }.distinct()
    }

    var urlIndex by remember(appId, primaryUrl) { mutableIntStateOf(0) }
    var allFailed by remember(appId, primaryUrl) { mutableStateOf(urls.isEmpty()) }

    if (allFailed || urlIndex >= urls.size) {
        Box(
            modifier = modifier
                .background(SdbPanel)
                .border(0.5.dp, SdbBorder),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (appId > 0) "#$appId" else "BD",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = SdbBlue
            )
        }
    } else {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(urls[urlIndex])
                .crossfade(true)
                .build(),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier.background(SdbPanel),
            onError = {
                if (urlIndex + 1 < urls.size) {
                    urlIndex += 1
                } else {
                    allFailed = true
                }
            }
        )
    }
}

data class SdbGameSummary(
    val appId: Int,
    val name: String,
    val capsuleUrl: String,
    val originalCents: Int,
    val finalCents: Int,
    val discountPct: Int,
    val currentPlayers: Int = 0,
    val peak24hEstimate: Int = 0,
    val releaseDateText: String = "",
    val windows: Boolean = true,
    val mac: Boolean = false,
    val linux: Boolean = false
) {
    val isFree: Boolean get() = finalCents == 0 && originalCents == 0
    val formattedFinal: String
        get() = if (isFree) "Gratis" else String.format(Locale("es", "ES"), "%.2f €", finalCents / 100.0)
    val formattedOriginal: String
        get() = String.format(Locale("es", "ES"), "%.2f €", originalCents / 100.0)
}

data class SdbFullAppData(
    val appId: Int,
    val name: String,
    val appType: String,
    val shortDesc: String,
    val supportedLanguages: String,
    val headerImage: String,
    val developers: List<String>,
    val publishers: List<String>,
    val releaseDate: String,
    val isFree: Boolean,
    val initialFormatted: String,
    val finalFormatted: String,
    val finalCents: Int,
    val discountPct: Int,
    val metacritic: Int?,
    val positiveReviews: Int,
    val negativeReviews: Int,
    val reviewScoreDesc: String,
    val achievementsCount: Int,
    val packageIds: List<Int>,
    val dlcAppIds: List<Int>,
    val genres: List<String>,
    val categories: List<String>,
    val screenshots: List<String>,
    val pcRequirementsMin: String,
    val windows: Boolean,
    val mac: Boolean,
    val linux: Boolean,
    val currentPlayers: Int,
    val peakTodayEstimate: Int,
    val allTimePeakEstimate: Int
)

data class SdbRegionalPrice(
    val regionKey: String,
    val currencyCode: String,
    val currencyLabel: String,
    val flag: String,
    val localPriceFormatted: String,
    val discountPct: Int,
    val priceInEur: Double,
    val diffPercentageVsEur: Double
)

data class SdbPatchNote(
    val gid: String,
    val title: String,
    val dateFormatted: String,
    val contentsClean: String,
    val feedLabel: String
)

data class SdbSaleEvent(
    val name: String,
    val category: String,
    val startDateMillis: Long,
    val endDateMillis: Long,
    val dateRangeText: String,
    val description: String
)

object SteamSalesSchedule {
    val officialEvents = listOf(
        SdbSaleEvent(
            name = "Steam Scream Fest (Halloween Sale)",
            category = "FESTIVAL TEMÁTICO",
            startDateMillis = 1792947600000L,
            endDateMillis = 1793552400000L,
            dateRangeText = "27 Oct - 03 Nov 2026",
            description = "Descuentos especiales en juegos de terror, supervivencia y misterio."
        ),
        SdbSaleEvent(
            name = "Steam Autumn Sale (Gran Rebaja de Otoño)",
            category = "MAJOR SEASONAL SALE",
            startDateMillis = 1795539600000L,
            endDateMillis = 1796144400000L,
            dateRangeText = "26 Nov - 03 Dic 2026",
            description = "Gran rebaja estacional de otoño y nominaciones a los Steam Awards."
        ),
        SdbSaleEvent(
            name = "Steam Winter Sale (Gran Rebaja de Invierno)",
            category = "MAJOR SEASONAL SALE",
            startDateMillis = 1797440400000L,
            endDateMillis = 1798909200000L,
            dateRangeText = "18 Dic 2026 - 04 Ene 2027",
            description = "El mayor evento de ofertas del año en todo el catálogo de Steam."
        ),
        SdbSaleEvent(
            name = "Steam Next Fest: February Edition",
            category = "FESTIVAL DE DEMOS",
            startDateMillis = 1802019600000L,
            endDateMillis = 1802624400000L,
            dateRangeText = "09 Feb - 16 Feb 2027",
            description = "Festival de demos jugables de próximos lanzamientos."
        ),
        SdbSaleEvent(
            name = "Steam Spring Sale (Gran Rebaja de Primavera)",
            category = "MAJOR SEASONAL SALE",
            startDateMillis = 1805216400000L,
            endDateMillis = 1805821200000L,
            dateRangeText = "18 Mar - 25 Mar 2027",
            description = "Rebaja estacional de primavera en miles de títulos."
        ),
        SdbSaleEvent(
            name = "Steam Summer Sale (Gran Rebaja de Verano)",
            category = "MAJOR SEASONAL SALE",
            startDateMillis = 1813770000000L,
            endDateMillis = 1814979600000L,
            dateRangeText = "25 Jun - 09 Jul 2027",
            description = "Gran rebaja anual de verano con descuentos masivos."
        )
    )
}

object ValveSteamClient {

    private val httpSemaphore = Semaphore(10)

    fun getJson(urlStr: String): String {
        return try {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", "SteamDB-Android/1.0.9")
                setRequestProperty("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
            }
            try {
                if (conn.responseCode in 200..299) {
                    conn.inputStream.bufferedReader().use { it.readText() }
                } else ""
            } finally {
                conn.disconnect()
            }
        } catch (_: Throwable) {
            ""
        }
    }

    private fun stripHtml(raw: String): String {
        return raw
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]*>"), "")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("\\[/?[a-zA-Z0-9_=\\s]+\\]"), "")
            .trim()
    }

    private fun extractBestImageUrl(item: JSONObject, appId: Int): String {
        val keys = listOf("large_capsule_image", "header_image", "small_capsule_image", "tiny_image", "capsule_image")
        for (k in keys) {
            val v = item.optString(k, "").trim()
            if (v.startsWith("http")) return v.replace("http://", "https://")
        }
        return "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/$appId/header.jpg"
    }

    suspend fun fetchLivePlayers(appId: Int): Int = withContext(Dispatchers.IO) {
        val raw = getJson("https://api.steampowered.com/ISteamUserStats/GetNumberOfCurrentPlayers/v1/?appid=$appId")
        if (raw.isBlank()) return@withContext 0
        try {
            JSONObject(raw).optJSONObject("response")?.optInt("player_count", 0) ?: 0
        } catch (_: Throwable) {
            0
        }
    }

    suspend fun fetchLiveSuggestions(query: String): List<SdbGameSummary> = withContext(Dispatchers.IO) {
        try {
            val clean = query.trim()
            if (clean.isEmpty()) return@withContext emptyList()
            val encoded = URLEncoder.encode(clean, "UTF-8")
            val raw = getJson("https://store.steampowered.com/api/storesearch/?term=$encoded&l=spanish&cc=es")
            if (raw.isBlank()) return@withContext emptyList()

            val items = JSONObject(raw).optJSONArray("items") ?: return@withContext emptyList()
            val out = mutableListOf<SdbGameSummary>()

            for (i in 0 until minOf(items.length(), 14)) {
                val obj = items.optJSONObject(i) ?: continue
                val id = obj.optInt("id", 0)
                if (id <= 0) continue

                val priceObj = obj.optJSONObject("price")
                val initial = priceObj?.optInt("initial", 0) ?: 0
                val finalP = priceObj?.optInt("final", 0) ?: 0
                val disc = if (initial > 0 && finalP < initial) ((initial - finalP) * 100) / initial else 0

                out.add(
                    SdbGameSummary(
                        appId = id,
                        name = obj.optString("name", "App $id"),
                        capsuleUrl = extractBestImageUrl(obj, id),
                        originalCents = initial,
                        finalCents = finalP,
                        discountPct = disc
                    )
                )
            }
            out.distinctBy { it.appId }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    suspend fun fetchStoreCategories(): Map<String, List<SdbGameSummary>> = withContext(Dispatchers.IO) {
        try {
            val raw = getJson("https://store.steampowered.com/api/featuredcategories?cc=es&l=spanish")
            if (raw.isBlank()) return@withContext emptyMap()

            val root = JSONObject(raw)
            val result = mutableMapOf<String, List<SdbGameSummary>>()

            for (key in listOf("top_sellers", "specials", "coming_soon", "new_releases")) {
                val catObj = root.optJSONObject(key)
                result[key] = parseCategoryArray(catObj).distinctBy { it.appId }
            }
            result
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    private fun parseCategoryArray(catObj: JSONObject?): List<SdbGameSummary> {
        if (catObj == null) return emptyList()
        val arr = catObj.optJSONArray("items") ?: return emptyList()
        val out = mutableListOf<SdbGameSummary>()

        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val id = item.optInt("id", 0)
            if (id <= 0) continue

            out.add(
                SdbGameSummary(
                    appId = id,
                    name = item.optString("name", "App $id"),
                    capsuleUrl = extractBestImageUrl(item, id),
                    originalCents = item.optInt("original_price", 0),
                    finalCents = item.optInt("final_price", 0),
                    discountPct = item.optInt("discount_percent", 0),
                    windows = item.optBoolean("windows_available", true),
                    mac = item.optBoolean("mac_available", false),
                    linux = item.optBoolean("linux_available", false)
                )
            )
        }
        return out.distinctBy { it.appId }
    }

    private val knownTop100Names = mapOf(
        730 to "Counter-Strike 2", 570 to "Dota 2", 578080 to "PUBG: BATTLEGROUNDS",
        1172470 to "Apex Legends", 271590 to "Grand Theft Auto V", 252490 to "Rust",
        1245620 to "ELDEN RING", 1086940 to "Baldur's Gate 3", 1091500 to "Cyberpunk 2077",
        553850 to "HELLDIVERS 2", 431960 to "Wallpaper Engine", 230410 to "Warframe",
        1623730 to "Palworld", 2358720 to "Black Myth: Wukong", 359550 to "Tom Clancy's Rainbow Six Siege",
        105600 to "Terraria", 413150 to "Stardew Valley", 294100 to "RimWorld",
        892970 to "Valheim", 1938090 to "Call of Duty", 236390 to "War Thunder",
        440 to "Team Fortress 2", 252950 to "Rocket League", 381210 to "Dead by Daylight",
        1174180 to "Red Dead Redemption 2", 1085660 to "Destiny 2", 238960 to "Path of Exile",
        582010 to "Monster Hunter: World", 1599340 to "Lost Ark", 322330 to "Don't Starve Together",
        221100 to "DayZ", 394360 to "Hearts of Iron IV", 289070 to "Sid Meier's Civilization VI",
        4000 to "Garry's Mod", 550 to "Left 4 Dead 2", 620 to "Portal 2",
        292030 to "The Witcher 3: Wild Hunt", 377160 to "Fallout 4", 489830 to "The Elder Scrolls V: Skyrim",
        261550 to "Mount & Blade II: Bannerlord", 1142710 to "Total War: WARHAMMER III",
        281990 to "Stellaris", 1158310 to "Crusader Kings III", 227300 to "Euro Truck Simulator 2",
        244850 to "Space Engineers", 346110 to "ARK: Survival Evolved", 2399830 to "ARK: Survival Ascended",
        1966720 to "Lethal Company", 960090 to "Bloons TD 6", 648800 to "Raft",
        1426210 to "It Takes Two", 1222670 to "The Sims 4", 1203220 to "NARAKA: BLADEPOINT",
        1818450 to "STALCRAFT: X", 1643320 to "S.T.A.L.K.E.R. 2: Heart of Chornobyl",
        218620 to "PAYDAY 2", 739630 to "Phasmophobia", 427520 to "Factorio",
        526870 to "Satisfactory", 548430 to "Deep Rock Galactic", 242760 to "The Forest",
        1326470 to "Sons Of The Forest", 304930 to "Unturned", 1097150 to "Fall Guys",
        1903340 to "Clair Obscur: Expedition 33", 2246340 to "Monster Hunter Wilds",
        2767030 to "Marvel Rivals", 2694490 to "Path of Exile 2", 2507950 to "Delta Force"
    )

    suspend fun fetchTop100MostPlayedGames(knownCatalog: List<SdbGameSummary>): List<SdbGameSummary> =
        withContext(Dispatchers.IO) {
            try {
                val catalogMap = knownCatalog.associateBy { it.appId }
                val raw = getJson("https://api.steampowered.com/ISteamChartsService/GetMostPlayedGames/v1/")

                if (raw.isNotBlank()) {
                    val ranks = JSONObject(raw).optJSONObject("response")?.optJSONArray("ranks")
                    if (ranks != null && ranks.length() > 0) {
                        val parsedRanks = mutableListOf<Triple<Int, Int, Int>>()
                        val seenIds = HashSet<Int>()
                        for (i in 0 until minOf(ranks.length(), 100)) {
                            val rObj = ranks.optJSONObject(i) ?: continue
                            val id = rObj.optInt("appid", 0)
                            val peak = rObj.optInt("peak_in_game", 0)
                            if (id > 0 && seenIds.add(id)) {
                                parsedRanks.add(Triple(id, i + 1, peak))
                            }
                        }

                        return@withContext parsedRanks.mapIndexed { idx, (id, _, peak) ->
                            async {
                                val cached = catalogMap[id]
                                var title = cached?.name ?: knownTop100Names[id]
                                var thumb = cached?.capsuleUrl
                                    ?: "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/$id/header.jpg"

                                if (title == null && idx < 45) {
                                    httpSemaphore.withPermit {
                                        val detailJson = getJson("https://store.steampowered.com/api/appdetails?appids=$id&cc=es&l=spanish&filters=basic")
                                        if (detailJson.isNotBlank()) {
                                            val d = JSONObject(detailJson).optJSONObject(id.toString())?.optJSONObject("data")
                                            if (d != null) {
                                                title = d.optString("name", "Steam App #$id")
                                                val hImg = d.optString("header_image", "")
                                                if (hImg.startsWith("http")) thumb = hImg.replace("http://", "https://")
                                            }
                                        }
                                    }
                                }

                                val liveNow = if (idx < 25) {
                                    httpSemaphore.withPermit { fetchLivePlayers(id) }
                                } else 0

                                val effectiveCurrent = if (liveNow > 0) liveNow else (peak * 0.78).toInt().coerceAtLeast(120)
                                val effectivePeak = maxOf(peak, effectiveCurrent)

                                SdbGameSummary(
                                    appId = id,
                                    name = title ?: "Steam Game (AppID $id)",
                                    capsuleUrl = thumb,
                                    originalCents = cached?.originalCents ?: 0,
                                    finalCents = cached?.finalCents ?: 0,
                                    discountPct = cached?.discountPct ?: 0,
                                    currentPlayers = effectiveCurrent,
                                    peak24hEstimate = effectivePeak
                                )
                            }
                        }.awaitAll().distinctBy { it.appId }.sortedByDescending { it.currentPlayers }
                    }
                }
            } catch (_: Throwable) {}

            knownTop100Names.entries.take(40).map { (id, name) ->
                async {
                    httpSemaphore.withPermit {
                        val live = fetchLivePlayers(id)
                        SdbGameSummary(
                            appId = id,
                            name = name,
                            capsuleUrl = "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/$id/header.jpg",
                            originalCents = 0,
                            finalCents = 0,
                            discountPct = 0,
                            currentPlayers = live,
                            peak24hEstimate = (live * 1.22).toInt()
                        )
                    }
                }
            }.awaitAll().filter { it.currentPlayers > 0 }.distinctBy { it.appId }.sortedByDescending { it.currentPlayers }
        }

    suspend fun fetchUpcomingCalendar(seedList: List<SdbGameSummary>): List<SdbGameSummary> =
        withContext(Dispatchers.IO) {
            val extra = mutableListOf<SdbGameSummary>()
            for (filter in listOf("comingsoon", "popularcomingsoon")) {
                try {
                    val raw = getJson("https://store.steampowered.com/search/results/?filter=$filter&json=1&count=40&cc=es&l=spanish")
                    if (raw.isBlank()) continue
                    val arr = JSONObject(raw).optJSONArray("items") ?: continue
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val logo = o.optString("logo", "")
                        val id = Regex("/apps/(\\d+)/").find(logo)?.groupValues?.get(1)?.toIntOrNull() ?: continue
                        extra.add(
                            SdbGameSummary(
                                appId = id,
                                name = o.optString("name", "App $id"),
                                capsuleUrl = "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/$id/header.jpg",
                                originalCents = 0,
                                finalCents = 0,
                                discountPct = 0
                            )
                        )
                    }
                } catch (_: Throwable) {}
            }
            (seedList + extra).distinctBy { it.appId }.take(60).map { item ->
                async {
                    httpSemaphore.withPermit {
                        val raw = getJson("https://store.steampowered.com/api/appdetails?appids=${item.appId}&cc=es&l=spanish&filters=basic,release_date,price_overview")
                        var releaseStr = "Próximamente"
                        var headerImg = item.capsuleUrl
                        var finalC = item.finalCents
                        if (raw.isNotBlank()) {
                            try {
                                val data = JSONObject(raw).optJSONObject(item.appId.toString())?.optJSONObject("data")
                                if (data != null) {
                                    releaseStr = data.optJSONObject("release_date")?.optString("date", "Próximamente") ?: "Próximamente"
                                    val apiHeader = data.optString("header_image", "")
                                    if (apiHeader.startsWith("http")) {
                                        headerImg = apiHeader.replace("http://", "https://")
                                    }
                                    data.optJSONObject("price_overview")?.optInt("final", 0)?.let { if (it > 0) finalC = it }
                                }
                            } catch (_: Throwable) {}
                        }
                        item.copy(releaseDateText = releaseStr, capsuleUrl = headerImg, finalCents = finalC, originalCents = maxOf(item.originalCents, finalC))
                    }
                }
            }.awaitAll().distinctBy { it.appId }
        }

    suspend fun fetchFullAppInspector(appId: Int): SdbFullAppData? = withContext(Dispatchers.IO) {
        try {
            val detailsDeferred = async { getJson("https://store.steampowered.com/api/appdetails?appids=$appId&cc=es&l=spanish") }
            val playersDeferred = async { fetchLivePlayers(appId) }
            val reviewsDeferred = async { getJson("https://store.steampowered.com/appreviews/$appId?json=1&language=all&purchase_type=all") }

            val rawDetails = detailsDeferred.await()
            if (rawDetails.isBlank()) return@withContext null

            val wrapper = JSONObject(rawDetails).optJSONObject(appId.toString()) ?: return@withContext null
            if (!wrapper.optBoolean("success", false)) return@withContext null
            val data = wrapper.optJSONObject("data") ?: return@withContext null

            val livePlayers = playersDeferred.await()
            val rawReviews = reviewsDeferred.await()

            var posRev = 0
            var negRev = 0
            var revDesc = "Valoraciones verificadas"
            if (rawReviews.isNotBlank()) {
                val qSum = JSONObject(rawReviews).optJSONObject("query_summary")
                if (qSum != null) {
                    posRev = qSum.optInt("total_positive", 0)
                    negRev = qSum.optInt("total_negative", 0)
                    revDesc = qSum.optString("review_score_desc", "Valoraciones")
                }
            }

            val priceObj = data.optJSONObject("price_overview")
            val isFree = data.optBoolean("is_free", false)
            val finalCents = priceObj?.optInt("final", 0) ?: 0
            val disc = priceObj?.optInt("discount_percent", 0) ?: 0

            val devs = mutableListOf<String>()
            data.optJSONArray("developers")?.let { for (i in 0 until it.length()) devs.add(it.optString(i)) }

            val pubs = mutableListOf<String>()
            data.optJSONArray("publishers")?.let { for (i in 0 until it.length()) pubs.add(it.optString(i)) }

            val packages = mutableListOf<Int>()
            data.optJSONArray("packages")?.let { for (i in 0 until it.length()) packages.add(it.optInt(i)) }

            val dlcs = mutableListOf<Int>()
            data.optJSONArray("dlc")?.let { for (i in 0 until minOf(it.length(), 40)) dlcs.add(it.optInt(i)) }

            val genres = mutableListOf<String>()
            data.optJSONArray("genres")?.let {
                for (i in 0 until it.length()) it.optJSONObject(i)?.optString("description")?.let { g -> genres.add(g) }
            }

            val cats = mutableListOf<String>()
            data.optJSONArray("categories")?.let {
                for (i in 0 until it.length()) it.optJSONObject(i)?.optString("description")?.let { c -> cats.add(c) }
            }

            val shots = mutableListOf<String>()
            data.optJSONArray("screenshots")?.let {
                for (i in 0 until minOf(it.length(), 12)) {
                    it.optJSONObject(i)?.optString("path_full")?.let { s -> shots.add(s.replace("http://", "https://")) }
                }
            }

            val pcReq = data.optJSONObject("pc_requirements")?.optString("minimum", "")?.let { stripHtml(it) } ?: "No especificado"
            val langs = stripHtml(data.optString("supported_languages", "Español, Inglés"))
            val relObj = data.optJSONObject("release_date")
            val plat = data.optJSONObject("platforms")

            val peak24 = (livePlayers * 1.24).toInt()
            val allTimeEst = maxOf((livePlayers * 3.4).toInt(), (posRev + negRev) / 3)
            val rawHeader = data.optString("header_image", "").replace("http://", "https://")

            SdbFullAppData(
                appId = appId,
                name = data.optString("name", "App $appId"),
                appType = data.optString("type", "game").uppercase(),
                shortDesc = stripHtml(data.optString("short_description", "Sin descripción.")),
                supportedLanguages = langs,
                headerImage = rawHeader.ifBlank { "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/$appId/header.jpg" },
                developers = devs.ifEmpty { listOf("N/A") },
                publishers = pubs.ifEmpty { listOf("N/A") },
                releaseDate = relObj?.optString("date", "N/A") ?: "N/A",
                isFree = isFree,
                initialFormatted = priceObj?.optString("initial_formatted", "").orEmpty(),
                finalFormatted = when {
                    isFree -> "Gratis (Free to Play)"
                    priceObj != null -> priceObj.optString("final_formatted", "N/A")
                    else -> "N/A"
                },
                finalCents = finalCents,
                discountPct = disc,
                metacritic = data.optJSONObject("metacritic")?.optInt("score"),
                positiveReviews = posRev,
                negativeReviews = negRev,
                reviewScoreDesc = revDesc,
                achievementsCount = data.optJSONObject("achievements")?.optInt("total") ?: 0,
                packageIds = packages.distinct(),
                dlcAppIds = dlcs.distinct(),
                genres = genres,
                categories = cats,
                screenshots = shots.distinct(),
                pcRequirementsMin = pcReq,
                windows = plat?.optBoolean("windows", true) ?: true,
                mac = plat?.optBoolean("mac", false) ?: false,
                linux = plat?.optBoolean("linux", false) ?: false,
                currentPlayers = livePlayers,
                peakTodayEstimate = peak24,
                allTimePeakEstimate = allTimeEst
            )
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun fetchAll41SteamCurrencies(appId: Int): List<SdbRegionalPrice> = withContext(Dispatchers.IO) {
        data class SteamCurrencySpec(
            val cc: String,
            val code: String,
            val label: String,
            val flag: String,
            val rateToEur: Double
        )

        val all41Currencies = listOf(
            SteamCurrencySpec("es", "EUR", "European Union Euro", "🇪🇸", 1.00),
            SteamCurrencySpec("az", "USD_CIS", "CIS - U.S. Dollar", "🇷🇺", 0.92),
            SteamCurrencySpec("ar", "USD_LATAM", "LATAM - U.S. Dollar", "🇦🇷", 0.92),
            SteamCurrencySpec("tr", "USD_MENA", "MENA - U.S. Dollar", "🇹🇷", 0.92),
            SteamCurrencySpec("pk", "USD_SASIA", "SASIA - U.S. Dollar", "🇵🇰", 0.92),
            SteamCurrencySpec("ua", "UAH", "Ukrainian Hryvnia", "🇺🇦", 0.0223),
            SteamCurrencySpec("in", "INR", "Indian Rupee", "🇮🇳", 0.0109),
            SteamCurrencySpec("id", "IDR", "Indonesian Rupiah", "🇮🇩", 0.000058),
            SteamCurrencySpec("cn", "CNY", "Chinese Yuan", "🇨🇳", 0.128),
            SteamCurrencySpec("br", "BRL", "Brazilian Real", "🇧🇷", 0.162),
            SteamCurrencySpec("vn", "VND", "Vietnamese Dong", "🇻🇳", 0.000036),
            SteamCurrencySpec("ph", "PHP", "Philippine Peso", "🇵🇭", 0.016),
            SteamCurrencySpec("th", "THB", "Thai Baht", "🇹🇭", 0.027),
            SteamCurrencySpec("my", "MYR", "Malaysian Ringgit", "🇲🇾", 0.21),
            SteamCurrencySpec("cl", "CLP", "Chilean Peso", "🇨🇱", 0.00096),
            SteamCurrencySpec("hk", "HKD", "Hong Kong Dollar", "🇭🇰", 0.118),
            SteamCurrencySpec("kw", "KWD", "Kuwaiti Dinar", "🇰🇼", 3.00),
            SteamCurrencySpec("tw", "TWD", "Taiwan Dollar", "🇹🇼", 0.0285),
            SteamCurrencySpec("kr", "KRW", "South Korean Won", "🇰🇷", 0.00067),
            SteamCurrencySpec("kz", "KZT", "Kazakhstani Tenge", "🇰🇿", 0.0019),
            SteamCurrencySpec("sa", "SAR", "Saudi Riyal", "🇸🇦", 0.245),
            SteamCurrencySpec("jp", "JPY", "Japanese Yen", "🇯🇵", 0.0061),
            SteamCurrencySpec("pe", "PEN", "Peruvian Sol", "🇵🇪", 0.245),
            SteamCurrencySpec("uy", "UYU", "Uruguayan Peso", "🇺🇾", 0.022),
            SteamCurrencySpec("mx", "MXN", "Mexican Peso", "🇲🇽", 0.046),
            SteamCurrencySpec("ca", "CAD", "Canadian Dollar", "🇨🇦", 0.66),
            SteamCurrencySpec("nz", "NZD", "New Zealand Dollar", "🇳🇿", 0.55),
            SteamCurrencySpec("ae", "AED", "U.A.E. Dirham", "🇦🇪", 0.25),
            SteamCurrencySpec("za", "ZAR", "South African Rand", "🇿🇦", 0.051),
            SteamCurrencySpec("qa", "QAR", "Qatari Riyal", "🇶🇦", 0.253),
            SteamCurrencySpec("sg", "SGD", "Singapore Dollar", "🇸🇬", 0.69),
            SteamCurrencySpec("co", "COP", "Colombian Peso", "🇨🇴", 0.00022),
            SteamCurrencySpec("au", "AUD", "Australian Dollar", "🇦🇺", 0.60),
            SteamCurrencySpec("cr", "CRC", "Costa Rican Colon", "🇨🇷", 0.0018),
            SteamCurrencySpec("us", "USD", "U.S. Dollar", "🇺🇸", 0.92),
            SteamCurrencySpec("gb", "GBP", "British Pound", "🇬🇧", 1.19),
            SteamCurrencySpec("pl", "PLN", "Polish Zloty", "🇵🇱", 0.232),
            SteamCurrencySpec("il", "ILS", "Israeli New Shekel", "🇮🇱", 0.25),
            SteamCurrencySpec("ch", "CHF", "Swiss Franc", "🇨🇭", 1.06),
            SteamCurrencySpec("no", "NOK", "Norwegian Krone", "🇳🇴", 0.085),
            SteamCurrencySpec("ru", "RUB", "Russian Ruble", "🇷🇺", 0.0095)
        )

        val fetched = all41Currencies.map { spec ->
            async {
                httpSemaphore.withPermit {
                    try {
                        val json = getJson("https://store.steampowered.com/api/appdetails?appids=$appId&cc=${spec.cc}&filters=price_overview,basic")
                        if (json.isBlank()) return@withPermit null
                        val appObj = JSONObject(json).optJSONObject(appId.toString()) ?: return@withPermit null
                        if (!appObj.optBoolean("success", false)) return@withPermit null
                        val data = appObj.optJSONObject("data") ?: return@withPermit null

                        if (data.optBoolean("is_free", false)) {
                            return@withPermit SdbRegionalPrice(
                                regionKey = spec.cc.uppercase(),
                                currencyCode = spec.code,
                                currencyLabel = spec.label,
                                flag = spec.flag,
                                localPriceFormatted = "Gratis",
                                discountPct = 0,
                                priceInEur = 0.0,
                                diffPercentageVsEur = 0.0
                            )
                        }

                        val p = data.optJSONObject("price_overview") ?: return@withPermit null
                        val finalUnits = p.optInt("final", 0) / 100.0
                        val eurVal = finalUnits * spec.rateToEur

                        SdbRegionalPrice(
                            regionKey = spec.cc.uppercase(),
                            currencyCode = p.optString("currency", spec.code),
                            currencyLabel = spec.label,
                            flag = spec.flag,
                            localPriceFormatted = p.optString("final_formatted", "$finalUnits ${spec.code}"),
                            discountPct = p.optInt("discount_percent", 0),
                            priceInEur = eurVal,
                            diffPercentageVsEur = 0.0
                        )
                    } catch (_: Throwable) {
                        null
                    }
                }
            }
        }.awaitAll().filterNotNull()

        val baseEur = fetched.find { it.regionKey == "ES" }?.priceInEur
            ?: fetched.maxOfOrNull { it.priceInEur } ?: 0.0

        fetched.map { item ->
            val diff = if (baseEur > 0.0) ((item.priceInEur - baseEur) / baseEur) * 100.0 else 0.0
            item.copy(diffPercentageVsEur = diff)
        }.distinctBy { it.regionKey }.sortedBy { it.priceInEur }
    }

    suspend fun fetchAppPatchNotes(appId: Int): List<SdbPatchNote> = withContext(Dispatchers.IO) {
        try {
            val raw = getJson("https://api.steampowered.com/ISteamNews/GetNewsForApp/v2/?appid=$appId&count=10&maxlength=700")
            if (raw.isBlank()) return@withContext emptyList()

            val items = JSONObject(raw).optJSONObject("appnews")?.optJSONArray("newsitems") ?: return@withContext emptyList()
            val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("es", "ES"))
            val out = mutableListOf<SdbPatchNote>()

            for (i in 0 until items.length()) {
                val n = items.optJSONObject(i) ?: continue
                val ts = n.optLong("date", 0L) * 1000L
                out.add(
                    SdbPatchNote(
                        gid = n.optString("gid", "note_$i"),
                        title = n.optString("title", "Actualización"),
                        dateFormatted = if (ts > 0) sdf.format(Date(ts)) else "Reciente",
                        contentsClean = stripHtml(n.optString("contents", "")),
                        feedLabel = n.optString("feedlabel", "Patch Notes")
                    )
                )
            }
            out.distinctBy { it.gid }
        } catch (_: Throwable) {
            emptyList()
        }
    }
}

class SdbMainViewModel : ViewModel() {

    private val safeHandler = CoroutineExceptionHandler { _, _ ->
        isLoading = false
        isInspectorLoading = false
        isLoadingPricesMatrix = false
        isLoadingSuggestions = false
    }

    var favorites by mutableStateOf<List<SdbFavoriteGame>>(emptyList())
        private set
    var latestVersion by mutableStateOf<String?>(null)
        private set
    val availableUpdate: Boolean
        get() = latestVersion?.let { UpdateChecker.isNewer(it, UpdateChecker.CURRENT_VERSION) } ?: false
    val favoriteDropCount: Int
        get() = favorites.count { it.droppedByCents > 0 }

    fun loadFavorites() {
        favorites = FavoritesStore.load()
    }

    fun isFavorite(appId: Int): Boolean = favorites.any { it.appId == appId }

    fun toggleFavorite(game: SdbGameSummary) {
        favorites = if (favorites.any { it.appId == game.appId }) {
            favorites.filterNot { it.appId == game.appId }
        } else {
            favorites + SdbFavoriteGame(
                appId = game.appId,
                name = game.name,
                capsuleUrl = game.capsuleUrl,
                lastSeenCents = game.finalCents,
                discountPct = game.discountPct,
                addedAtMillis = System.currentTimeMillis()
            )
        }
        FavoritesStore.save(favorites)
    }

    fun removeFavorite(appId: Int) {
        favorites = favorites.filterNot { it.appId == appId }
        FavoritesStore.save(favorites)
    }

    fun setFavoriteTargetPrice(appId: Int, euros: Double) {
        favorites = favorites.map {
            if (it.appId == appId) it.copy(targetPriceCents = (euros * 100).toInt()) else it
        }
        FavoritesStore.save(favorites)
    }

    private fun updateFavoritePrices(catalog: List<SdbGameSummary>) {
        if (favorites.isEmpty()) return
        val priceMap = catalog.filter { it.finalCents > 0 }.associateBy { it.appId }
        if (priceMap.isEmpty()) return
        var changed = false
        val updated = favorites.map { f ->
            val g = priceMap[f.appId]
            if (g != null && g.finalCents != f.lastSeenCents) {
                changed = true
                val drop = if (f.lastSeenCents > 0 && g.finalCents < f.lastSeenCents) f.lastSeenCents - g.finalCents else 0
                f.copy(lastSeenCents = g.finalCents, discountPct = g.discountPct, droppedByCents = drop)
            } else f
        }
        if (changed) {
            favorites = updated
            FavoritesStore.save(favorites)
        }
    }

    var isLoading by mutableStateOf(true)
        private set

    var mostPlayedGames by mutableStateOf<List<SdbGameSummary>>(emptyList())
        private set
    var topWantedGames by mutableStateOf<List<SdbGameSummary>>(emptyList())
        private set
    var activeSalesGames by mutableStateOf<List<SdbGameSummary>>(emptyList())
        private set
    var upcomingCalendarGames by mutableStateOf<List<SdbGameSummary>>(emptyList())
        private set

    var topSearchText by mutableStateOf("")
        private set
    var liveSuggestions by mutableStateOf<List<SdbGameSummary>>(emptyList())
        private set
    var isLoadingSuggestions by mutableStateOf(false)
        private set
    private var debounceJob: Job? = null

    var calcCart by mutableStateOf<List<SdbGameSummary>>(emptyList())
        private set

    var inspectorAppData by mutableStateOf<SdbFullAppData?>(null)
        private set
    var inspectorPrices by mutableStateOf<List<SdbRegionalPrice>>(emptyList())
        private set
    var inspectorNews by mutableStateOf<List<SdbPatchNote>>(emptyList())
        private set
    var isInspectorLoading by mutableStateOf(false)
        private set
    var isLoadingPricesMatrix by mutableStateOf(false)
        private set
    var isInspectorVisible by mutableStateOf(false)
        private set

    init {
        favorites = FavoritesStore.load()
        refreshAllSteamData()
    }

    fun onTopSearchQueryChanged(newValue: String) {
        topSearchText = newValue
        debounceJob?.cancel()

        val clean = newValue.trim()
        if (clean.isEmpty()) {
            liveSuggestions = emptyList()
            isLoadingSuggestions = false
            return
        }

        debounceJob = viewModelScope.launch(safeHandler) {
            isLoadingSuggestions = true
            delay(200L)
            try {
                liveSuggestions = ValveSteamClient.fetchLiveSuggestions(clean)
            } finally {
                isLoadingSuggestions = false
            }
        }
    }

    fun clearLiveSearch() {
        topSearchText = ""
        liveSuggestions = emptyList()
    }

    fun refreshAllSteamData() {
        viewModelScope.launch(safeHandler) {
            isLoading = true
            try {
                val updateDef = async { UpdateChecker.fetchLatestVersion() }
                val cats = ValveSteamClient.fetchStoreCategories()
                val topSellersRaw = cats["top_sellers"].orEmpty()
                val specialsRaw = cats["specials"].orEmpty()
                val comingSoonRaw = cats["coming_soon"].orEmpty()
                val newReleasesRaw = cats["new_releases"].orEmpty()

                val allKnown = (topSellersRaw + specialsRaw + newReleasesRaw + comingSoonRaw).distinctBy { it.appId }

                val top100Def = async { ValveSteamClient.fetchTop100MostPlayedGames(allKnown) }
                val upcomingDef = async { ValveSteamClient.fetchUpcomingCalendar(comingSoonRaw) }
                val wantedDef = async {
                    (topSellersRaw + newReleasesRaw).distinctBy { it.appId }.take(24).map { g ->
                        async(Dispatchers.IO) {
                            val p = ValveSteamClient.fetchLivePlayers(g.appId)
                            g.copy(currentPlayers = p, peak24hEstimate = (p * 1.25).toInt())
                        }
                    }.awaitAll().distinctBy { it.appId }
                }

                mostPlayedGames = top100Def.await()
                upcomingCalendarGames = upcomingDef.await()
                topWantedGames = wantedDef.await()
                activeSalesGames = specialsRaw.distinctBy { it.appId }.sortedByDescending { it.discountPct }

                updateFavoritePrices(allKnown + mostPlayedGames + topWantedGames + activeSalesGames)
                latestVersion = updateDef.await()

                if (calcCart.isEmpty() && topWantedGames.isNotEmpty()) {
                    calcCart = topWantedGames.filter { !it.isFree }.take(4)
                }
            } finally {
                isLoading = false
            }
        }
    }

    fun openAppInspector(appId: Int) {
        clearLiveSearch()
        viewModelScope.launch(safeHandler) {
            isInspectorVisible = true
            isInspectorLoading = true
            isLoadingPricesMatrix = true
            inspectorAppData = null
            inspectorPrices = emptyList()
            inspectorNews = emptyList()

            try {
                val appDef = async { ValveSteamClient.fetchFullAppInspector(appId) }
                val newsDef = async { ValveSteamClient.fetchAppPatchNotes(appId) }
                val pricesDef = async { ValveSteamClient.fetchAll41SteamCurrencies(appId) }

                inspectorAppData = appDef.await()
                inspectorAppData?.let { app ->
                    if (app.finalCents > 0) {
                        updateFavoritePrices(
                            listOf(
                                SdbGameSummary(
                                    appId = app.appId,
                                    name = app.name,
                                    capsuleUrl = app.headerImage,
                                    originalCents = 0,
                                    finalCents = app.finalCents,
                                    discountPct = app.discountPct
                                )
                            )
                        )
                    }
                }
                inspectorNews = newsDef.await()
                isInspectorLoading = false

                inspectorPrices = pricesDef.await()
            } finally {
                isInspectorLoading = false
                isLoadingPricesMatrix = false
            }
        }
    }

    fun closeAppInspector() {
        isInspectorVisible = false
        inspectorAppData = null
    }

    fun toggleCalculatorItem(game: SdbGameSummary) {
        calcCart = if (calcCart.any { it.appId == game.appId }) {
            calcCart.filterNot { it.appId == game.appId }
        } else {
            (calcCart + game).distinctBy { it.appId }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        FavoritesStore.init(this)
        setContent {
            MaterialTheme(colorScheme = SteamDbDarkColors) {
                SteamDbRootApp()
            }
        }
    }
}

enum class MainNavSection(
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    HOME("Portada (Home)", "Resumen general de SteamDB", Icons.Default.Home),
    CHARTS("Steam Charts (Top 100)", "Ranking en vivo de los 100 más jugados", Icons.Default.ShowChart),
    SALES("Sales Explorer", "Ofertas y descuentos activos en España", Icons.Default.LocalOffer),
    FAVORITES("Favoritos ⭐", "Vigilancia de bajadas de precio", Icons.Default.Star),
    CALENDAR("Release Calendar", "Próximos lanzamientos con póster oficial", Icons.Default.CalendarMonth),
    EVENTS("Next Steam Sales", "Cuenta atrás vinculada al próximo festival", Icons.Default.Timer),
    CALC("Calculator", "Llaves TF2 necesarias en cualquier divisa de Steam", Icons.Default.Calculate),
    ORACLE("El Oráculo de Gaben", "¿Gatillazo o ahorro? Lanza la moneda", Icons.Default.Casino)
}

@Composable
fun SteamDbRootApp(vm: SdbMainViewModel = viewModel()) {
    var currentSection by remember { mutableStateOf(MainNavSection.HOME) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    BackHandler(enabled = drawerState.isOpen || vm.isInspectorVisible || vm.topSearchText.isNotEmpty() || currentSection != MainNavSection.HOME) {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            vm.topSearchText.isNotEmpty() -> {
                vm.clearLiveSearch()
                focusManager.clearFocus()
            }
            vm.isInspectorVisible -> vm.closeAppInspector()
            currentSection != MainNavSection.HOME -> currentSection = MainNavSection.HOME
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = SdbHeader,
                drawerContentColor = SdbTextMain,
                modifier = Modifier.width(300.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SdbBg)
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SdbPanel)
                                .border(1.5.dp, SdbBlue, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("DB", fontWeight = FontWeight.Black, fontSize = 16.sp, color = SdbBlue, fontFamily = FontFamily.Monospace)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("SteamDB", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Color.White)
                            Text("Cliente Nativo v1.0.9 • 41 Divisas", fontSize = 11.sp, color = SdbBrightGreen, fontFamily = FontFamily.Monospace)
                        }
                    }
                }

                HorizontalDivider(color = SdbBorder)
                Spacer(Modifier.height(8.dp))

                MainNavSection.entries.forEach { section ->
                    val isSelected = !vm.isInspectorVisible && currentSection == section
                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                imageVector = section.icon,
                                contentDescription = section.title,
                                tint = if (isSelected) SdbBlue else SdbTextMuted
                            )
                        },
                        label = {
                            Column {
                                Text(
                                    text = section.title,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = if (isSelected) Color.White else SdbTextMain
                                )
                                Text(
                                    text = section.subtitle,
                                    fontSize = 10.sp,
                                    color = SdbTextMuted
                                )
                            }
                        },
                        selected = isSelected,
                        onClick = {
                            vm.clearLiveSearch()
                            vm.closeAppInspector()
                            currentSection = section
                            scope.launch { drawerState.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = SdbBlue.copy(alpha = 0.16f),
                            unselectedContainerColor = Color.Transparent
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }

                Spacer(Modifier.weight(1f))
                HorizontalDivider(color = SdbBorder)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Valve Store & Steamworks API", fontSize = 10.sp, color = SdbTextMuted, fontFamily = FontFamily.Monospace)
                    Badge(containerColor = SdbBrightGreen.copy(alpha = 0.2f)) {
                        Text("ONLINE", color = SdbBrightGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                Surface(color = SdbHeader, shadowElevation = 6.dp) {
                    Column(modifier = Modifier.statusBarsPadding()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (vm.isInspectorVisible) {
                                IconButton(
                                    onClick = { vm.closeAppInspector() },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = Color.White)
                                }
                            } else {
                                IconButton(
                                    onClick = { scope.launch { drawerState.open() } },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.Menu, contentDescription = "Abrir Menú", tint = SdbBlue)
                                }
                            }

                            IconButton(
                                onClick = {
                                    vm.clearLiveSearch()
                                    vm.closeAppInspector()
                                    currentSection = MainNavSection.HOME
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Home,
                                    contentDescription = "Ir a Portada Home",
                                    tint = if (!vm.isInspectorVisible && currentSection == MainNavSection.HOME) SdbBrightGreen else SdbTextMain
                                )
                            }

                            Spacer(Modifier.width(4.dp))

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(SdbBg)
                                    .border(1.5.dp, if (vm.topSearchText.isNotEmpty()) SdbBlue else SdbBorder, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = SdbBlue,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Box(modifier = Modifier.weight(1f)) {
                                        if (vm.topSearchText.isEmpty()) {
                                            Text(
                                                text = "Buscar en SteamDB...",
                                                color = SdbTextMuted,
                                                fontSize = 13.sp,
                                                maxLines = 1
                                            )
                                        }
                                        BasicTextField(
                                            value = vm.topSearchText,
                                            onValueChange = { vm.onTopSearchQueryChanged(it) },
                                            singleLine = true,
                                            textStyle = androidx.compose.ui.text.TextStyle(
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium
                                            ),
                                            cursorBrush = SolidColor(SdbBlue),
                                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                            keyboardActions = KeyboardActions(onSearch = {
                                                focusManager.clearFocus()
                                                val directId = vm.topSearchText.trim().toIntOrNull()
                                                if (directId != null) {
                                                    vm.openAppInspector(directId)
                                                } else if (vm.liveSuggestions.isNotEmpty()) {
                                                    vm.openAppInspector(vm.liveSuggestions.first().appId)
                                                }
                                            }),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    if (vm.isLoadingSuggestions) {
                                        CircularProgressIndicator(
                                            color = SdbBlue,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    if (vm.topSearchText.isNotEmpty()) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Limpiar",
                                            tint = SdbTextMuted,
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clickable { vm.clearLiveSearch() }
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.width(4.dp))
                            IconButton(
                                onClick = { vm.refreshAllSteamData() },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = "Actualizar", tint = SdbBlue)
                            }
                        }
                    }
                }
            },
            containerColor = SdbBg
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (vm.isInspectorVisible) {
                    FullScreenAppInspector(vm = vm, onBack = { vm.closeAppInspector() })
                } else {
                    when (currentSection) {
                        MainNavSection.HOME -> SteamDbHomePortalScreen(
                            vm = vm,
                            onNavigate = { target -> currentSection = target }
                        )
                        MainNavSection.CHARTS -> SteamDbChartsTab(vm)
                        MainNavSection.SALES -> SteamDbSalesTab(vm)
                        MainNavSection.FAVORITES -> SteamDbFavoritesTab(vm)
                        MainNavSection.CALENDAR -> SteamDbCalendarTab(vm)
                        MainNavSection.EVENTS -> SteamDbNextSalesTab()
                        MainNavSection.CALC -> SteamDbCalculatorTab(vm)
                        MainNavSection.ORACLE -> SteamDbGabenOracleTab(vm)
                    }
                }

                if (vm.topSearchText.trim().isNotEmpty()) {
                    LiveAutocompleteDropdownOverlay(
                        query = vm.topSearchText.trim(),
                        suggestions = vm.liveSuggestions,
                        isLoading = vm.isLoadingSuggestions,
                        onSelectGame = { appId ->
                            focusManager.clearFocus()
                            vm.openAppInspector(appId)
                        },
                        onDismiss = {
                            focusManager.clearFocus()
                            vm.clearLiveSearch()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SteamDbHomePortalScreen(
    vm: SdbMainViewModel,
    onNavigate: (MainNavSection) -> Unit
) {
    val numFmt = remember { NumberFormat.getIntegerInstance(Locale("es", "ES")) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            nowMillis = System.currentTimeMillis()
        }
    }

    if (vm.isLoading) {
        LoadingStateBox("Cargando portada de SteamDB...")
        return
    }

    val nextFutureEvent = SteamSalesSchedule.officialEvents.firstOrNull { it.startDateMillis > nowMillis }
        ?: SteamSalesSchedule.officialEvents.first()

    val targetMillis = nextFutureEvent.startDateMillis
    val remSec = ((targetMillis - nowMillis) / 1000L).coerceAtLeast(0L)
    val d = remSec / 86400
    val h = (remSec % 86400) / 3600
    val m = (remSec % 3600) / 60
    val s = remSec % 60

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SdbHeader),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SdbBlue.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "STEAMDB DATABASE PORTAL",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = SdbBlue,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Base de datos en tiempo real de precios, jugadores y paquetes de Steam.",
                                fontSize = 12.sp,
                                color = SdbTextMain
                            )
                        }
                        Button(
                            onClick = { onNavigate(MainNavSection.ORACLE) },
                            colors = ButtonDefaults.buttonColors(containerColor = SdbBlue),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("🪙 Oráculo", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFF0A0E14))
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(SdbBg)
                            .border(1.dp, SdbBorder, RoundedCornerShape(6.dp))
                            .clickable { onNavigate(MainNavSection.EVENTS) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "⏳ TIEMPO RESTANTE PARA PRÓXIMO EVENTO",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = SdbSaleBadgeText,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = nextFutureEvent.name,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = nextFutureEvent.dateRangeText,
                                fontSize = 10.sp,
                                color = SdbTextMuted
                            )
                        }
                        Text(
                            text = "${d}d ${String.format("%02d:%02d:%02d", h, m, s)}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = SdbSaleBadgeText
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = { onNavigate(MainNavSection.CHARTS) },
                            label = { Text("Top 100 Charts", fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.Default.ShowChart, contentDescription = null, modifier = Modifier.size(15.dp), tint = SdbBrightGreen) }
                        )
                        AssistChip(
                            onClick = { onNavigate(MainNavSection.SALES) },
                            label = { Text("Ofertas (${vm.activeSalesGames.size})", fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.Default.LocalOffer, contentDescription = null, modifier = Modifier.size(15.dp), tint = SdbSaleBadgeText) }
                        )
                        AssistChip(
                            onClick = { onNavigate(MainNavSection.ORACLE) },
                            label = { Text("Oráculo de Gaben", fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.Default.Casino, contentDescription = null, modifier = Modifier.size(15.dp), tint = SdbBlue) }
                        )
                        AssistChip(
                            onClick = { onNavigate(MainNavSection.FAVORITES) },
                            label = { Text("⭐ Favoritos (${vm.favorites.size})", fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(15.dp), tint = SdbSaleBadgeText) }
                        )
                        AssistChip(
                            onClick = { onNavigate(MainNavSection.CALC as MainNavSection) },
                            label = { Text("Calculadora", fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(15.dp), tint = SdbOrange) }
                        )
                    }
                }
            }
        }

        if (vm.availableUpdate) {
            item {
                val ctx = LocalContext.current
                Card(
                    colors = CardDefaults.cardColors(containerColor = SdbHeader),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().border(1.5.dp, SdbSaleBadgeText, RoundedCornerShape(8.dp))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "🚀 NUEVA VERSIÓN ${vm.latestVersion} DISPONIBLE",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = SdbSaleBadgeText,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                "Tienes la ${UpdateChecker.CURRENT_VERSION}. Descarga la APK nueva desde GitHub Releases.",
                                fontSize = 11.sp,
                                color = SdbTextMain
                            )
                        }
                        Button(
                            onClick = {
                                ctx.startActivity(
                                    android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(UpdateChecker.RELEASES_PAGE_URL))
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SdbSaleBadgeText),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("Descargar", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFF0A0E14))
                        }
                    }
                }
            }
        }

        item {
            HomePortalTableBlock(
                title = "MOST PLAYED GAMES",
                actionLabel = "Ver Top 100 →",
                accentColor = SdbBrightGreen,
                rightHeader = "PLAYERS NOW",
                games = vm.mostPlayedGames.take(8),
                onActionClick = { onNavigate(MainNavSection.CHARTS) },
                onGameClick = { vm.openAppInspector(it) },
                rightValueProvider = { g -> numFmt.format(g.currentPlayers) }
            )
        }

        item {
            HomePortalTableBlock(
                title = "POPULAR & TRENDING RELEASES",
                actionLabel = "Ver Ranking →",
                accentColor = SdbBlue,
                rightHeader = "PRECIO / DTO",
                games = vm.topWantedGames.take(8),
                onActionClick = { onNavigate(MainNavSection.CHARTS) },
                onGameClick = { vm.openAppInspector(it) },
                rightValueProvider = { g ->
                    if (g.discountPct > 0) "-${g.discountPct}%  ${g.formattedFinal}" else g.formattedFinal
                }
            )
        }

        item {
            HomePortalTableBlock(
                title = "HOT SALES & BIGGEST DISCOUNTS",
                actionLabel = "Ver Ofertas →",
                accentColor = SdbSaleBadgeText,
                rightHeader = "OFERTA ES",
                games = vm.activeSalesGames.take(8),
                onActionClick = { onNavigate(MainNavSection.SALES) },
                onGameClick = { vm.openAppInspector(it) },
                rightValueProvider = { g -> "-${g.discountPct}%  ${g.formattedFinal}" }
            )
        }
    }
}

@Composable
fun HomePortalTableBlock(
    title: String,
    actionLabel: String,
    accentColor: Color,
    rightHeader: String,
    games: List<SdbGameSummary>,
    onActionClick: () -> Unit,
    onGameClick: (Int) -> Unit,
    rightValueProvider: (SdbGameSummary) -> String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SdbHeader),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SdbBorder, RoundedCornerShape(8.dp))
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SdbPanel)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = accentColor,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = actionLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = SdbBlue,
                    modifier = Modifier.clickable { onActionClick() }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SdbBg)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("APPLICATION", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = SdbTextMuted, fontFamily = FontFamily.Monospace)
                Text(rightHeader, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = SdbTextMuted, fontFamily = FontFamily.Monospace)
            }

            games.forEachIndexed { idx, g ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (idx % 2 == 0) SdbHeader else SdbRowAlt)
                        .border(0.5.dp, SdbBorder)
                        .clickable { onGameClick(g.appId) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SteamSmartThumb(
                        appId = g.appId,
                        primaryUrl = g.capsuleUrl,
                        contentDescription = g.name,
                        modifier = Modifier
                            .width(64.dp)
                            .height(30.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = g.name,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "AppID ${g.appId}",
                            fontSize = 10.sp,
                            color = SdbTextMuted,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(
                        text = rightValueProvider(g),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        color = accentColor
                    )
                }
            }
        }
    }
}

@Composable
fun SteamDbChartsTab(vm: SdbMainViewModel) {
    var subTab by remember { mutableIntStateOf(0) }
    var displayLimit by remember { mutableIntStateOf(25) }
    val numFmt = remember { NumberFormat.getIntegerInstance(Locale("es", "ES")) }

    if (vm.isLoading) {
        LoadingStateBox("Sincronizando el Top 100 de jugadores simultáneos de Steam...")
        return
    }

    val maxConcurrent = (vm.mostPlayedGames.maxOfOrNull { it.currentPlayers } ?: 1).coerceAtLeast(1)
    val totalInChart = vm.mostPlayedGames.sumOf { it.currentPlayers }
    val visibleMostPlayed = vm.mostPlayedGames.take(displayLimit)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SdbHeader),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, SdbBorder, RoundedCornerShape(8.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("STEAM CHARTS • TOP ${vm.mostPlayedGames.size} LIVE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SdbBlue, fontFamily = FontFamily.Monospace)
                            Text("${numFmt.format(totalInChart)} jugadores en el Top", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = SdbBrightGreen)
                        }
                        Badge(containerColor = SdbBrightGreen.copy(alpha = 0.2f)) {
                            Text("⚡ TOP 100 API", color = SdbBrightGreen, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    val top10 = vm.mostPlayedGames.take(10)
                    if (top10.isNotEmpty()) {
                        LivePlayersBarChartCanvas(games = top10, maxPlayers = maxConcurrent)
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(SdbHeader)
                    .border(1.dp, SdbBorder, RoundedCornerShape(6.dp))
                    .padding(3.dp)
            ) {
                val titles = listOf("Más Jugados (Top 100)", "Más Deseados / Populares")
                titles.forEachIndexed { idx, label ->
                    val active = subTab == idx
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (active) SdbPanel else Color.Transparent)
                            .clickable { subTab = idx }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (active) SdbBlue else SdbTextMuted
                        )
                    }
                }
            }
        }

        if (subTab == 0) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Mostrar:", fontSize = 11.sp, color = SdbTextMuted, fontFamily = FontFamily.Monospace)
                    listOf(25 to "Top 25", 50 to "Top 50", 100 to "Ver Top 100").forEach { (limit, label) ->
                        FilterChip(
                            selected = displayLimit == limit,
                            onClick = { displayLimit = limit },
                            label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SdbPanel)
                    .border(1.dp, SdbBorder)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("#", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SdbTextMuted, modifier = Modifier.width(28.dp))
                Text("JUEGO / APPID", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SdbTextMuted, modifier = Modifier.weight(1f))
                if (subTab == 0) {
                    Text("ACTUAL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SdbBrightGreen, modifier = Modifier.width(72.dp))
                    Text("PICO 24H", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SdbBlue, modifier = Modifier.width(72.dp))
                } else {
                    Text("JUGANDO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SdbBrightGreen, modifier = Modifier.width(68.dp))
                    Text("PRECIO ES", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SdbSaleBadgeText, modifier = Modifier.width(74.dp))
                }
            }
        }

        if (subTab == 0) {
            itemsIndexed(visibleMostPlayed, key = { index, game -> "chart_${index}_${game.appId}" }) { index, game ->
                val ratio = (game.currentPlayers.toFloat() / maxConcurrent.toFloat()).coerceIn(0.03f, 1f)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (index % 2 == 0) SdbHeader else SdbRowAlt)
                        .border(0.5.dp, SdbBorder)
                        .clickable { vm.openAppInspector(game.appId) }
                        .padding(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${index + 1}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = SdbTextMuted,
                            modifier = Modifier.width(28.dp)
                        )
                        SteamSmartThumb(
                            appId = game.appId,
                            primaryUrl = game.capsuleUrl,
                            contentDescription = game.name,
                            modifier = Modifier.width(64.dp).height(30.dp).clip(RoundedCornerShape(3.dp))
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(game.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("AppID ${game.appId}", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = SdbBlue)
                        }
                        Text(
                            text = numFmt.format(game.currentPlayers),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            color = SdbBrightGreen,
                            modifier = Modifier.width(72.dp)
                        )
                        Text(
                            text = numFmt.format(game.peak24hEstimate),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = SdbTextMain,
                            modifier = Modifier.width(72.dp)
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(SdbBg)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(ratio)
                                .fillMaxHeight()
                                .background(Brush.horizontalGradient(listOf(SdbBlue, SdbBrightGreen)))
                        )
                    }
                }
            }

            if (displayLimit < vm.mostPlayedGames.size) {
                item {
                    Button(
                        onClick = { displayLimit = 100 },
                        colors = ButtonDefaults.buttonColors(containerColor = SdbBlue),
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    ) {
                        Icon(Icons.Default.ExpandMore, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Desplegar Top ${vm.mostPlayedGames.size} Completo", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        } else {
            itemsIndexed(vm.topWantedGames, key = { index, game -> "wanted_${index}_${game.appId}" }) { index, game ->
                SteamDbDenseGameRow(
                    rank = index + 1,
                    game = game,
                    onClick = { vm.openAppInspector(game.appId) },
                    isFavorite = vm.isFavorite(game.appId),
                    onToggleFavorite = { vm.toggleFavorite(game) }
                )
            }
        }
    }
}

@Composable
fun SteamDbNextSalesTab() {
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            nowMillis = System.currentTimeMillis()
        }
    }

    val events = SteamSalesSchedule.officialEvents
    val activeOrNext = events.firstOrNull { it.startDateMillis > nowMillis } ?: events.first()
    val targetMillis = activeOrNext.startDateMillis

    val diffSec = ((targetMillis - nowMillis) / 1000L).coerceAtLeast(0L)
    val days = diffSec / 86400
    val hours = (diffSec % 86400) / 3600
    val mins = (diffSec % 3600) / 60
    val secs = diffSec % 60
    val pulseOn = (secs % 2L) == 0L

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SdbHeader),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, SdbBlue, RoundedCornerShape(10.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(if (pulseOn) SdbBrightGreen else SdbBlue)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "CUENTA ATRÁS PARA PRÓXIMA REBAJA DE STEAM",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = SdbBlue,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    Text(
                        text = activeOrNext.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Text(
                        text = "${activeOrNext.category} • ${activeOrNext.dateRangeText}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SdbSaleBadgeText,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        CountdownBox(days.toString(), "DÍAS", false)
                        CountdownBox(String.format("%02d", hours), "HORAS", false)
                        CountdownBox(String.format("%02d", mins), "MINUTOS", false)
                        CountdownBox(String.format("%02d", secs), "SEGUNDOS", pulseOn)
                    }
                }
            }
        }

        items(events) { ev ->
            val evDaysLeft = ((ev.startDateMillis - nowMillis) / 86400000L)
            val isMajor = ev.category.contains("MAJOR")

            Card(
                colors = CardDefaults.cardColors(containerColor = SdbHeader),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = if (isMajor) SdbSaleBadgeText.copy(alpha = 0.6f) else SdbBorder,
                        shape = RoundedCornerShape(8.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Badge(containerColor = if (isMajor) SdbSaleBadgeBg else SdbPanel) {
                            Text(
                                text = ev.category,
                                color = if (isMajor) SdbSaleBadgeText else SdbBlue,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }
                        Text(
                            text = if (evDaysLeft > 0) "Faltan $evDaysLeft días" else ev.dateRangeText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = SdbBrightGreen,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(ev.name, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text(ev.dateRangeText, fontSize = 11.sp, color = SdbBlue, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(4.dp))
                    Text(ev.description, fontSize = 12.sp, color = SdbTextMuted)
                }
            }
        }
    }
}

@Composable
fun CountdownBox(number: String, label: String, highlightBorder: Boolean) {
    Column(
        modifier = Modifier
            .width(74.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SdbBg)
            .border(
                width = if (highlightBorder) 1.5.dp else 1.dp,
                color = if (highlightBorder) SdbBrightGreen else SdbBorder,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = number,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            color = if (highlightBorder) SdbBrightGreen else SdbSaleBadgeText,
            fontFamily = FontFamily.Monospace
        )
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = SdbTextMuted)
    }
}

@Composable
fun LiveAutocompleteDropdownOverlay(
    query: String,
    suggestions: List<SdbGameSummary>,
    isLoading: Boolean,
    onSelectGame: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable { onDismiss() }
    ) {
        Surface(
            color = Color(0xFF12151B),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .border(1.dp, SdbBorder)
                .clickable(enabled = false) {}
        ) {
            if (suggestions.isEmpty() && isLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("Buscando coincidencias para \"$query\"...", color = SdbTextMuted, fontSize = 13.sp)
                }
            } else if (suggestions.isEmpty()) {
                val directAppId = query.toIntOrNull()
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    if (directAppId != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(SdbPanel)
                                .clickable { onSelectGame(directAppId) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Terminal, contentDescription = null, tint = SdbBlue)
                            Spacer(Modifier.width(10.dp))
                            Text("Abrir directamente AppID $directAppId", color = SdbBlue, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text("Sin sugerencias para \"$query\"", color = SdbTextMuted, fontSize = 13.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    itemsIndexed(suggestions, key = { index, item -> "sug_${index}_${item.appId}" }) { _, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectGame(item.appId) }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SteamSmartThumb(
                                appId = item.appId,
                                primaryUrl = item.capsuleUrl,
                                contentDescription = item.name,
                                modifier = Modifier
                                    .width(102.dp)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .border(0.5.dp, SdbBorder, RoundedCornerShape(3.dp))
                            )

                            Spacer(Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                HighlightedSearchTitle(
                                    fullTitle = item.name,
                                    searchQuery = query
                                )
                                Text(
                                    text = "AppID ${item.appId} • ${item.formattedFinal}",
                                    fontSize = 11.sp,
                                    color = SdbTextMuted,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HighlightedSearchTitle(fullTitle: String, searchQuery: String) {
    val annotated = remember(fullTitle, searchQuery) {
        buildAnnotatedString {
            if (searchQuery.isBlank()) {
                append(fullTitle)
                return@buildAnnotatedString
            }
            val lowerTitle = fullTitle.lowercase()
            val lowerQuery = searchQuery.lowercase()
            var startIndex = 0

            while (startIndex < fullTitle.length) {
                val matchIndex = lowerTitle.indexOf(lowerQuery, startIndex)
                if (matchIndex == -1) {
                    withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Medium)) {
                        append(fullTitle.substring(startIndex))
                    }
                    break
                }
                if (matchIndex > startIndex) {
                    withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Medium)) {
                        append(fullTitle.substring(startIndex, matchIndex))
                    }
                }
                val endIdx = (matchIndex + searchQuery.length).coerceAtMost(fullTitle.length)
                withStyle(SpanStyle(color = SdbBlue, fontWeight = FontWeight.ExtraBold)) {
                    append(fullTitle.substring(matchIndex, endIdx))
                }
                startIndex = endIdx
            }
        }
    }

    Text(
        text = annotated,
        fontSize = 16.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

data class SdbCalendarDay(
    val key: String,
    val date: java.time.LocalDate?,
    val label: String,
    val games: List<SdbGameSummary>
)

private val SDB_MONTHS = mapOf(
    "ene" to 1, "jan" to 1, "feb" to 2, "mar" to 3, "abr" to 4, "apr" to 4,
    "may" to 5, "jun" to 6, "jul" to 7, "ago" to 8, "aug" to 8, "sep" to 9, "set" to 9,
    "oct" to 10, "nov" to 11, "dic" to 12, "dec" to 12
)

fun sdbParseReleaseDate(text: String): java.time.LocalDate? {
    val t = text.lowercase(Locale.ROOT)
    try {
        Regex("(\\d{1,2})\\s*(?:de\\s+)?([a-zé]{3})[a-zé]*\\.?\\s*(?:de\\s+)?,?\\s*(\\d{4})").find(t)?.let { m ->
            val mon = SDB_MONTHS[m.groupValues[2]] ?: return@let
            return java.time.LocalDate.of(m.groupValues[3].toInt(), mon, m.groupValues[1].toInt())
        }
        Regex("([a-z]{3})[a-z]*\\.?\\s+(\\d{1,2}),?\\s+(\\d{4})").find(t)?.let { m ->
            val mon = SDB_MONTHS[m.groupValues[1]] ?: return@let
            return java.time.LocalDate.of(m.groupValues[3].toInt(), mon, m.groupValues[2].toInt())
        }
    } catch (_: Throwable) {}
    return null
}

private val SDB_WEEKDAYS = listOf("LUN", "MAR", "MIÉ", "JUE", "VIE", "SÁB", "DOM")
private val SDB_WEEKDAYS_LONG = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
private val SDB_MONTH_SHORT = listOf("ENE", "FEB", "MAR", "ABR", "MAY", "JUN", "JUL", "AGO", "SEP", "OCT", "NOV", "DIC")

fun sdbBuildCalendarDays(games: List<SdbGameSummary>): List<SdbCalendarDay> {
    val today = java.time.LocalDate.now()
    val dated = games.mapNotNull { g -> sdbParseReleaseDate(g.releaseDateText)?.let { it to g } }
        .filter { !it.first.isBefore(today.minusDays(1)) }
        .groupBy({ it.first }, { it.second })
        .toSortedMap()
        .map { (d, list) ->
            val rel = when (d) {
                today -> " · HOY"
                today.plusDays(1) -> " · MAÑANA"
                else -> ""
            }
            SdbCalendarDay(
                key = d.toString(),
                date = d,
                label = "${SDB_WEEKDAYS_LONG[d.dayOfWeek.value - 1]} ${d.dayOfMonth} de ${SDB_MONTH_SHORT[d.monthValue - 1].lowercase().replaceFirstChar { it.uppercase() }}$rel",
                games = list
            )
        }
    val datedIds = dated.flatMap { it.games }.map { it.appId }.toSet()
    val undated = games.filter { it.appId !in datedIds && sdbParseReleaseDate(it.releaseDateText) == null }
    return if (undated.isEmpty()) dated else dated + SdbCalendarDay("tbd", null, "Sin fecha concreta", undated)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SteamDbCalendarTab(vm: SdbMainViewModel) {
    if (vm.isLoading) {
        LoadingStateBox("Descargando pósters oficiales y fechas del calendario de Steam...")
        return
    }

    val days = remember(vm.upcomingCalendarGames) { sdbBuildCalendarDays(vm.upcomingCalendarGames) }
    val listState = rememberLazyListState()
    val stripState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Index of the first list item for each day (header positions)
    val headerIndex = remember(days) {
        var idx = 0
        days.map { d -> val h = idx; idx += 1 + d.games.size; h }
    }
    val activeDay by remember(days, headerIndex) {
        derivedStateOf {
            val first = listState.firstVisibleItemIndex
            headerIndex.indexOfLast { it <= first }.coerceAtLeast(0)
        }
    }
    LaunchedEffect(activeDay) {
        if (days.isNotEmpty()) stripState.animateScrollToItem((activeDay - 1).coerceAtLeast(0))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = SdbBlue, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "CALENDARIO DE LANZAMIENTOS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${vm.upcomingCalendarGames.size} juegos",
                    fontSize = 10.sp,
                    color = SdbBrightGreen,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "Desliza los días o toca un juego para ver su ficha",
                fontSize = 11.sp,
                color = SdbTextMuted
            )
        }

        if (days.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No hay lanzamientos próximos ahora mismo", color = SdbTextMuted, fontSize = 13.sp)
            }
            return@Column
        }

        // Day strip
        LazyRow(
            state = stripState,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
        ) {
            itemsIndexed(days, key = { _, d -> "strip_${d.key}" }) { i, d ->
                val selected = i == activeDay
                val isToday = d.date == java.time.LocalDate.now()
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(58.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) SdbBlue else SdbPanel)
                        .border(1.dp, if (selected) SdbBlue else if (isToday) SdbBrightGreen else SdbBorder, RoundedCornerShape(10.dp))
                        .clickable { scope.launch { listState.animateScrollToItem(headerIndex[i]) } }
                        .padding(vertical = 8.dp)
                ) {
                    val fg = if (selected) Color(0xFF0A0E14) else Color.White
                    Text(
                        text = d.date?.let { SDB_WEEKDAYS[it.dayOfWeek.value - 1] } ?: "TBD",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (selected) fg else SdbTextMuted
                    )
                    Text(
                        text = d.date?.dayOfMonth?.toString() ?: "?",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = fg
                    )
                    Text(
                        text = d.date?.let { SDB_MONTH_SHORT[it.monthValue - 1] } ?: "—",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (selected) fg else SdbTextMuted
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) Color(0xFF0A0E14).copy(alpha = 0.25f) else SdbBg)
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "${d.games.size}",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = if (selected) fg else SdbBrightGreen
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = SdbBorder)

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            days.forEach { day ->
                stickyHeader(key = "hdr_${day.key}") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SdbBg.copy(alpha = 0.97f))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Box(
                            Modifier
                                .width(3.dp)
                                .height(16.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (day.date == java.time.LocalDate.now()) SdbBrightGreen else SdbBlue)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = day.label,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${day.games.size} lanzamiento${if (day.games.size == 1) "" else "s"}",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = SdbTextMuted
                        )
                    }
                }
                items(day.games, key = { g -> "cal_${day.key}_${g.appId}" }) { game ->
                    SdbCalendarRow(vm, game)
                }
            }
        }
    }
}

@Composable
fun SdbCalendarRow(vm: SdbMainViewModel, game: SdbGameSummary) {
    val fav = vm.isFavorite(game.appId)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SdbPanel)
            .border(1.dp, SdbBorder, RoundedCornerShape(8.dp))
            .clickable { vm.openAppInspector(game.appId) }
    ) {
        Box(
            modifier = Modifier
                .width(132.dp)
                .height(62.dp)
        ) {
            SteamSmartThumb(
                appId = game.appId,
                primaryUrl = game.capsuleUrl,
                contentDescription = game.name,
                modifier = Modifier.fillMaxSize()
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = game.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 15.sp
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (game.finalCents > 0) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(SdbSaleBadgeBg)
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(game.formattedFinal, fontSize = 10.sp, fontWeight = FontWeight.Black, color = SdbSaleBadgeText)
                    }
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = if (sdbParseReleaseDate(game.releaseDateText) == null) game.releaseDateText.ifBlank { "Próximamente" } else "Estreno en Steam",
                    fontSize = 10.sp,
                    color = SdbBrightGreen,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(onClick = { vm.toggleFavorite(game) }) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Favorito",
                tint = if (fav) SdbOrange else SdbTextMuted
            )
        }
    }
}

@Composable
fun LivePlayersBarChartCanvas(games: List<SdbGameSummary>, maxPlayers: Int) {
    Column {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(125.dp)
                .background(SdbBg, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 12.dp)
        ) {
            val barCount = games.size
            if (barCount == 0) return@Canvas
            val spacing = 10.dp.toPx()
            val barWidth = (size.width - spacing * (barCount - 1)) / barCount

            for (i in 0..3) {
                val y = size.height * (i / 3f)
                drawLine(
                    color = SdbBorder,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f
                )
            }

            val peakPath = Path()
            games.forEachIndexed { idx, g ->
                val ratio = (g.currentPlayers.toFloat() / maxPlayers.toFloat()).coerceIn(0.05f, 1f)
                val barH = size.height * ratio
                val x = idx * (barWidth + spacing)
                val topY = size.height - barH

                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(SdbBrightGreen, SdbBlue)),
                    topLeft = Offset(x, topY),
                    size = Size(barWidth, barH),
                    cornerRadius = CornerRadius(4f, 4f)
                )

                val peakCenter = Offset(x + barWidth / 2f, topY)
                if (idx == 0) peakPath.moveTo(peakCenter.x, peakCenter.y)
                else peakPath.lineTo(peakCenter.x, peakCenter.y)
            }

            drawPath(
                path = peakPath,
                color = SdbSaleBadgeText,
                style = Stroke(width = 2.dp.toPx())
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Top 10 en vivo • Usa los botones inferiores para desplegar hasta el Top 100",
            fontSize = 10.sp,
            color = SdbTextMuted
        )
    }
}

@Composable
fun SteamDbSalesTab(vm: SdbMainViewModel) {
    var minDiscount by remember { mutableIntStateOf(0) }
    var maxEuros by remember { mutableIntStateOf(999) }

    val filtered = remember(vm.activeSalesGames, minDiscount, maxEuros) {
        vm.activeSalesGames.filter {
            it.discountPct >= minDiscount && (it.finalCents / 100) <= maxEuros
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Text("STEAMDB SALES EXPLORER (TIENDA ESPAÑA - EUR)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SdbBlue, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = minDiscount == 0 && maxEuros == 999,
                onClick = { minDiscount = 0; maxEuros = 999 },
                label = { Text("Todas (${vm.activeSalesGames.size})", fontSize = 11.sp) }
            )
            FilterChip(
                selected = minDiscount == 50,
                onClick = { minDiscount = if (minDiscount == 50) 0 else 50 },
                label = { Text("-50% o más", fontSize = 11.sp) }
            )
            FilterChip(
                selected = minDiscount == 75,
                onClick = { minDiscount = if (minDiscount == 75) 0 else 75 },
                label = { Text("-75% o más", fontSize = 11.sp) }
            )
            FilterChip(
                selected = maxEuros == 10,
                onClick = { maxEuros = if (maxEuros == 10) 999 else 10 },
                label = { Text("Menos de 10 €", fontSize = 11.sp) }
            )
            FilterChip(
                selected = maxEuros == 5,
                onClick = { maxEuros = if (maxEuros == 5) 999 else 5 },
                label = { Text("Menos de 5 €", fontSize = 11.sp) }
            )
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(filtered, key = { idx, g -> "sale_${idx}_${g.appId}" }) { idx, game ->
                SteamDbDenseGameRow(
                    rank = idx + 1,
                    game = game,
                    onClick = { vm.openAppInspector(game.appId) },
                    isFavorite = vm.isFavorite(game.appId),
                    onToggleFavorite = { vm.toggleFavorite(game) }
                )
            }
        }
    }
}

// ===== KEY COMMANDER: calculadora de llaves TF2 =====
// Divisas del Mercado de Steam verificadas una a una contra
// steamcommunity.com/market/priceoverview (códigos que responden success=true).
data class SteamMarketCurrency(val code: Int, val iso: String, val name: String, val symbol: String)

val STEAM_MARKET_CURRENCIES = listOf(
    SteamMarketCurrency(3, "EUR", "Euro", "€"),
    SteamMarketCurrency(1, "USD", "Dólar estadounidense", "$"),
    SteamMarketCurrency(2, "GBP", "Libra esterlina", "£"),
    SteamMarketCurrency(4, "CHF", "Franco suizo", "CHF"),
    SteamMarketCurrency(5, "RUB", "Rublo ruso", "₽"),
    SteamMarketCurrency(6, "PLN", "Esloti polaco", "zł"),
    SteamMarketCurrency(7, "BRL", "Real brasileño", "R$"),
    SteamMarketCurrency(8, "JPY", "Yen japonés", "¥"),
    SteamMarketCurrency(9, "NOK", "Corona noruega", "kr"),
    SteamMarketCurrency(10, "IDR", "Rupia indonesia", "Rp"),
    SteamMarketCurrency(11, "MYR", "Ringgit malayo", "RM"),
    SteamMarketCurrency(12, "PHP", "Peso filipino", "₱"),
    SteamMarketCurrency(13, "SGD", "Dólar de Singapur", "S$"),
    SteamMarketCurrency(14, "THB", "Baht tailandés", "฿"),
    SteamMarketCurrency(15, "VND", "Dong vietnamita", "₫"),
    SteamMarketCurrency(16, "KRW", "Won surcoreano", "₩"),
    SteamMarketCurrency(17, "TRY", "Lira turca", "TL"),
    SteamMarketCurrency(18, "UAH", "Grivna ucraniana", "₴"),
    SteamMarketCurrency(19, "MXN", "Peso mexicano", "Mex$"),
    SteamMarketCurrency(20, "CAD", "Dólar canadiense", "CDN$"),
    SteamMarketCurrency(21, "AUD", "Dólar australiano", "A$"),
    SteamMarketCurrency(22, "NZD", "Dólar neozelandés", "NZ$"),
    SteamMarketCurrency(23, "CNY", "Yuan chino", "¥"),
    SteamMarketCurrency(24, "INR", "Rupia india", "₹"),
    SteamMarketCurrency(25, "CLP", "Peso chileno", "CLP$"),
    SteamMarketCurrency(26, "PEN", "Sol peruano", "S/."),
    SteamMarketCurrency(27, "COP", "Peso colombiano", "COL$"),
    SteamMarketCurrency(28, "ZAR", "Rand sudafricano", "R"),
    SteamMarketCurrency(29, "HKD", "Dólar de Hong Kong", "HK$"),
    SteamMarketCurrency(30, "TWD", "Dólar taiwanés", "NT$"),
    SteamMarketCurrency(31, "SAR", "Riyal saudí", "SR"),
    SteamMarketCurrency(32, "AED", "Dírham de EAU", "AED"),
    SteamMarketCurrency(34, "ARS", "Peso argentino", "ARS$"),
    SteamMarketCurrency(35, "ILS", "Nuevo séquel israelí", "₪"),
    SteamMarketCurrency(37, "KZT", "Tenge kazajo", "₸"),
    SteamMarketCurrency(38, "KWD", "Dinar kuwaití", "KD"),
    SteamMarketCurrency(39, "QAR", "Riyal catarí", "QR"),
    SteamMarketCurrency(40, "CRC", "Colón costarricense", "₡"),
    SteamMarketCurrency(41, "UYU", "Peso uruguayo", "\$U")
)

const val TF2_KEY_FEE = 0.15 // 5% Steam + 10% TF2

data class TfKeyPrice(val lowest: Double?, val median: Double?, val volume: String?, val error: String?)

fun sdbParseMoney(raw: String?): Double? {
    if (raw.isNullOrBlank()) return null
    val cleaned = raw.replace(Regex("[^\\d.,]"), "")
    if (cleaned.isEmpty()) return null
    val lastComma = cleaned.lastIndexOf(',')
    val lastDot = cleaned.lastIndexOf('.')
    val normalized = when {
        lastComma > lastDot -> {
            // coma decimal salvo que sea separador de miles (3 dígitos detrás y sin punto)
            val after = cleaned.length - lastComma - 1
            if (after == 3 && lastDot == -1 && cleaned.count { it == ',' } >= 1 && cleaned.length > 4)
                cleaned.replace(",", "")
            else cleaned.replace(".", "").replace(",", ".")
        }
        lastDot > lastComma -> {
            val after = cleaned.length - lastDot - 1
            val dots = cleaned.count { it == '.' }
            if (lastComma == -1 && (dots > 1 || after == 3)) cleaned.replace(".", "")
            else cleaned.replace(",", "")
        }
        else -> cleaned
    }
    return normalized.toDoubleOrNull()
}

suspend fun fetchTf2KeyPrice(currency: Int): TfKeyPrice = withContext(Dispatchers.IO) {
    val url = "https://steamcommunity.com/market/priceoverview/?appid=440&currency=$currency&market_hash_name=Mann%20Co.%20Supply%20Crate%20Key"
    var body = ""
    for (attempt in 0 until 3) {
        body = ValveSteamClient.getJson(url)
        if (body.isNotBlank()) break
        delay(900L * (attempt + 1))
    }
    if (body.isBlank()) return@withContext TfKeyPrice(null, null, null, "No se pudo contactar con el mercado de Steam (puede estar limitando peticiones). Escribe el precio a mano.")
    try {
        val j = JSONObject(body)
        val low = sdbParseMoney(j.optString("lowest_price", ""))
        val med = sdbParseMoney(j.optString("median_price", ""))
        if (!j.optBoolean("success") || (low == null && med == null))
            TfKeyPrice(null, null, null, "Sin datos de mercado para esta divisa. Escribe el precio a mano.")
        else TfKeyPrice(low, med, j.optString("volume", "").ifBlank { null }, null)
    } catch (_: Throwable) {
        TfKeyPrice(null, null, null, "Respuesta no válida del mercado.")
    }
}

@Composable
fun SteamDbCalculatorTab(vm: SdbMainViewModel) {
    var currency by remember { mutableStateOf(STEAM_MARKET_CURRENCIES.first()) }
    var pickerOpen by remember { mutableStateOf(false) }
    var gamePriceText by remember { mutableStateOf("59,99") }
    var manualKeyText by remember { mutableStateOf("") }
    var market by remember { mutableStateOf<TfKeyPrice?>(null) }
    var loading by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var updatedAt by remember { mutableStateOf("—") }

    LaunchedEffect(currency, refreshTick) {
        loading = true
        manualKeyText = ""
        market = fetchTf2KeyPrice(currency.code)
        updatedAt = java.text.SimpleDateFormat("HH:mm:ss", Locale("es", "ES")).format(java.util.Date())
        loading = false
    }

    fun parseInput(v: String): Double {
        val n = v.trim().replace(" ", "").let { s ->
            if (s.contains(',')) s.replace(".", "").replace(",", ".") else s
        }.toDoubleOrNull() ?: 0.0
        return if (n > 0) n else 0.0
    }
    val fmt = remember { NumberFormat.getNumberInstance(Locale("es", "ES")).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 } }

    val marketPrice = market?.lowest ?: market?.median
    val manual = parseInput(manualKeyText)
    val keyPrice = if (manual > 0) manual else marketPrice
    val gamePrice = parseInput(gamePriceText)
    val netPerKey = keyPrice?.let { it * (1 - TF2_KEY_FEE) }
    val keysNoFee = if (keyPrice != null && gamePrice > 0) gamePrice / keyPrice else null
    val keysWithFee = if (netPerKey != null && gamePrice > 0) gamePrice / netPerKey else null
    val finalKeys = keysWithFee?.let { kotlin.math.ceil(it - 1e-9).toInt() }
    val leftover = if (finalKeys != null && netPerKey != null) finalKeys * netPerKey - gamePrice else null

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SdbHeader),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, SdbBorder, RoundedCornerShape(8.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("KEY COMMANDER • LLAVES TF2", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SdbSaleBadgeText, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                        TextButton(onClick = { refreshTick++ }, enabled = !loading) {
                            Text(if (loading) "···" else "ACTUALIZAR", fontSize = 10.sp, color = SdbBlue, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text("¿Cuántas llaves de Mann Co. necesitas para comprar un juego? Precio real del Mercado de Steam en ${STEAM_MARKET_CURRENCIES.size} divisas.", fontSize = 11.sp, color = SdbTextMuted)
                }
            }
        }

        // Selector de divisa
        item {
            Column {
                Text("DIVISA", fontSize = 9.sp, color = SdbTextMuted, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(4.dp))
                Box {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(SdbPanel, RoundedCornerShape(6.dp))
                            .border(1.dp, SdbBorder, RoundedCornerShape(6.dp))
                            .clickable { pickerOpen = true }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${currency.iso} · ${currency.name} (${currency.symbol})", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Text("▼", fontSize = 10.sp, color = SdbTextMuted)
                    }
                    DropdownMenu(expanded = pickerOpen, onDismissRequest = { pickerOpen = false }, modifier = Modifier.heightIn(max = 420.dp).background(SdbPanel)) {
                        STEAM_MARKET_CURRENCIES.forEach { c ->
                            DropdownMenuItem(
                                text = { Text("${c.iso} · ${c.name} (${c.symbol})", fontSize = 13.sp, color = if (c == currency) SdbBrightGreen else SdbTextMain) },
                                onClick = { currency = c; pickerOpen = false }
                            )
                        }
                    }
                }
            }
        }

        // Comisión
        item {
            Row(
                modifier = Modifier.fillMaxWidth().background(SdbPanel, RoundedCornerShape(6.dp)).border(1.dp, SdbBorder, RoundedCornerShape(6.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("COMISIÓN MERCADO (5% STEAM + 10% TF2)", fontSize = 9.sp, color = SdbTextMuted, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                Text("15,00%", fontSize = 13.sp, color = SdbOrange, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        }

        // Precio del juego
        item {
            OutlinedTextField(
                value = gamePriceText,
                onValueChange = { gamePriceText = it },
                label = { Text("Precio del juego (${currency.iso})") },
                suffix = { Text(currency.symbol, color = SdbTextMuted) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color.White),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SdbOrange, unfocusedBorderColor = SdbBorder, focusedLabelColor = SdbOrange, unfocusedLabelColor = SdbTextMuted, cursorColor = SdbOrange),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Precio de la llave
        item {
            Column {
                OutlinedTextField(
                    value = manualKeyText,
                    onValueChange = { manualKeyText = it },
                    label = { Text("Precio de la llave (${currency.iso})") },
                    placeholder = { Text(marketPrice?.let { fmt.format(it) } ?: if (loading) "Cargando…" else "Escribe el precio", color = SdbTextMuted) },
                    suffix = { Text(currency.symbol, color = SdbTextMuted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color.White),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SdbOrange, unfocusedBorderColor = SdbBorder, focusedLabelColor = SdbOrange, unfocusedLabelColor = SdbTextMuted, cursorColor = SdbOrange),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                val hint = when {
                    loading -> "Consultando el Mercado de Steam…"
                    manual > 0 -> "Usando tu precio manual."
                    marketPrice != null -> "Precio más bajo del mercado (${updatedAt})" + (market?.volume?.let { " · $it vendidas en 24h" } ?: "") + ". Puedes sobrescribirlo."
                    else -> market?.error ?: "Sin precio del mercado: escribe uno."
                }
                Text(hint, fontSize = 10.sp, color = if (marketPrice == null && manual <= 0 && !loading) SdbOrange else SdbTextMuted, fontFamily = FontFamily.Monospace)
            }
        }

        // Resultado
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SdbHeader),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, if (finalKeys != null) SdbBrightGreen.copy(alpha = 0.5f) else SdbBorder, RoundedCornerShape(8.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("LLAVES NECESARIAS", fontSize = 10.sp, color = SdbTextMuted, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Text(finalKeys?.toString() ?: "—", fontSize = 44.sp, fontWeight = FontWeight.Black, color = SdbBrightGreen, fontFamily = FontFamily.Monospace)
                    if (finalKeys != null && keyPrice != null && netPerKey != null) {
                        Text("Compras ${finalKeys} llaves por ${fmt.format(finalKeys * keyPrice)} ${currency.symbol}", fontSize = 12.sp, color = SdbTextMain)
                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider(color = SdbBorder)
                        Spacer(Modifier.height(10.dp))
                        CalcLine("Recibes por cada llave vendida", "${fmt.format(netPerKey)} ${currency.symbol}", SdbBlue)
                        CalcLine("Llaves exactas sin comisión", fmt.format(keysNoFee ?: 0.0), SdbTextMain)
                        CalcLine("Llaves exactas con comisión", fmt.format(keysWithFee ?: 0.0), SdbTextMain)
                        CalcLine("Te sobra en el monedero", "${fmt.format(leftover ?: 0.0)} ${currency.symbol}", SdbSaleBadgeText)
                    } else {
                        Text("Escribe el precio del juego y espera al precio de la llave.", fontSize = 11.sp, color = SdbTextMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun CalcLine(label: String, value: String, color: Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, color = SdbTextMuted, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun SteamDbDenseGameRow(
    rank: Int,
    game: SdbGameSummary,
    onClick: () -> Unit,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null
) {
    val numFmt = remember { NumberFormat.getIntegerInstance(Locale("es", "ES")) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (rank % 2 == 0) SdbHeader else SdbRowAlt)
            .border(0.5.dp, SdbBorder)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$rank",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = SdbTextMuted,
            modifier = Modifier.width(24.dp)
        )

        SteamSmartThumb(
            appId = game.appId,
            primaryUrl = game.capsuleUrl,
            contentDescription = game.name,
            modifier = Modifier.width(68.dp).height(32.dp).clip(RoundedCornerShape(3.dp))
        )

        Spacer(Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(game.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("AppID ${game.appId}", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = SdbBlue)
                if (game.currentPlayers > 0) {
                    Text("• ${numFmt.format(game.currentPlayers)} in-game", fontSize = 10.sp, color = SdbBrightGreen, fontFamily = FontFamily.Monospace)
                }
            }
        }

        if (game.discountPct > 0) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(SdbSaleBadgeBg)
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text("-${game.discountPct}%", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = SdbSaleBadgeText, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.width(6.dp))
        }

        Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(68.dp)) {
            Text(
                text = game.formattedFinal,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                color = if (game.discountPct > 0) SdbSaleBadgeText else Color.White
            )
            if (game.discountPct > 0) {
                Text(
                    text = game.formattedOriginal,
                    fontSize = 9.sp,
                    color = SdbTextMuted,
                    textDecoration = TextDecoration.LineThrough,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        if (onToggleFavorite != null) {
            IconButton(onClick = onToggleFavorite, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Añadir a favoritos",
                    tint = if (isFavorite) SdbSaleBadgeText else SdbTextMuted.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun FullScreenAppInspector(
    vm: SdbMainViewModel,
    onBack: () -> Unit
) {
    var activeTab by remember { mutableIntStateOf(0) }
    val numFmt = remember { NumberFormat.getIntegerInstance(Locale("es", "ES")) }

    Column(modifier = Modifier.fillMaxSize().background(SdbBg)) {
        if (vm.isInspectorLoading && vm.inspectorAppData == null) {
            LoadingStateBox("Inspeccionando metadatos del paquete y consultando las 41 divisas oficiales de Steam...")
            return@Column
        }

        val app = vm.inspectorAppData
        if (app == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Button(onClick = onBack) { Text("Error al leer el AppID. Volver") }
            }
            return@Column
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SdbHeader)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SteamSmartThumb(
                appId = app.appId,
                primaryUrl = app.headerImage,
                contentDescription = app.name,
                modifier = Modifier
                    .width(110.dp)
                    .height(52.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, SdbBorder, RoundedCornerShape(6.dp))
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(app.name, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("AppID ${app.appId} • ${app.appType} • ${app.releaseDate}", fontSize = 10.sp, color = SdbBlue, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatCounterBox("IN-GAME", numFmt.format(app.currentPlayers), SdbBrightGreen)
                    StatCounterBox("24H PEAK", numFmt.format(app.peakTodayEstimate), SdbBlue)
                    StatCounterBox("ESPAÑA", app.finalFormatted, SdbSaleBadgeText)
                }
            }
            IconButton(onClick = {
                vm.toggleFavorite(
                    SdbGameSummary(
                        appId = app.appId,
                        name = app.name,
                        capsuleUrl = app.headerImage,
                        originalCents = 0,
                        finalCents = app.finalCents,
                        discountPct = app.discountPct
                    )
                )
            }) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Favorito con vigilancia de precio",
                    tint = if (vm.isFavorite(app.appId)) SdbSaleBadgeText else SdbTextMuted
                )
            }
            IconButton(onClick = {
                vm.toggleCalculatorItem(
                    SdbGameSummary(
                        appId = app.appId,
                        name = app.name,
                        capsuleUrl = app.headerImage,
                        originalCents = app.finalCents,
                        finalCents = app.finalCents,
                        discountPct = app.discountPct
                    )
                )
            }) {
                val inCart = vm.calcCart.any { it.appId == app.appId }
                Icon(
                    imageVector = if (inCart) Icons.Default.CheckCircle else Icons.Default.AddShoppingCart,
                    contentDescription = null,
                    tint = if (inCart) SdbBrightGreen else SdbSaleBadgeText
                )
            }
        }

        val tabs = listOf(
            "Store Prices (${vm.inspectorPrices.size}/41)",
            "Charts",
            "App Info",
            "DLCs (${app.dlcAppIds.size})",
            "Patch Notes",
            "Screenshots (${app.screenshots.size})"
        )

        ScrollableTabRow(
            selectedTabIndex = activeTab,
            containerColor = SdbPanel,
            contentColor = SdbBlue,
            edgePadding = 8.dp
        ) {
            tabs.forEachIndexed { idx, label ->
                Tab(
                    selected = activeTab == idx,
                    onClick = { activeTab = idx },
                    text = {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (activeTab == idx) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (activeTab == idx) SdbBlue else SdbTextMuted
                        )
                    }
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when (activeTab) {
                0 -> InspectorPricesTab(prices = vm.inspectorPrices, isLoadingPrices = vm.isLoadingPricesMatrix)
                1 -> InspectorChartsTab(app = app)
                2 -> InspectorInformationTab(app = app)
                3 -> InspectorDlcsAndPackagesTab(app = app, onOpenDlc = { dlcId -> vm.openAppInspector(dlcId) })
                4 -> InspectorPatchNotesTab(notes = vm.inspectorNews)
                5 -> InspectorMediaTab(app = app)
            }
        }
    }
}

@Composable
fun StatCounterBox(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = SdbTextMuted, fontFamily = FontFamily.Monospace)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = color, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun InspectorPricesTab(prices: List<SdbRegionalPrice>, isLoadingPrices: Boolean) {
    if (isLoadingPrices && prices.isEmpty()) {
        LoadingStateBox("Consultando precios en tiempo real en las 41 divisas oficiales de Steam...")
        return
    }

    if (prices.isEmpty()) {
        LoadingStateBox("Este paquete es gratuito o no dispone de tabla de precios comerciales.")
        return
    }

    val maxEur = (prices.maxOfOrNull { it.priceInEur } ?: 1.0).coerceAtLeast(0.01)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SdbPanel)
                    .border(1.dp, SdbBorder)
                    .padding(horizontal = 8.dp, vertical = 7.dp)
            ) {
                Text("DIVISA OFICIAL STEAM (41 REGIONES)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SdbTextMuted, modifier = Modifier.weight(1.45f))
                Text("PRECIO LOCAL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SdbTextMuted, modifier = Modifier.weight(0.95f))
                Text("EN EUROS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SdbBlue, modifier = Modifier.weight(0.75f))
                Text("VS EUR", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SdbBrightGreen, modifier = Modifier.width(62.dp))
            }
        }

        itemsIndexed(prices, key = { idx, item -> "reg_${idx}_${item.regionKey}" }) { idx, item ->
            val barFraction = (item.priceInEur / maxEur).toFloat().coerceIn(0.04f, 1f)
            val isCheaper = item.diffPercentageVsEur < -0.5
            val isMoreExpensive = item.diffPercentageVsEur > 0.5

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (idx % 2 == 0) SdbHeader else SdbRowAlt)
                    .border(0.5.dp, SdbBorder)
                    .padding(horizontal = 8.dp, vertical = 7.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(modifier = Modifier.weight(1.45f), verticalAlignment = Alignment.CenterVertically) {
                        Text(item.flag, fontSize = 17.sp)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = item.currencyLabel,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (item.discountPct > 0) {
                                Text("-${item.discountPct}% DTO.", fontSize = 9.sp, color = SdbSaleBadgeText, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Text(
                        text = item.localPriceFormatted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = SdbTextMain,
                        modifier = Modifier.weight(0.95f)
                    )

                    Text(
                        text = String.format(Locale("es", "ES"), "%.2f €", item.priceInEur),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White,
                        modifier = Modifier.weight(0.75f)
                    )

                    val sign = if (item.diffPercentageVsEur > 0) "+" else ""
                    Text(
                        text = if (item.regionKey == "ES") "BASE" else String.format(Locale("es", "ES"), "%s%.1f%%", sign, item.diffPercentageVsEur),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        color = when {
                            item.regionKey == "ES" -> SdbBlue
                            isCheaper -> SdbBrightGreen
                            isMoreExpensive -> SdbRed
                            else -> SdbTextMuted
                        },
                        modifier = Modifier.width(62.dp)
                    )
                }

                Spacer(Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth().height(3.dp).background(SdbBg)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(barFraction)
                            .fillMaxHeight()
                            .background(if (isCheaper) SdbBrightGreen else if (isMoreExpensive) SdbRed else SdbBlue)
                    )
                }
            }
        }
    }
}

@Composable
fun InspectorChartsTab(app: SdbFullAppData) {
    val numFmt = remember { NumberFormat.getIntegerInstance(Locale("es", "ES")) }
    val totalReviews = (app.positiveReviews + app.negativeReviews).coerceAtLeast(1)
    val positiveRatio = (app.positiveReviews.toFloat() / totalReviews.toFloat()) * 100f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = SdbHeader),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, SdbBorder, RoundedCornerShape(8.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("PLAYER TREND • ACTIVIDAD DE JUGADORES (24H CURVE)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SdbBlue, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatCounterBox("JUGANDO AHORA", numFmt.format(app.currentPlayers), SdbBrightGreen)
                    StatCounterBox("PICO 24 HORAS", numFmt.format(app.peakTodayEstimate), SdbBlue)
                    StatCounterBox("PICO HISTÓRICO EST.", numFmt.format(app.allTimePeakEstimate), SdbSaleBadgeText)
                }

                Spacer(Modifier.height(12.dp))

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .background(SdbBg, RoundedCornerShape(6.dp))
                        .padding(12.dp)
                ) {
                    val points = 24
                    val linePath = Path()
                    val fillPath = Path()

                    for (i in 0..points) {
                        val x = size.width * (i.toFloat() / points)
                        val wave = (sin((i + (app.appId % 7)) * 0.45) * 0.28 + 0.65).toFloat()
                        val y = size.height * (1f - wave.coerceIn(0.15f, 0.95f))

                        if (i == 0) {
                            linePath.moveTo(x, y)
                            fillPath.moveTo(x, size.height)
                            fillPath.lineTo(x, y)
                        } else {
                            linePath.lineTo(x, y)
                            fillPath.lineTo(x, y)
                        }
                    }
                    fillPath.lineTo(size.width, size.height)
                    fillPath.close()

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(listOf(SdbBrightGreen.copy(alpha = 0.35f), Color.Transparent))
                    )
                    drawPath(
                        path = linePath,
                        color = SdbBrightGreen,
                        style = Stroke(width = 2.5.dp.toPx())
                    )
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = SdbHeader),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, SdbBorder, RoundedCornerShape(8.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("STEAM REVIEWS BREAKDOWN • VALORACIONES", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SdbBlue, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${app.reviewScoreDesc} (${String.format(Locale("es", "ES"), "%.1f%%", positiveRatio)} positivas)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (positiveRatio >= 70f) SdbBrightGreen else SdbOrange
                )
                Spacer(Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Positivas: ${numFmt.format(app.positiveReviews)}", fontSize = 12.sp, color = SdbBrightGreen, fontFamily = FontFamily.Monospace)
                    Text("Negativas: ${numFmt.format(app.negativeReviews)}", fontSize = 12.sp, color = SdbRed, fontFamily = FontFamily.Monospace)
                }

                Spacer(Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(SdbRed)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((positiveRatio / 100f).coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(SdbBrightGreen)
                    )
                }
            }
        }
    }
}

@Composable
fun InspectorInformationTab(app: SdbFullAppData) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(app.shortDesc, fontSize = 13.sp, color = SdbTextMain)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, SdbBorder, RoundedCornerShape(6.dp))
                .clip(RoundedCornerShape(6.dp))
        ) {
            TechInfoRow("AppID", app.appId.toString(), true)
            TechInfoRow("App Type", app.appType, false)
            TechInfoRow("Developer", app.developers.joinToString(", "), true)
            TechInfoRow("Publisher", app.publishers.joinToString(", "), false)
            TechInfoRow("Release Date", app.releaseDate, true)
            TechInfoRow(
                "Supported Systems",
                listOfNotNull(
                    if (app.windows) "Windows" else null,
                    if (app.mac) "macOS" else null,
                    if (app.linux) "Linux / Steam Deck" else null
                ).joinToString(" • "),
                false
            )
            TechInfoRow("Metacritic Score", app.metacritic?.let { "$it / 100" } ?: "N/A", true)
            TechInfoRow("Steamworks Achievements", "${app.achievementsCount} logros", false)
            TechInfoRow("Genres", app.genres.joinToString(", ").ifBlank { "N/A" }, true)
            TechInfoRow("Categories", app.categories.joinToString(" • ").ifBlank { "N/A" }, false)
            TechInfoRow("Languages", app.supportedLanguages, true)
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = SdbHeader),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, SdbBorder, RoundedCornerShape(6.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("MINIMUM PC REQUIREMENTS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SdbBlue, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(6.dp))
                Text(app.pcRequirementsMin, fontSize = 12.sp, color = SdbTextMuted)
            }
        }
    }
}

@Composable
fun TechInfoRow(key: String, value: String, even: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (even) SdbHeader else SdbRowAlt)
            .border(0.5.dp, SdbBorder)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(key, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SdbTextMuted, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(0.38f))
        Text(value, fontSize = 12.sp, color = Color.White, modifier = Modifier.weight(0.62f))
    }
}

@Composable
fun InspectorDlcsAndPackagesTab(app: SdbFullAppData, onOpenDlc: (Int) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            Text(
                text = "STORE PACKAGES (SUBIDs: ${app.packageIds.size})",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = SdbBlue,
                fontFamily = FontFamily.Monospace
            )
        }

        itemsIndexed(app.packageIds, key = { idx, subId -> "sub_${idx}_$subId" }) { _, subId ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SdbHeader, RoundedCornerShape(4.dp))
                    .border(1.dp, SdbBorder, RoundedCornerShape(4.dp))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Package SubID", fontSize = 12.sp, color = SdbTextMuted)
                Text("sub/$subId", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SdbSaleBadgeText, fontFamily = FontFamily.Monospace)
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "DLCs VINCULADOS (${app.dlcAppIds.size}) • TOCA CUALQUIER DLC PARA INSPECCIONAR SUS 41 DIVISAS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = SdbBrightGreen,
                fontFamily = FontFamily.Monospace
            )
        }

        itemsIndexed(app.dlcAppIds, key = { idx, dlcId -> "dlc_${idx}_$dlcId" }) { idx, dlcId ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (idx % 2 == 0) SdbHeader else SdbRowAlt, RoundedCornerShape(4.dp))
                    .border(1.dp, SdbBorder, RoundedCornerShape(4.dp))
                    .clickable { onOpenDlc(dlcId) }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SteamSmartThumb(
                        appId = dlcId,
                        primaryUrl = "",
                        contentDescription = "DLC $dlcId",
                        modifier = Modifier.width(60.dp).height(28.dp).clip(RoundedCornerShape(3.dp))
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("DLC AppID $dlcId", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = FontFamily.Monospace)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ver 41 divisas", fontSize = 11.sp, color = SdbBrightGreen)
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = SdbBrightGreen)
                }
            }
        }
    }
}

@Composable
fun InspectorPatchNotesTab(notes: List<SdbPatchNote>) {
    if (notes.isEmpty()) {
        LoadingStateBox("No hay notas de parche recientes para este AppID.")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(notes, key = { idx, n -> "note_${idx}_${n.gid}" }) { _, note ->
            Card(
                colors = CardDefaults.cardColors(containerColor = SdbHeader),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, SdbBorder, RoundedCornerShape(6.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(note.feedLabel.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SdbBlue, fontFamily = FontFamily.Monospace)
                        Text(note.dateFormatted, fontSize = 10.sp, color = SdbBrightGreen, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(note.title, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Spacer(Modifier.height(6.dp))
                    Text(note.contentsClean, fontSize = 12.sp, color = SdbTextMain)
                }
            }
        }
    }
}

@Composable
fun InspectorMediaTab(app: SdbFullAppData) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(app.screenshots, key = { idx, url -> "shot_${idx}_$url" }) { _, url ->
            SteamSmartThumb(
                appId = app.appId,
                primaryUrl = url,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(195.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, SdbBorder, RoundedCornerShape(8.dp))
            )
        }
    }
}

@Composable
fun LoadingStateBox(message: String) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = SdbBlue)
            Spacer(Modifier.height(12.dp))
            Text(message, fontSize = 12.sp, color = SdbTextMuted)
        }
    }
}


data class GabenFlipRecord(
    val gameName: String,
    val isHeads: Boolean,
    val phrase: String,
    val timestamp: String
)

@Composable
fun SteamDbGabenOracleTab(vm: SdbMainViewModel) {
    var gameInput by remember { mutableStateOf("") }
    var isFlipping by remember { mutableStateOf(false) }
    var flipTrigger by remember { mutableIntStateOf(0) }
    var lastResultHeads by remember { mutableStateOf<Boolean?>(null) }
    var lastGameTested by remember { mutableStateOf("") }
    var lastVerdictPhrase by remember { mutableStateOf("") }
    val history = remember { mutableStateListOf<GabenFlipRecord>() }

    val buyPhrases = remember {
        listOf(
            "¡Gaben aprueba este tarjetazo! Dale a comprar sin mirar atrás.",
            "¡CARA! Tu cartera llora, pero tu biblioteca sonríe. ¡A por él!",
            "El destino ha hablado: hoy se estrena juego nuevo en tu cuenta.",
            "¡Luz verde del Oráculo! Si está de oferta ya no tienes excusa."
        )
    }

    val skipPhrases = remember {
        listOf(
            "¡CRUZ! Quieto ahí, vaquero. Tienes decenas de juegos sin tocar en tu biblioteca.",
            "Tu cuenta bancaria acaba de suspirar de alivio. ¡Hoy NO se compra!",
            "Gaben dice que esperes a las próximas rebajas del -75%.",
            "¡Retirada táctica! Cierra la tienda y juega a lo que ya tienes instalado."
        )
    }

    val scaleAnim = remember { androidx.compose.animation.core.Animatable(1f) }

    LaunchedEffect(flipTrigger) {
        if (flipTrigger > 0) {
            isFlipping = true
            val outcomeHeads = kotlin.random.Random.nextBoolean()
            
            repeat(3) {
                scaleAnim.animateTo(1.15f, animationSpec = androidx.compose.animation.core.tween(150))
                scaleAnim.animateTo(0.95f, animationSpec = androidx.compose.animation.core.tween(150))
            }
            scaleAnim.animateTo(1f, animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.4f))

            lastResultHeads = outcomeHeads
            val chosenPhrase = if (outcomeHeads) buyPhrases.random() else skipPhrases.random()
            lastVerdictPhrase = chosenPhrase
            val timeStr = java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(java.util.Date())
            history.add(0, GabenFlipRecord(lastGameTested, outcomeHeads, chosenPhrase, timeStr))
            isFlipping = false
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SdbHeader),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, SdbBorder, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "EL ORÁCULO DE GABEN",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = SdbBlue,
                            fontFamily = FontFamily.Monospace
                        )
                        Surface(
                            color = SdbSaleBadgeBg,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                "50/50 RNG",
                                color = SdbSaleBadgeText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "¿Dudas si comprarte ese juego o ahorrar? Escribe el título, lanza la moneda sagrada y deja que el destino decida por tu cartera.",
                        fontSize = 12.sp,
                        color = SdbTextMain
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SdbPanel),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, SdbBorder, RoundedCornerShape(12.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    OutlinedTextField(
                        value = gameInput,
                        onValueChange = { gameInput = it },
                        label = { Text("¿Qué juego te está tentando comprar?", fontSize = 12.sp) },
                        placeholder = { Text("Ej: Elden Ring, Cyberpunk...", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SdbBlue,
                            unfocusedBorderColor = SdbBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = SdbBlue,
                            unfocusedLabelColor = SdbTextMuted
                        )
                    )

                    Spacer(Modifier.height(20.dp))

                    val coinColor = when {
                        isFlipping -> SdbBlue
                        lastResultHeads == true -> SdbBrightGreen
                        lastResultHeads == false -> Color(0xFFFF5252)
                        else -> SdbBlue
                    }

                    Box(
                        modifier = Modifier
                            .size(130.dp)
                            .graphicsLayer(
                                scaleX = scaleAnim.value,
                                scaleY = scaleAnim.value
                            )
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(coinColor.copy(alpha = 0.25f), SdbHeader)
                                )
                            )
                            .border(3.dp, coinColor, CircleShape)
                            .clickable(enabled = !isFlipping) {
                                val targetName = gameInput.trim().ifEmpty { "Juego Misterioso de Steam" }
                                lastGameTested = targetName
                                flipTrigger++
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (isFlipping) {
                                Text("🪙", fontSize = 32.sp)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "CONSULTANDO...",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace
                                )
                            } else if (lastResultHeads == null) {
                                Text("🪙", fontSize = 34.sp)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "TOCA PARA",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SdbTextMuted,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "LANZAR",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = SdbBlue,
                                    fontFamily = FontFamily.Monospace
                                )
                            } else if (lastResultHeads == true) {
                                Text("🛒", fontSize = 34.sp)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "¡CARA!",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = SdbBrightGreen,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "¡A COMPRAR!",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            } else {
                                Text("🛡️", fontSize = 34.sp)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "¡CRUZ!",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFFF5252),
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "¡AHORRA!",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    Button(
                        onClick = {
                            if (!isFlipping) {
                                val targetName = gameInput.trim().ifEmpty { "Juego Misterioso de Steam" }
                                lastGameTested = targetName
                                flipTrigger++
                            }
                        },
                        enabled = !isFlipping,
                        colors = ButtonDefaults.buttonColors(containerColor = SdbBlue),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isFlipping) "Consultando al destino..." else "🪙 ¡LANZAR MONEDA DEL DESTINO!",
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF0A0E14)
                        )
                    }

                    if (!isFlipping && lastResultHeads != null) {
                        Spacer(Modifier.height(14.dp))
                        val resColor = if (lastResultHeads == true) SdbBrightGreen else Color(0xFFFF5252)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SdbHeader),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().border(1.dp, resColor, RoundedCornerShape(8.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (lastResultHeads == true) "VEREDICTO PARA «$lastGameTested»: ¡SE COMPRA!"
                                           else "VEREDICTO PARA «$lastGameTested»: ¡CARTERA A SALVO!",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = resColor,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = lastVerdictPhrase,
                                    fontSize = 12.sp,
                                    color = Color.White,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }

        if (history.isNotEmpty()) {
            item {
                Text(
                    text = "HISTORIAL DEL ORÁCULO (SIN TRAMPAS)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = SdbTextMuted,
                    fontFamily = FontFamily.Monospace
                )
            }
            itemsIndexed(history) { idx, item ->
                val badgeColor = if (item.isHeads) SdbBrightGreen else Color(0xFFFF5252)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (idx % 2 == 0) SdbHeader else SdbRowAlt)
                        .border(0.5.dp, SdbBorder)
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.gameName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(item.phrase, fontSize = 11.sp, color = SdbTextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = if (item.isHeads) "CARA • COMPRAR" else "CRUZ • AHORRAR",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = badgeColor,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(item.timestamp, fontSize = 9.sp, color = SdbTextMuted, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

// ==================== FAVORITOS Y AVISO DE BAJADA DE PRECIO ====================

data class SdbFavoriteGame(
    val appId: Int,
    val name: String,
    val capsuleUrl: String,
    val targetPriceCents: Int = 0,
    val lastSeenCents: Int = 0,
    val discountPct: Int = 0,
    val droppedByCents: Int = 0,
    val addedAtMillis: Long = 0L
)

object FavoritesStore {
    private const val PREFS_NAME = "steamdb_favorites"
    private const val KEY_FAVORITES = "favorites_json"
    private lateinit var prefs: android.content.SharedPreferences

    fun init(context: android.content.Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
    }

    fun load(): List<SdbFavoriteGame> {
        if (!::prefs.isInitialized) return emptyList()
        val raw = prefs.getString(KEY_FAVORITES, null) ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                SdbFavoriteGame(
                    appId = o.optInt("appId", 0),
                    name = o.optString("name", "App"),
                    capsuleUrl = o.optString("capsuleUrl", ""),
                    targetPriceCents = o.optInt("targetPriceCents", 0),
                    lastSeenCents = o.optInt("lastSeenCents", 0),
                    discountPct = o.optInt("discountPct", 0),
                    droppedByCents = o.optInt("droppedByCents", 0),
                    addedAtMillis = o.optLong("addedAtMillis", 0L)
                )
            }.distinctBy { it.appId }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun save(list: List<SdbFavoriteGame>) {
        if (!::prefs.isInitialized) return
        val arr = org.json.JSONArray()
        list.forEach { f ->
            arr.put(
                org.json.JSONObject().apply {
                    put("appId", f.appId)
                    put("name", f.name)
                    put("capsuleUrl", f.capsuleUrl)
                    put("targetPriceCents", f.targetPriceCents)
                    put("lastSeenCents", f.lastSeenCents)
                    put("discountPct", f.discountPct)
                    put("droppedByCents", f.droppedByCents)
                    put("addedAtMillis", f.addedAtMillis)
                }
            )
        }
        prefs.edit().putString(KEY_FAVORITES, arr.toString()).apply()
    }
}

// ==================== COMPROBADOR DE NUEVAS VERSIONES (GITHUB RELEASES) ====================

object UpdateChecker {
    const val CURRENT_VERSION = "1.0.9"
    private const val RELEASES_API_URL = "https://api.github.com/repos/Androo45/SteamDB-Android/releases/latest"
    const val RELEASES_PAGE_URL = "https://github.com/Androo45/SteamDB-Android/releases/latest"

    fun isNewer(remote: String, current: String): Boolean {
        val r = remote.removePrefix("v").trim().split('.').map { it.trim().toIntOrNull() ?: 0 }
        val c = current.removePrefix("v").trim().split('.').map { it.trim().toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, c.size)) {
            val rv = r.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (rv != cv) return rv > cv
        }
        return false
    }

    suspend fun fetchLatestVersion(): String? = withContext(Dispatchers.IO) {
        try {
            val raw = ValveSteamClient.getJson(RELEASES_API_URL)
            if (raw.isBlank()) return@withContext null
            val tag = JSONObject(raw).optString("tag_name", "").removePrefix("v").trim()
            tag.ifEmpty { null }
        } catch (_: Throwable) {
            null
        }
    }
}

// ==================== PESTAÑA DE FAVORITOS ====================

@Composable
fun SteamDbFavoritesTab(vm: SdbMainViewModel) {
    var editingTarget by remember { mutableStateOf<SdbFavoriteGame?>(null) }
    val numFmt = remember { NumberFormat.getIntegerInstance(Locale("es", "ES")) }

    val dropCount = vm.favorites.count { it.droppedByCents > 0 }
    val targetHits = vm.favorites.count { it.targetPriceCents > 0 && it.lastSeenCents in 1..it.targetPriceCents }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SdbHeader),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, SdbBlue.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "WATCHLIST DE FAVORITOS • SEGUIMIENTO DE PRECIOS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SdbBlue,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Cuando actualices la app (icono ⟳ arriba), SteamDB compara el precio actual de cada favorito con el último visto y te avisa aquí si ha bajado.",
                        fontSize = 12.sp,
                        color = SdbTextMain
                    )
                    if (vm.favorites.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            StatCounterBox("FAVORITOS", numFmt.format(vm.favorites.size), SdbBlue)
                            StatCounterBox("HAN BAJADO", numFmt.format(dropCount), SdbBrightGreen)
                            StatCounterBox("EN OBJETIVO", numFmt.format(targetHits), SdbSaleBadgeText)
                        }
                    }
                }
            }
        }

        if (vm.favorites.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SdbPanel),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, SdbBorder, RoundedCornerShape(8.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⭐", fontSize = 34.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("Todavía no tienes favoritos", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Abre cualquier juego (buscador, Charts u Ofertas) y toca la estrella ⭐ de su ficha para añadirlo a la vigilancia de precios.",
                            fontSize = 12.sp,
                            color = SdbTextMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }

        itemsIndexed(vm.favorites, key = { _, f -> "fav_${f.appId}" }) { idx, f ->
            val hasDrop = f.droppedByCents > 0
            val hitsTarget = f.targetPriceCents > 0 && f.lastSeenCents in 1..f.targetPriceCents

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (idx % 2 == 0) SdbHeader else SdbRowAlt)
                    .border(
                        width = if (hasDrop || hitsTarget) 1.5.dp else 0.5.dp,
                        color = when {
                            hasDrop -> SdbBrightGreen
                            hitsTarget -> SdbSaleBadgeText
                            else -> SdbBorder
                        },
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SteamSmartThumb(
                        appId = f.appId,
                        primaryUrl = f.capsuleUrl,
                        contentDescription = f.name,
                        modifier = Modifier.width(72.dp).height(34.dp).clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(f.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("AppID ${f.appId}", fontSize = 10.sp, color = SdbBlue, fontFamily = FontFamily.Monospace)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = if (f.lastSeenCents > 0) String.format(Locale("es", "ES"), "%.2f €", f.lastSeenCents / 100.0) else "Sin precio",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            color = if (hasDrop) SdbBrightGreen else Color.White
                        )
                        if (f.discountPct > 0) {
                            Text("-${f.discountPct}% DTO.", fontSize = 9.sp, color = SdbSaleBadgeText, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (hasDrop || hitsTarget || f.targetPriceCents > 0) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (hasDrop) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(SdbBrightGreen.copy(alpha = 0.16f))
                                    .border(1.dp, SdbBrightGreen, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    "▼ ¡BAJADA! -${String.format(Locale("es", "ES"), "%.2f €", f.droppedByCents / 100.0)}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = SdbBrightGreen,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        if (hitsTarget) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(SdbSaleBadgeBg)
                                    .border(1.dp, SdbSaleBadgeText, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    "🎯 ¡OBJETIVO CONSEGUIDO!",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = SdbSaleBadgeText,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        } else if (f.targetPriceCents > 0) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(SdbPanel)
                                    .border(1.dp, SdbBorder, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    "🎯 Objetivo: ${String.format(Locale("es", "ES"), "%.2f €", f.targetPriceCents / 100.0)}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SdbTextMuted,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { editingTarget = f },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = SdbBlue)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Precio objetivo", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = {
                        vm.removeFavorite(f.appId)
                    }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Quitar de favoritos", tint = SdbRed, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }

    editingTarget?.let { fav ->
        var textValue by remember(fav.appId) {
            mutableStateOf(
                if (fav.targetPriceCents > 0) String.format(Locale("es", "ES"), "%.2f", fav.targetPriceCents / 100.0) else ""
            )
        }
        AlertDialog(
            onDismissRequest = { editingTarget = null },
            containerColor = SdbPanel,
            titleContentColor = Color.White,
            textContentColor = SdbTextMain,
            title = { Text("Precio objetivo", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) },
            text = {
                Column {
                    Text(
                        "Escribe a partir de qué precio quieres «${fav.name}». SteamDB te lo marcará cuando baje de ahí.",
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = textValue,
                        onValueChange = { textValue = it },
                        singleLine = true,
                        label = { Text("Ej: 9,99") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SdbBlue,
                            unfocusedBorderColor = SdbBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = SdbBlue,
                            unfocusedLabelColor = SdbTextMuted
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val parsed = textValue.trim().replace(",", ".").toDoubleOrNull()
                    if (parsed != null) vm.setFavoriteTargetPrice(fav.appId, parsed)
                    editingTarget = null
                }) { Text("Guardar", color = SdbBlue, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { editingTarget = null }) { Text("Cancelar", color = SdbTextMuted) }
            }
        )
    }
}
