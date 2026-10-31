package com.nishanth.instamapper

import android.app.Application
import android.webkit.CookieManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nishanth.instamapper.data.AppSettings
import com.nishanth.instamapper.data.ClimateService
import com.nishanth.instamapper.data.InstagramFetcher
import com.nishanth.instamapper.data.Place
import com.nishanth.instamapper.data.PlaceResolver
import com.nishanth.instamapper.data.RecentReel
import com.nishanth.instamapper.data.RecentStore
import com.nishanth.instamapper.data.Updates
import com.nishanth.instamapper.data.Diagnostics
import com.nishanth.instamapper.data.NO_CONNECTION_HELP
import com.nishanth.instamapper.data.NoConnectionException
import com.nishanth.instamapper.data.ReelFetchException
import com.nishanth.instamapper.data.isNetworkProblem
import com.nishanth.instamapper.data.ReelInfo
import com.nishanth.instamapper.data.SeasonReport
import com.nishanth.instamapper.data.SettingsRepo
import com.nishanth.instamapper.data.Spot
import com.nishanth.instamapper.data.SpotStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

enum class Tab { HOME, SPOTS, SETTINGS }

/** What the user wants to do once a place is chosen. */
enum class PlaceAction(val title: String) {
    SAVE("Add this spot to your map"),
    SEASON("Best season / time to visit"),
    DIRECTIONS("Get directions"),
    NEARBY("Stays & food nearby"),
    SHARE("Share this spot"),
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val settingsRepo = SettingsRepo(app)
    private val spotStore = SpotStore(app)
    private val recentStore = RecentStore(app)

    var settings by mutableStateOf(settingsRepo.load()); private set
    var spots by mutableStateOf(spotStore.load()); private set

    var tab by mutableStateOf(Tab.HOME)
    var showLogin by mutableStateOf(false)
    var loggedIn by mutableStateOf(InstagramFetcher.isLoggedIn()); private set

    var input by mutableStateOf("")
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var reel by mutableStateOf<ReelInfo?>(null); private set

    var places by mutableStateOf<List<Place>?>(null); private set
    var placesLoading by mutableStateOf(false); private set
    var placeMessage by mutableStateOf<String?>(null); private set
    var placeProgress by mutableStateOf<String?>(null); private set
    var placeNote by mutableStateOf<String?>(null); private set
    var selectedPlace by mutableStateOf<Place?>(null)
    var placeSheet by mutableStateOf<PlaceAction?>(null)

    var showSeason by mutableStateOf(false)
    var season by mutableStateOf<SeasonReport?>(null); private set
    var seasonLoading by mutableStateOf(false); private set
    var seasonError by mutableStateOf<String?>(null); private set

    var showAudio by mutableStateOf(false)
    var toast by mutableStateOf<String?>(null)

    var recents by mutableStateOf(recentStore.load()); private set
    var showOnboarding by mutableStateOf(!settingsRepo.onboarded)
    var update by mutableStateOf<Updates.Info?>(null); private set
    var focusedSpotId by mutableStateOf<String?>(null)

    var errorIsNetwork by mutableStateOf(false); private set
    var errorDetails by mutableStateOf<String?>(null); private set
    var diagnostics by mutableStateOf<Diagnostics.Report?>(null); private set
    var diagnosticsRunning by mutableStateOf(false); private set
    var showDiagnostics by mutableStateOf(false)

    fun runDiagnostics() {
        showDiagnostics = true
        if (diagnosticsRunning) return
        diagnosticsRunning = true
        diagnostics = null
        viewModelScope.launch {
            try {
                diagnostics = Diagnostics.run(getApplication<Application>())
            } finally {
                diagnosticsRunning = false
            }
        }
    }

    init {
        viewModelScope.launch { update = Updates.check() }
    }

    fun finishOnboarding() {
        settingsRepo.onboarded = true
        showOnboarding = false
    }

    fun removeRecent(r: RecentReel) {
        recents = recents.filterNot { it.url == r.url }
        recentStore.save(recents)
    }

    private var requestId = 0

