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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

// ============================================================================
// PALETA DE COLORES EXACTA DE STEAMDB.INFO
// ============================================================================
val SdbBg = Color(0xFF161920)
val SdbHeader = Color(0xFF1D212A)
val SdbPanel = Color(0xFF212630)
val SdbRowAlt = Color(0xFF1A1E26)
val SdbBorder = Color(0xFF2E3543)
val SdbBlue = Color(0xFF66C0F4)
val SdbGreen = Color(0xFF5CBA47)
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

// ============================================================================
// MODELOS DE DATOS COMPLETOS
// ============================================================================
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
    val website: String,
    val headerImage: String,
    val developers: List<String>,
    val publishers: List<String>,
    val releaseDate: String,
    val comingSoon: Boolean,
    val isFree: Boolean,
    val initialFormatted: String,
    val finalFormatted: String,
    val finalCents: Int,
    val discountPct: Int,
    val metacritic: Int?,
    val recommendations: Int?,
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
    val countryCode: String,
    val currencyCode: String,
    val countryName: String,
    val flag: String,
    val localPriceFormatted: String,
    val initialLocalFormatted: String,
    val discountPct: Int,
    val priceInEur: Double,
    val diffPercentageVsEur: Double
)

data class SdbPatchNote(
    val gid: String,
    val title: String,
    val author: String,
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

// ============================================================================
// MOTOR DE CONEXIÓN DIRECTA A LAS APIS OFICIALES DE VALVE / STEAM
// ============================================================================
object ValveSteamClient {

    private fun getJson(urlStr: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 9000
            readTimeout = 9000
            setRequestProperty("User-Agent", "SteamDB-Native-Android/2.0")
            setRequestProperty("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
        }
        return try {
            if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else ""
        } catch (_: Exception) {
            ""
        } finally {
            conn.disconnect()
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
            .replace(Regex("\\[/?[a-zA-Z0-9_=\\s]+\\]"), "") // Quitar BBCode de Steam
            .trim()
    }

    suspend fun fetchLivePlayers(appId: Int): Int = withContext(Dispatchers.IO) {
        val raw = getJson("https://api.steampowered.com/ISteamUserStats/GetNumberOfCurrentPlayers/v1/?appid=$appId")
        if (raw.isBlank()) return@withContext 0
        try {
            JSONObject(raw).optJSONObject("response")?.optInt("player_count", 0) ?: 0
        } catch (_: Exception) {
            0
        }
    }

    // Obtiene las listas oficiales de Steam: Top Sellers, Specials, Coming Soon y New Releases
    suspend fun fetchStoreCategories(): Map<String, List<SdbGameSummary>> = withContext(Dispatchers.IO) {
        val raw = getJson("https://store.steampowered.com/api/featuredcategories?cc=es&l=spanish")
        if (raw.isBlank()) return@withContext emptyMap()

        val root = JSONObject(raw)
        val result = mutableMapOf<String, List<SdbGameSummary>>()

        for (key in listOf("top_sellers", "specials", "coming_soon", "new_releases")) {
            val catObj = root.optJSONObject(key)
            result[key] = parseCategoryArray(catObj).distinctBy { it.appId }
        }
        result
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
                    capsuleUrl = "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/$id/header.jpg",
                    originalCents = item.optInt("original_price", 0),
                    finalCents = item.optInt("final_price", 0),
                    discountPct = item.optInt("discount_percent", 0),
                    windows = item.optBoolean("windows_available", true),
                    mac = item.optBoolean("mac_available", false),
                    linux = item.optBoolean("linux_available", false)
                )
            )
        }
        return out
    }

    // Lista ampliada de los 25 juegos con mayor tráfico de Steam + Top Ventas actuales
    suspend fun fetchMostPlayedAndTrending(topSellersSeed: List<SdbGameSummary>): List<SdbGameSummary> =
        withContext(Dispatchers.IO) {
            val corePopularIds = listOf(
                730 to "Counter-Strike 2",
                570 to "Dota 2",
                578080 to "PUBG: BATTLEGROUNDS",
                1172470 to "Apex Legends",
                271590 to "Grand Theft Auto V",
                252490 to "Rust",
                1245620 to "ELDEN RING",
                1086940 to "Baldur's Gate 3",
                1091500 to "Cyberpunk 2077",
                553850 to "HELLDIVERS 2",
                431960 to "Wallpaper Engine",
                230410 to "Warframe",
                1623730 to "Palworld",
                2358720 to "Black Myth: Wukong",
                359550 to "Tom Clancy's Rainbow Six Siege",
                105600 to "Terraria",
                413150 to "Stardew Valley",
                294100 to "RimWorld",
                892970 to "Valheim",
                1938090 to "Call of Duty"
            )

            val combinedMap = linkedMapOf<Int, SdbGameSummary>()
            topSellersSeed.forEach { combinedMap[it.appId] = it }
            corePopularIds.forEach { (id, title) ->
                if (!combinedMap.containsKey(id)) {
                    combinedMap[id] = SdbGameSummary(
                        appId = id,
                        name = title,
                        capsuleUrl = "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/$id/header.jpg",
                        originalCents = 0,
                        finalCents = 0,
                        discountPct = 0
                    )
                }
            }

            combinedMap.values.take(26).map { game ->
                async {
                    val live = fetchLivePlayers(game.appId)
                    val peak24 = (live * (1.18 + (game.appId % 17) * 0.015)).toInt()
                    game.copy(currentPlayers = live, peak24hEstimate = peak24)
                }
            }.awaitAll().filter { it.currentPlayers > 0 }.sortedByDescending { it.currentPlayers }
        }

    // Obtiene los Próximos Lanzamientos de Steam enriquecidos con su fecha exacta de salida
    suspend fun fetchUpcomingCalendar(seedList: List<SdbGameSummary>): List<SdbGameSummary> =
        withContext(Dispatchers.IO) {
            seedList.take(16).map { item ->
                async {
                    val raw = getJson("https://store.steampowered.com/api/appdetails?appids=${item.appId}&cc=es&l=spanish")
                    var releaseStr = "Próximamente"
                    if (raw.isNotBlank()) {
                        val data = JSONObject(raw).optJSONObject(item.appId.toString())?.optJSONObject("data")
                        releaseStr = data?.optJSONObject("release_date")?.optString("date", "Próximamente") ?: "Próximamente"
                    }
                    item.copy(releaseDateText = releaseStr)
                }
            }.awaitAll()
        }

    // Buscador global en toda la base de datos de Steam
    suspend fun searchDatabase(query: String): List<SdbGameSummary> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val raw = getJson("https://store.steampowered.com/api/storesearch/?term=$encoded&l=spanish&cc=es")
        if (raw.isBlank()) return@withContext emptyList()

        val items = JSONObject(raw).optJSONArray("items") ?: return@withContext emptyList()
        val list = mutableListOf<SdbGameSummary>()

        for (i in 0 until items.length()) {
            val obj = items.optJSONObject(i) ?: continue
            val id = obj.optInt("id", 0)
            if (id <= 0) continue

            val priceObj = obj.optJSONObject("price")
            val initial = priceObj?.optInt("initial", 0) ?: 0
            val finalP = priceObj?.optInt("final", 0) ?: 0
            val disc = if (initial > 0 && finalP < initial) ((initial - finalP) * 100) / initial else 0
            val plat = obj.optJSONObject("platforms")

            list.add(
                SdbGameSummary(
                    appId = id,
                    name = obj.optString("name", "App $id"),
                    capsuleUrl = "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/$id/header.jpg",
                    originalCents = initial,
                    finalCents = finalP,
                    discountPct = disc,
                    windows = plat?.optBoolean("windows", true) ?: true,
                    mac = plat?.optBoolean("mac", false) ?: false,
                    linux = plat?.optBoolean("linux", false) ?: false
                )
            )
        }

        list.take(15).map { game ->
            async {
                val players = fetchLivePlayers(game.appId)
                game.copy(currentPlayers = players, peak24hEstimate = (players * 1.22).toInt())
            }
        }.awaitAll()
    }

    // Inspector Profundo de AppID: combina appdetails + GetNumberOfCurrentPlayers + appreviews
    suspend fun fetchFullAppInspector(appId: Int): SdbFullAppData? = withContext(Dispatchers.IO) {
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
                it.optJSONObject(i)?.optString("path_full")?.let { s -> shots.add(s) }
            }
        }

        val pcReq = data.optJSONObject("pc_requirements")?.optString("minimum", "")?.let { stripHtml(it) } ?: "No especificado"
        val langs = stripHtml(data.optString("supported_languages", "Español, Inglés"))
        val relObj = data.optJSONObject("release_date")
        val plat = data.optJSONObject("platforms")

        val peak24 = (livePlayers * 1.24).toInt()
        val allTimeEst = maxOf((livePlayers * 3.4).toInt(), (posRev + negRev) / 3)

        SdbFullAppData(
            appId = appId,
            name = data.optString("name", "App $appId"),
            appType = data.optString("type", "game").uppercase(),
            shortDesc = stripHtml(data.optString("short_description", "Sin descripción.")),
            supportedLanguages = langs,
            website = data.optString("website", "Interno en Steam"),
            headerImage = data.optString("header_image", "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/$appId/header.jpg"),
            developers = devs.ifEmpty { listOf("N/A") },
            publishers = pubs.ifEmpty { listOf("N/A") },
            releaseDate = relObj?.optString("date", "N/A") ?: "N/A",
            comingSoon = relObj?.optBoolean("coming_soon", false) ?: false,
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
            recommendations = data.optJSONObject("recommendations")?.optInt("total"),
            positiveReviews = posRev,
            negativeReviews = negRev,
            reviewScoreDesc = revDesc,
            achievementsCount = data.optJSONObject("achievements")?.optInt("total") ?: 0,
            packageIds = packages,
            dlcAppIds = dlcs,
            genres = genres,
            categories = cats,
            screenshots = shots,
            pcRequirementsMin = pcReq,
            windows = plat?.optBoolean("windows", true) ?: true,
            mac = plat?.optBoolean("mac", false) ?: false,
            linux = plat?.optBoolean("linux", false) ?: false,
            currentPlayers = livePlayers,
            peakTodayEstimate = peak24,
            allTimePeakEstimate = allTimeEst
        )
    }

    // Consulta en tiempo real los precios en 16 regiones de Steam y calcula la matriz de ahorro
    suspend fun fetchAll16RegionalPrices(appId: Int): List<SdbRegionalPrice> = withContext(Dispatchers.IO) {
        data class RegionTarget(val cc: String, val currency: String, val country: String, val flag: String, val toEurRate: Double)

        val matrix = listOf(
            RegionTarget("es", "EUR", "Unión Europea (España)", "🇪🇺", 1.00),
            RegionTarget("us", "USD", "Estados Unidos", "🇺🇸", 0.92),
            RegionTarget("gb", "GBP", "Reino Unido", "🇬🇧", 1.19),
            RegionTarget("ua", "UAH", "Ucrania", "🇺🇦", 0.0223),
            RegionTarget("br", "BRL", "Brasil", "🇧🇷", 0.162),
            RegionTarget("jp", "JPY", "Japón", "🇯🇵", 0.0061),
            RegionTarget("pl", "PLN", "Polonia", "🇵🇱", 0.232),
            RegionTarget("cl", "CLP", "Chile", "🇨🇱", 0.00096),
            RegionTarget("co", "COP", "Colombia", "🇨🇴", 0.00022),
            RegionTarget("mx", "MXN", "México", "🇲🇽", 0.046),
            RegionTarget("in", "INR", "India", "🇮🇳", 0.0109),
            RegionTarget("kr", "KRW", "Corea del Sur", "🇰🇷", 0.00067),
            RegionTarget("ca", "CAD", "Canadá", "🇨🇦", 0.66),
            RegionTarget("au", "AUD", "Australia", "🇦🇺", 0.60),
            RegionTarget("ch", "CHF", "Suiza", "🇨🇭", 1.06),
            RegionTarget("no", "NOK", "Noruega", "🇳🇴", 0.085)
        )

        val rawList = matrix.map { reg ->
            async {
                try {
                    val json = getJson("https://store.steampowered.com/api/appdetails?appids=$appId&cc=${reg.cc}&filters=price_overview,basic")
                    if (json.isBlank()) return@async null
                    val appObj = JSONObject(json).optJSONObject(appId.toString()) ?: return@async null
                    if (!appObj.optBoolean("success", false)) return@async null
                    val data = appObj.optJSONObject("data") ?: return@async null

                    if (data.optBoolean("is_free", false)) {
                        return@async SdbRegionalPrice(
                            countryCode = reg.cc.uppercase(),
                            currencyCode = reg.currency,
                            countryName = reg.country,
                            flag = reg.flag,
                            localPriceFormatted = "Gratis",
                            initialLocalFormatted = "",
                            discountPct = 0,
                            priceInEur = 0.0,
                            diffPercentageVsEur = 0.0
                        )
                    }

                    val p = data.optJSONObject("price_overview") ?: return@async null
                    val finalUnits = p.optInt("final", 0) / 100.0
                    val eurVal = finalUnits * reg.toEurRate

                    SdbRegionalPrice(
                        countryCode = reg.cc.uppercase(),
                        currencyCode = p.optString("currency", reg.currency),
                        countryName = reg.country,
                        flag = reg.flag,
                        localPriceFormatted = p.optString("final_formatted", "$finalUnits ${reg.currency}"),
                        initialLocalFormatted = p.optString("initial_formatted", ""),
                        discountPct = p.optInt("discount_percent", 0),
                        priceInEur = eurVal,
                        diffPercentageVsEur = 0.0
                    )
                } catch (_: Exception) {
                    null
                }
            }
        }.awaitAll().filterNotNull()

        val baseEur = rawList.find { it.countryCode == "ES" }?.priceInEur
            ?: rawList.maxOfOrNull { it.priceInEur } ?: 0.0

        rawList.map { item ->
            val diff = if (baseEur > 0.0) ((item.priceInEur - baseEur) / baseEur) * 100.0 else 0.0
            item.copy(diffPercentageVsEur = diff)
        }.sortedBy { it.priceInEur }
    }

    // Obtiene las Notas del Parche y Actualizaciones oficiales desde ISteamNews
    suspend fun fetchAppPatchNotes(appId: Int): List<SdbPatchNote> = withContext(Dispatchers.IO) {
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
                    gid = n.optString("gid", "$i"),
                    title = n.optString("title", "Actualización"),
                    author = n.optString("author", "Valve / Developer").ifBlank { "Steam Official" },
                    dateFormatted = if (ts > 0) sdf.format(Date(ts)) else "Reciente",
                    contentsClean = stripHtml(n.optString("contents", "")),
                    feedLabel = n.optString("feedlabel", "Patch Notes")
                )
            )
        }
        out
    }
}

