package com.popquest.bubbleshooter

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
import org.json.JSONObject

/**
 * Google Play Billing (library 8).
 * Product IDs must match the in-app products you create in Play Console (README step 4).
 */
class BillingManager(private val a: MainActivity) : PurchasesUpdatedListener {

    companion object {
        val CONSUMABLE = setOf("coins_500", "coins_1200", "coins_3500")
        val ONE_TIME = setOf("remove_ads", "starter_pack")
        val ALL = ONE_TIME + CONSUMABLE
    }

    private val client: BillingClient = BillingClient.newBuilder(a)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    private val details = mutableMapOf<String, ProductDetails>()
    private val delivered = mutableSetOf<String>() // purchase tokens already sent to the game this session

    fun connect() {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    loadProducts()
                    restore(showResult = false)
                }
            }
            override fun onBillingServiceDisconnected() { /* reconnects automatically */ }
        })
    }

    fun close() = runCatching { client.endConnection() }

    private fun loadProducts() {
        val products = ALL.map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(products).build()
        client.queryProductDetailsAsync(params) { result, productResult ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            val prices = JSONObject()
            for (d in productResult.productDetailsList) {
                details[d.productId] = d
                d.oneTimePurchaseOfferDetails?.let { prices.put(d.productId, it.formattedPrice) }
            }
            a.js("onPrices", prices.toString())
        }
    }

    fun buy(productId: String) {
        val d = details[productId]
        if (d == null) {
            a.js("onBuyError", "The store isn't ready yet. Check your internet connection and try again.")
            if (client.isReady) loadProducts()
            return
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build()))
            .build()
        client.launchBillingFlow(a, params)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases?.forEach { handle(it) }
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> restore(showResult = false)
            else -> a.js("onBuyError", "Google Play couldn't complete the purchase. Please try again.")
        }
    }

    private fun handle(p: Purchase) {
        if (p.purchaseState != Purchase.PurchaseState.PURCHASED) {
            if (p.purchaseState == Purchase.PurchaseState.PENDING) a.js("onBuyError", "Payment pending. Your item arrives once it's approved.")
            return
        }
        val firstTime = delivered.add(p.purchaseToken)
        for (id in p.products) {
            if (id in CONSUMABLE) {
                // Give coins, then consume so the pack can be bought again.
                if (firstTime) a.js("onPurchase", id)
                client.consumeAsync(ConsumeParams.newBuilder().setPurchaseToken(p.purchaseToken).build()) { _, _ -> }
            } else {
                // One-time items (Remove ads, Starter pack): the game ignores repeats.
                a.js("onPurchase", id)
                if (!p.isAcknowledged) {
                    client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()) { }
                }
            }
        }
    }

    fun refreshPurchases() { if (client.isReady) restore(showResult = false) }

    fun restore(showResult: Boolean) {
        if (!client.isReady) {
            if (showResult) a.js("onRestore", false)
            return
        }
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) purchases.forEach { handle(it) }
            if (showResult) a.js("onRestore", purchases.any { "remove_ads" in it.products })
        }
    }
}
