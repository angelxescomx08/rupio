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
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.rupio.app.BuildConfig
import com.rupio.app.domain.Accent
import com.rupio.app.domain.SupportTier
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
    val goldIcon: Boolean = false,
) {
    val best: SupportTier? get() = SupportTier.best(owned)
    val isSupporter: Boolean get() = owned.isNotEmpty()
    val effectiveAccent: Accent get() = Accent.effective(accent, best)
    fun price(t: SupportTier): String = prices[t] ?: t.fallbackPrice
}

sealed interface SupportEvent {
    /** Compra confirmada → pantalla de gracias. */
    data class Thanks(val tier: SupportTier) : SupportEvent
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
class SupportRepository(private val app: Application, private val scope: CoroutineScope) : PurchasesUpdatedListener {
    private val prefs = app.getSharedPreferences("rupio_supporter", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(loadCached())
    val state: StateFlow<SupportState> = _state
    private val _events = MutableSharedFlow<SupportEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<SupportEvent> = _events

    private var client: BillingClient? = null
    @Volatile private var connecting = false
    private val details = mutableMapOf<SupportTier, ProductDetails>()

    /** Robolectric (pruebas) no tiene Google Play: ahí no se intenta conectar. */
    private val canUseStore = !Build.FINGERPRINT.orEmpty().contains("robolectric", ignoreCase = true)

    init { applyAccent() }

    // ---------- Estado guardado ----------

    private fun loadCached(): SupportState {
        val owned = prefs.getStringSet("owned", emptySet()).orEmpty().mapNotNull(SupportTier::of).toMutableSet()
        if (BuildConfig.DEBUG) owned += prefs.getStringSet("debug_owned", emptySet()).orEmpty().mapNotNull(SupportTier::of)
        return SupportState(
            owned = owned,
            pending = prefs.getStringSet("pending", emptySet()).orEmpty().mapNotNull(SupportTier::of).toSet(),
            accent = Accent.of(prefs.getString("accent", null)),
            goldIcon = prefs.getBoolean("gold_icon", false),
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
        val products = SupportTier.entries.map {
            QueryProductDetailsParams.Product.newBuilder().setProductId(it.productId).setProductType(BillingClient.ProductType.INAPP).build()
        }
        c.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(products).build()) { result, res ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            val prices = mutableMapOf<SupportTier, String>()
            for (pd in res.productDetailsList) {
                val tier = SupportTier.of(pd.productId) ?: continue
                details[tier] = pd
                pd.oneTimePurchaseOfferDetails?.formattedPrice?.let { prices[tier] = it }
            }
            _state.update { it.copy(prices = prices, storeReady = details.isNotEmpty()) }
        }
        c.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            // La lista de Google Play es la verdad: lo que ya no aparece (reembolsos) se quita.
            val bought = mutableSetOf<SupportTier>(); val pending = mutableSetOf<SupportTier>()
            for (p in purchases) {
                val tiers = p.products.mapNotNull(SupportTier::of)
                when (p.purchaseState) {
                    Purchase.PurchaseState.PURCHASED -> { bought += tiers; if (!p.isAcknowledged) acknowledge(p) }
                    Purchase.PurchaseState.PENDING -> pending += tiers
                }
            }
            val wasPending = _state.value.pending
            setOwned(bought, pending)
            // Un pago pendiente que se completó mientras la app estaba cerrada también merece su "gracias".
            // (Restaurar compras al reinstalar no muestra nada: no es una compra nueva.)
            SupportTier.best(bought intersect wasPending)?.let { _events.tryEmit(SupportEvent.Thanks(it)) }
        }
    }

    private fun setOwned(storeOwned: Set<SupportTier>, pending: Set<SupportTier>) {
        saveOwned(storeOwned, pending)
        _state.update { it.copy(owned = storeOwned + debugOwned(), pending = pending - storeOwned) }
        afterOwnershipChange()
    }

    // ---------- Comprar ----------

