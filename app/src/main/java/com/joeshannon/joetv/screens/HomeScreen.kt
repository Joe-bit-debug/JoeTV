package com.joeshannon.joetv.screens

// -----------------------------------------------------------------------------
// JoeTV Home Screen
//
// Main launcher screen for JoeTV.
//
// Responsibilities:
// • Display installed applications
// • Manage favorites and recently opened apps
// • Hide and restore applications
// • Display weather, date, and time
// • Handle TV remote navigation
// • Launch Android TV applications
// -----------------------------------------------------------------------------


import android.content.Context
import android.content.res.Configuration
import android.content.Intent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.tv.material3.Text
import java.time.LocalDateTime
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import android.content.ActivityNotFoundException
import android.net.Uri
import android.provider.Settings
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat



/**
 * Represents an installed application shown on the JoeTV home screen.
 */
data class JoeTvApp(
    val name: String,
    val packageName: String,
    val description: String,
    val initials: String
)


/**
 * Main entry point for the JoeTV launcher.
 *
 * Initializes app data, user preferences, weather, sounds,
 * and builds the complete home screen UI.
 */
@Composable
fun HomeScreen(context: Context) {

    var calendarPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CALENDAR
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val calendarPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            calendarPermissionGranted = granted
        }


    // Create the manager responsible for discovering installed apps.
    val appManager = remember(context.applicationContext) {
        AppManager(context.applicationContext)
    }

    // Handles all UI sounds used throughout JoeTV.
    val soundManager = remember(context.applicationContext) {
        JoeTvSoundManager(context.applicationContext)
    }

    val apps by appManager.apps
    val lifecycleOwner = LocalLifecycleOwner.current

    // Start managers when the screen appears and clean them up when leaving.
    DisposableEffect(appManager, soundManager, lifecycleOwner) {
        appManager.start()
        soundManager.playHome()

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                appManager.refresh()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            appManager.stop()
            soundManager.release()
        }
    }

    // Persistent storage for favorites, recents, and hidden apps.
    val preferences = remember {
        context.getSharedPreferences(
            "joetv_preferences",
            Context.MODE_PRIVATE
        )
    }

    var favoritePackages by remember {
        mutableStateOf(
            preferences.getStringSet(
                "favorite_packages",
                setOf(
                    "org.smarttube.stable",
                    "com.lagradost.cloudstream3",
                    "org.videolan.vlc"
                )
            )?.toSet() ?: emptySet()
        )
    }

    var recentPackages by remember {
        mutableStateOf(
            preferences.getString(
                "recent_packages",
                ""
            )
                ?.split("|")
                ?.filter { it.isNotBlank() }
                ?: emptyList()
        )
    }

    var hiddenPackages by remember {
        mutableStateOf(
            preferences.getStringSet(
                "hidden_packages",
                emptySet()
            )?.toSet() ?: emptySet()
        )
    }

    var favoriteNotice by remember {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(favoriteNotice) {
        if (favoriteNotice != null) {
            delay(1_800)
            favoriteNotice = null
        }
    }

    val visibleApps = apps.filterNot { app ->
        app.packageName in hiddenPackages
    }

    val hiddenApps = apps.filter { app ->
        app.packageName in hiddenPackages
    }

    val favoriteApps = visibleApps.filter { app ->
        app.packageName in favoritePackages
    }

    val recentApps = recentPackages.mapNotNull { packageName ->
        visibleApps.find { app -> app.packageName == packageName }
    }


    /**
     * Saves an application to the Recently Opened section.
     */
    fun recordRecent(app: JoeTvApp) {
        val updatedRecents =
            (listOf(app.packageName) + recentPackages)
                .distinct()
                .take(6)

        recentPackages = updatedRecents

        preferences.edit()
            .putString(
                "recent_packages",
                updatedRecents.joinToString("|")
            )
            .apply()
    }


    /**
     * Hides or restores an application from the home screen.
     */
    fun toggleHidden(app: JoeTvApp) {
        val wasHidden = app.packageName in hiddenPackages

        val updatedHidden =
            if (wasHidden) {
                hiddenPackages - app.packageName
            } else {
                hiddenPackages + app.packageName
            }

        hiddenPackages = updatedHidden

        preferences.edit()
            .putStringSet(
                "hidden_packages",
                updatedHidden
            )
            .apply()

        favoriteNotice = if (wasHidden) {
            "${app.name} restored to Your Apps"
        } else {
            "${app.name} hidden from JoeTV"
        }
    }


    /**
     * Adds or removes an application from Favorites.
     */
    fun toggleFavorite(app: JoeTvApp) {
        val wasFavorite = app.packageName in favoritePackages

        val updatedFavorites =
            if (wasFavorite) {
                favoritePackages - app.packageName
            } else {
                favoritePackages + app.packageName
            }

        favoritePackages = updatedFavorites

        preferences.edit()
            .putStringSet(
                "favorite_packages",
                updatedFavorites
            )
            .apply()

        favoriteNotice = if (wasFavorite) {
            "${app.name} removed from Favorites"
        } else {
            "${app.name} added to Favorites"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF05070B))
    ) {
        JoeTvMovingBackground()

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 60.dp)
        ) {
            item {
                JoeTvHero(
                    context = context,
                    calendarPermissionGranted = calendarPermissionGranted,
                    onRequestCalendarPermission = {
                        calendarPermissionLauncher.launch(
                            Manifest.permission.READ_CALENDAR
                        )
                    }
                )
            }

            item {
                SectionHeader(
                    title = "Favorites",
                    subtitle = "Press the bookmark button to add or remove apps"
                )
            }

            item {
                if (favoriteApps.isEmpty()) {
                    EmptyFavoritesCard()
                } else {
                    AppRow(
                        context = context,
                        soundManager = soundManager,
                        apps = favoriteApps,
                        favoritePackages = favoritePackages,
                        onFocused = { },
                        onOpen = { app ->
                            recordRecent(app)
                            launchApp(
                                context = context,
                                packageName = app.packageName
                            )
                        },
                        onToggleFavorite = { app ->
                            toggleFavorite(app)
                        },
                        onToggleHidden = { app ->
                            toggleHidden(app)
                        }
                    )
                }
            }

            item {
                SectionHeader(
                    title = "Recently Opened",
                    subtitle = "Jump back into your latest apps"
                )
            }

            item {
                if (recentApps.isEmpty()) {
                    EmptyRecentAppsCard()
                } else {
                    AppRow(
                        context = context,
                        soundManager = soundManager,
                        apps = recentApps,
                        favoritePackages = favoritePackages,
                        onFocused = { },
                        onOpen = { app ->
                            recordRecent(app)
                            launchApp(
                                context = context,
                                packageName = app.packageName
                            )
                        },
                        onToggleFavorite = { app ->
                            toggleFavorite(app)
                        },
                        onToggleHidden = { app ->
                            toggleHidden(app)
                        }
                    )
                }
            }

            item {
                SectionHeader(
                    title = "Your Apps",
                    subtitle = "OK to open  •  Bookmark to favorite  •  X to hide"
                )
            }

            item {
                AppRow(
                    context = context,
                    soundManager = soundManager,
                    apps = visibleApps,
                    favoritePackages = favoritePackages,
                    onFocused = { },
                    onOpen = { app ->
                        recordRecent(app)
                        launchApp(
                            context = context,
                            packageName = app.packageName
                        )
                    },
                    onToggleFavorite = { app ->
                        toggleFavorite(app)
                    },
                    onToggleHidden = { app ->
                        toggleHidden(app)
                    }
                )
            }

            if (hiddenApps.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Hidden Apps",
                        subtitle = "Press X to restore an app"
                    )
                }

                item {
                    AppRow(
                        context = context,
                        soundManager = soundManager,
                        apps = hiddenApps,
                        favoritePackages = favoritePackages,
                        hiddenPackages = hiddenPackages,
                        onFocused = { },
                        onOpen = { app ->
                            recordRecent(app)
                            launchApp(
                                context = context,
                                packageName = app.packageName
                            )
                        },
                        onToggleFavorite = { app ->
                            toggleFavorite(app)
                        },
                        onToggleHidden = { app ->
                            toggleHidden(app)
                        }
                    )
                }
            }

            item {
                SectionHeader(
                    title = "Continue Watching",
                    subtitle = "Jump back into your media"
                )
            }

            item {
                ContinueWatchingRow()
            }

            item {
                JoeTvFooter()
            }
        }

        favoriteNotice?.let { message ->
            FavoriteNoticeBanner(
                message = message,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 34.dp)
            )
        }
    }
}