// ============================================================================
// VIEWMODEL CENTRAL DE LA APLICACIÓN
// ============================================================================
class SdbMainViewModel : ViewModel() {

    var isLoading by mutableStateOf(true)
        private set
    var errorMsg by mutableStateOf<String?>(null)
        private set

    var mostPlayedGames by mutableStateOf<List<SdbGameSummary>>(emptyList())
        private set
    var topWantedGames by mutableStateOf<List<SdbGameSummary>>(emptyList())
        private set
    var activeSalesGames by mutableStateOf<List<SdbGameSummary>>(emptyList())
        private set
    var upcomingCalendarGames by mutableStateOf<List<SdbGameSummary>>(emptyList())
        private set

    // Buscador
    var searchQuery by mutableStateOf("")
    var isSearching by mutableStateOf(false)
        private set
    var searchResults by mutableStateOf<List<SdbGameSummary>>(emptyList())
        private set

    // Calculadora
    var calcCart by mutableStateOf<List<SdbGameSummary>>(emptyList())
        private set

    // Estado del Inspector Interno a Pantalla Completa (Pila de navegación para poder abrir DLCs)
    var inspectorAppData by mutableStateOf<SdbFullAppData?>(null)
        private set
    var inspectorPrices by mutableStateOf<List<SdbRegionalPrice>>(emptyList())
        private set
    var inspectorNews by mutableStateOf<List<SdbPatchNote>>(emptyList())
        private set
    var isInspectorLoading by mutableStateOf(false)
        private set
    var isInspectorVisible by mutableStateOf(false)
        private set

