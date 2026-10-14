package com.example.steamdb

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.NumberFormat
import java.util.Locale

val SteamDbBg = Color(0xFF11151C)
val SteamDbSurface = Color(0xFF1B202B)
val SteamDbCard = Color(0xFF232A38)
val SteamDbBlue = Color(0xFF66C0F4)
val SteamDbDiscountBg = Color(0xFF4C6B22)
val SteamDbDiscountText = Color(0xFFBEEE11)
val SteamDbLiveGreen = Color(0xFF00E676)
val SteamDbMuted = Color(0xFF8F98A0)

private val SteamDbColorScheme = darkColorScheme(
    primary = SteamDbBlue,
    secondary = SteamDbLiveGreen,
    tertiary = SteamDbDiscountText,
    background = SteamDbBg,
    surface = SteamDbSurface,
    surfaceVariant = SteamDbCard,
    onPrimary = Color(0xFF0B1219),
    onBackground = Color(0xFFE5E9F0),
    onSurface = Color(0xFFE5E9F0),
    onSurfaceVariant = SteamDbMuted
)

data class SteamGameItem(
    val appId: Int,
    val name: String,
    val imageUrl: String,
    val originalPriceCents: Int,
    val finalPriceCents: Int,
    val discountPercent: Int,
    val currency: String = "EUR",
    val windows: Boolean = true,
    val mac: Boolean = false,
    val linux: Boolean = false,
    val metascore: String = "",
    val currentPlayers: Int? = null
) {
    val isFree: Boolean get() = finalPriceCents == 0 && originalPriceCents == 0
    val finalFormatted: String
        get() = if (isFree) "Gratis" else String.format(Locale("es", "ES"), "%.2f €", finalPriceCents / 100.0)
    val originalFormatted: String
        get() = String.format(Locale("es", "ES"), "%.2f €", originalPriceCents / 100.0)
}

data class SteamAppDetails(
    val appId: Int,
    val name: String,
    val type: String,
    val shortDescription: String,
    val headerImage: String,
    val developers: List<String>,
    val publishers: List<String>,
    val releaseDate: String,
    val isFree: Boolean,
    val initialPriceFormatted: String,
    val finalPriceFormatted: String,
    val finalPriceCents: Int,
    val discountPercent: Int,
    val metacriticScore: Int?,
    val recommendationsTotal: Int?,
    val achievementsTotal: Int?,
    val dlcCount: Int,
    val genres: List<String>,
    val categories: List<String>,
    val screenshots: List<String>,
    val windows: Boolean,
    val mac: Boolean,
    val linux: Boolean,
    val currentPlayers: Int
)

data class RegionalPriceComparison(
    val countryCode: String,
    val countryName: String,
    val flagEmoji: String,
    val localFormatted: String,
    val convertedToEur: Double,
    val discountPercent: Int
)

object SteamApiRepository {