@Composable
private fun FavoriteNoticeBanner(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xF21A2030))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.18f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(
                horizontal = 24.dp,
                vertical = 14.dp
            )
    ) {
        Text(
            text = message,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}


/**
 * Animated background displayed behind the launcher.
 */
@Composable
private fun JoeTvMovingBackground() {
    val transition = rememberInfiniteTransition(
        label = "JoeTVBackground"
    )

    val blueX by transition.animateFloat(
        initialValue = -180f,
        targetValue = 210f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 24000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BlueX"
    )

    val blueY by transition.animateFloat(
        initialValue = -100f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 28000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BlueY"
    )

    val purpleX by transition.animateFloat(
        initialValue = 190f,
        targetValue = -150f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 32000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PurpleX"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF121A2A),
                        Color(0xFF080B12),
                        Color(0xFF030407)
                    )
                )
            )
    ) {
        Box(
            modifier = Modifier
                .offset(
                    x = blueX.dp,
                    y = blueY.dp
                )
                .size(470.dp)
                .background(
                    color = Color(0x273B82F6),
                    shape = CircleShape
                )
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(
                    x = purpleX.dp,
                    y = 110.dp
                )
                .size(430.dp)
                .background(
                    color = Color(0x248B5CF6),
                    shape = CircleShape
                )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0x33000000),
                            Color(0xAA000000)
                        )
                    )
                )
        )
    }
}