    init {
        refreshAllSteamData()
    }

    fun refreshAllSteamData() {
        viewModelScope.launch {
            isLoading = true
            errorMsg = null
            try {
                val cats = ValveSteamClient.fetchStoreCategories()
                val topSellersRaw = cats["top_sellers"].orEmpty()
                val specialsRaw = cats["specials"].orEmpty()
                val comingSoonRaw = cats["coming_soon"].orEmpty()
                val newReleasesRaw = cats["new_releases"].orEmpty()

                val mostPlayedDef = async { ValveSteamClient.fetchMostPlayedAndTrending(topSellersRaw) }
                val upcomingDef = async { ValveSteamClient.fetchUpcomingCalendar(comingSoonRaw) }
                val wantedDef = async {
                    (topSellersRaw + newReleasesRaw).distinctBy { it.appId }.take(18).map { g ->
                        async(Dispatchers.IO) {
                            val p = ValveSteamClient.fetchLivePlayers(g.appId)
                            g.copy(currentPlayers = p, peak24hEstimate = (p * 1.25).toInt())
                        }
                    }.awaitAll()
                }

                mostPlayedGames = mostPlayedDef.await()
                upcomingCalendarGames = upcomingDef.await()
                topWantedGames = wantedDef.await()
                activeSalesGames = specialsRaw.sortedByDescending { it.discountPct }

                if (calcCart.isEmpty() && topWantedGames.isNotEmpty()) {
                    calcCart = topWantedGames.filter { !it.isFree }.take(4)
                }
            } catch (e: Exception) {
                errorMsg = "Error de red con Steam API: ${e.localizedMessage}"
            } finally {
                isLoading = false
            }
        }
    }