    private fun httpGet(urlString: String): String {
        val url = URL(urlString)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("User-Agent", "SteamDB-Android-Client/1.0")
            setRequestProperty("Accept-Language", "es-ES,es;q=0.9")
        }
        return try {
            if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                ""
            }
        } finally {
            conn.disconnect()
        }
    }

    suspend fun fetchFeaturedCategories(): Triple<List<SteamGameItem>, List<SteamGameItem>, List<SteamGameItem>> =
        withContext(Dispatchers.IO) {
            val response = httpGet("https://store.steampowered.com/api/featuredcategories?cc=es&l=spanish")
            if (response.isBlank()) return@withContext Triple(emptyList(), emptyList(), emptyList())

            val root = JSONObject(response)
            val topSellers = parseCategoryItems(root.optJSONObject("top_sellers"))
            val specials = parseCategoryItems(root.optJSONObject("specials"))
            val newReleases = parseCategoryItems(root.optJSONObject("new_releases"))

            Triple(
                topSellers.distinctBy { it.appId },
                specials.distinctBy { it.appId },
                newReleases.distinctBy { it.appId }
            )
        }

    private fun parseCategoryItems(categoryObj: JSONObject?): List<SteamGameItem> {
        if (categoryObj == null) return emptyList()
        val itemsArray = categoryObj.optJSONArray("items") ?: return emptyList()
        val list = mutableListOf<SteamGameItem>()

        for (i in 0 until itemsArray.length()) {
            val item = itemsArray.optJSONObject(i) ?: continue
            val id = item.optInt("id", 0)
            if (id == 0) continue

            list.add(
                SteamGameItem(
                    appId = id,
                    name = item.optString("name", "App $id"),
                    imageUrl = item.optString(
                        "large_capsule_image",
                        "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/$id/header.jpg"
                    ),
                    originalPriceCents = item.optInt("original_price", 0),
                    finalPriceCents = item.optInt("final_price", 0),
                    discountPercent = item.optInt("discount_percent", 0),
                    currency = item.optString("currency", "EUR"),
                    windows = item.optBoolean("windows_available", true),
                    mac = item.optBoolean("mac_available", false),
                    linux = item.optBoolean("linux_available", false)
                )
            )
        }
        return list
    }

    suspend fun fetchCurrentPlayers(appId: Int): Int = withContext(Dispatchers.IO) {
        try {
            val json = httpGet("https://api.steampowered.com/ISteamUserStats/GetNumberOfCurrentPlayers/v1/?appid=$appId")
            if (json.isBlank()) return@withContext 0
            val resp = JSONObject(json).optJSONObject("response")
            resp?.optInt("player_count", 0) ?: 0
        } catch (_: Exception) {
            0
        }
    }

    suspend fun searchStore(query: String): List<SteamGameItem> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val json = httpGet("https://store.steampowered.com/api/storesearch/?term=$encoded&l=spanish&cc=es")
        if (json.isBlank()) return@withContext emptyList()

        val root = JSONObject(json)
        val items = root.optJSONArray("items") ?: return@withContext emptyList()
        val results = mutableListOf<SteamGameItem>()

        for (i in 0 until items.length()) {
            val obj = items.optJSONObject(i) ?: continue
            val appId = obj.optInt("id", 0)
            if (appId == 0) continue

            val priceObj = obj.optJSONObject("price")
            val initialCents = priceObj?.optInt("initial", 0) ?: 0
            val finalCents = priceObj?.optInt("final", 0) ?: 0
            val discount = if (initialCents > 0 && finalCents < initialCents) {
                ((initialCents - finalCents) * 100) / initialCents
            } else 0

            val platformsObj = obj.optJSONObject("platforms")

            results.add(
                SteamGameItem(
                    appId = appId,
                    name = obj.optString("name", "App $appId"),
                    imageUrl = "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/$appId/header.jpg",
                    originalPriceCents = initialCents,
                    finalPriceCents = finalCents,
                    discountPercent = discount,
                    windows = platformsObj?.optBoolean("windows", true) ?: true,
                    mac = platformsObj?.optBoolean("mac", false) ?: false,
                    linux = platformsObj?.optBoolean("linux", false) ?: false,
                    metascore = obj.optString("metascore", "")
                )
            )
        }
        results
    }

    suspend fun fetchAppDetails(appId: Int): SteamAppDetails? = withContext(Dispatchers.IO) {
        val json = httpGet("https://store.steampowered.com/api/appdetails?appids=$appId&cc=es&l=spanish")
        if (json.isBlank()) return@withContext null

        val root = JSONObject(json)
        val appWrapper = root.optJSONObject(appId.toString()) ?: return@withContext null
        if (!appWrapper.optBoolean("success", false)) return@withContext null

        val data = appWrapper.optJSONObject("data") ?: return@withContext null
        val players = fetchCurrentPlayers(appId)

        val priceObj = data.optJSONObject("price_overview")
        val isFree = data.optBoolean("is_free", false)
        val finalCents = priceObj?.optInt("final", 0) ?: 0
        val discount = priceObj?.optInt("discount_percent", 0) ?: 0

        val devs = mutableListOf<String>()
        data.optJSONArray("developers")?.let { arr ->
            for (i in 0 until arr.length()) devs.add(arr.optString(i))
        }

        val pubs = mutableListOf<String>()
        data.optJSONArray("publishers")?.let { arr ->
            for (i in 0 until arr.length()) pubs.add(arr.optString(i))
        }

        val genres = mutableListOf<String>()
        data.optJSONArray("genres")?.let { arr ->
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.optString("description")?.let { genres.add(it) }
            }
        }

        val categories = mutableListOf<String>()
        data.optJSONArray("categories")?.let { arr ->
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.optString("description")?.let { categories.add(it) }
            }
        }

        val screenshots = mutableListOf<String>()
        data.optJSONArray("screenshots")?.let { arr ->
            for (i in 0 until minOf(arr.length(), 8)) {
                arr.optJSONObject(i)?.optString("path_thumbnail")?.let { screenshots.add(it) }
            }
        }

        val platforms = data.optJSONObject("platforms")

        SteamAppDetails(
            appId = appId,
            name = data.optString("name", "App $appId"),
            type = data.optString("type", "game").uppercase(),
            shortDescription = data.optString("short_description", "Sin descripción disponible."),
            headerImage = data.optString(
                "header_image",
                "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/$appId/header.jpg"
            ),
            developers = devs.ifEmpty { listOf("Desconocido") },
            publishers = pubs.ifEmpty { listOf("Desconocido") },
            releaseDate = data.optJSONObject("release_date")?.optString("date", "N/A") ?: "N/A",
            isFree = isFree,
            initialPriceFormatted = priceObj?.optString("initial_formatted", "").orEmpty(),
            finalPriceFormatted = when {
                isFree -> "Gratis (Free to Play)"
                priceObj != null -> priceObj.optString("final_formatted", "N/A")
                else -> "No disponible"
            },
            finalPriceCents = finalCents,
            discountPercent = discount,
            metacriticScore = data.optJSONObject("metacritic")?.optInt("score"),
            recommendationsTotal = data.optJSONObject("recommendations")?.optInt("total"),
            achievementsTotal = data.optJSONObject("achievements")?.optInt("total"),
            dlcCount = data.optJSONArray("dlc")?.length() ?: 0,
            genres = genres,
            categories = categories,
            screenshots = screenshots,
            windows = platforms?.optBoolean("windows", true) ?: true,
            mac = platforms?.optBoolean("mac", false) ?: false,
            linux = platforms?.optBoolean("linux", false) ?: false,
            currentPlayers = players
        )
    }

    suspend fun fetchRegionalPrices(appId: Int): List<RegionalPriceComparison> = withContext(Dispatchers.IO) {
        data class RegionSpec(val cc: String, val name: String, val flag: String, val rateToEur: Double)

        val regions = listOf(
            RegionSpec("es", "Zona Euro (España)", "🇪🇺", 1.00),
            RegionSpec("us", "Estados Unidos (USD)", "🇺🇸", 0.92),
            RegionSpec("gb", "Reino Unido (GBP)", "🇬🇧", 1.19),
            RegionSpec("jp", "Japón (JPY)", "🇯🇵", 0.0061),
            RegionSpec("br", "Brasil (BRL)", "🇧🇷", 0.16),
            RegionSpec("pl", "Polonia (PLN)", "🇵🇱", 0.23),
            RegionSpec("ua", "Ucrania (UAH)", "🇺🇦", 0.022)
        )

        val deferredList = regions.map { region ->
            async {
                try {
                    val json = httpGet("https://store.steampowered.com/api/appdetails?appids=$appId&cc=${region.cc}")
                    if (json.isBlank()) return@async null
                    val appObj = JSONObject(json).optJSONObject(appId.toString()) ?: return@async null
                    if (!appObj.optBoolean("success", false)) return@async null
                    val data = appObj.optJSONObject("data") ?: return@async null

                    if (data.optBoolean("is_free", false)) {
                        return@async RegionalPriceComparison(
                            countryCode = region.cc.uppercase(),
                            countryName = region.name,
                            flagEmoji = region.flag,
                            localFormatted = "Gratis",
                            convertedToEur = 0.0,
                            discountPercent = 0
                        )
                    }

                    val priceOverview = data.optJSONObject("price_overview") ?: return@async null
                    val finalRaw = priceOverview.optInt("final", 0) / 100.0
                    val formatted = priceOverview.optString("final_formatted", "$finalRaw")
                    val discount = priceOverview.optInt("discount_percent", 0)
                    val inEur = finalRaw * region.rateToEur

                    RegionalPriceComparison(
                        countryCode = region.cc.uppercase(),
                        countryName = region.name,
                        flagEmoji = region.flag,
                        localFormatted = formatted,
                        convertedToEur = inEur,
                        discountPercent = discount
                    )
                } catch (_: Exception) {
                    null
                }
            }
        }

        deferredList.awaitAll().filterNotNull().sortedBy { it.convertedToEur }
    }
}