private data class WeatherInfo(
    val temperature: Int,
    val apparentTemperature: Int,
    val high: Int,
    val low: Int,
    val weatherCode: Int,
    val isDay: Boolean
)

private enum class WeatherScene {
    CLEAR,
    PARTLY_CLOUDY,
    CLOUDY,
    FOG,
    RAIN,
    STORM,
    SNOW
}

private fun weatherSceneFor(code: Int): WeatherScene = when (code) {
    0 -> WeatherScene.CLEAR
    1, 2 -> WeatherScene.PARTLY_CLOUDY
    3 -> WeatherScene.CLOUDY
    45, 48 -> WeatherScene.FOG
    51, 53, 55, 56, 57,
    61, 63, 65, 66, 67,
    80, 81, 82 -> WeatherScene.RAIN
    71, 73, 75, 77, 85, 86 -> WeatherScene.SNOW
    95, 96, 99 -> WeatherScene.STORM
    else -> WeatherScene.PARTLY_CLOUDY
}

private fun weatherLabel(scene: WeatherScene): String = when (scene) {
    WeatherScene.CLEAR -> "Clear"
    WeatherScene.PARTLY_CLOUDY -> "Partly cloudy"
    WeatherScene.CLOUDY -> "Cloudy"
    WeatherScene.FOG -> "Foggy"
    WeatherScene.RAIN -> "Rain"
    WeatherScene.STORM -> "Thunderstorms"
    WeatherScene.SNOW -> "Snow"
}


/**
 * Downloads the current weather from the Open-Meteo API.
 */