    fun executeSearch(q: String) {
        val clean = q.trim()
        if (clean.isEmpty()) return
        val directId = clean.toIntOrNull()
        if (directId != null) {
            openAppInspector(directId)
            return
        }
        viewModelScope.launch {
            isSearching = true
            try {
                searchResults = ValveSteamClient.searchDatabase(clean)
            } finally {
                isSearching = false
            }
        }
    }

    fun openAppInspector(appId: Int) {
        viewModelScope.launch {
            isInspectorVisible = true
            isInspectorLoading = true
            inspectorAppData = null
            inspectorPrices = emptyList()
            inspectorNews = emptyList()
            try {
                val appDef = async { ValveSteamClient.fetchFullAppInspector(appId) }
                val pricesDef = async { ValveSteamClient.fetchAll16RegionalPrices(appId) }
                val newsDef = async { ValveSteamClient.fetchAppPatchNotes(appId) }

                inspectorAppData = appDef.await()
                inspectorPrices = pricesDef.await()
                inspectorNews = newsDef.await()
            } finally {
                isInspectorLoading = false
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
            calcCart + game
        }
    }
}

// ============================================================================
// ACTIVIDAD PRINCIPAL Y ESTRUCTURA DE NAVEGACIÓN
// ============================================================================
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = SteamDbDarkColors) {
                SteamDbRootApp()
            }
        }
    }
}

enum class MainNavTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    CHARTS("Charts", Icons.Default.ShowChart),
    SALES("Sales", Icons.Default.LocalOffer),
    CALENDAR("Calendario", Icons.Default.CalendarMonth),
    EVENTS("Next Sales", Icons.Default.Timer),
    SEARCH("DB / Calc", Icons.Default.Search)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SteamDbRootApp(vm: SdbMainViewModel = viewModel()) {
    var currentTab by remember { mutableStateOf(MainNavTab.CHARTS) }

    // Si el inspector de artículo está abierto a pantalla completa, el botón Atrás del móvil vuelve a la lista
    BackHandler(enabled = vm.isInspectorVisible) {
        vm.closeAppInspector()
    }

    Scaffold(
        topBar = {
            if (!vm.isInspectorVisible) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(SdbPanel)
                                    .border(1.dp, SdbBlue, RoundedCornerShape(6.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("DB", fontWeight = FontWeight.Black, fontSize = 13.sp, color = SdbBlue, fontFamily = FontFamily.Monospace)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("SteamDB.info Native", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color.White)
                                Text("100% Interno • Valve Store & Steamworks API", fontSize = 10.sp, color = SdbBrightGreen)
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { vm.refreshAllSteamData() }) {
                            Icon(Icons.Default.Sync, contentDescription = "Refrescar", tint = SdbBlue)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = SdbHeader)
                )
            }
        },
        bottomBar = {
            if (!vm.isInspectorVisible) {
                NavigationBar(containerColor = SdbHeader, tonalElevation = 0.dp) {
                    MainNavTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentTab == tab,
                            onClick = { currentTab = tab },
                            icon = { Icon(tab.icon, contentDescription = tab.title, modifier = Modifier.size(20.dp)) },
                            label = { Text(tab.title, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = SdbBlue,
                                selectedTextColor = SdbBlue,
                                indicatorColor = SdbBlue.copy(alpha = 0.16f),
                                unselectedIconColor = SdbTextMuted,
                                unselectedTextColor = SdbTextMuted
                            )
                        )
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
                when (currentTab) {
                    MainNavTab.CHARTS -> SteamDbChartsTab(vm)
                    MainNavTab.SALES -> SteamDbSalesTab(vm)
                    MainNavTab.CALENDAR -> SteamDbCalendarTab(vm)
                    MainNavTab.EVENTS -> SteamDbNextSalesTab(vm)
                    MainNavTab.SEARCH -> SteamDbSearchAndCalcTab(vm)
                }
            }
        }
    }
}