class SteamDbViewModel : ViewModel() {

    var isLoadingHome by mutableStateOf(true)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    var topSellers by mutableStateOf<List<SteamGameItem>>(emptyList())
        private set
    var specials by mutableStateOf<List<SteamGameItem>>(emptyList())
        private set
    var newReleases by mutableStateOf<List<SteamGameItem>>(emptyList())
        private set

    var searchQuery by mutableStateOf("")
    var isSearching by mutableStateOf(false)
        private set
    var searchResults by mutableStateOf<List<SteamGameItem>>(emptyList())
        private set

    var regionalAppIdInput by mutableStateOf("1091500")
    var isFetchingRegions by mutableStateOf(false)
        private set
    var regionalPrices by mutableStateOf<List<RegionalPriceComparison>>(emptyList())
        private set
    var regionalGameTitle by mutableStateOf("Cyberpunk 2077")
        private set

    var calculatorItems by mutableStateOf<List<SteamGameItem>>(emptyList())
        private set

    var selectedAppDetails by mutableStateOf<SteamAppDetails?>(null)
        private set
    var selectedAppRegions by mutableStateOf<List<RegionalPriceComparison>>(emptyList())
        private set
    var isLoadingAppDetails by mutableStateOf(false)
        private set

    init {
        loadDashboardData()
        loadRegionalComparison(1091500)
    }

