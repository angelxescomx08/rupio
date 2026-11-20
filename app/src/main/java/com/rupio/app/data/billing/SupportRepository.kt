package com.rupio.app.data.billing

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.rupio.app.BuildConfig
import com.rupio.app.domain.Accent
import com.rupio.app.domain.AppIconStyle
import com.rupio.app.core.tr
import com.rupio.app.domain.SupportTier
import com.rupio.app.domain.SupportUpgrade
import com.rupio.app.domain.WallBoost
import com.rupio.app.ui.theme.RupioColors
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SupportState(
    /** Niveles comprados (confirmados por Google Play, o guardados de la última vez que respondió). */
    val owned: Set<SupportTier> = emptySet(),
    /** Pagos en espera (p. ej. pago en efectivo en OXXO): se activan solos cuando se completan. */
    val pending: Set<SupportTier> = emptySet(),
    /** Precio con formato de Google Play, en la moneda del usuario. */
    val prices: Map<SupportTier, String> = emptyMap(),
    /** true cuando Google Play respondió y los productos existen (app instalada desde Play). */
    val storeReady: Boolean = false,
    val buying: SupportTier? = null,
    val message: String? = null,
    val accent: Accent = Accent.AZUL,
    /** v2.0: ícono elegido (antes solo había dorado sí/no). */
    val appIcon: AppIconStyle = AppIconStyle.NORMAL,
    /** v1.8: precios de los aportes del muro (Google Play). */
    val wallPrices: Map<WallBoost, String> = emptyMap(),
    /** v1.8: llegó una compra del muro pero falta el nombre (y el permiso para publicarlo). */
    val wallNeedsName: Boolean = false,
    /** v1.8: registrando una compra del muro en el servidor. */
    val wallRegistering: Boolean = false,
    /** v2.1: precios de las mejoras (pagar solo la diferencia) y cuáles existen en Google Play. */
    val upgradePrices: Map<SupportUpgrade, String> = emptyMap(),
    val upgradeReady: Set<SupportUpgrade> = emptySet(),
) {
    val best: SupportTier? get() = SupportTier.best(owned)
    val isSupporter: Boolean get() = owned.isNotEmpty()
    val effectiveAccent: Accent get() = Accent.effective(accent, best)
    val effectiveIcon: AppIconStyle get() = AppIconStyle.effective(appIcon, best)
    val isPatron: Boolean get() = SupportTier.MECENAS in owned
    fun price(t: SupportTier): String = prices[t] ?: t.fallbackPrice
    fun price(b: WallBoost): String = wallPrices[b] ?: if (b == WallBoost.BIG) price(SupportTier.MECENAS) else b.fallbackPrice

    /**
     * v2.1: la mejora con la que se sube de tu nivel a [t] pagando la diferencia. Solo si Google Play ya tiene ese
     * producto (si no, se cobra el nivel completo); en la versión de pruebas se muestra con su precio sugerido.
     */
    fun upgradeFor(t: SupportTier): SupportUpgrade? = SupportUpgrade.between(best, t)?.takeIf { it in upgradeReady || BuildConfig.DEBUG }
    /** Lo que se paga para tener [t]: la diferencia si hay mejora, o el precio completo. */
    fun priceFor(t: SupportTier): String = upgradeFor(t)?.let { upgradePrices[it] ?: it.fallbackPrice } ?: price(t)
}

sealed interface SupportEvent {
    /** Compra confirmada → pantalla de gracias. [wallRank]: tu lugar si acabas de entrar al muro. */
    data class Thanks(val tier: SupportTier, val wallRank: Int? = null) : SupportEvent
    /** v1.8: un aporte extra ya cuenta en el muro. */
    data class WallRaised(val rank: Int?, val previousRank: Int?) : SupportEvent
}

/**
 * Compras de apoyo con Google Play Billing (biblioteca 9).
 *
 *  - Productos de pago único ("no consumibles"): se reconocen (acknowledge) al instante; si no, Google los
 *    reembolsa a los 3 días.
 *  - El estado se guarda en el teléfono para que funcione sin internet y el mensaje no "parpadee" al abrir.
 *    Cada vez que abres la app se confirma con Google Play (así se respetan reembolsos y reinstalaciones).
 *  - Pagos pendientes (efectivo en OXXO, transferencias): se muestran como "pendiente" y se activan solos.
 *  - Pertenece a la cuenta de Google del teléfono, no a la cuenta de Rupio: cerrar sesión NO lo quita.
 *
 * Sin servidor propio la verificación es en el teléfono: para un apoyo voluntario que solo quita un mensaje y
 * da adornos es suficiente (no se protege contenido de pago).
 */