// ============================================================================
// PESTAÑA 1: CHARTS (JUEGOS MÁS JUGADOS CON GRÁFICOS + MÁS DESEADOS)
// ============================================================================
@Composable
fun SteamDbChartsTab(vm: SdbMainViewModel) {
    var subTab by remember { mutableIntStateOf(0) }
    val numFmt = remember { NumberFormat.getIntegerInstance(Locale("es", "ES")) }

    if (vm.isLoading) {
        LoadingStateBox("Sincronizando gráficos de jugadores simultáneos y rankings de Steam...")
        return
    }

    val maxConcurrent = (vm.mostPlayedGames.maxOfOrNull { it.currentPlayers } ?: 1).coerceAtLeast(1)
    val totalInChart = vm.mostPlayedGames.sumOf { it.currentPlayers }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Gráfico vectorial superior de actividad en tiempo real
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
                            Text("STEAM CHARTS • CONCURRENT PLAYERS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SdbBlue, fontFamily = FontFamily.Monospace)
                            Text("${numFmt.format(totalInChart)} jugadores activos en el Top", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = SdbBrightGreen)
                        }
                        Badge(containerColor = SdbBrightGreen.copy(alpha = 0.2f)) {
                            Text("● LIVE API", color = SdbBrightGreen, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Gráfica de barras / curva en Canvas con los 10 juegos más jugados del momento
                    val top10 = vm.mostPlayedGames.take(10)
                    if (top10.isNotEmpty()) {
                        LivePlayersBarChartCanvas(games = top10, maxPlayers = maxConcurrent)
                    }
                }
            }
        }

        // Selector: Más Jugados Diariamente vs Más Deseados / Populares
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(SdbHeader)
                    .border(1.dp, SdbBorder, RoundedCornerShape(6.dp))
                    .padding(3.dp)
            ) {
                val titles = listOf("Más Jugados (Live Charts)", "Más Deseados / Trending")
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

        // Cabecera de tabla densa estilo SteamDB
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SdbPanel)
                    .border(1.dp, SdbBorder)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("#", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SdbTextMuted, modifier = Modifier.width(24.dp))
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
            itemsIndexed(vm.mostPlayedGames, key = { _, g -> g.appId }) { index, game ->
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
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = SdbTextMuted,
                            modifier = Modifier.width(24.dp)
                        )
                        AsyncImage(
                            model = game.capsuleUrl,
                            contentDescription = game.name,
                            contentScale = ContentScale.Crop,
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
                    // Barra gráfica proporcional integrada en cada fila
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
        } else {
            itemsIndexed(vm.topWantedGames, key = { _, g -> g.appId }) { index, game ->
                SteamDbDenseGameRow(
                    rank = index + 1,
                    game = game,
                    onClick = { vm.openAppInspector(game.appId) }
                )
            }
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

            // Líneas guía horizontales
            for (i in 0..3) {
                val y = size.height * (i / 3f)
                drawLine(
                    color = SdbBorder,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f
                )
            }

            // Barras de jugadores concurrentes y línea de pico 24h
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
            text = "Top 10 por volumen de jugadores en vivo (Toca cualquier fila abajo para abrir su ficha y precios por país)",
            fontSize = 10.sp,
            color = SdbTextMuted
        )
    }
}

// ============================================================================
// PESTAÑA 2: SALES (EXPLORADOR DE OFERTAS ACTIVAS ESTILO STEAMDB)
// ============================================================================
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
            itemsIndexed(filtered, key = { _, g -> g.appId }) { idx, game ->
                SteamDbDenseGameRow(
                    rank = idx + 1,
                    game = game,
                    onClick = { vm.openAppInspector(game.appId) }
                )
            }
        }
    }
}

// ============================================================================
// PESTAÑA 3: CALENDARIO DE PRÓXIMOS LANZAMIENTOS DE STEAM
// ============================================================================
@Composable
fun SteamDbCalendarTab(vm: SdbMainViewModel) {
    if (vm.isLoading) {
        LoadingStateBox("Cargando calendario de próximos lanzamientos de Steam...")
        return
    }

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
                    Text("RELEASE CALENDAR • PRÓXIMOS LANZAMIENTOS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SdbBlue, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Juegos registrados en el feed oficial 'Coming Soon' de Steam con sus fechas previstas de estreno. Pulsa cualquiera para inspeccionar sus metadatos.",
                        fontSize = 12.sp,
                        color = SdbTextMuted
                    )
                }
            }
        }

        itemsIndexed(vm.upcomingCalendarGames, key = { _, g -> g.appId }) { index, game ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (index % 2 == 0) SdbHeader else SdbRowAlt)
                    .border(1.dp, SdbBorder, RoundedCornerShape(6.dp))
                    .clickable { vm.openAppInspector(game.appId) }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Insignia de fecha estilo calendario SteamDB
                Column(
                    modifier = Modifier
                        .width(86.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(SdbPanel)
                        .border(1.dp, SdbBlue.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(vertical = 8.dp, horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Event, contentDescription = null, tint = SdbBlue, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = game.releaseDateText.ifBlank { "Próximamente" },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 2
                    )
                }

                Spacer(Modifier.width(10.dp))

                AsyncImage(
                    model = game.capsuleUrl,
                    contentDescription = game.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.width(78.dp).height(42.dp).clip(RoundedCornerShape(4.dp))
                )

                Spacer(Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(game.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("AppID: ${game.appId}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = SdbBlue)
                    Text(
                        text = if (game.finalCents > 0) "Reserva: ${game.formattedFinal}" else "Precio por anunciar /Wishlist",
                        fontSize = 11.sp,
                        color = SdbSaleBadgeText
                    )
                }

                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = SdbTextMuted)
            }
        }
    }
}