    fun loadDashboardData() {
        viewModelScope.launch {
            isLoadingHome = true
            errorMessage = null
            try {
                val (tops, sales, news) = SteamApiRepository.fetchFeaturedCategories()
                specials = sales
                newReleases = news

                val enrichedTops = withContext(Dispatchers.IO) {
                    tops.take(15).map { game ->
                        async {
                            val players = SteamApiRepository.fetchCurrentPlayers(game.appId)
                            game.copy(currentPlayers = players)
                        }
                    }.awaitAll()
                }.sortedByDescending { it.currentPlayers ?: 0 }

                topSellers = enrichedTops

                if (calculatorItems.isEmpty() && enrichedTops.isNotEmpty()) {
                    calculatorItems = enrichedTops.take(4)
                }
            } catch (e: Exception) {
                errorMessage = "Error al conectar con Steam API: ${e.localizedMessage}"
            } finally {
                isLoadingHome = false
            }
        }
    }

    fun performSearch(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            isSearching = true
            try {
                val rawResults = SteamApiRepository.searchStore(query)
                val enriched = withContext(Dispatchers.IO) {
                    rawResults.take(12).map { item ->
                        async {
                            val players = SteamApiRepository.fetchCurrentPlayers(item.appId)
                            item.copy(currentPlayers = players)
                        }
                    }.awaitAll()
                }
                searchResults = enriched
            } finally {
                isSearching = false
            }
        }
    }

    fun loadRegionalComparison(appId: Int) {
        viewModelScope.launch {
            isFetchingRegions = true
            try {
                val detailsDeferred = async { SteamApiRepository.fetchAppDetails(appId) }
                val regionsDeferred = async { SteamApiRepository.fetchRegionalPrices(appId) }
                val details = detailsDeferred.await()
                regionalGameTitle = details?.name ?: "AppID $appId"
                regionalPrices = regionsDeferred.await()
            } finally {
                isFetchingRegions = false
            }
        }
    }

    fun openInspector(appId: Int) {
        viewModelScope.launch {
            isLoadingAppDetails = true
            selectedAppDetails = null
            selectedAppRegions = emptyList()
            try {
                val detailsDeferred = async { SteamApiRepository.fetchAppDetails(appId) }
                val regionsDeferred = async { SteamApiRepository.fetchRegionalPrices(appId) }
                selectedAppDetails = detailsDeferred.await()
                selectedAppRegions = regionsDeferred.await()
            } finally {
                isLoadingAppDetails = false
            }
        }
    }

    fun closeInspector() {
        selectedAppDetails = null
        isLoadingAppDetails = false
    }

    fun toggleCalculatorGame(game: SteamGameItem) {
        calculatorItems = if (calculatorItems.any { it.appId == game.appId }) {
            calculatorItems.filterNot { it.appId == game.appId }
        } else {
            calculatorItems + game
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = SteamDbColorScheme) {
                SteamDbAppScreen()
            }
        }
    }
}

