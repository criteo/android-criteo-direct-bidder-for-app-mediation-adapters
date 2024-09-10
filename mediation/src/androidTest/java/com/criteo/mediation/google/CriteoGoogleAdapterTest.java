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

package com.criteo.mediation.google;

import static com.criteo.publisher.CriteoUtil.TEST_CP_ID;
import static com.criteo.publisher.CriteoUtil.clearCriteo;
import static com.criteo.publisher.CriteoUtil.givenInitializedCriteo;
import static com.criteo.publisher.TestAdUnits.BANNER_320_50;
import static com.criteo.publisher.TestAdUnits.INTERSTITIAL;
import static com.criteo.publisher.TestAdUnits.NATIVE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.criteo.publisher.mock.MockedDependenciesRule;
import com.criteo.publisher.model.AdSize;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.mediation.MediationAdLoadCallback;
import com.google.android.gms.ads.mediation.MediationBannerAd;
import com.google.android.gms.ads.mediation.MediationBannerAdCallback;
import com.google.android.gms.ads.mediation.MediationInterstitialAd;
import com.google.android.gms.ads.mediation.MediationInterstitialAdCallback;
import com.google.android.gms.ads.mediation.MediationNativeAdCallback;
import com.google.android.gms.ads.mediation.UnifiedNativeAdMapper;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;


public class CriteoGoogleAdapterTest {

    @Rule
    public MockedDependenciesRule mockedDependenciesRule = new MockedDependenciesRule();

    @Mock
    private MediationAdLoadCallback<MediationInterstitialAd, MediationInterstitialAdCallback> interstitialCallback;

    @Mock
    private MediationAdLoadCallback<MediationBannerAd, MediationBannerAdCallback> bannerCallback;

    @Mock
    private MediationAdLoadCallback<UnifiedNativeAdMapper, MediationNativeAdCallback> nativeCallback;

    private AdapterHelper adapterHelper;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        adapterHelper = new AdapterHelper();