    fun buy(activity: Activity, tier: SupportTier) {
        val c = client
        val pd = details[tier]
        if (c == null || !c.isReady || pd == null) {
            connect()
            _state.update { it.copy(message = "Los pagos funcionan cuando instalas Rupio desde Google Play. Revisa tu conexión e intenta de nuevo.") }
            return
        }
        val params = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(pd)
        pd.oneTimePurchaseOfferDetails?.offerToken?.let { params.setOfferToken(it) }
        _state.update { it.copy(buying = tier, message = null) }
        val r = c.launchBillingFlow(activity, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(params.build())).build())
        if (r.responseCode != BillingClient.BillingResponseCode.OK) onPurchasesUpdated(r, null)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases.orEmpty().forEach(::handle)
            BillingClient.BillingResponseCode.USER_CANCELED -> _state.update { it.copy(buying = null) }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                _state.update { it.copy(buying = null, message = "Ya tenías este nivel. ¡Gracias otra vez! 💙") }; refresh()
            }
            BillingClient.BillingResponseCode.NETWORK_ERROR, BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE ->
                _state.update { it.copy(buying = null, message = "Sin conexión con Google Play. Revisa tu internet e intenta de nuevo.") }
            else -> _state.update { it.copy(buying = null, message = "No se pudo completar el pago. No se te cobró nada; intenta de nuevo.") }
        }
    }

    private fun handle(p: Purchase) {
        val tiers = p.products.mapNotNull(SupportTier::of).toSet()
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
                    message = "Tu pago quedó pendiente. Cuando lo completes (por ejemplo en OXXO) se activa solo.") }
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

    fun setAccent(a: Accent) {
        if (!a.unlockedBy(_state.value.best)) return
        prefs.edit().putString("accent", a.id).apply()
        _state.update { it.copy(accent = a) }
        applyAccent()
    }

    fun setGoldIcon(enabled: Boolean) {
        val allowed = enabled && _state.value.best == SupportTier.FAN
        prefs.edit().putBoolean("gold_icon", allowed).apply()
        _state.update { it.copy(goldIcon = allowed) }
        AppIcon.set(app, gold = allowed)
    }

    private fun afterOwnershipChange() {
        applyAccent()
        // Si se reembolsó el nivel Fan, el ícono vuelve al normal.
        if (_state.value.goldIcon && _state.value.best != SupportTier.FAN) setGoldIcon(false)
    }

    private fun applyAccent() = RupioColors.applyAccent(Color(_state.value.effectiveAccent.argb))

    // ---------- Mensaje al terminar ----------

    val lastPromptedWorkoutId: String? get() = prefs.getString("last_prompt_workout", null)
    val lastPromptedMilestone: Int get() = prefs.getInt("last_prompt_milestone", 0)
    val lastPromptAt: Long get() = prefs.getLong("last_prompt_at", 0)
    val dismissals: Int get() = prefs.getInt("prompt_dismissals", 0)
    fun markPrompted(workoutId: String, milestone: Int = 0) {
        prefs.edit().putString("last_prompt_workout", workoutId).putInt("last_prompt_milestone", milestone)
            .putLong("last_prompt_at", System.currentTimeMillis()).apply()
    }
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
        val storeOwned = prefs.getStringSet("owned", emptySet()).orEmpty().mapNotNull(SupportTier::of).toSet()
        _state.update { it.copy(owned = storeOwned) }
        afterOwnershipChange()
    }
}

/**
 * Ícono alternativo (nivel Fan): dos "alias" de la misma pantalla en el manifiesto; se activa uno y se apaga el otro.
 * Algunos lanzadores tardan unos segundos en mostrar el cambio.
 */
object AppIcon {
    private const val DEFAULT = "com.rupio.app.LauncherDefault"
    private const val GOLD = "com.rupio.app.LauncherGold"

    fun set(context: Context, gold: Boolean) {
        val pm = context.packageManager
        fun state(on: Boolean) = if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        runCatching {
            // Primero se enciende el nuevo y luego se apaga el viejo: nunca queda la app sin ícono.
            pm.setComponentEnabledSetting(ComponentName(context, if (gold) GOLD else DEFAULT), state(true), PackageManager.DONT_KILL_APP)
            pm.setComponentEnabledSetting(ComponentName(context, if (gold) DEFAULT else GOLD), state(false), PackageManager.DONT_KILL_APP)
        }
    }
}
