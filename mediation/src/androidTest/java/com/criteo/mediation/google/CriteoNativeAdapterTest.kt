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

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Drawable
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.rule.ActivityTestRule
import com.criteo.mediation.google.activity.DummyActivity
import com.criteo.mediation.google.advancednative.CriteoNativeEventLoader;
import com.criteo.publisher.BidListener
import com.criteo.publisher.BidManager
import com.criteo.publisher.CriteoUtil.givenInitializedCriteo
import com.criteo.publisher.StubConstants
import com.criteo.publisher.TestAdUnits
import com.criteo.publisher.advancednative.CriteoMediaView
import com.criteo.publisher.advancednative.NativeInternalForAdMob
import com.criteo.publisher.adview.Redirection
import com.criteo.publisher.concurrent.ThreadingUtil.runOnMainThreadAndWait
import com.criteo.publisher.context.ContextData
import com.criteo.publisher.mock.MockBean
import com.criteo.publisher.mock.MockedDependenciesRule
import com.criteo.publisher.mock.SpyBean
import com.criteo.publisher.model.AdUnit
import com.criteo.publisher.model.CdbResponseSlot
import com.criteo.publisher.model.nativeads.NativeAssets
import com.criteo.publisher.network.PubSdkApi
import com.google.android.gms.ads.*
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.mockito.AdditionalAnswers.delegatesTo
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever
import org.mockito.kotlin.eq
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.CountDownLatch
import javax.inject.Inject


class CriteoNativeAdapterTest {

  private companion object {
    const val ADMOB_AD_UNIT_ID = "ca-app-pub-8459323526901202/2863808899"

    val TITLE_TAG = Any()
    val DESCRIPTION_TAG = Any()
    val PRICE_TAG = Any()
    val CALL_TO_ACTION_TAG = Any()
    val ADVERTISER_DOMAIN_TAG = Any()
    val ADVERTISER_DESCRIPTION_TAG = Any()
    val ADVERTISER_LOGO_TAG = Any()
  }

  @Rule
  @JvmField
  val mockedDependenciesRule = MockedDependenciesRule()

  @Rule
  @JvmField
  var testRule: ActivityTestRule<DummyActivity> = ActivityTestRule(DummyActivity::class.java)

  @Inject
  private lateinit var context: Context

  @SpyBean
  private lateinit var bidManager: BidManager

  @MockBean
  private lateinit var redirection: Redirection

  @SpyBean
  private lateinit var api: PubSdkApi

  @Mock
  private lateinit var adListener: AdListener

  @Before
  fun setUp() {
    MockitoAnnotations.openMocks(this)

    // Google requires hashed(MD5) DEVICE_ID
    val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
      .toMD5()
      .toUpperCase(Locale.ROOT)
    MobileAds.initialize(context)
    MobileAds.setRequestConfiguration(RequestConfiguration.Builder()
        .setTestDeviceIds(listOf(deviceId))
        .build())
  }

