/*
 *    Copyright 2020 Criteo
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.criteo.mediation.google

import android.app.Application
import android.content.Context
import android.util.Log
import com.criteo.mediation.google.advancednative.CriteoNativeEventLoader
import com.criteo.publisher.Criteo
import com.criteo.publisher.CriteoInitException
import com.criteo.publisher.model.AdUnit
import com.criteo.publisher.model.BannerAdUnit
import com.criteo.publisher.model.InterstitialAdUnit
import com.criteo.publisher.model.NativeAdUnit
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.mediation.Adapter
import com.google.android.gms.ads.mediation.InitializationCompleteCallback
import com.google.android.gms.ads.mediation.MediationAdConfiguration
import com.google.android.gms.ads.mediation.MediationAdLoadCallback
import com.google.android.gms.ads.mediation.MediationBannerAd
import com.google.android.gms.ads.mediation.MediationBannerAdCallback
import com.google.android.gms.ads.mediation.MediationBannerAdConfiguration
import com.google.android.gms.ads.mediation.MediationConfiguration
import com.google.android.gms.ads.mediation.MediationInterstitialAd
import com.google.android.gms.ads.mediation.MediationInterstitialAdCallback
import com.google.android.gms.ads.mediation.MediationInterstitialAdConfiguration
import com.google.android.gms.ads.mediation.MediationNativeAdCallback
import com.google.android.gms.ads.mediation.MediationNativeAdConfiguration
import com.google.android.gms.ads.mediation.UnifiedNativeAdMapper
import com.google.android.gms.ads.mediation.VersionInfo
import org.json.JSONException
import org.json.JSONObject

class CriteoAdapter : Adapter() {

    private lateinit var bannerAdUnit: BannerAdUnit
    private lateinit var interstitialAdUnit: InterstitialAdUnit
    private lateinit var nativeAdUnit: NativeAdUnit

    private lateinit var bannerEventLoader: CriteoBannerEventLoader
    private lateinit var interstitialEventLoader: CriteoInterstitialEventLoader
    private lateinit var nativeEventLoader: CriteoNativeEventLoader

    private enum class FormatType {
        BANNER, INTERSTITIAL, NATIVE
    }

    override fun loadBannerAd(
        configuration: MediationBannerAdConfiguration,
        callback: MediationAdLoadCallback<MediationBannerAd, MediationBannerAdCallback>
    ) {
        if (initialize(
                configuration,
                FormatType.BANNER,
                callback,
                configuration.taggedForChildDirectedTreatment().toCriteoChildDirectedTreatmentFlag()
            )
        ) {
            bannerEventLoader = CriteoBannerEventLoader(configuration, callback, bannerAdUnit)
            bannerEventLoader.loadAd()
        }
    }

    override fun loadInterstitialAd(
        configuration: MediationInterstitialAdConfiguration,
        callback: MediationAdLoadCallback<MediationInterstitialAd, MediationInterstitialAdCallback>
    ) {
        if (initialize(
                configuration,
                FormatType.INTERSTITIAL,
                callback,
                configuration.taggedForChildDirectedTreatment().toCriteoChildDirectedTreatmentFlag()
            )
        ) {
            interstitialEventLoader = CriteoInterstitialEventLoader(callback, interstitialAdUnit)
            interstitialEventLoader.loadAd()
        }
    }

    override fun loadNativeAd(
        configuration: MediationNativeAdConfiguration,
        callback: MediationAdLoadCallback<UnifiedNativeAdMapper, MediationNativeAdCallback>
    ) {
        if (initialize(
                configuration,
                FormatType.NATIVE,
                callback,
                configuration.taggedForChildDirectedTreatment().toCriteoChildDirectedTreatmentFlag()
            )
        ) {
            nativeEventLoader = CriteoNativeEventLoader(configuration, callback, nativeAdUnit)
            nativeEventLoader.loadAd()
        }
    }

    override fun initialize(
        context: Context,
        initializationCompleteCallback: InitializationCompleteCallback,
        list: MutableList<MediationConfiguration>
    ) {
        initializationCompleteCallback.onInitializationSucceeded()
    }

    override fun getVersionInfo(): VersionInfo {
        val version = VersionProvider.getMediationAdapterVersionName()
        val splits = version.split('.')
        if (splits.size >= 4) {
            return try {
                val major = splits[0].toInt()
                val minor = splits[1].toInt()
                val micro = splits[2].toInt() * 100 + splits[3].toInt()
                VersionInfo(major, minor, micro)
            } catch (ex: NumberFormatException) {
                DEFAULT_VERSION_INFO
            }
        }
        return DEFAULT_VERSION_INFO
    }

    override fun getSDKVersionInfo(): VersionInfo {
        val sdkVersion = Criteo.getVersion()
        val splits = sdkVersion.split('.')
        if (splits.size >= 3) {
            return try {
                val major = splits[0].toInt()
                val minor = splits[1].toInt()
                val micro = splits[2].toInt()
                VersionInfo(major, minor, micro)
            } catch (ex: NumberFormatException) {
                DEFAULT_VERSION_INFO
            }
        }
        return DEFAULT_VERSION_INFO
    }

    private fun initialize(
        mediationAdConfiguration: MediationAdConfiguration,
        formatType: FormatType,
        listener: MediationAdLoadCallback<*, *>,
        tagForChildDirectedTreatment: Boolean?
    ): Boolean {
        val serverParameter = mediationAdConfiguration.serverParameters.getString(
            SERVER_PARAMETER_KEY, ""
        )
        if (serverParameter.isNullOrEmpty()) {
            val error = emptyServerParameterError()
            listener.onFailure(error)
            Log.e(TAG, error.message)
            return false
        }

        val criteoPublisherId: String
        val inventoryGroupId: String?
        val adUnitId: String
        try {
            val parameters = JSONObject(serverParameter)
            criteoPublisherId = parameters.getString(CRITEO_PUBLISHER_ID)
            inventoryGroupId = parameters.optString(INVENTORY_GROUP_ID) ?: null
            adUnitId = parameters.getString(AD_UNIT_ID)
        } catch (e: JSONException) {
            val error = readingServerParameterError()
            listener.onFailure(error)
            Log.e(TAG, error.message, e)
            return false
        }

        val adUnit = initAdUnit(
            formatType,
            adUnitId,
            (mediationAdConfiguration as? MediationBannerAdConfiguration)?.adSize
        )
        try {
            Criteo.getInstance().setTagForChildDirectedTreatment(tagForChildDirectedTreatment)
            return true
        } catch (ex: Exception) {
            try {
                Criteo.Builder(
                    (mediationAdConfiguration.context.applicationContext as Application),
                    criteoPublisherId
                )
                    // TODO: move AdUnit creation to separate loaders when prefetch feature is removed
                    .adUnits(listOf(adUnit))
                    .inventoryGroupId(inventoryGroupId)
                    .tagForChildDirectedTreatment(tagForChildDirectedTreatment)
                    .init()
            } catch (e: CriteoInitException) {
                val error = adapterInitializationError()
                listener.onFailure(error)
                Log.e(TAG, error.message, e)
                return false
            }
            listener.onFailure(noFillError())
            return false
        }
    }

    private fun initAdUnit(
        formatType: FormatType,
        adUnitId: String,
        size: AdSize?
    ): AdUnit {
        return when (formatType) {
            FormatType.BANNER -> {
                val adMobSize = com.criteo.publisher.model.AdSize(
                    size!!.width,
                    size.height
                )
                BannerAdUnit(adUnitId, adMobSize).also { bannerAdUnit = it }
            }
            FormatType.INTERSTITIAL -> InterstitialAdUnit(adUnitId).also {
                interstitialAdUnit = it
            }
            FormatType.NATIVE -> NativeAdUnit(adUnitId).also {
                nativeAdUnit = it
            }
        }
    }

    private fun Int.toCriteoChildDirectedTreatmentFlag(): Boolean? {
        return when (this) {
            MediationAdConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE -> true
            MediationAdConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE -> false
            else -> null
        }
    }

    companion object {
        private val TAG = CriteoAdapter::class.java.simpleName

        internal const val SERVER_PARAMETER_KEY = "parameter"

        @JvmStatic
        internal val DEFAULT_VERSION_INFO = VersionInfo(0, 0, 0)

        private const val CRITEO_PUBLISHER_ID = "cpId"
        private const val INVENTORY_GROUP_ID = "inventoryGroupId"
        private const val AD_UNIT_ID = "adUnitId"
    }

}