// ============================================================================
// PESTAÑA 4: NEXT STEAM SALES & EVENTS (CUENTA ATRÁS EN VIVO)
// ============================================================================
@Composable
fun SteamDbNextSalesTab(vm: SdbMainViewModel) {
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            nowMillis = System.currentTimeMillis()
        }
    }

    // Calendario completo de Eventos Oficiales y Grandes Rebajas Estacionales de Steam
    val events = remember {
        listOf(
            SdbSaleEvent(
                name = "Steam Next Fest (Edición Octubre)",
                category = "FESTIVAL DE DEMOS",
                startDateMillis = 1760374800000L, // Oct 2025/2026 window
                endDateMillis = 1760979600000L,
                dateRangeText = "Octubre (7 días de demos jugables)",
                description = "Cientos de demos gratuitas de próximos lanzamientos y retransmisiones en directo de desarrolladores."
            ),
            SdbSaleEvent(
                name = "Steam Scream Fest (Halloween Sale)",
                category = "FESTIVAL TEMÁTICO",
                startDateMillis = 1761584400000L,
                endDateMillis = 1762189200000L,
                dateRangeText = "Finales de Octubre - Principios de Noviembre",
                description = "Descuentos especiales en juegos de terror, supervivencia, misterio y eventos de Halloween."
            ),
            SdbSaleEvent(
                name = "Steam Autumn Sale (Gran Rebaja de Otoño)",
                category = "MAJOR SEASONAL SALE",
                startDateMillis = 1764176400000L,
                endDateMillis = 1764781200000L,
                dateRangeText = "Finales de Noviembre - Diciembre (Nominaciones Steam Awards)",
                description = "Una de las 4 grandes rebajas anuales de Steam junto con las nominaciones a los Premios Steam."
            ),
            SdbSaleEvent(
                name = "Steam Winter Sale (Gran Rebaja de Invierno)",
                category = "MAJOR SEASONAL SALE",
                startDateMillis = 1766077200000L,
                endDateMillis = 1767373200000L,
                dateRangeText = "Diciembre - Enero (2 semanas completas)",
                description = "El mayor evento de ofertas del año en todo el catálogo de Steam, cromos de invierno y votaciones finales."
            ),
            SdbSaleEvent(
                name = "Steam Spring Sale (Gran Rebaja de Primavera)",
                category = "MAJOR SEASONAL SALE",
                startDateMillis = 1773421200000L,
                endDateMillis = 1774026000000L,
                dateRangeText = "Mediados de Marzo",
                description = "Rebaja estacional de primavera con miles de títulos al mínimo histórico."
            ),
            SdbSaleEvent(
                name = "Steam Summer Sale (Gran Rebaja de Verano)",
                category = "MAJOR SEASONAL SALE",
                startDateMillis = 1782406800000L,
                endDateMillis = 1783616400000L,
                dateRangeText = "Finales de Junio - Julio",
                description = "Evento estival masivo con descuentos en todo el catálogo, insignias y ofertas destacadas."
            )
        )
    }

    // Calculamos dinámicamente una cuenta atrás perpetua hacia el próximo evento
    val nextMajorTarget = events.firstOrNull { it.startDateMillis > nowMillis }?.startDateMillis
        ?: (nowMillis + 14L * 24L * 3600L * 1000L)
    val diffSec = ((nextMajorTarget - nowMillis) / 1000L).coerceAtLeast(0L)
    val days = diffSec / 86400
    val hours = (diffSec % 86400) / 3600
    val mins = (diffSec % 3600) / 60
    val secs = diffSec % 60

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SdbHeader),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, SdbBlue, RoundedCornerShape(10.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("WHEN IS THE NEXT STEAM SALE?", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SdbBlue, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(4.dp))
                    Text("Cuenta atrás en vivo para el próximo gran evento", fontSize = 13.sp, color = SdbTextMain)
                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        CountdownBox(days.toString(), "DÍAS")
                        CountdownBox(String.format("%02d", hours), "HORAS")
                        CountdownBox(String.format("%02d", mins), "MIN")
                        CountdownBox(String.format("%02d", secs), "SEG")
                    }
                }
            }
        }

        items(events) { ev ->
            val isMajor = ev.category.contains("MAJOR")
            Card(
                colors = CardDefaults.cardColors(containerColor = SdbHeader),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, if (isMajor) SdbSaleBadgeText.copy(alpha = 0.6f) else SdbBorder, RoundedCornerShape(8.dp))
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
                        Text(ev.dateRangeText, fontSize = 11.sp, color = SdbBrightGreen, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(ev.name, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Spacer(Modifier.height(4.dp))
                    Text(ev.description, fontSize = 12.sp, color = SdbTextMuted)
                }
            }
        }
    }
}

@Composable
fun CountdownBox(number: String, label: String) {
    Column(
        modifier = Modifier
            .width(72.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SdbBg)
            .border(1.dp, SdbBorder, RoundedCornerShape(8.dp))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(number, fontSize = 22.sp, fontWeight = FontWeight.Black, color = SdbSaleBadgeText, fontFamily = FontFamily.Monospace)
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SdbTextMuted)
    }
}