  // @todo: enable when update google mobile ads sdk
  @Ignore @Test
  fun loadNativeAd_GivenValidBid_RenderAllNativePayload() {
    val expectedAssets = StubConstants.STUB_NATIVE_ASSETS
    val expectedProduct = expectedAssets.product
    val adUnit = TestAdUnits.NATIVE
    lateinit var adView: NativeAdView
    val adViewIsRendered = CountDownLatch(1)

    // Given
    givenInitializedCriteo(adUnit)
    mockedDependenciesRule.waitForIdleState()
    givenAdUnitReplacedBy(adUnit)

    // When
    val adLoader = AdLoader.Builder(context, ADMOB_AD_UNIT_ID).forNativeAd {
      adView = NativeAdView(context)

      val layout = LinearLayout(context)
      layout.orientation = LinearLayout.VERTICAL
      layout.addView(createTextView(context, TITLE_TAG, it.headline))
      layout.addView(createTextView(context, DESCRIPTION_TAG, it.body))
      layout.addView(createTextView(context, PRICE_TAG, it.price))
      layout.addView(createTextView(context, CALL_TO_ACTION_TAG, it.callToAction))
      layout.addView(createTextView(context, ADVERTISER_DOMAIN_TAG, it.extras["crtn_advdomain"] as String?))
      layout.addView(createTextView(context, ADVERTISER_DESCRIPTION_TAG, it.advertiser))
      layout.addView(adView.createMediaView(context, it.mediaContent))
      layout.addView(createImageView(context, ADVERTISER_LOGO_TAG, it.icon?.drawable))

      adView.addView(layout)
      adView.setNativeAd(it)

      adViewIsRendered.countDown()
    }.withAdListener(adListener).build()

    adLoader.loadAd(AdRequest.Builder().build())

    adViewIsRendered.await()
    mockedDependenciesRule.waitForIdleState()

    // Then
    assertThat(adView.findTextWithTag(TITLE_TAG)).isEqualTo(expectedProduct.title)
    assertThat(adView.findTextWithTag(DESCRIPTION_TAG)).isEqualTo(expectedProduct.description)
    assertThat(adView.findTextWithTag(PRICE_TAG)).isEqualTo(expectedProduct.price)
    assertThat(adView.findTextWithTag(CALL_TO_ACTION_TAG)).isEqualTo(expectedProduct.callToAction)
    assertThat(adView.findTextWithTag(ADVERTISER_DOMAIN_TAG)).isEqualTo(expectedAssets.advertiserDomain)
    assertThat(adView.findTextWithTag(ADVERTISER_DESCRIPTION_TAG)).isEqualTo(expectedAssets.advertiserDescription)

    // Impression
    adView.assertDisplayTriggerImpressionPixels(expectedAssets.impressionPixels)

    // AdChoice
    val adChoiceView = adView.findViewWithTag<ImageView>(CriteoNativeEventLoader.AD_CHOICE_TAG)
    assertThat(adChoiceView).isNotNull
    assertThat(adChoiceView.drawable).isNotNull

    // Click
    adView.assertClickRedirectTo(expectedProduct.clickUrl, true)
    adChoiceView.assertClickRedirectTo(expectedAssets.privacyOptOutClickUrl, false)

    // Images
    assertThat(adView.mediaView?.findDrawable()).isNotNull
    assertThat(adView.findDrawableWithTag(ADVERTISER_LOGO_TAG)).isNotNull.satisfies {
      assertThat(it?.intrinsicWidth).isGreaterThan(0)
      assertThat(it?.intrinsicHeight).isGreaterThan(0)
    }
  }

  // @todo: enable when update google mobile ads sdk
  @Ignore @Test
  fun loadNativeAd_GivenInvalidBid_NotifyAdMobForFailure() {
    val adUnit = TestAdUnits.NATIVE_UNKNOWN
    lateinit var nativeAd: NativeAd
    val adIsReceived = CountDownLatch(1)

    // Given
    givenInitializedCriteo(adUnit)
    mockedDependenciesRule.waitForIdleState()
    givenAdUnitReplacedBy(adUnit)

    // When
    val adLoader = AdLoader.Builder(context, ADMOB_AD_UNIT_ID).forNativeAd {
      nativeAd = it
      adIsReceived.countDown()
    }.build()

    adLoader.loadAd(AdRequest.Builder().build())
    adIsReceived.await()
    mockedDependenciesRule.waitForIdleState()

    // In the waterfall, there is first the Criteo adapter, then the AdMob one (which is imposed).
    // Verifying that the response come from the AdMob adapter implies that the Criteo one did
    // notify AdMob framework for the no bid.
    assertThat(nativeAd.responseInfo?.mediationAdapterClassName)
        .isEqualTo("com.google.ads.mediation.admob.AdMobAdapter")
  }