private suspend fun loadWeather(): WeatherInfo? = withContext(Dispatchers.IO) {
    /*
     * These coordinates are Manhattan, Kansas.
     * Change them later if you want JoeTV tied to another home location.
     */
    val latitude = 39.1836
    val longitude = -96.5717

    runCatching {
        val endpoint =
            "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=$latitude" +
                    "&longitude=$longitude" +
                    "&current=temperature_2m,apparent_temperature,is_day,weather_code" +
                    "&daily=temperature_2m_max,temperature_2m_min" +
                    "&temperature_unit=fahrenheit" +
                    "&timezone=auto" +
                    "&forecast_days=1"

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 6_000
            readTimeout = 6_000
            setRequestProperty("Accept", "application/json")
        }

        try {
            if (connection.responseCode !in 200..299) {
                return@runCatching null
            }

            val body = connection.inputStream
                .bufferedReader()
                .use { it.readText() }

            val root = JSONObject(body)
            val current = root.getJSONObject("current")
            val daily = root.getJSONObject("daily")

            WeatherInfo(
                temperature = current.getDouble("temperature_2m").toInt(),
                apparentTemperature =
                    current.getDouble("apparent_temperature").toInt(),
                high = daily.getJSONArray("temperature_2m_max")
                    .getDouble(0)
                    .toInt(),
                low = daily.getJSONArray("temperature_2m_min")
                    .getDouble(0)
                    .toInt(),
                weatherCode = current.getInt("weather_code"),
                isDay = current.getInt("is_day") == 1
            )
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}


/**
 * Chooses a hero layout based on the active display aspect ratio.
 *
 * Native Tab S9+ landscape is approximately 16:10.
 * JoeTV TV mode is forced to 1920x1080, which is 16:9.
 */
@Composable
private fun JoeTvHero(
    context: Context,
    calendarPermissionGranted: Boolean,
    onRequestCalendarPermission: () -> Unit
) {
    val configuration = LocalConfiguration.current

    val screenWidth = configuration.screenWidthDp.coerceAtLeast(1)
    val screenHeight = configuration.screenHeightDp.coerceAtLeast(1)
    val aspectRatio = screenWidth.toFloat() / screenHeight.toFloat()

    val isTvLayout =
        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
                aspectRatio >= 1.59f

    if (isTvLayout) {
        JoeTvHeroTv(
            context = context,
            calendarPermissionGranted = calendarPermissionGranted,
            onRequestCalendarPermission = onRequestCalendarPermission
        )
    } else {
        JoeTvHeroTablet()
    }
}


/**
 * Shared time and weather state used by both hero layouts.
 */
@Composable
private fun rememberHeroState(): Triple<LocalDateTime, WeatherInfo?, String> {
    var currentTime by remember {
        mutableStateOf(LocalDateTime.now())
    }

    val weather by produceState<WeatherInfo?>(initialValue = null) {
        while (true) {
            value = loadWeather()
            delay(30 * 60 * 1_000L)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = LocalDateTime.now()
            delay(30_000)
        }
    }

    val greeting = when (currentTime.hour) {
        in 5..11 -> "Good morning, Joe"
        in 12..16 -> "Good afternoon, Joe"
        else -> "Good evening, Joe"
    }

    return Triple(currentTime, weather, greeting)
}


/**
 * Compact 16:9 hero used when JoeTV is running at 1920x1080.
 */
@Composable
private fun JoeTvHeroTv(
    context: Context,
    calendarPermissionGranted: Boolean,
    onRequestCalendarPermission: () -> Unit
) {
    var focused by remember {
        mutableStateOf(false)
    }

    val (currentTime, weather, greeting) = rememberHeroState()

    val nextEvent by produceState<JoeTvCalendarEvent?>(
        initialValue = null,
        key1 = calendarPermissionGranted
    ) {
        if (calendarPermissionGranted) {
            value = loadNextCalendarEvent(context)
        }
    }

    val timeText = currentTime.format(
        DateTimeFormatter.ofPattern("h:mm a")
    )

    val dateText = currentTime.format(
        DateTimeFormatter.ofPattern("EEEE, MMMM d")
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(218.dp)
            .padding(
                start = 38.dp,
                end = 38.dp,
                top = 18.dp,
                bottom = 12.dp
            )
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xF026324A),
                        Color(0xDD161C2A),
                        Color(0xC010141E)
                    )
                )
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) {
                    Color.White.copy(alpha = 0.70f)
                } else {
                    Color.White.copy(alpha = 0.10f)
                },
                shape = RoundedCornerShape(26.dp)
            )
            .onFocusChanged {
                focused = it.isFocused
            }
            .focusable()
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 28.dp,
                    end = 20.dp,
                    top = 18.dp,
                    bottom = 18.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1.35f)
            ) {
                Text(
                    text = "JOETV",
                    color = Color(0xFF8CB8FF),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp
                )

                Spacer(modifier = Modifier.height(7.dp))

                Text(
                    text = greeting,
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = "Everything you want to watch, all in one place.",
                    color = Color.White.copy(alpha = 0.68f),
                    fontSize = 13.sp,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(15.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HeroPill(
                        text = timeText,
                        compact = true
                    )

                    HeroPill(
                        text = dateText,
                        compact = true
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            CalendarHeroCard(
                event = nextEvent,
                permissionGranted = calendarPermissionGranted,
                modifier = Modifier.width(175.dp),
                onClick = {
                    if (!calendarPermissionGranted) {
                        onRequestCalendarPermission()
                    }
                }
            )

            Spacer(modifier = Modifier.width(14.dp))

            WeatherHeroCard(
                weather = weather,
                cardWidth = 210.dp,
                cardHeight = 135.dp,
                temperatureFontSize = 29.sp,
                conditionFontSize = 11.sp,
                detailsFontSize = 9.sp,
                cornerRadius = 22.dp
            )
        }
    }
}


/**
 * Calendar preview shown in the TV hero.
 *
 * This currently displays a connection placeholder. The visual card is ready
 * for Google Calendar data once sign-in and API access are added.
 */
@Composable
private fun CalendarHeroCard(
    event: JoeTvCalendarEvent?,
    permissionGranted: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember {
        mutableStateOf(false)
    }

    val eventTime = event?.let {
        java.time.Instant
            .ofEpochMilli(it.startTimeMillis)
            .atZone(java.time.ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("h:mm a"))
    }

    val countdown = event?.let {
        val minutes =
            ((it.startTimeMillis - System.currentTimeMillis()) / 60_000L)
                .coerceAtLeast(0)

        when {
            minutes == 0L -> "Starting now"
            minutes < 60L -> "Starts in $minutes min"
            else -> {
                val hours = minutes / 60
                val remainingMinutes = minutes % 60

                if (remainingMinutes == 0L) {
                    "Starts in $hours hr"
                } else {
                    "Starts in ${hours}h ${remainingMinutes}m"
                }
            }
        }
    }

    Box(
        modifier = modifier
            .height(135.dp)
            .graphicsLayer {
                val scale = if (focused) 1.03f else 1f
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF253755),
                        Color(0xFF18243A)
                    )
                )
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) {
                    Color.White.copy(alpha = 0.85f)
                } else {
                    Color.White.copy(alpha = 0.14f)
                },
                shape = RoundedCornerShape(22.dp)
            )
            .onFocusChanged {
                focused = it.isFocused
            }
            .focusable()
            .clickable {
                onClick()
            }
            .padding(
                horizontal = 16.dp,
                vertical = 14.dp
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "NEXT UP",
                color = Color(0xFF8CB8FF),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.6.sp
            )

            Column {
                Text(
                    text = when {
                        !permissionGranted -> "Calendar"
                        event != null -> event.title
                        else -> "Nothing scheduled"
                    },
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = when {
                        !permissionGranted -> "Permission required"
                        event != null -> eventTime.orEmpty()
                        else -> "You're all caught up"
                    },
                    color = Color.White.copy(alpha = 0.62f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = when {
                    !permissionGranted -> "Press OK to connect"
                    event != null -> countdown.orEmpty()
                    else -> "Enjoy your day"
                },
                color = Color.White.copy(alpha = 0.46f),
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}


/**
 * Original spacious hero used at the tablet's native aspect ratio.
 */
@Composable
private fun JoeTvHeroTablet() {
    var focused by remember {
        mutableStateOf(false)
    }

    val (currentTime, weather, greeting) = rememberHeroState()

    val timeText = currentTime.format(
        DateTimeFormatter.ofPattern("h:mm a")
    )

    val dateText = currentTime.format(
        DateTimeFormatter.ofPattern("EEEE, MMMM d")
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(330.dp)
            .padding(
                start = 48.dp,
                end = 48.dp,
                top = 32.dp,
                bottom = 18.dp
            )
            .clip(RoundedCornerShape(30.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xF026324A),
                        Color(0xDD161C2A),
                        Color(0xC010141E)
                    )
                )
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) {
                    Color.White.copy(alpha = 0.70f)
                } else {
                    Color.White.copy(alpha = 0.10f)
                },
                shape = RoundedCornerShape(30.dp)
            )
            .onFocusChanged {
                focused = it.isFocused
            }
            .focusable()
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(
                    start = 38.dp,
                    end = 390.dp
                )
        ) {
            Text(
                text = "JOETV",
                color = Color(0xFF8CB8FF),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = greeting,
                color = Color.White,
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(7.dp))

            Text(
                text = "Everything you want to watch, all in one place.",
                color = Color.White.copy(alpha = 0.68f),
                fontSize = 18.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HeroPill(timeText)
                HeroPill(dateText)
                HeroPill(
                    weather?.let {
                        "${it.temperature}° • ${weatherLabel(weatherSceneFor(it.weatherCode))}"
                    } ?: "Weather loading"
                )
            }
        }

        WeatherHeroCard(
            weather = weather,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 34.dp)
        )
    }
}