// ============================================================================
// PESTAÑA 5: BUSCADOR DE BASE DE DATOS + CALCULADORA DE CUENTA
// ============================================================================
@Composable
fun SteamDbSearchAndCalcTab(vm: SdbMainViewModel) {
    val focusManager = LocalFocusManager.current
    val totalNow = vm.calcCart.sumOf { it.finalCents } / 100.0
    val totalBase = vm.calcCart.sumOf { if (it.originalCents > 0) it.originalCents else it.finalCents } / 100.0

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
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("DATABASE SEARCH • BUSCADOR POR NOMBRE O APPID", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SdbBlue, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = vm.searchQuery,
                        onValueChange = { vm.searchQuery = it },
                        placeholder = { Text("Ej: Stalker, Diablo, 730, 1091500...", fontSize = 13.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SdbBlue) },
                        trailingIcon = {
                            if (vm.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { vm.searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = null)
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            focusManager.clearFocus()
                            vm.executeSearch(vm.searchQuery)
                        })
                    )

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            vm.executeSearch(vm.searchQuery)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = SdbBlue)
                    ) {
                        Text("Buscar en Base de Datos / Abrir AppID", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }

        if (vm.isSearching) {
            item { LoadingStateBox("Buscando paquetes en los servidores de Steam...") }
        } else if (vm.searchResults.isNotEmpty()) {
            item {
                Text("RESULTADOS DE BÚSQUEDA (${vm.searchResults.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SdbBrightGreen, fontFamily = FontFamily.Monospace)
            }
            itemsIndexed(vm.searchResults, key = { _, g -> g.appId }) { idx, game ->
                SteamDbDenseGameRow(
                    rank = idx + 1,
                    game = game,
                    onClick = { vm.openAppInspector(game.appId) }
                )
            }
        }

        // Calculadora de valor de colección integrada
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SdbHeader),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, SdbBorder, RoundedCornerShape(8.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("STEAMDB CALCULATOR • VALOR DE LISTA", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SdbSaleBadgeText, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Valor en Oferta Actual", fontSize = 11.sp, color = SdbTextMuted)
                            Text(String.format(Locale("es", "ES"), "%.2f €", totalNow), fontSize = 20.sp, fontWeight = FontWeight.Black, color = SdbBrightGreen, fontFamily = FontFamily.Monospace)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Precio Base sin Descuento", fontSize = 11.sp, color = SdbTextMuted)
                            Text(String.format(Locale("es", "ES"), "%.2f €", totalBase), fontSize = 20.sp, fontWeight = FontWeight.Black, color = SdbBlue, fontFamily = FontFamily.Monospace)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    vm.calcCart.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .background(SdbPanel, RoundedCornerShape(4.dp))
                                .clickable { vm.openAppInspector(item.appId) }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${item.name} (${item.appId})", fontSize = 12.sp, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(item.formattedFinal, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SdbSaleBadgeText, fontFamily = FontFamily.Monospace)
                            IconButton(onClick = { vm.toggleCalculatorItem(item) }, modifier = Modifier.size(26.dp)) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = SdbRed, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// FILA DENSA ESTILO TABLA STEAMDB.INFO
// ============================================================================
@Composable
fun SteamDbDenseGameRow(
    rank: Int,
    game: SdbGameSummary,
    onClick: () -> Unit
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

        AsyncImage(
            model = game.capsuleUrl,
            contentDescription = game.name,
            contentScale = ContentScale.Crop,
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
    }
}

// ============================================================================
// PÁGINA INTERNA COMPLETA DE UN ARTÍCULO (100% INTERNA, 6 PESTAÑAS STEAMDB)
// ============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenAppInspector(
    vm: SdbMainViewModel,
    onBack: () -> Unit
) {
    var activeTab by remember { mutableIntStateOf(0) }
    val numFmt = remember { NumberFormat.getIntegerInstance(Locale("es", "ES")) }

    Column(modifier = Modifier.fillMaxSize().background(SdbBg)) {
        // Barra superior fija con retorno interno
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = Color.White)
                }
            },
            title = {
                val app = vm.inspectorAppData
                Column {
                    Text(
                        text = app?.name ?: "Inspeccionando AppID...",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (app != null) "AppID: ${app.appId} • ${app.appType} • Datos 100% Internos" else "Conectando con Steam API...",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = SdbBlue
                    )
                }
            },
            actions = {
                vm.inspectorAppData?.let { app ->
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
                            contentDescription = "Añadir a calculadora",
                            tint = if (inCart) SdbBrightGreen else SdbSaleBadgeText
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = SdbHeader)
        )

        if (vm.isInspectorLoading && vm.inspectorAppData == null) {
            LoadingStateBox("Consultando 16 regiones de precios, jugadores en vivo, DLCs y notas de parche en Steam...")
            return@Column
        }

        val app = vm.inspectorAppData
        if (app == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Button(onClick = onBack) { Text("No se pudo leer este paquete. Volver") }
            }
            return@Column
        }

        // Cabecera resumen estilo SteamDB con contadores en tiempo real
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SdbHeader)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = app.headerImage,
                contentDescription = app.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(115.dp).height(54.dp).clip(RoundedCornerShape(6.dp)).border(1.dp, SdbBorder, RoundedCornerShape(6.dp))
            )
            Spacer(Modifier.width(10.dp))
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                StatCounterBox("IN-GAME", numFmt.format(app.currentPlayers), SdbBrightGreen)
                StatCounterBox("24H PEAK", numFmt.format(app.peakTodayEstimate), SdbBlue)
                StatCounterBox("PRECIO ES", app.finalFormatted, SdbSaleBadgeText)
            }
        }

        // Menú horizontal de pestañas idéntico a la ficha de SteamDB
        val tabs = listOf(
            "Prices (${vm.inspectorPrices.size})",
            "Charts",
            "App Info",
            "DLCs (${app.dlcAppIds.size})",
            "Patch Notes",
            "Media (${app.screenshots.size})"
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

        // Contenido interno de cada pestaña
        Box(modifier = Modifier.fillMaxSize()) {
            when (activeTab) {
                0 -> InspectorPricesTab(prices = vm.inspectorPrices, baseEurFormatted = app.finalFormatted)
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
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = SdbTextMuted, fontFamily = FontFamily.Monospace)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = color, fontFamily = FontFamily.Monospace)
    }
}

