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

import static com.criteo.mediation.google.CriteoAdapter.SERVER_PARAMETER_KEY;
import static com.criteo.publisher.CriteoUtil.TEST_CP_ID;
import static com.criteo.publisher.CriteoUtil.TEST_INVENTORY_GROUP_ID;
import static com.criteo.publisher.concurrent.ThreadingUtil.runOnMainThreadAndWait;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import com.criteo.publisher.model.AdSize;
import com.criteo.publisher.model.AdUnit;
import com.criteo.publisher.model.BannerAdUnit;
import com.criteo.publisher.model.InterstitialAdUnit;
import com.criteo.publisher.model.NativeAdUnit;
import com.google.android.gms.ads.mediation.MediationAdLoadCallback;
import com.google.android.gms.ads.mediation.MediationBannerAd;
import com.google.android.gms.ads.mediation.MediationBannerAdCallback;
import com.google.android.gms.ads.mediation.MediationBannerAdConfiguration;
import com.google.android.gms.ads.mediation.MediationInterstitialAd;
import com.google.android.gms.ads.mediation.MediationInterstitialAdCallback;
import com.google.android.gms.ads.mediation.MediationInterstitialAdConfiguration;
import com.google.android.gms.ads.mediation.MediationNativeAdCallback;
import com.google.android.gms.ads.mediation.MediationNativeAdConfiguration;
import com.google.android.gms.ads.mediation.UnifiedNativeAdMapper;

import org.json.JSONException;
import org.json.JSONObject;

public class AdapterHelper {

  @NonNull
  private final CriteoAdapter adapter = new CriteoAdapter();

  @NonNull
  private final Context context = ApplicationProvider.getApplicationContext();

  @NonNull
  public final MediationBannerAdConfiguration mediationBannedAdConfiguration = mock(MediationBannerAdConfiguration.class);

  @NonNull
  public final MediationNativeAdConfiguration mediationNativeAdConfiguration = mock(MediationNativeAdConfiguration.class);

  @NonNull
  public final MediationInterstitialAdConfiguration mediationInterstitialAdConfiguration = mock(MediationInterstitialAdConfiguration.class);

  public void loadBannerAd(
      @NonNull String serverParameters,
      @NonNull AdSize size,
      @NonNull MediationAdLoadCallback<MediationBannerAd, MediationBannerAdCallback> callback
  ) {
    com.google.android.gms.ads.AdSize adSize = new com.google.android.gms.ads.AdSize(
        size.getWidth(),
        size.getHeight()
    );

    when(mediationBannedAdConfiguration.getAdSize()).thenReturn(adSize);
    when(mediationBannedAdConfiguration.getServerParameters()).thenReturn(bundleServerParameters(serverParameters));
    when(mediationBannedAdConfiguration.getContext()).thenReturn(context);

    runOnMainThreadAndWait(() -> adapter.loadBannerAd(
            mediationBannedAdConfiguration,
            callback
    ));
  }

  public void loadBannerAd(
      @NonNull BannerAdUnit adUnit,
      @NonNull MediationAdLoadCallback<MediationBannerAd, MediationBannerAdCallback> callback
  ) {
    String serverParameters = getServerParameters(adUnit);
    loadBannerAd(serverParameters, adUnit.getSize(), callback);
  }

  public void loadNativeAd(
      @NonNull String serverParameters,
      @NonNull MediationAdLoadCallback<UnifiedNativeAdMapper, MediationNativeAdCallback> callback
  ) {
    when(mediationNativeAdConfiguration.getServerParameters()).thenReturn(bundleServerParameters(serverParameters));
    when(mediationNativeAdConfiguration.getContext()).thenReturn(context);

    runOnMainThreadAndWait(() -> adapter.loadNativeAd(
            mediationNativeAdConfiguration,
            callback
    ));
  }

  public void loadNativeAd(
      @NonNull NativeAdUnit adUnit,
      @NonNull MediationAdLoadCallback<UnifiedNativeAdMapper, MediationNativeAdCallback> callback
  ) {
    String serverParameters = getServerParameters(adUnit);
    loadNativeAd(serverParameters, callback);
  }

  public void loadInterstitialAd(
      @NonNull String serverParameters,
      @NonNull MediationAdLoadCallback<MediationInterstitialAd, MediationInterstitialAdCallback> callback
  ) {
    when(mediationInterstitialAdConfiguration.getServerParameters()).thenReturn(bundleServerParameters(serverParameters));
    when(mediationInterstitialAdConfiguration.getContext()).thenReturn(context);

    runOnMainThreadAndWait(() -> adapter.loadInterstitialAd(
            mediationInterstitialAdConfiguration,
            callback
    ));
  }

  public void loadInterstitialAd(
      @NonNull InterstitialAdUnit adUnit,
      @NonNull MediationAdLoadCallback<MediationInterstitialAd, MediationInterstitialAdCallback> callback
  ) {
    String serverParameters = getServerParameters(adUnit);
    loadInterstitialAd(serverParameters, callback);
  }

  private static String getServerParameters(AdUnit adUnit) {
    try {
      JSONObject serverParams = new JSONObject();
      serverParams.put("cpId", TEST_CP_ID);
      serverParams.put("inventoryGroupId", TEST_INVENTORY_GROUP_ID);
      serverParams.put("adUnitId", adUnit.getAdUnitId());
      return serverParams.toString();
    } catch (JSONException e) {
      throw new IllegalStateException(e);
    }
  }

  private Bundle bundleServerParameters(String serverParameters) {
    Bundle serverParametersBundle = new Bundle();
    serverParametersBundle.putString(SERVER_PARAMETER_KEY, serverParameters);
    return serverParametersBundle;
  }
}
