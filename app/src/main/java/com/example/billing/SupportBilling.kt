package com.example.billing

import android.app.Activity
import android.content.Context
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
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.example.data.prefs.UserPrefsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** A support option as the Support screen shows it; the price string always comes from Play. */
data class SupportOffer(val plan: Plan, val price: String, val details: ProductDetails, val offerToken: String?)

enum class Plan { MONTHLY, YEARLY, LIFETIME }

/**
 * Play Billing 9 wrapper. One subscription product with monthly and yearly base plans,
 * plus a one-time lifetime product. Play is the truth; DataStore only caches the entitlement
 * so the app works offline and the free/supporter limits apply instantly at launch.
 */
class SupportBilling(context: Context, private val prefs: UserPrefsRepository) : PurchasesUpdatedListener {
    companion object {
        const val SUBSCRIPTION_ID = "winrah_support"
        const val MONTHLY_PLAN = "monthly"
        const val YEARLY_PLAN = "yearly"
        const val LIFETIME_ID = "winrah_lifetime"
        /** A live subscription is re-checked at every launch; the cache only bridges offline gaps. */
        private const val SUBSCRIPTION_CACHE_MS = 3L * 24 * 60 * 60 * 1000
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _offers = MutableStateFlow<List<SupportOffer>>(emptyList())
    val offers: StateFlow<List<SupportOffer>> = _offers.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val client = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    private var ready: CompletableDeferred<Boolean>? = null

    private suspend fun connect(): Boolean {
        if (client.isReady) return true
        ready?.let { if (it.isActive) return it.await() }
        val d = CompletableDeferred<Boolean>().also { ready = it }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                d.complete(result.responseCode == BillingClient.BillingResponseCode.OK)
            }
            override fun onBillingServiceDisconnected() { if (d.isActive) d.complete(false) }
        })
        return d.await()
    }

    /** Loads prices and re-checks what the user owns. Safe to call at every launch. */
    fun refresh() = scope.launch {
        // Play may be missing (emulators, sideloads, tests); the app must work without it.
        runCatching {
            if (!connect()) return@launch
            loadOffers()
            restore()
        }
    }

    private suspend fun loadOffers() {
        val subs = query(SUBSCRIPTION_ID, BillingClient.ProductType.SUBS)
        val life = query(LIFETIME_ID, BillingClient.ProductType.INAPP)
        val list = mutableListOf<SupportOffer>()
        subs?.subscriptionOfferDetails.orEmpty().forEach { o ->
            val plan = when (o.basePlanId) { MONTHLY_PLAN -> Plan.MONTHLY; YEARLY_PLAN -> Plan.YEARLY; else -> null }
            // Base plan only (no offerId): one row per plan, the plain recurring price.
            if (plan != null && o.offerId == null) {
                val price = o.pricingPhases.pricingPhaseList.lastOrNull()?.formattedPrice ?: return@forEach
                list += SupportOffer(plan, price, subs!!, o.offerToken)
            }
        }
        life?.oneTimePurchaseOfferDetails?.let { list += SupportOffer(Plan.LIFETIME, it.formattedPrice, life, it.offerToken) }
        _offers.value = list.sortedBy { it.plan.ordinal }
    }

    private suspend fun query(id: String, type: String): ProductDetails? {
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(
            QueryProductDetailsParams.Product.newBuilder().setProductId(id).setProductType(type).build()
        )).build()
        val r = client.queryProductDetails(params)
        return r.productDetailsList?.firstOrNull()
    }

    fun buy(activity: Activity, offer: SupportOffer) {
        val pd = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(offer.details)
        offer.offerToken?.let { pd.setOfferToken(it) }
        val r = client.launchBillingFlow(activity, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(pd.build())).build())
        if (r.responseCode != BillingClient.BillingResponseCode.OK) _message.value = "تعذر فتح Google Play. حاول لاحقًا."
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> scope.launch { purchases.orEmpty().forEach { handle(it) } }
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refresh()
            else -> _message.value = "لم تكتمل العملية. لم يُخصم أي مبلغ."
        }
    }

    /** Re-reads owned purchases (also the "restore" button). */
    suspend fun restore() {
        if (!connect()) return
        for (type in listOf(BillingClient.ProductType.SUBS, BillingClient.ProductType.INAPP)) {
            val r = client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(type).build())
            if (r.billingResult.responseCode == BillingClient.BillingResponseCode.OK) r.purchasesList.forEach { handle(it) }
        }
    }

    private suspend fun handle(p: Purchase) {
        when (p.purchaseState) {
            Purchase.PurchaseState.PENDING -> { _message.value = "الدفع قيد المعالجة. سيُفعَّل الدعم فور تأكيده."; return }
            Purchase.PurchaseState.PURCHASED -> Unit
            else -> return
        }
        if (!p.isAcknowledged) {
            client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build())
        }
        val current = prefs.prefs.first()
        if (LIFETIME_ID in p.products) {
            prefs.setEntitlement(current.supporterUntilMs, lifetime = true)
        } else if (SUBSCRIPTION_ID in p.products) {
            val until = maxOf(current.supporterUntilMs, System.currentTimeMillis() + SUBSCRIPTION_CACHE_MS)
            prefs.setEntitlement(until, current.lifetimeSupporter)
        }
        _message.value = "شكرًا لدعمك! كل المزايا مفعّلة."
    }

    fun clearMessage() { _message.value = null }
}