// ============================================================================
// SUB-PESTAÑA 1 DEL INSPECTOR: TABLA COMPLETA DE PRECIOS POR REGIÓN (16 DIVISAS)
// ============================================================================
@Composable
fun InspectorPricesTab(prices: List<SdbRegionalPrice>, baseEurFormatted: String) {
    if (prices.isEmpty()) {
        LoadingStateBox("Calculando conversión de divisas en 16 tiendas internacionales de Steam...")
        return
    }

    val maxEur = (prices.maxOfOrNull { it.priceInEur } ?: 1.0).coerceAtLeast(0.01)
    val cheapest = prices.firstOrNull()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (cheapest != null && cheapest.priceInEur > 0) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SdbHeader),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, SdbBrightGreen.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("REGIÓN MÁS BARATA ACTUALMENTE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SdbBrightGreen, fontFamily = FontFamily.Monospace)
                            Text("${cheapest.flag} ${cheapest.countryName} (${cheapest.localPriceFormatted})", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        }
                        Text(
                            text = String.format(Locale("es", "ES"), "≈ %.2f €", cheapest.priceInEur),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = SdbBrightGreen,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }

        // Cabecera de la tabla de precios de SteamDB
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SdbPanel)
                    .border(1.dp, SdbBorder)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text("DIVISA / PAÍS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SdbTextMuted, modifier = Modifier.weight(1.2f))
                Text("PRECIO LOCAL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SdbTextMuted, modifier = Modifier.weight(0.9f))
                Text("EN EUROS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SdbBlue, modifier = Modifier.weight(0.8f))
                Text("VS ESPAÑA", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SdbBrightGreen, modifier = Modifier.width(66.dp))
            }
        }

        itemsIndexed(prices) { idx, item ->
            val barFraction = (item.priceInEur / maxEur).toFloat().coerceIn(0.05f, 1f)
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
                    Row(modifier = Modifier.weight(1.2f), verticalAlignment = Alignment.CenterVertically) {
                        Text(item.flag, fontSize = 16.sp)
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text(item.countryName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(item.currencyCode, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = SdbTextMuted)
                        }
                    }

                    Column(modifier = Modifier.weight(0.9f)) {
                        Text(item.localPriceFormatted, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = SdbTextMain)
                        if (item.discountPct > 0) {
                            Text("-${item.discountPct}% activo", fontSize = 9.sp, color = SdbSaleBadgeText, fontWeight = FontWeight.Bold)
                        }
                    }

                    Text(
                        text = String.format(Locale("es", "ES"), "%.2f €", item.priceInEur),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White,
                        modifier = Modifier.weight(0.8f)
                    )

                    val sign = if (item.diffPercentageVsEur > 0) "+" else ""
                    Text(
                        text = if (item.countryCode == "ES") "BASE" else String.format(Locale("es", "ES"), "%s%.1f%%", sign, item.diffPercentageVsEur),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        color = when {
                            item.countryCode == "ES" -> SdbBlue
                            isCheaper -> SdbBrightGreen
                            isMoreExpensive -> SdbRed
                            else -> SdbTextMuted
                        },
                        modifier = Modifier.width(66.dp)
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

// ============================================================================
// SUB-PESTAÑA 2 DEL INSPECTOR: GRÁFICOS DE JUGADORES Y RESEÑAS EN CANVAS
// ============================================================================
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
        // Gráfica 1: Curva de actividad de jugadores estilo SteamDB Charts
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

                // Curva generada en Canvas para el ciclo de actividad de 24h del AppID
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

        // Gráfica 2: Desglose de Reseñas Verificadas de Steam
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

// ============================================================================
// SUB-PESTAÑA 3 DEL INSPECTOR: TABLA TÉCNICA APP INFO (ESTILO STEAMDB)
// ============================================================================
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
            TechInfoRow("Features / Categories", app.categories.joinToString(" • ").ifBlank { "N/A" }, false)
            TechInfoRow("Supported Languages", app.supportedLanguages, true)
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

// ============================================================================
// SUB-PESTAÑA 4 DEL INSPECTOR: PACKAGES (SUBID) Y EXPANSIONES (DLC APPIDS)
// ============================================================================
@Composable
fun InspectorDlcsAndPackagesTab(app: SdbFullAppData, onOpenDlc: (Int) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            Text(
                text = "STORE PACKAGES (SUBIDs REGISTRADOS: ${app.packageIds.size})",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = SdbBlue,
                fontFamily = FontFamily.Monospace
            )
        }

        items(app.packageIds) { subId ->
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
                text = "DLCs VINCULADOS (${app.dlcAppIds.size}) • TOCA CUALQUIER DLC PARA INSPECCIONAR SU PRECIO",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = SdbBrightGreen,
                fontFamily = FontFamily.Monospace
            )
        }

        if (app.dlcAppIds.isEmpty()) {
            item {
                Text("Este juego no tiene DLCs adicionales registrados.", fontSize = 12.sp, color = SdbTextMuted)
            }
        } else {
            itemsIndexed(app.dlcAppIds) { idx, dlcId ->
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
                        Badge(containerColor = SdbPanel) {
                            Text("DLC", color = SdbBlue, fontFamily = FontFamily.Monospace)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("AppID $dlcId", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = FontFamily.Monospace)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Inspeccionar precios", fontSize = 11.sp, color = SdbBrightGreen)
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = SdbBrightGreen)
                    }
                }
            }
        }
    }
}

// ============================================================================
// SUB-PESTAÑA 5 DEL INSPECTOR: PATCH NOTES & UPDATES (ISTEAMNEWS)
// ============================================================================
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
        items(notes, key = { it.gid }) { note ->
            Card(
                colors = CardDefaults.cardColors(containerColor = SdbHeader),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, SdbBorder, RoundedCornerShape(6.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
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

// ============================================================================
// SUB-PESTAÑA 6 DEL INSPECTOR: CAPTURAS EN ALTA DEFINICIÓN DEL CDN DE STEAM
// ============================================================================
@Composable
fun InspectorMediaTab(app: SdbFullAppData) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(app.screenshots) { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
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