enum class AppSection(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    CHARTS("Charts Live", Icons.Default.Leaderboard),
    SALES("Ofertas", Icons.Default.LocalOffer),
    SEARCH("Explorar DB", Icons.Default.Search),
    TOOLS("Regiones / Calc", Icons.Default.CurrencyExchange)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SteamDbAppScreen(vm: SteamDbViewModel = viewModel()) {
    var currentSection by remember { mutableStateOf(AppSection.CHARTS) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SteamDbBlue.copy(alpha = 0.18f))
                                .border(1.dp, SteamDbBlue, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = null,
                                tint = SteamDbBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "SteamDB Telemetry",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp,
                                color = Color.White
                            )
                            Text(
                                text = "Conectado a Valve Storefront API (ES / EUR)",
                                fontSize = 11.sp,
                                color = SteamDbLiveGreen
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { vm.loadDashboardData() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualizar datos en vivo", tint = SteamDbBlue)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SteamDbSurface)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = SteamDbSurface) {
                AppSection.entries.forEach { section ->
                    NavigationBarItem(
                        selected = currentSection == section,
                        onClick = { currentSection = section },
                        icon = { Icon(section.icon, contentDescription = section.label) },
                        label = { Text(section.label, fontSize = 11.sp, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SteamDbBlue,
                            selectedTextColor = SteamDbBlue,
                            indicatorColor = SteamDbBlue.copy(alpha = 0.15f),
                            unselectedIconColor = SteamDbMuted,
                            unselectedTextColor = SteamDbMuted
                        )
                    )
                }
            }
        },
        containerColor = SteamDbBg
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentSection) {
                AppSection.CHARTS -> ChartsSectionScreen(vm)
                AppSection.SALES -> SalesSectionScreen(vm)
                AppSection.SEARCH -> SearchDatabaseScreen(vm)
                AppSection.TOOLS -> ToolsAndCalculatorScreen(vm)
            }

            if (vm.isLoadingAppDetails || vm.selectedAppDetails != null) {
                AppIdInspectorDialog(vm = vm, onDismiss = { vm.closeInspector() })
            }
        }
    }
}

@Composable
fun ChartsSectionScreen(vm: SteamDbViewModel) {
    var selectedCategory by remember { mutableIntStateOf(0) }
    val numFormat = remember { NumberFormat.getIntegerInstance(Locale("es", "ES")) }

    if (vm.isLoadingHome) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = SteamDbBlue)
                Spacer(Modifier.height(12.dp))
                Text("Consultando servidores de Steam en tiempo real...", color = SteamDbMuted, fontSize = 13.sp)
            }
        }
        return
    }

    vm.errorMessage?.let { msg ->
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.CloudOff, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(8.dp))
                Text(msg, color = Color.White)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { vm.loadDashboardData() }) { Text("Reintentar conexión") }
            }
        }
        return
    }

    val totalConcurrentSample = vm.topSellers.sumOf { it.currentPlayers ?: 0 }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                KpiMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "JUGADORES EN TOP",
                    value = numFormat.format(totalConcurrentSample),
                    subtitle = "En vivo ahora mismo",
                    accentColor = SteamDbLiveGreen
                )
                KpiMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "OFERTAS ACTIVAS",
                    value = "${vm.specials.size} Apps",
                    subtitle = "Hasta -${vm.specials.maxOfOrNull { it.discountPercent } ?: 0}% DTO.",
                    accentColor = SteamDbDiscountText
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SteamDbSurface)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val tabs = listOf("Más Jugados / Top Ventas", "Nuevos Lanzamientos")
                tabs.forEachIndexed { index, title ->
                    val active = selectedCategory == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (active) SteamDbCard else Color.Transparent)
                            .clickable { selectedCategory = index }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            fontSize = 13.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            color = if (active) SteamDbBlue else SteamDbMuted
                        )
                    }
                }
            }
        }

        val activeList = if (selectedCategory == 0) vm.topSellers else vm.newReleases

        items(activeList, key = { it.appId }) { game ->
            SteamGameRowCard(
                game = game,
                inCalculator = vm.calculatorItems.any { it.appId == game.appId },
                onClick = { vm.openInspector(game.appId) },
                onToggleCalc = { vm.toggleCalculatorGame(game) }
            )
        }
    }
}

@Composable
fun KpiMetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String,
    accentColor: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = SteamDbSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SteamDbMuted)
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = accentColor, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
        }
    }
}

@Composable
fun SalesSectionScreen(vm: SteamDbViewModel) {
    var minDiscount by remember { mutableIntStateOf(0) }
    var maxPriceEuros by remember { mutableIntStateOf(999) }

    val filteredSales = remember(vm.specials, minDiscount, maxPriceEuros) {
        vm.specials
            .filter { it.discountPercent >= minDiscount && (it.finalPriceCents / 100) <= maxPriceEuros }
            .sortedByDescending { it.discountPercent }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("Filtros Rápidos de Descuento (Steam España)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SteamDbBlue)
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = minDiscount == 0 && maxPriceEuros == 999,
                onClick = { minDiscount = 0; maxPriceEuros = 999 },
                label = { Text("Todas (${vm.specials.size})") }
            )
            FilterChip(
                selected = minDiscount == 50,
                onClick = { minDiscount = if (minDiscount == 50) 0 else 50 },
                label = { Text("-50% o más") }
            )
            FilterChip(
                selected = minDiscount == 75,
                onClick = { minDiscount = if (minDiscount == 75) 0 else 75 },
                label = { Text("-75% o más") }
            )
            FilterChip(
                selected = maxPriceEuros == 10,
                onClick = { maxPriceEuros = if (maxPriceEuros == 10) 999 else 10 },
                label = { Text("Menos de 10 €") }
            )
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(filteredSales, key = { it.appId }) { game ->
                SteamGameRowCard(
                    game = game,
                    inCalculator = vm.calculatorItems.any { it.appId == game.appId },
                    onClick = { vm.openInspector(game.appId) },
                    onToggleCalc = { vm.toggleCalculatorGame(game) }
                )
            }
        }
    }
}