/**
 * Displays the weather card shown on the right side
 * of the hero banner.
 */
@Composable
private fun WeatherHeroCard(
    weather: WeatherInfo?,
    modifier: Modifier = Modifier,
    cardWidth: androidx.compose.ui.unit.Dp = 320.dp,
    cardHeight: androidx.compose.ui.unit.Dp = 210.dp,
    temperatureFontSize: androidx.compose.ui.unit.TextUnit = 42.sp,
    conditionFontSize: androidx.compose.ui.unit.TextUnit = 15.sp,
    detailsFontSize: androidx.compose.ui.unit.TextUnit = 12.sp,
    cornerRadius: androidx.compose.ui.unit.Dp = 30.dp
) {
    val scene = weather?.let {
        weatherSceneFor(it.weatherCode)
    } ?: WeatherScene.PARTLY_CLOUDY

    val isDay = weather?.isDay ?: true

    val cardColors = when {
        !isDay -> listOf(
            Color(0xFF202B55),
            Color(0xFF131A36)
        )
        scene == WeatherScene.CLEAR -> listOf(
            Color(0xFF4C91FF),
            Color(0xFF6B65F6)
        )
        scene == WeatherScene.RAIN ||
                scene == WeatherScene.STORM -> listOf(
            Color(0xFF46627F),
            Color(0xFF28364D)
        )
        scene == WeatherScene.SNOW -> listOf(
            Color(0xFF73B7D8),
            Color(0xFF486F91)
        )
        else -> listOf(
            Color(0xFF527DCC),
            Color(0xFF5159B2)
        )
    }

    Box(
        modifier = modifier
            .size(
                width = cardWidth,
                height = cardHeight
            )
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                Brush.linearGradient(cardColors)
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.18f),
                shape = RoundedCornerShape(cornerRadius)
            )
    ) {
        WeatherIllustration(
            scene = scene,
            isDay = isDay,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = 22.dp,
                    bottom = 18.dp
                )
        ) {
            Text(
                text = weather?.let { "${it.temperature}°" } ?: "--°",
                color = Color.White,
                fontSize = temperatureFontSize,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = weather?.let {
                    weatherLabel(scene)
                } ?: "Loading weather",
                color = Color.White.copy(alpha = 0.88f),
                fontSize = conditionFontSize,
                fontWeight = FontWeight.SemiBold
            )

            if (weather != null) {
                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = "H ${weather.high}°  •  L ${weather.low}°",
                    color = Color.White.copy(alpha = 0.66f),
                    fontSize = detailsFontSize
                )
            }
        }
    }
}


/**
 * Draws the animated weather artwork.
 */