        when(interstitialCallback.onSuccess(any())).thenReturn(mock(MediationInterstitialAdCallback.class));
        when(bannerCallback.onSuccess(any())).thenReturn(mock(MediationBannerAdCallback.class));
        when(nativeCallback.onSuccess(any())).thenReturn(mock(MediationNativeAdCallback.class));
    }

    @Test
    public void requestNativeAd_GivenEmptyServerParameter_NotifyForInvalidRequest() throws Exception {
        String serverParameter = "";

        adapterHelper.loadNativeAd(serverParameter, nativeCallback);

        verify(nativeCallback).onFailure(argThat(new IsEqualToOtherAdError(AdErrorKt.emptyServerParameterError())));
    }

    @Test
    public void requestNativeAd_GivenServerParameterWithoutCpId_NotifyForError() throws Exception {
        JSONObject serverParams = new JSONObject();
        serverParams.put("adUnitId", BANNER_320_50.getAdUnitId());
        String serverParameter = serverParams.toString();

        adapterHelper.loadNativeAd(serverParameter, nativeCallback);

        verify(nativeCallback).onFailure(argThat(new IsEqualToOtherAdError(AdErrorKt.readingServerParameterError())));
    }

    @Test
    public void requestNativeAd_GivenServerParameterWithoutAdUnit_NotifyForError() throws Exception {
        JSONObject serverParams = new JSONObject();
        serverParams.put("cpId", TEST_CP_ID);
        String serverParameter = serverParams.toString();

        adapterHelper.loadNativeAd(serverParameter, nativeCallback);

        verify(nativeCallback).onFailure(argThat(new IsEqualToOtherAdError(AdErrorKt.readingServerParameterError())));
    }

    @Test
    public void requestNativeAd_GivenServerParameterWithInventoryGroupIdEqualsNull_NotifyForError() throws Exception {
        JSONObject serverParams = new JSONObject();
        serverParams.put("cpId", TEST_CP_ID);
        serverParams.put("inventoryGroupId", JSONObject.NULL);
        serverParams.put("adUnitId", BANNER_320_50.getAdUnitId());
        String serverParameter = serverParams.toString();

        adapterHelper.loadNativeAd(serverParameter, nativeCallback);

        verify(nativeCallback).onFailure(argThat(new IsEqualToOtherAdError(AdErrorKt.noFillError())));
    }

    @Test
    public void requestNativeAd_GivenServerParameterWithInventoryGroupId_NotifyForError() throws Exception {
        JSONObject serverParams = new JSONObject();
        serverParams.put("cpId", TEST_CP_ID);
        serverParams.put("inventoryGroupId", "myInventoryId");
        serverParams.put("adUnitId", BANNER_320_50.getAdUnitId());
        String serverParameter = serverParams.toString();

        adapterHelper.loadNativeAd(serverParameter, nativeCallback);

        verify(nativeCallback).onFailure(argThat(new IsEqualToOtherAdError(AdErrorKt.noFillError())));
    }

    @Test
    public void requestBannerAd_GivenEmptyServerParameter_NotifyForInvalidRequest() throws Exception {
        String serverParameter = "";

        adapterHelper.loadBannerAd(serverParameter, new AdSize(320, 50), bannerCallback);

        verify(bannerCallback).onFailure(argThat(new IsEqualToOtherAdError(AdErrorKt.emptyServerParameterError())));
    }

    @Test
    public void requestBannerAd_GivenServerParameterWithoutCpId_NotifyForError() throws Exception {
        JSONObject serverParams = new JSONObject();
        serverParams.put("adUnitId", BANNER_320_50.getAdUnitId());
        String serverParameter = serverParams.toString();

        adapterHelper.loadBannerAd(serverParameter, new AdSize(320, 50), bannerCallback);

        verify(bannerCallback).onFailure(argThat(new IsEqualToOtherAdError(AdErrorKt.readingServerParameterError())));
    }

    @Test
    public void requestBannerAd_GivenServerParameterWithoutAdUnit_NotifyForError() throws Exception {
        JSONObject serverParams = new JSONObject();
        serverParams.put("cpId", TEST_CP_ID);
        String serverParameter = serverParams.toString();

        adapterHelper.loadBannerAd(serverParameter, new AdSize(320, 50), bannerCallback);

        verify(bannerCallback).onFailure(argThat(new IsEqualToOtherAdError(AdErrorKt.readingServerParameterError())));
    }

    @Test
    public void requestBannerAd_GivenServerParameterWithInventoryGroupIdEqualsNull_NotifyForError() throws Exception {
        JSONObject serverParams = new JSONObject();
        serverParams.put("cpId", TEST_CP_ID);
        serverParams.put("inventoryGroupId", JSONObject.NULL);
        serverParams.put("adUnitId", BANNER_320_50.getAdUnitId());
        String serverParameter = serverParams.toString();

        adapterHelper.loadBannerAd(serverParameter, new AdSize(320, 50), bannerCallback);

        verify(bannerCallback).onFailure(argThat(new IsEqualToOtherAdError(AdErrorKt.noFillError())));
    }

    @Test
    public void requestBannerAd_GivenServerParameterWithInventoryGroupId_NotifyForError() throws Exception {
        JSONObject serverParams = new JSONObject();
        serverParams.put("cpId", TEST_CP_ID);
        serverParams.put("inventoryGroupId", "myInventoryId");
        serverParams.put("adUnitId", BANNER_320_50.getAdUnitId());
        String serverParameter = serverParams.toString();

        adapterHelper.loadBannerAd(serverParameter, new AdSize(320, 50), bannerCallback);

        verify(bannerCallback).onFailure(argThat(new IsEqualToOtherAdError(AdErrorKt.noFillError())));
    }

    @Test
    public void requestInterstitialAd_GivenEmptyServerParameter_NotifyForInvalidRequest() throws Exception {
        String serverParameter = "";

        adapterHelper.loadInterstitialAd(serverParameter, interstitialCallback);

        verify(interstitialCallback).onFailure(argThat(new IsEqualToOtherAdError(AdErrorKt.emptyServerParameterError())));
    }

    @Test
    public void requestInterstitialAd_GivenServerParameterWithoutCpId_NotifyForError() throws Exception {
        JSONObject serverParams = new JSONObject();
        serverParams.put("adUnitId", BANNER_320_50.getAdUnitId());
        String serverParameter = serverParams.toString();

        adapterHelper.loadInterstitialAd(serverParameter, interstitialCallback);

        verify(interstitialCallback).onFailure(argThat(new IsEqualToOtherAdError(AdErrorKt.readingServerParameterError())));
    }

    @Test
    public void requestInterstitialAd_GivenServerParameterWithoutAdUnit_NotifyForError() throws Exception {
        JSONObject serverParams = new JSONObject();
        serverParams.put("cpId", TEST_CP_ID);
        String serverParameter = serverParams.toString();

        adapterHelper.loadInterstitialAd(serverParameter, interstitialCallback);

        verify(interstitialCallback).onFailure(argThat(new IsEqualToOtherAdError(AdErrorKt.readingServerParameterError())));
    }

    @Test
    public void requestInterstitialAd_GivenServerParameterWithInventoryGroupIdEqualsNull_NotifyForError() throws Exception {
        JSONObject serverParams = new JSONObject();
        serverParams.put("cpId", TEST_CP_ID);
        serverParams.put("inventoryGroupId", JSONObject.NULL);
        serverParams.put("adUnitId", BANNER_320_50.getAdUnitId());
        String serverParameter = serverParams.toString();

        adapterHelper.loadInterstitialAd(serverParameter, interstitialCallback);

        verify(interstitialCallback).onFailure(argThat(new IsEqualToOtherAdError(AdErrorKt.noFillError())));
    }

    @Test
    public void requestInterstitialAd_GivenServerParameterWithInventoryGroupId_NotifyForError() throws Exception {
        JSONObject serverParams = new JSONObject();
        serverParams.put("cpId", TEST_CP_ID);
        serverParams.put("inventoryGroupId", "myInventoryId");
        serverParams.put("adUnitId", BANNER_320_50.getAdUnitId());
        String serverParameter = serverParams.toString();

        adapterHelper.loadInterstitialAd(serverParameter, interstitialCallback);

        verify(interstitialCallback).onFailure(argThat(new IsEqualToOtherAdError(AdErrorKt.noFillError())));
    }

    @Test
    public void givenNotInitializedCriteo_WhenLoadingNativeTwice_MissFirstOpportunityBecauseOfBidCachingAndSucceedOnNextOne()
        throws Exception {
        clearCriteo();

        loadValidNative();
        loadValidNative();

        checkMissFirstNativeOpportunityBecauseOfBidCachingAndSucceedOnNextOne();
    }

    @Test
    public void givenInitializedCriteo_WhenLoadingNativeTwice_MissFirstOpportunityBecauseOfBidCachingAndSucceedOnNextOne()
        throws Exception {
        givenInitializedCriteo();

        loadValidNative();
        loadValidNative();

        checkMissFirstNativeOpportunityBecauseOfBidCachingAndSucceedOnNextOne();
    }

    @Test
    public void givenNotInitializedCriteo_WhenLoadingBannerTwice_MissFirstOpportunityBecauseOfBidCachingAndSucceedOnNextOne()
            throws Exception {
        clearCriteo();

        loadValidBanner();
        loadValidBanner();

        checkMissFirstBannerOpportunityBecauseOfBidCachingAndSucceedOnNextOne();
    }

    @Test
    public void givenInitializedCriteo_WhenLoadingBannerTwice_MissFirstOpportunityBecauseOfBidCachingAndSucceedOnNextOne()
            throws Exception {
        givenInitializedCriteo();

        loadValidBanner();
        loadValidBanner();

        checkMissFirstBannerOpportunityBecauseOfBidCachingAndSucceedOnNextOne();
    }

    @Test
    public void givenNotInitializedCriteo_WhenLoadingInterstitialTwice_MissFirstOpportunityBecauseOfBidCachingAndSucceedOnNextOne()
          throws Exception {
        clearCriteo();

        loadValidInterstitial();
        loadValidInterstitial();

        checkMissFirstInterstitialOpportunityBecauseOfBidCachingAndSucceedOnNextOne();
    }

    @Test
    public void givenInitializedCriteo_WhenLoadingInterstitialTwice_MissFirstOpportunityBecauseOfBidCachingAndSucceedOnNextOne()
            throws Exception {
        givenInitializedCriteo();

        loadValidInterstitial();
        loadValidInterstitial();

        checkMissFirstInterstitialOpportunityBecauseOfBidCachingAndSucceedOnNextOne();
    }

    private void loadValidNative() {
        adapterHelper.loadNativeAd(NATIVE, nativeCallback);
        mockedDependenciesRule.waitForIdleState();
    }

    private void loadValidBanner() {
        adapterHelper.loadBannerAd(BANNER_320_50, bannerCallback);
        mockedDependenciesRule.waitForIdleState();
    }

    private void loadValidInterstitial() {
        adapterHelper.loadInterstitialAd(INTERSTITIAL, interstitialCallback);
        mockedDependenciesRule.waitForIdleState();
    }

    private void checkMissFirstNativeOpportunityBecauseOfBidCachingAndSucceedOnNextOne() {
        InOrder inOrder = inOrder(nativeCallback);
        inOrder.verify(nativeCallback).onFailure(argThat(new IsEqualToOtherAdError(AdErrorKt.noFillError())));
        inOrder.verify(nativeCallback).onSuccess(any(UnifiedNativeAdMapper.class));
        inOrder.verifyNoMoreInteractions();
    }

    private void checkMissFirstBannerOpportunityBecauseOfBidCachingAndSucceedOnNextOne() {
        InOrder inOrder = inOrder(bannerCallback);
        inOrder.verify(bannerCallback).onFailure(argThat(new IsEqualToOtherAdError(AdErrorKt.noFillError())));
        inOrder.verify(bannerCallback).onSuccess(any(MediationBannerAd.class));
        inOrder.verifyNoMoreInteractions();
    }

    private void checkMissFirstInterstitialOpportunityBecauseOfBidCachingAndSucceedOnNextOne() {
        InOrder inOrder = inOrder(interstitialCallback);
        inOrder.verify(interstitialCallback).onFailure(argThat(new IsEqualToOtherAdError(AdErrorKt.noFillError())));
        inOrder.verify(interstitialCallback).onSuccess(any(MediationInterstitialAd.class));
        inOrder.verifyNoMoreInteractions();
    }
}