@Composable
fun SearchDatabaseScreen(vm: SteamDbViewModel) {
    val focusManager = LocalFocusManager.current

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = vm.searchQuery,
            onValueChange = { vm.searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Buscar por nombre o AppID (ej. Elden Ring, 1091500)...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SteamDbBlue) },
            trailingIcon = {
                if (vm.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { vm.searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Limpiar")
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                focusManager.clearFocus()
                val directAppId = vm.searchQuery.trim().toIntOrNull()
                if (directAppId != null) {
                    vm.openInspector(directAppId)
                } else {
                    vm.performSearch(vm.searchQuery)
                }
            }),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    focusManager.clearFocus()
                    val directAppId = vm.searchQuery.trim().toIntOrNull()
                    if (directAppId != null) {
                        vm.openInspector(directAppId)
                    } else {
                        vm.performSearch(vm.searchQuery)
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = SteamDbBlue)
            ) {
                Icon(Icons.Default.ManageSearch, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Consultar Base de Datos", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(12.dp))

        if (vm.isSearching) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SteamDbBlue)
            }
        } else if (vm.searchResults.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Terminal, contentDescription = null, tint = SteamDbMuted, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Introduce cualquier juego o código AppID directo para inspeccionar paquetes, precios y jugadores activos.",
                        color = SteamDbMuted,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(vm.searchResults, key = { it.appId }) { game ->
                    SteamGameRowCard(
                        game = game,
                        inCalculator = vm.calculatorItems.any { it.appId == game.appId },
                        onClick = { vm.openInspector(game.appId) },
                        onToggleCalc = { vm.toggleCalculatorGame(game) }
                    )
                }
            }
        }
    }
}