@Composable
private fun WeatherIllustration(
    scene: WeatherScene,
    isDay: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(
        label = "WeatherAnimation"
    )

    val drift by transition.animateFloat(
        initialValue = -6f,
        targetValue = 7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4_500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "CloudDrift"
    )

    val rainOffset by transition.animateFloat(
        initialValue = -12f,
        targetValue = 22f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850),
            repeatMode = RepeatMode.Restart
        ),
        label = "Rain"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Soft atmospheric glow.
        drawCircle(
            color = Color.White.copy(alpha = 0.08f),
            radius = w * 0.40f,
            center = Offset(w * 0.86f, h * 0.10f)
        )

        if (isDay) {
            drawCircle(
                color = Color(0xFFFFDC72),
                radius = 29.dp.toPx(),
                center = Offset(w * 0.75f, h * 0.30f)
            )
            drawCircle(
                color = Color(0xFFFFF1B0).copy(alpha = 0.30f),
                radius = 41.dp.toPx(),
                center = Offset(w * 0.75f, h * 0.30f)
            )
        } else {
            drawCircle(
                color = Color(0xFFFFF2C7),
                radius = 25.dp.toPx(),
                center = Offset(w * 0.76f, h * 0.28f)
            )
            drawCircle(
                color = cardBackgroundApprox(scene),
                radius = 23.dp.toPx(),
                center = Offset(w * 0.80f, h * 0.24f)
            )

            listOf(
                Offset(w * 0.59f, h * 0.18f),
                Offset(w * 0.87f, h * 0.16f),
                Offset(w * 0.92f, h * 0.38f)
            ).forEach {
                drawCircle(
                    color = Color.White.copy(alpha = 0.72f),
                    radius = 1.6.dp.toPx(),
                    center = it
                )
            }
        }

        when (scene) {
            WeatherScene.CLEAR -> {
                // Sun/moon is enough; keep this condition clean.
            }

            WeatherScene.PARTLY_CLOUDY,
            WeatherScene.CLOUDY,
            WeatherScene.RAIN,
            WeatherScene.STORM,
            WeatherScene.SNOW -> {
                val cloudX = w * 0.66f + drift.dp.toPx()
                val cloudY = h * 0.43f

                val cloudColor = when (scene) {
                    WeatherScene.STORM -> Color(0xFFD2DAE8)
                    WeatherScene.RAIN -> Color(0xFFE4EBF5)
                    else -> Color.White
                }

                drawCloud(
                    center = Offset(cloudX, cloudY),
                    cloudColor = cloudColor
                )

                if (scene == WeatherScene.CLOUDY) {
                    drawCloud(
                        center = Offset(
                            w * 0.80f - drift.dp.toPx(),
                            h * 0.32f
                        ),
                        scale = 0.72f,
                        cloudColor = Color.White.copy(alpha = 0.74f)
                    )
                }

                if (scene == WeatherScene.RAIN ||
                    scene == WeatherScene.STORM
                ) {
                    repeat(4) { index ->
                        val x = cloudX - 40.dp.toPx() +
                                index * 25.dp.toPx()
                        val y = cloudY + 30.dp.toPx() +
                                rainOffset.dp.toPx()

                        drawLine(
                            color = Color(0xFFB9E8FF),
                            start = Offset(x, y),
                            end = Offset(
                                x - 6.dp.toPx(),
                                y + 14.dp.toPx()
                            ),
                            strokeWidth = 3.dp.toPx()
                        )
                    }
                }

                if (scene == WeatherScene.STORM) {
                    val lightning = Path().apply {
                        moveTo(
                            cloudX + 3.dp.toPx(),
                            cloudY + 24.dp.toPx()
                        )
                        lineTo(
                            cloudX - 10.dp.toPx(),
                            cloudY + 51.dp.toPx()
                        )
                        lineTo(
                            cloudX + 2.dp.toPx(),
                            cloudY + 48.dp.toPx()
                        )
                        lineTo(
                            cloudX - 7.dp.toPx(),
                            cloudY + 72.dp.toPx()
                        )
                        lineTo(
                            cloudX + 21.dp.toPx(),
                            cloudY + 40.dp.toPx()
                        )
                        lineTo(
                            cloudX + 8.dp.toPx(),
                            cloudY + 43.dp.toPx()
                        )
                        close()
                    }

                    drawPath(
                        path = lightning,
                        color = Color(0xFFFFE66D)
                    )
                }

                if (scene == WeatherScene.SNOW) {
                    repeat(5) { index ->
                        val x = cloudX - 48.dp.toPx() +
                                index * 24.dp.toPx()
                        val y = cloudY + 44.dp.toPx() +
                                ((index % 2) * 14).dp.toPx()

                        drawCircle(
                            color = Color.White.copy(alpha = 0.92f),
                            radius = 3.1.dp.toPx(),
                            center = Offset(x, y)
                        )
                    }
                }
            }

            WeatherScene.FOG -> {
                repeat(4) { index ->
                    val top = h * 0.26f +
                            index * 18.dp.toPx()
                    drawRoundRect(
                        color = Color.White.copy(
                            alpha = 0.50f - index * 0.07f
                        ),
                        topLeft = Offset(
                            w * 0.52f +
                                    if (index % 2 == 0) drift.dp.toPx()
                                    else -drift.dp.toPx(),
                            top
                        ),
                        size = Size(
                            width = w * (0.36f - index * 0.025f),
                            height = 7.dp.toPx()
                        ),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                            20.dp.toPx()
                        )
                    )
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCloud(
    center: Offset,
    scale: Float = 1f,
    cloudColor: Color
) {
    val baseWidth = 128.dp.toPx() * scale
    val baseHeight = 39.dp.toPx() * scale

    drawRoundRect(
        color = cloudColor,
        topLeft = Offset(
            center.x - baseWidth / 2,
            center.y - 2.dp.toPx() * scale
        ),
        size = Size(baseWidth, baseHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(
            28.dp.toPx() * scale
        )
    )

    drawCircle(
        color = cloudColor,
        radius = 31.dp.toPx() * scale,
        center = Offset(
            center.x - 27.dp.toPx() * scale,
            center.y - 7.dp.toPx() * scale
        )
    )

    drawCircle(
        color = cloudColor,
        radius = 39.dp.toPx() * scale,
        center = Offset(
            center.x + 12.dp.toPx() * scale,
            center.y - 19.dp.toPx() * scale
        )
    )

    drawCircle(
        color = cloudColor,
        radius = 26.dp.toPx() * scale,
        center = Offset(
            center.x + 46.dp.toPx() * scale,
            center.y - 5.dp.toPx() * scale
        )
    )
}

private fun cardBackgroundApprox(
    scene: WeatherScene
): Color = when (scene) {
    WeatherScene.RAIN,
    WeatherScene.STORM -> Color(0xFF394C67)
    WeatherScene.SNOW -> Color(0xFF5A8DA8)
    else -> Color(0xFF405E9F)
}

@Composable
private fun HeroPill(
    text: String,
    compact: Boolean = false
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.08f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(50)
            )
            .padding(
                horizontal = if (compact) 10.dp else 14.dp,
                vertical = if (compact) 6.dp else 8.dp
            )
    ) {
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.80f),
            fontSize = if (compact) 11.sp else 13.sp
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier.padding(
            start = 48.dp,
            end = 48.dp,
            top = 18.dp,
            bottom = 5.dp
        )
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = subtitle,
            color = Color.White.copy(alpha = 0.46f),
            fontSize = 14.sp
        )
    }
}