  private fun givenAdUnitReplacedBy(adUnit: AdUnit) {
    // The AdMob account is setup to use a prod AdUnit (for Bugfest). For automated tests, we
    // replace it by a test AdUnit.

    doAnswer {
      val context: ContextData = it.getArgument(1)
      val listener: BidListener = it.getArgument(2)
      val realBidManager = mockingDetails(it.mock).mockCreationSettings.spiedInstance as BidManager
      realBidManager.getBidForAdUnit(adUnit, context, object : BidListener {
        override fun onBidResponse(cdbResponseSlot: CdbResponseSlot) {
          listener.onBidResponse(cdbResponseSlot.updateAdvertiserLogoWithSupportedImage())
        }

        override fun onNoBid() {
          listener.onNoBid()
        }
      })
    }.whenever(bidManager).getBidForAdUnit(any(), any(), any())
  }

  private fun CdbResponseSlot.updateAdvertiserLogoWithSupportedImage(): CdbResponseSlot {
    // The advertiser logo returned by the stub of CDB is an SVG, which is not supported.
    // To test that the adapter works correctly for the logo, we need to swap it with a supported
    // image. Such as the product one.
    val nativeAssets = mock<NativeAssets>(defaultAnswer = delegatesTo(nativeAssets!!))
    whenever(nativeAssets.advertiserLogoUrl).doReturn(this.nativeAssets!!.product.imageUrl)
    val spiedSlot = spy(this)
    whenever(spiedSlot.nativeAssets).doReturn(nativeAssets)
    return spiedSlot
  }

  private fun createTextView(context: Context, tag: Any, text: String?): TextView {
    val view = TextView(context)
    view.tag = tag
    view.text = text
    return view
  }

  private fun NativeAdView.createMediaView(context: Context, mediaContent: MediaContent?): MediaView {
    val view = MediaView(context)
    mediaView = view
    mediaContent?.let {
      view.setMediaContent(it)
    }
    return view
  }

  private fun createImageView(context: Context, tag: Any, drawable: Drawable?): ImageView {
    val view = ImageView(context)
    view.tag = tag
    view.setImageDrawable(drawable)
    return view
  }

  private fun View.findTextWithTag(tag: Any): CharSequence {
    return findViewWithTag<TextView>(tag).text
  }

  private fun View.findDrawableWithTag(tag: Any): Drawable? {
    return findViewWithTag<ImageView>(tag).drawable
  }

  private fun MediaView.findDrawable(): Drawable? {
    return NativeInternalForAdMob.getImageView(getChildAt(0) as CriteoMediaView).drawable
  }

  private fun View.assertClickRedirectTo(
      expectedRedirectionUri: URI,
      notifyAdMobListener: Boolean
  ) {
    clearInvocations(redirection)
    clearInvocations(adListener)

    runOnMainThreadAndWait {
      performClick()
    }

    mockedDependenciesRule.waitForIdleState()

    var expectedComponentName: ComponentName? = null
    testRule.runOnUiThread {
      expectedComponentName = testRule.activity.componentName
    }

    verify(redirection).redirect(
        eq(expectedRedirectionUri.toString()),
        eq(expectedComponentName),
        any()
    )

    val quantifier = if (notifyAdMobListener) times(1) else never()
    verify(adListener, quantifier).onAdClicked()
  }

  private fun View.assertDisplayTriggerImpressionPixels(expectedPixels: List<URL>) {
    clearInvocations(adListener)

    testRule.runOnUiThread {
      testRule.activity.setContentView(this)
    }
    mockedDependenciesRule.waitForIdleState()

    verify(adListener).onAdImpression()

    expectedPixels.forEach {
      verify(api).executeRawGet(it)
    }
  }

  private fun String.toMD5(): String {
    return trim().run {
      MessageDigest.getInstance("MD5")
        .digest(toByteArray())
        .joinToString("") {
          "%02x".format(it)
        }
    }
  }
}