@Composable
fun ToolsAndCalculatorScreen(vm: SteamDbViewModel) {
    val focusManager = LocalFocusManager.current
    val totalFinalEur = vm.calculatorItems.sumOf { it.finalPriceCents } / 100.0
    val totalOriginalEur = vm.calculatorItems.sumOf {
        if (it.originalPriceCents > 0) it.originalPriceCents else it.finalPriceCents
    } / 100.0
    val savedEur = (totalOriginalEur - totalFinalEur).coerceAtLeast(0.0)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SteamDbSurface),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Public, contentDescription = null, tint = SteamDbBlue)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Comparador Regional de Divisas", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                            Text("Consulta simultánea en 7 tiendas internacionales de Steam", fontSize = 11.sp, color = SteamDbMuted)
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = vm.regionalAppIdInput,
                            onValueChange = { vm.regionalAppIdInput = it.filter { ch -> ch.isDigit() } },
                            label = { Text("AppID (ej. 1091500, 1245620, 271590)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                focusManager.clearFocus()
                                vm.regionalAppIdInput.toIntOrNull()?.let { vm.loadRegionalComparison(it) }
                            })
                        )
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                vm.regionalAppIdInput.toIntOrNull()?.let { vm.loadRegionalComparison(it) }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SteamDbBlue)
                        ) {
                            Text("Comparar", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val quickApps = listOf(
                            "Cyberpunk 2077" to 1091500,
                            "Elden Ring" to 1245620,
                            "Baldur's Gate 3" to 1086940,
                            "GTA V" to 271590,
                            "Helldivers 2" to 553850
                        )
                        quickApps.forEach { (label, id) ->
                            SuggestionChip(
                                onClick = {
                                    vm.regionalAppIdInput = id.toString()
                                    vm.loadRegionalComparison(id)
                                },
                                label = { Text("$label ($id)", fontSize = 11.sp) }
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    if (vm.isFetchingRegions) {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = SteamDbBlue)
                        }
                    } else if (vm.regionalPrices.isNotEmpty()) {
                        Text(
                            text = "Precios en vivo para: ${vm.regionalGameTitle}",
                            fontWeight = FontWeight.Bold,
                            color = SteamDbLiveGreen,
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(8.dp))

                        val esPrice = vm.regionalPrices.find { it.countryCode == "ES" }?.convertedToEur
                            ?: vm.regionalPrices.maxOf { it.convertedToEur }

                        vm.regionalPrices.forEach { reg ->
                            val diffPct = if (esPrice > 0.0) ((reg.convertedToEur - esPrice) / esPrice) * 100.0 else 0.0
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SteamDbCard)
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Text(reg.flagEmoji, fontSize = 18.sp)
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(reg.countryName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        Text("Moneda local: ${reg.localFormatted}", fontSize = 11.sp, color = SteamDbMuted)
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = String.format(Locale("es", "ES"), "≈ %.2f €", reg.convertedToEur),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color.White
                                    )
                                    if (reg.countryCode != "ES" && esPrice > 0) {
                                        val sign = if (diffPct > 0) "+" else ""
                                        Text(
                                            text = String.format(Locale("es", "ES"), "%s%.1f%% vs ES", sign, diffPct),
                                            fontSize = 11.sp,
                                            color = if (diffPct < 0) SteamDbLiveGreen else Color(0xFFFF8A80)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SteamDbSurface),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Calculate, contentDescription = null, tint = SteamDbDiscountText)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Calculadora de Biblioteca Steam", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                            Text("Pulsa el icono '+' en cualquier juego de la app para añadirlo al cálculo", fontSize = 11.sp, color = SteamDbMuted)
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        KpiMetricCard(
                            modifier = Modifier.weight(1f),
                            title = "VALOR ACTUAL (OFERTAS)",
                            value = String.format(Locale("es", "ES"), "%.2f €", totalFinalEur),
                            subtitle = "${vm.calculatorItems.size} juegos en lista",
                            accentColor = SteamDbBlue
                        )
                        KpiMetricCard(
                            modifier = Modifier.weight(1f),
                            title = "AHORRO ACUMULADO",
                            value = String.format(Locale("es", "ES"), "%.2f €", savedEur),
                            subtitle = String.format(Locale("es", "ES"), "PVP base: %.2f €", totalOriginalEur),
                            accentColor = SteamDbDiscountText
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    vm.calculatorItems.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SteamDbCard)
                                .clickable { vm.openInspector(item.appId) }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("AppID: ${item.appId} • ${item.finalFormatted}", fontSize = 11.sp, color = SteamDbMuted)
                            }
                            IconButton(onClick = { vm.toggleCalculatorGame(item) }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Quitar", tint = Color(0xFFFF8A80))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SteamGameRowCard(
    game: SteamGameItem,
    inCalculator: Boolean,
    onClick: () -> Unit,
    onToggleCalc: () -> Unit
) {
    val numFormat = remember { NumberFormat.getIntegerInstance(Locale("es", "ES")) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = SteamDbSurface),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = game.imageUrl,
                contentDescription = game.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(96.dp)
                    .height(54.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(SteamDbCard)
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = game.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White
                )

                Spacer(Modifier.height(3.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "ID: ${game.appId}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = SteamDbBlue
                    )

                    if (game.currentPlayers != null && game.currentPlayers > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(SteamDbLiveGreen)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "${numFormat.format(game.currentPlayers)} en vivo",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SteamDbLiveGreen
                            )
                        }
                    }
                }

                Spacer(Modifier.height(3.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (game.windows) Text("WIN", fontSize = 9.sp, color = SteamDbMuted, fontFamily = FontFamily.Monospace)
                    if (game.mac) Text("MAC", fontSize = 9.sp, color = SteamDbMuted, fontFamily = FontFamily.Monospace)
                    if (game.linux) Text("LINUX", fontSize = 9.sp, color = SteamDbMuted, fontFamily = FontFamily.Monospace)
                    if (game.metascore.isNotBlank()) {
                        Text("MC: ${game.metascore}", fontSize = 9.sp, color = SteamDbDiscountText, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                if (game.discountPercent > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(SteamDbDiscountBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "-${game.discountPercent}%",
                            color = SteamDbDiscountText,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp
                        )
                    }
                    Text(
                        text = game.originalFormatted,
                        fontSize = 10.sp,
                        color = SteamDbMuted,
                        textDecoration = TextDecoration.LineThrough
                    )
                }

                Text(
                    text = game.finalFormatted,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    color = if (game.discountPercent > 0) SteamDbDiscountText else Color.White
                )
            }

            IconButton(onClick = onToggleCalc) {
                Icon(
                    imageVector = if (inCalculator) Icons.Default.CheckCircle else Icons.Default.AddCircleOutline,
                    contentDescription = "Añadir a calculadora",
                    tint = if (inCalculator) SteamDbLiveGreen else SteamDbMuted
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppIdInspectorDialog(
    vm: SteamDbViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val numFormat = remember { NumberFormat.getIntegerInstance(Locale("es", "ES")) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SteamDbBg,
        scrimColor = Color.Black.copy(alpha = 0.75f)
    ) {
        if (vm.isLoadingAppDetails && vm.selectedAppDetails == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = SteamDbBlue)
                    Spacer(Modifier.height(10.dp))
                    Text("Extrayendo telemetría y paquetes del AppID...", color = SteamDbMuted, fontSize = 13.sp)
                }
            }
        } else {
            val details = vm.selectedAppDetails ?: return@ModalBottomSheet

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(175.dp)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    AsyncImage(
                        model = details.headerImage,
                        contentDescription = details.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, SteamDbBg.copy(alpha = 0.95f))
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Badge(containerColor = SteamDbBlue) {
                                Text("AppID ${details.appId}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                            Badge(containerColor = SteamDbCard) {
                                Text(details.type, color = Color.White)
                            }
                            if (details.currentPlayers > 0) {
                                Badge(containerColor = SteamDbLiveGreen) {
                                    Text("${numFormat.format(details.currentPlayers)} Jugando Ahora", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(details.name, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SteamDbSurface)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("PRECIO OFICIAL (ESPAÑA)", fontSize = 10.sp, color = SteamDbMuted, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = details.finalPriceFormatted,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (details.discountPercent > 0) SteamDbDiscountText else Color.White
                            )
                            if (details.discountPercent > 0) {
                                Text(
                                    text = details.initialPriceFormatted,
                                    fontSize = 12.sp,
                                    color = SteamDbMuted,
                                    textDecoration = TextDecoration.LineThrough
                                )
                                Badge(containerColor = SteamDbDiscountBg) {
                                    Text("-${details.discountPercent}%", color = SteamDbDiscountText)
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://steamdb.info/app/${details.appId}/"))
                                context.startActivity(intent)
                            }
                        ) {
                            Text("Web DB", fontSize = 12.sp)
                        }
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://store.steampowered.com/app/${details.appId}/"))
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SteamDbBlue)
                        ) {
                            Text("Tienda", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(details.shortDescription, fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f))
                Spacer(Modifier.height(14.dp))

                Text("METADATOS TÉCNICOS DEL PAQUETE", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SteamDbBlue)
                Spacer(Modifier.height(6.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SteamDbSurface)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MetadataRow("AppID Primario", details.appId.toString())
                    MetadataRow("Desarrollador", details.developers.joinToString(", "))
                    MetadataRow("Editor (Publisher)", details.publishers.joinToString(", "))
                    MetadataRow("Fecha de lanzamiento", details.releaseDate)
                    MetadataRow(
                        "Sistemas Soportados",
                        listOfNotNull(
                            if (details.windows) "Windows" else null,
                            if (details.mac) "macOS" else null,
                            if (details.linux) "Linux / SteamOS" else null
                        ).joinToString(" • ")
                    )
                    details.metacriticScore?.let { MetadataRow("Metacritic Score", "$it / 100") }
                    details.recommendationsTotal?.let { MetadataRow("Reseñas verificadas", numFormat.format(it)) }
                    details.achievementsTotal?.let { MetadataRow("Logros de Steamworks", "$it logros") }
                    MetadataRow("Paquetes DLC registrados", "${details.dlcCount} DLCs")
                    if (details.genres.isNotEmpty()) {
                        MetadataRow("Géneros", details.genres.joinToString(", "))
                    }
                }

                AnimatedVisibility(visible = vm.selectedAppRegions.isNotEmpty()) {
                    Column {
                        Spacer(Modifier.height(14.dp))
                        Text("COMPARATIVA DE PRECIOS POR DIVISA", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SteamDbBlue)
                        Spacer(Modifier.height(6.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(SteamDbSurface)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            vm.selectedAppRegions.forEach { reg ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("${reg.flagEmoji} ${reg.countryName}", fontSize = 12.sp, color = Color.White)
                                    Text(
                                        text = "${reg.localFormatted} (≈ ${String.format(Locale("es", "ES"), "%.2f €", reg.convertedToEur)})",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = SteamDbLiveGreen
                                    )
                                }
                            }
                        }
                    }
                }

                if (details.screenshots.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Text("CAPTURAS DEL SERVIDOR DE STEAM", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SteamDbBlue)
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(details.screenshots) { shotUrl ->
                            AsyncImage(
                                model = shotUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .width(200.dp)
                    .height(112.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SteamDbCard)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 12.sp, color = SteamDbMuted, modifier = Modifier.weight(0.42f))
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier.weight(0.58f)
        )
    }
}