class SupportRepository(
    private val app: Application,
    private val scope: CoroutineScope,
    /** v1.8: muro de agradecimientos (nivel Mecenas). null en pruebas que no lo usan. */
    private val wall: WallRepository? = null,
    /** Id de la cuenta de Rupio lista para el muro (con correo confirmado), o null. */
    private val wallAccountId: () -> String? = { null },
) : PurchasesUpdatedListener {
    private val prefs = app.getSharedPreferences("rupio_supporter", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(loadCached())
    val state: StateFlow<SupportState> = _state
    private val _events = MutableSharedFlow<SupportEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<SupportEvent> = _events

    private var client: BillingClient? = null
    @Volatile private var connecting = false
    private val details = mutableMapOf<SupportTier, ProductDetails>()
    private val upgradeDetails = mutableMapOf<SupportUpgrade, ProductDetails>()
    private val wallDetails = mutableMapOf<WallBoost, ProductDetails>()
    /** Compras del muro que esperan el nombre (la persona no lo había escrito antes de pagar). */
    private val awaitingName = mutableListOf<Purchase>()
    /** Tokens que se están registrando (abrir la app y volver a ella pueden pedir lo mismo a la vez). */
    private val inFlight = mutableSetOf<String>()
    /** Niveles que confirmó Google Play (sin los simulados ni el Mecenas, que lo confirma el servidor). */
    @Volatile private var storeOwned: Set<SupportTier> = emptySet()

    /** Robolectric (pruebas) no tiene Google Play: ahí no se intenta conectar. */
    private val canUseStore = !Build.FINGERPRINT.orEmpty().contains("robolectric", ignoreCase = true)

    init {
        storeOwned = prefs.getStringSet("owned", emptySet()).orEmpty().mapNotNull(SupportTier::of).filter { it != SupportTier.MECENAS }.toSet()
        applyAccent()
        // El nivel Mecenas lo da el servidor (tu lugar en el muro): cuando cambia, se recalculan los adornos.
        wall?.let { w -> scope.launch { w.state.collect { recomputeOwned() } } }
    }

    private fun recomputeOwned() {
        val patron = wall?.isPatron == true
        val owned = storeOwned + debugOwned() + (if (patron) setOf(SupportTier.MECENAS) else emptySet())
        if (owned != _state.value.owned) { _state.update { it.copy(owned = owned) }; afterOwnershipChange() }
    }

    // ---------- Estado guardado ----------

    private fun loadCached(): SupportState {
        val owned = prefs.getStringSet("owned", emptySet()).orEmpty().mapNotNull(SupportTier::of).filter { it != SupportTier.MECENAS }.toMutableSet()
        if (BuildConfig.DEBUG) owned += prefs.getStringSet("debug_owned", emptySet()).orEmpty().mapNotNull(SupportTier::of)
        if (wall?.isPatron == true) owned += SupportTier.MECENAS
        return SupportState(
            owned = owned,
            pending = prefs.getStringSet("pending", emptySet()).orEmpty().mapNotNull(SupportTier::of).toSet(),
            accent = Accent.of(prefs.getString("accent", null)),
            // v2.0: "gold_icon" (versiones de antes) pasa a ser el ícono dorado.
            appIcon = prefs.getString("app_icon", null)?.let(AppIconStyle::of)
                ?: if (prefs.getBoolean("gold_icon", false)) AppIconStyle.ORO else AppIconStyle.NORMAL,
        )
    }

    private fun saveOwned(storeOwned: Set<SupportTier>, pending: Set<SupportTier>) {
        prefs.edit().putStringSet("owned", storeOwned.map { it.productId }.toSet())
            .putStringSet("pending", pending.map { it.productId }.toSet()).apply()
    }

    private fun debugOwned(): Set<SupportTier> =
        if (BuildConfig.DEBUG) prefs.getStringSet("debug_owned", emptySet()).orEmpty().mapNotNull(SupportTier::of).toSet() else emptySet()

    // ---------- Conexión con Google Play ----------

    fun connect() {
        if (!canUseStore) return
        val c = client ?: runCatching {
            BillingClient.newBuilder(app)
                .setListener(this)
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .enableAutoServiceReconnection()
                .build()
        }.getOrNull()?.also { client = it } ?: return
        if (c.isReady) { refresh(); return }
        if (connecting) return   // ya se está conectando (al abrir la app y al volver a ella se pide a la vez)
        connecting = true
        runCatching {
            c.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    connecting = false
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) refresh()
                    else _state.update { it.copy(storeReady = false) }
                }
                override fun onBillingServiceDisconnected() { connecting = false /* enableAutoServiceReconnection reconecta solo */ }
            })
        }.onFailure { connecting = false }
    }

    /** Pide a Google Play precios y compras. Se llama al abrir la app y al volver a ella. */
    fun refresh() {
        val c = client?.takeIf { it.isReady } ?: return connect()
        val ids = (SupportTier.entries.map { it.productId } + WallBoost.productIds + SupportUpgrade.productIds).distinct()
        val products = ids.map {
            QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(BillingClient.ProductType.INAPP).build()
        }
        c.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(products).build()) { result, res ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            val prices = mutableMapOf<SupportTier, String>()
            val wallPrices = mutableMapOf<WallBoost, String>()
            val upPrices = mutableMapOf<SupportUpgrade, String>()
            for (pd in res.productDetailsList) {
                val price = pd.oneTimePurchaseOfferDetails?.formattedPrice
                SupportTier.of(pd.productId)?.let { tier -> details[tier] = pd; price?.let { prices[tier] = it } }
                WallBoost.of(pd.productId)?.let { b -> wallDetails[b] = pd; price?.let { wallPrices[b] = it } }
                SupportUpgrade.of(pd.productId)?.let { u -> upgradeDetails[u] = pd; price?.let { upPrices[u] = it } }
            }
            _state.update { it.copy(prices = prices, wallPrices = wallPrices, storeReady = details.isNotEmpty(),
                upgradePrices = upPrices, upgradeReady = upgradeDetails.keys.toSet()) }
        }
        c.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            // La lista de Google Play es la verdad: lo que ya no aparece (reembolsos) se quita.
            val bought = mutableSetOf<SupportTier>(); val pending = mutableSetOf<SupportTier>()
            val upgrades = mutableSetOf<SupportUpgrade>(); val pendingUp = mutableSetOf<SupportUpgrade>()
            for (p in purchases) {
                // v1.8: las compras del muro se consumen al registrarse; si siguen aquí, falta registrarlas.
                if (p.products.any(WallBoost::isWallProduct)) { handleWall(p, fromRestore = true); continue }
                val tiers = p.products.mapNotNull(SupportTier::of)
                val ups = p.products.mapNotNull(SupportUpgrade::of)
                when (p.purchaseState) {
                    Purchase.PurchaseState.PURCHASED -> { bought += tiers; upgrades += ups; if (!p.isAcknowledged) acknowledge(p) }
                    Purchase.PurchaseState.PENDING -> { pending += tiers; pendingUp += ups }
                }
            }
            val wasPending = _state.value.pending
            // v2.1: las mejoras dan su nivel si tienes el de abajo; una mejora pendiente deja pendiente su nivel.
            val owned = SupportUpgrade.effective(bought, upgrades)
            setOwned(owned, pending + pendingUp.map { it.to } - owned)
            bought.clear(); bought += owned
            // Un pago pendiente que se completó mientras la app estaba cerrada también merece su "gracias".
            // (Restaurar compras al reinstalar no muestra nada: no es una compra nueva.)
            SupportTier.best(bought intersect wasPending)?.let { _events.tryEmit(SupportEvent.Thanks(it)) }
        }
    }

    private fun setOwned(storeOwned: Set<SupportTier>, pending: Set<SupportTier>) {
        val store = storeOwned - SupportTier.MECENAS
        this.storeOwned = store
        saveOwned(store, pending)
        val patron = if (wall?.isPatron == true) setOf(SupportTier.MECENAS) else emptySet()
        _state.update { it.copy(owned = store + debugOwned() + patron, pending = pending - store) }
        afterOwnershipChange()
    }

    // ---------- Comprar ----------

    fun buy(activity: Activity, tier: SupportTier) {
        if (tier == SupportTier.MECENAS) { _state.update { it.copy(message = tr("Para ser Mecenas primero escribe tu nombre para el muro.", "To become a patron, first enter your name for the wall.")) }; return }
        val c = client
        // v2.1: si hay mejora para este nivel (pagar solo la diferencia), se compra esa.
        val pd = SupportUpgrade.between(_state.value.best, tier)?.let { upgradeDetails[it] } ?: details[tier]
        if (c == null || !c.isReady || pd == null) {
            connect()
            _state.update { it.copy(message = NO_PLAY) }
            return
        }
        val params = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(pd)
        pd.oneTimePurchaseOfferDetails?.offerToken?.let { params.setOfferToken(it) }
        _state.update { it.copy(buying = tier, message = null) }
        val r = c.launchBillingFlow(activity, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(params.build())).build())
        if (r.responseCode != BillingClient.BillingResponseCode.OK) onPurchasesUpdated(r, null)
    }

    /**
     * v1.8: entrar al muro (Mecenas) o sumar un aporte. [displayName] = nombre que la persona aceptó publicar (solo al
     * entrar, o para cambiarlo). La compra va atada a la cuenta de Rupio (obfuscatedAccountId = sha256 del id): el
     * servidor lo comprueba, así nadie registra tu compra en otra cuenta.
     */
    fun buyWall(activity: Activity, boost: WallBoost, displayName: String?) {
        val uid = wallAccountId()
        if (uid == null || wall == null) { _state.update { it.copy(message = WallError.NO_ACCOUNT.userText) }; return }
        val c = client
        val pd = wallDetails[boost]
        if (c == null || !c.isReady || pd == null) {
            connect()
            _state.update { it.copy(message = NO_PLAY) }
            return
        }
        displayName?.let { prefs.edit().putString("wall_pending_name", it).apply() }
        val params = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(pd)
        pd.oneTimePurchaseOfferDetails?.offerToken?.let { params.setOfferToken(it) }
        _state.update { it.copy(buying = SupportTier.MECENAS, message = null) }
        val r = c.launchBillingFlow(activity, BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(params.build()))
            .setObfuscatedAccountId(WallRepository.accountHash(uid))
            .build())
        if (r.responseCode != BillingClient.BillingResponseCode.OK) onPurchasesUpdated(r, null)
    }

    /** La persona escribió su nombre después de pagar (la compra esperaba en [awaitingName]). */
    fun completeWallName(displayName: String) {
        prefs.edit().putString("wall_pending_name", displayName).apply()
        val list = synchronized(awaitingName) { awaitingName.toList().also { awaitingName.clear() } }
        _state.update { it.copy(wallNeedsName = false) }
        list.forEach { handleWall(it, fromRestore = false) }
    }

    fun dismissWallName() = _state.update { it.copy(wallNeedsName = false) }

    private fun handleWall(p: Purchase, fromRestore: Boolean) {
        when (p.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                // Se reconoce enseguida (si no, Google la reembolsa a los 3 días) y se consume hasta que el servidor la registra.
                if (!p.isAcknowledged) acknowledge(p)
                scope.launch { registerWall(p) }
            }
            Purchase.PurchaseState.PENDING -> if (!fromRestore) _state.update { it.copy(buying = null,
                message = tr("Tu pago quedó pendiente. Cuando lo completes (por ejemplo en OXXO) aparecerás en el muro.", "Your payment is pending. Once you complete it (for example at OXXO), you'll appear on the wall.")) }
            else -> _state.update { it.copy(buying = null) }
        }
    }

    private suspend fun registerWall(p: Purchase) {
        val w = wall ?: return
        if (!synchronized(inFlight) { inFlight.add(p.purchaseToken) }) return
        val before = w.state.value.me
        _state.update { it.copy(buying = null, wallRegistering = true) }
        try {
            val name = prefs.getString("wall_pending_name", null)
            val me = w.register(p.originalJson, p.signature, name)
            prefs.edit().remove("wall_pending_name").apply()
            consume(p)
            recomputeOwned()
            _state.update { it.copy(wallRegistering = false) }
            if (before?.isPatron != true) _events.tryEmit(SupportEvent.Thanks(SupportTier.MECENAS, me.rank))
            else _events.tryEmit(SupportEvent.WallRaised(me.rank, before.rank))
        } catch (e: Exception) {
            val err = WallError.from(e)
            _state.update { it.copy(wallRegistering = false) }
            when (err) {
                WallError.NAME_REQUIRED, WallError.NAME_LENGTH, WallError.NAME_INVALID -> {
                    synchronized(awaitingName) { if (awaitingName.none { it.purchaseToken == p.purchaseToken }) awaitingName += p }
                    _state.update { it.copy(wallNeedsName = true, message = if (err == WallError.NAME_REQUIRED) null else err.userText) }
                }
                // Sin internet, sin cuenta o servidor sin configurar: la compra queda sin consumir y se reintenta al volver.
                else -> _state.update { it.copy(message = err.userText) }
            }
        } finally {
            synchronized(inFlight) { inFlight.remove(p.purchaseToken) }
        }
    }

    private fun consume(p: Purchase, attempt: Int = 0) {
        val c = client ?: return
        c.consumeAsync(ConsumeParams.newBuilder().setPurchaseToken(p.purchaseToken).build()) { r, _ ->
            // Si no se pudo consumir, al abrir la app vuelve a aparecer; el servidor responde "ya registrada" sin sumar dos veces.
            if (r.responseCode != BillingClient.BillingResponseCode.OK && attempt < 3)
                scope.launch { delay(2_000L shl attempt); consume(p, attempt + 1) }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases.orEmpty().forEach(::handle)
            BillingClient.BillingResponseCode.USER_CANCELED -> _state.update { it.copy(buying = null) }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                _state.update { it.copy(buying = null, message = tr("Ya tenías este nivel. ¡Gracias otra vez! 💙", "You already had this level. Thanks again! 💙")) }; refresh()
            }
            BillingClient.BillingResponseCode.NETWORK_ERROR, BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE ->
                _state.update { it.copy(buying = null, message = tr("Sin conexión con Google Play. Revisa tu internet e intenta de nuevo.", "Can't reach Google Play. Check your internet and try again.")) }
            else -> _state.update { it.copy(buying = null, message = tr("No se pudo completar el pago. No se te cobró nada; intenta de nuevo.", "The payment couldn't be completed. You weren't charged; please try again.")) }
        }
    }

    private fun handle(p: Purchase) {
        if (p.products.any(WallBoost::isWallProduct)) { handleWall(p, fromRestore = false); return }
        // v2.1: una mejora da su nivel (si tienes el de abajo, que es lo único a lo que se ofrece).
        val tiers = (p.products.mapNotNull(SupportTier::of) +
            p.products.mapNotNull(SupportUpgrade::of).filter { it.from in _state.value.owned }.map { it.to }).toSet()
        if (tiers.isEmpty()) return
        when (p.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                if (!p.isAcknowledged) acknowledge(p)
                val s = _state.value
                val newOnes = tiers - s.owned
                setOwned((s.owned - debugOwned()) + tiers, s.pending - tiers)
                _state.update { it.copy(buying = null) }
                SupportTier.best(newOnes)?.let { _events.tryEmit(SupportEvent.Thanks(it)) }
            }
            Purchase.PurchaseState.PENDING -> {
                val s = _state.value
                saveOwned(s.owned - debugOwned(), s.pending + tiers)
                _state.update { it.copy(buying = null, pending = it.pending + tiers,
                    message = tr("Tu pago quedó pendiente. Cuando lo completes (por ejemplo en OXXO) se activa solo.", "Your payment is pending. Once you complete it (for example at OXXO), it turns on by itself.")) }
            }
            else -> _state.update { it.copy(buying = null) }
        }
    }

    /** Reconoce la compra; reintenta unas veces si falla la red (si nunca se reconoce, Google reembolsa a los 3 días). */
    private fun acknowledge(p: Purchase, attempt: Int = 0) {
        val c = client ?: return
        c.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()) { r ->
            if (r.responseCode != BillingClient.BillingResponseCode.OK && attempt < 4)
                scope.launch { delay(2_000L shl attempt); acknowledge(p, attempt + 1) }
        }
    }

    // ---------- Adornos ----------

    fun clearMessage() = _state.update { it.copy(message = null) }

    private val NO_PLAY: String get() = tr("Los pagos funcionan cuando instalas Rupio desde Google Play. Revisa tu conexión e intenta de nuevo.",
        "Payments work when Rupio is installed from Google Play. Check your connection and try again.")

    fun setAccent(a: Accent) {
        if (!a.unlockedBy(_state.value.best)) return
        prefs.edit().putString("accent", a.id).apply()
        _state.update { it.copy(accent = a) }
        applyAccent()
    }

    /** v2.0: ícono de la app (normal, plateado, dorado o Aurora). Solo cambia si el nivel lo tiene. */
    fun setAppIcon(style: AppIconStyle) {
        val allowed = AppIconStyle.effective(style, _state.value.best)
        prefs.edit().putString("app_icon", allowed.id).remove("gold_icon").apply()
        _state.update { it.copy(appIcon = allowed) }
        AppIcon.set(app, allowed)
    }

    private fun afterOwnershipChange() {
        applyAccent()
        // Si se reembolsó el nivel, el ícono vuelve al normal.
        val s = _state.value
        if (s.appIcon != AppIconStyle.NORMAL && !s.appIcon.unlockedBy(s.best)) setAppIcon(AppIconStyle.NORMAL)
    }

    private fun applyAccent() = _state.value.effectiveAccent.let { RupioColors.applyAccent(Color(it.argb), Color(it.lightArgb)) }

    // ---------- Mensaje al terminar ----------

    val lastPromptedWorkoutId: String? get() = prefs.getString("last_prompt_workout", null)
    val lastPromptedMilestone: Int get() = prefs.getInt("last_prompt_milestone", 0)
    val lastPromptAt: Long get() = prefs.getLong("last_prompt_at", 0)
    val dismissals: Int get() = prefs.getInt("prompt_dismissals", 0)
    fun markPrompted(workoutId: String, milestone: Int = 0) {
        prefs.edit().putString("last_prompt_workout", workoutId).putInt("last_prompt_milestone", milestone)
            .putLong("last_prompt_at", System.currentTimeMillis()).apply()
    }
    /** v2.0: la vista previa del muro en Inicio se cerró con "Ocultar" (sigue en Progreso, Perfil y Apoya Rupio). */
    private val _homeWallHidden = MutableStateFlow(prefs.getBoolean("home_wall_hidden", false))
    val homeWallHidden: StateFlow<Boolean> = _homeWallHidden
    fun hideHomeWall() { prefs.edit().putBoolean("home_wall_hidden", true).apply(); _homeWallHidden.value = true }

    /** "Ahora no": a las 3 veces ya no se vuelve a mostrar el mensaje (Perfil sigue teniendo la fila de apoyo). */
    fun markDismissed() { prefs.edit().putInt("prompt_dismissals", dismissals + 1).apply() }

    // ---------- Solo en la versión de pruebas (debug) ----------

    /** Simula una compra para probar recompensas sin Google Play. En la versión publicada no existe el botón. */
    fun debugGrant(tier: SupportTier) {
        if (!BuildConfig.DEBUG) return
        val set = prefs.getStringSet("debug_owned", emptySet()).orEmpty() + tier.productId
        prefs.edit().putStringSet("debug_owned", set).apply()
        val newOne = tier !in _state.value.owned
        _state.update { it.copy(owned = it.owned + tier, buying = null) }
        afterOwnershipChange()
        if (newOne) _events.tryEmit(SupportEvent.Thanks(tier))
    }

    fun debugReset() {
        if (!BuildConfig.DEBUG) return
        prefs.edit().remove("debug_owned").remove("last_prompt_workout").remove("last_prompt_milestone").remove("last_prompt_at").remove("prompt_dismissals").apply()
        val patron = if (wall?.isPatron == true) setOf(SupportTier.MECENAS) else emptySet()
        _state.update { it.copy(owned = storeOwned + patron) }
        afterOwnershipChange()
    }
}

/**
 * Íconos alternativos (v2.0: plateado desde Licuado, dorado desde Fan, Aurora para Mecenas): "alias" de la misma
 * pantalla en el manifiesto; se activa uno y se apagan los demás.
 * Algunos lanzadores tardan unos segundos en mostrar el cambio.
 */
object AppIcon {
    fun set(context: Context, style: AppIconStyle) {
        val pm = context.packageManager
        fun state(on: Boolean) = if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        runCatching {
            // Primero se enciende el nuevo y luego se apagan los demás: nunca queda la app sin ícono.
            pm.setComponentEnabledSetting(ComponentName(context, style.alias), state(true), PackageManager.DONT_KILL_APP)
            AppIconStyle.entries.filter { it != style }.forEach {
                pm.setComponentEnabledSetting(ComponentName(context, it.alias), state(false), PackageManager.DONT_KILL_APP)
            }
        }
    }
}