/**
 * Displays a horizontally scrolling row of application cards.
 */
@Composable
private fun AppRow(
    context: Context,
    soundManager: JoeTvSoundManager,
    apps: List<JoeTvApp>,
    favoritePackages: Set<String>,
    hiddenPackages: Set<String> = emptySet(),
    onFocused: (JoeTvApp) -> Unit,
    onOpen: (JoeTvApp) -> Unit,
    onToggleFavorite: (JoeTvApp) -> Unit,
    onToggleHidden: (JoeTvApp) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(
            start = 48.dp,
            end = 48.dp,
            top = 10.dp,
            bottom = 28.dp
        )
    ) {
        items(
            items = apps,
            key = { app -> app.packageName }
        ) { app ->
            JoeTvAppCard(
                context = context,
                soundManager = soundManager,
                app = app,
                isFavorite = app.packageName in favoritePackages,
                isHidden = app.packageName in hiddenPackages,
                onFocused = {
                    onFocused(app)
                },
                onOpen = {
                    onOpen(app)
                },
                onToggleFavorite = {
                    onToggleFavorite(app)
                },
                onToggleHidden = {
                    onToggleHidden(app)
                }
            )
        }
    }
}


/**
 * Displays a single application card and handles focus,
 * launching, favorites, and hide shortcuts.
 */