    fun analyze(text: String = input) {
        input = text
        tab = Tab.HOME
        if (text.isBlank()) { error = "Paste an Instagram reel link first."; return }
        val id = ++requestId
        loading = true
        error = null
        errorIsNetwork = false
        errorDetails = null
        reel = null
        resetPlaceState()
        viewModelScope.launch {
            try {
                val r = InstagramFetcher.fetch(getApplication<Application>(), text)
                if (id == requestId) {
                    reel = r
                    recents = recentStore.add(recents, r)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (id == requestId) {
                    errorIsNetwork = e is NoConnectionException || e.isNetworkProblem()
                    errorDetails = (e as? ReelFetchException)?.details
                    error = if (errorIsNetwork && e !is NoConnectionException) NO_CONNECTION_HELP
                    else e.message ?: "Something went wrong"
                }
            } finally {
                if (id == requestId) loading = false
            }
        }
    }

    fun closeReel() {
        requestId++
        reel = null
        loading = false
        resetPlaceState()
    }

    private fun resetPlaceState() {
        places = null; selectedPlace = null; placeSheet = null; placeMessage = null; placeNote = null
        season = null; seasonError = null; showSeason = false; showAudio = false
    }

    // ---- places ------------------------------------------------------------------

    fun requestPlace(action: PlaceAction) {
        placeSheet = action
        if (places == null && !placesLoading) resolvePlaces()
    }

    private fun resolvePlaces() {
        val r = reel ?: return
        placesLoading = true
        placeMessage = null
        viewModelScope.launch {
            try {
                val res = PlaceResolver.resolve(getApplication<Application>(), r, settings) { step ->
                    viewModelScope.launch { placeProgress = step }
                }
                val found = res.places
                places = found
                if (selectedPlace == null) selectedPlace = found.firstOrNull()
                placeNote = res.note
                if (found.isEmpty()) placeMessage = "Couldn't work out the place from this reel. Search for it below."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                places = emptyList()
                placeMessage = "Place lookup failed: ${e.message}. Try searching below."
            } finally {
                placesLoading = false
                placeProgress = null
            }
        }
    }

    /** Throw away the detected places and look again (e.g. after adding an AI key). */
    fun retryPlaces() {
        places = null
        selectedPlace = null
        placeNote = null
        resolvePlaces()
    }

    fun searchPlace(query: String) {
        if (query.isBlank()) return
        placesLoading = true
        placeMessage = null
        viewModelScope.launch {
            try {
                val res = PlaceResolver.geocode(getApplication<Application>(), query, 5)
                if (res.isEmpty()) {
                    placeMessage = "No results for \"$query\". Try adding the city or state."
                } else {
                    places = (res + (places ?: emptyList())).distinctBy { it.key }
                    selectedPlace = res.first()
                }
            } finally {
                placesLoading = false
            }
        }
    }

    fun saveSpot(p: Place) {
        val r = reel
        val spot = Spot(
            id = "${r?.shortcode ?: "manual"}_${System.currentTimeMillis()}",
            name = p.name, lat = p.lat, lng = p.lng, address = p.address,
            reelUrl = r?.url.orEmpty(), username = r?.username, thumbnailUrl = r?.thumbnailUrl,
            savedAt = System.currentTimeMillis(),
            mapsQuery = p.mapsQuery,
        )
        spots = listOf(spot) + spots.filterNot { it.reelUrl == spot.reelUrl && it.name == spot.name }
        spotStore.save(spots)
        focusedSpotId = spot.id
        toast = "Saved \"${p.name}\" to My Spots"
    }

    fun deleteSpot(s: Spot) {
        spots = spots.filterNot { it.id == s.id }
        spotStore.save(spots)
    }

    fun exportKml() = spotStore.exportKml(spots)

    // ---- season ------------------------------------------------------------------

    fun openSeason(p: Place) {
        selectedPlace = p
        placeSheet = null
        showSeason = true
        if (season?.place?.key == p.key && seasonError == null) return
        season = null
        seasonError = null
        seasonLoading = true
        viewModelScope.launch {
            try {
                season = ClimateService.report(p, settings)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                seasonError = e.message ?: "Couldn't load weather history"
            } finally {
                seasonLoading = false
            }
        }
    }

    // ---- settings / login --------------------------------------------------------

    fun updateSettings(s: AppSettings) {
        settings = s
        settingsRepo.save(s)
        toast = "Settings saved"
    }

    /** After signing in, reload the open reel so it picks up audio/location/video. */
    fun onLoginClosed() {
        showLogin = false
        val wasLoggedIn = loggedIn
        refreshLogin()
        if (!wasLoggedIn && loggedIn) {
            reel?.let { analyze(it.url) }
            toast = "Signed in to Instagram"
        }
    }

    fun refreshLogin() {
        loggedIn = InstagramFetcher.isLoggedIn()
    }

    fun logout() {
        CookieManager.getInstance().removeAllCookies { refreshLogin() }
        CookieManager.getInstance().flush()
        loggedIn = false
    }
}