@Composable
private fun JoeTvAppCard(
    context: Context,
    soundManager: JoeTvSoundManager,
    app: JoeTvApp,
    isFavorite: Boolean,
    isHidden: Boolean,
    onFocused: () -> Unit,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleHidden: () -> Unit
) {
    var focused by remember {
        mutableStateOf(false)
    }

    val appIcon = remember(app.packageName) {
        runCatching {
            context.packageManager
                .getApplicationIcon(app.packageName)
                .toBitmap(
                    width = 96,
                    height = 96
                )
                .asImageBitmap()
        }.getOrNull()
    }

    Box(
        modifier = Modifier
            .size(
                width = 215.dp,
                height = 124.dp
            )
            .graphicsLayer {
                val scale = if (focused) 1.07f else 1f
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(19.dp))
            .background(
                if (focused) {
                    Color(0xFF36425E)
                } else {
                    Color(0xE0191D27)
                }
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) {
                    Color.White
                } else {
                    Color.White.copy(alpha = 0.08f)
                },
                shape = RoundedCornerShape(19.dp)
            )
            .onFocusChanged {
                focused = it.isFocused

                if (it.isFocused) {
                    soundManager.playMove()
                    onFocused()
                }
            }
            // JoeTV controller shortcuts:
            // Bookmark / Y = Favorite
            // X = Hide or Restore
            // DPAD and OK continue through normal TV navigation. DPAD and OK continue through normal TV focus handling.
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    false
                } else {
                    when (event.key) {
                        Key.Bookmark,
                        Key.ButtonY -> {
                            onToggleFavorite()
                            true
                        }

                        Key.ButtonX -> {
                            onToggleHidden()
                            true
                        }

                        else -> false
                    }
                }
            }
            .focusable()
            .clickable {
                soundManager.playSelect {
                    onOpen()
                }
            }
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center
        ) {
            if (appIcon != null) {
                Image(
                    bitmap = appIcon,
                    contentDescription = "${app.name} icon",
                    modifier = Modifier.size(38.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(
                    text = app.initials,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (isFavorite) {
            Text(
                text = "★",
                modifier = Modifier.align(Alignment.TopEnd),
                color = Color(0xFFFFD54F),
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (isHidden) {
            Text(
                text = "HIDDEN",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp),
                color = Color.White.copy(alpha = 0.62f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        Column(
            modifier = Modifier.align(Alignment.BottomStart)
        ) {
            Text(
                text = app.name,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = app.description,
                color = Color.White.copy(alpha = 0.50f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EmptyFavoritesCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 48.dp,
                end = 48.dp,
                top = 10.dp,
                bottom = 28.dp
            )
            .height(96.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.07f),
                shape = RoundedCornerShape(18.dp)
            )
            .padding(20.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = "Highlight an app and press the bookmark button to add it.",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 15.sp
        )
    }
}

@Composable
private fun EmptyRecentAppsCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 48.dp,
                end = 48.dp,
                top = 10.dp,
                bottom = 28.dp
            )
            .height(96.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.07f),
                shape = RoundedCornerShape(18.dp)
            )
            .padding(20.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = "Open an app and it will appear here.",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 15.sp
        )
    }
}


/**
 * Placeholder media section for future streaming integrations.
 */
@Composable
private fun ContinueWatchingRow() {
    val mediaItems = listOf(
        "Recently Played",
        "Local Videos",
        "Recommended",
        "Watch Later"
    )

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(
            start = 48.dp,
            end = 48.dp,
            top = 10.dp,
            bottom = 30.dp
        )
    ) {
        items(mediaItems) { title ->
            MediaCard(title = title)
        }
    }
}

@Composable
private fun MediaCard(title: String) {
    var focused by remember {
        mutableStateOf(false)
    }

    Box(
        modifier = Modifier
            .size(
                width = 260.dp,
                height = 140.dp
            )
            .graphicsLayer {
                val scale = if (focused) 1.05f else 1f
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(19.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF242B3D),
                        Color(0xFF151924)
                    )
                )
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) {
                    Color.White
                } else {
                    Color.White.copy(alpha = 0.07f)
                },
                shape = RoundedCornerShape(19.dp)
            )
            .onFocusChanged {
                focused = it.isFocused
            }
            .focusable()
            .padding(18.dp)
    ) {
        Text(
            text = title,
            modifier = Modifier.align(Alignment.BottomStart),
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}


/**
 * Footer displayed at the bottom of the launcher.
 */
@Composable
private fun JoeTvFooter() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 48.dp,
                end = 48.dp,
                top = 18.dp,
                bottom = 40.dp
            )
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.07f),
                shape = RoundedCornerShape(18.dp)
            )
            .padding(
                horizontal = 24.dp,
                vertical = 20.dp
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "JoeTV",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Custom entertainment system",
                color = Color.White.copy(alpha = 0.48f),
                fontSize = 13.sp
            )
        }

        Text(
            text = "Version 2.0",
            color = Color.White.copy(alpha = 0.42f),
            fontSize = 13.sp
        )
    }
}


/**
 * Launches the selected application.
 *
 * Attempts to use the Android TV launch intent first,
 * then falls back to the standard Android launch intent.
 */
private fun launchApp(
    context: Context,
    packageName: String
) {
    if (packageName.isBlank()) return

    val launchIntent =
        context.packageManager.getLeanbackLaunchIntentForPackage(packageName)
            ?: context.packageManager.getLaunchIntentForPackage(packageName)

    launchIntent?.let { intent ->
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

private fun openBluetoothSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)
    } catch (exception: ActivityNotFoundException) {
        val fallbackIntent = Intent(Settings.ACTION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(fallbackIntent)
    }
}

private fun openSoundAssistant(context: Context) {
    val packageName = "com.samsung.android.soundassistant"

    val launchIntent =
        context.packageManager.getLaunchIntentForPackage(packageName)

    if (launchIntent != null) {
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
    } else {
        try {
            // Opens Sound Assistant in the Samsung Galaxy Store
            val storeIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("samsungapps://ProductDetail/$packageName")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(storeIntent)
        } catch (exception: ActivityNotFoundException) {
            // Final fallback: open the Galaxy Store web listing
            val browserIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse(
                    "https://galaxystore.samsung.com/detail/$packageName"
                )
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(browserIntent)
        }
    }
}