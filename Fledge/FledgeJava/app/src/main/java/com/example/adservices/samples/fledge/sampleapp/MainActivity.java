/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.adservices.samples.fledge.sampleapp;

import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import com.example.adservices.samples.fledge.sampleapp.databinding.ActivityMainBinding;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Android application activity for testing FLEDGE API
 */
@RequiresApi(api = 34)
public class MainActivity extends AppCompatActivity {

    // Log tag
    public static final String TAG = "FledgeSample";

    // The sample buyer and seller for the custom audiences
    // private static final AdTechIdentifier BUYER = AdTechIdentifier.fromString("samplebuyer.com");
    // private static final AdTechIdentifier SELLER = AdTechIdentifier.fromString("sampleseller.com");

    // Set override URIs
    private static final String BIDDING_LOGIC_OVERRIDE_URI = "https://sample-abcbuyer.com/bidding";
    private static final String SCORING_LOGIC_OVERRIDE_URI = "https://sample-abcseller.com/scoring/js";
    private static final String TRUSTED_SCORING_OVERRIDE_URI = "https://sample-abcseller.com/scoring/trusted";
    private static final String REPORTING_OVERRIDE_URI = "example.com";

    // JSON string objects that will be used during ad selection
    private static final AdSelectionSignals TRUSTED_SCORING_SIGNALS = AdSelectionSignals.fromString(
        "{\n"
            + "\t\"render_uri_1\": \"signals_for_1\",\n"
            + "\t\"render_uri_2\": \"signals_for_2\"\n"
            + "}");
    private static final AdSelectionSignals TRUSTED_BIDDING_SIGNALS = AdSelectionSignals.fromString(
        "{\n"
            + "\t\"example\": \"example\",\n"
            + "\t\"valid\": \"Also valid\",\n"
            + "\t\"list\": \"list\",\n"
            + "\t\"of\": \"of\",\n"
            + "\t\"keys\": \"trusted bidding signal Values\"\n"
            + "}");

    // JS files
    private static final String BIDDING_LOGIC_FILE = "BiddingLogic.js";
    private static final String DECISION_LOGIC_FILE = "DecisionLogic.js";

    // The names for the shirts and shoes custom audience
    private static final String SHOES_NAME = "shoes";
    private static final String SHIRTS_NAME = "shirts";

    // Shirts and shoes render URIS
    private static final Uri SHOES_RENDER_URI = Uri.parse("shoes-uri.shoestld");
    private static final Uri SHIRTS_RENDER_URI = Uri.parse("shirts-uri.shirtstld");

    // Executor to be used for API calls
    private static final Executor EXECUTOR = Executors.newCachedThreadPool();

    // Strings to inform user a field in missing
    private static final String MISSING_FIELD_STRING_FORMAT_RESTART_APP = "ERROR: %s is missing, " +
        "restart the activity using the directions in the README. The app will not be usable " +
        "until this is done.";

    private static final String MISSING_FIELD_STRING_FORMAT_USE_OVERRIDES = "ERROR: %s is missing, " +
        "restart the activity using the directions in the README. You may still use the dev overrides "
        + "without restarting.";

    private Uri mBiddingLogicUri;
    private Uri mScoringLogicUri;
    private Uri mTrustedDataUri;
    private AdTechIdentifier mBuyer;
    private AdTechIdentifier mSeller;

    /**
     * Does the initial setup for the app. This includes reading the Javascript server URIs from the
     * start intent, creating the ad selection and custom audience wrappers to wrap the APIs, and
     * tying the UI elements to the wrappers so that button clicks trigger the appropriate methods.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Context context = getApplicationContext();
        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        View view = binding.getRoot();
        setContentView(view);
        EventLogManager eventLog = new EventLogManager(binding.eventLog);

        try {
            // Set override URIS since overrides are on by default
            mBiddingLogicUri = Uri.parse(BIDDING_LOGIC_OVERRIDE_URI);
            mScoringLogicUri = Uri.parse(SCORING_LOGIC_OVERRIDE_URI);
            mTrustedDataUri = Uri.parse(TRUSTED_SCORING_OVERRIDE_URI);
            mBuyer = resolveAdTechIdentifier(mBiddingLogicUri);
            mSeller = resolveAdTechIdentifier(mScoringLogicUri);

            // Get override reporting URI
            String reportingUri = getIntentOrDefault("reportingUrl", REPORTING_OVERRIDE_URI);

            // Replace override URIs in JS
            String overrideDecisionJS = replaceReportingURI(assetFileToString(DECISION_LOGIC_FILE), reportingUri);
            String overrideBiddingJs = replaceReportingURI(assetFileToString(BIDDING_LOGIC_FILE), reportingUri);

            // Set up ad selection
            AdSelectionWrapper adWrapper = new AdSelectionWrapper(
                Collections.singletonList(mBuyer), mSeller, mScoringLogicUri, mTrustedDataUri, context, EXECUTOR);
            binding.runAdsButton.setOnClickListener(v ->
                adWrapper.runAdSelection(eventLog::writeEvent, binding.adSpace::setText));

            // Set up Custom Audience Wrapper(CAs)
            CustomAudienceWrapper caWrapper = new CustomAudienceWrapper(context, EXECUTOR);

            // Set up CA buttons
            setupJoinCAButtons(caWrapper, eventLog, binding, mBiddingLogicUri, context);
            setupLeaveCAButtons(caWrapper, eventLog, binding, mBiddingLogicUri, context);

            // Set up remote overrides by default
            useOverrides(eventLog,adWrapper, caWrapper, overrideDecisionJS, overrideBiddingJs,TRUSTED_SCORING_SIGNALS, TRUSTED_BIDDING_SIGNALS, mBiddingLogicUri, context);

            // Set up Override Switch
            binding.overrideSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (isChecked) {
                        useOverrides(eventLog, adWrapper, caWrapper, overrideDecisionJS, overrideBiddingJs, TRUSTED_SCORING_SIGNALS, TRUSTED_BIDDING_SIGNALS, mBiddingLogicUri, context);
                    } else {
                        try {
                            mBiddingLogicUri = Uri.parse(getIntentOrError("biddingUrl", eventLog, MISSING_FIELD_STRING_FORMAT_USE_OVERRIDES));
                            mScoringLogicUri = Uri.parse(getIntentOrError("scoringUrl", eventLog, MISSING_FIELD_STRING_FORMAT_USE_OVERRIDES));
                            mTrustedDataUri = Uri.parse(getIntentOrDefault("trustedScoringUrl", mBiddingLogicUri + "/trusted"));
                            mBuyer = resolveAdTechIdentifier(mBiddingLogicUri);
                            mSeller = resolveAdTechIdentifier(mScoringLogicUri);

                            // Set with new scoring uri
                            adWrapper.resetAdSelectionConfig(Collections.singletonList(mBuyer), mSeller, mScoringLogicUri, mTrustedDataUri);

                            // // Leave CAs
                            // caWrapper.leaveCa(SHOES_NAME, eventLog::writeEvent);
                            // caWrapper.leaveCa(SHIRTS_NAME, eventLog::writeEvent);

                            // Reset join custom audience buttons as they rely on different biddingLogicUri
                            setupJoinCAButtons(caWrapper, eventLog, binding, mBiddingLogicUri, context);
                            setupLeaveCAButtons(caWrapper, eventLog, binding, mBiddingLogicUri, context);

                            resetOverrides(eventLog, adWrapper, caWrapper);
                        } catch (Exception e) {
                            binding.overrideSwitch.setChecked(true);
                            Log.e(TAG, "Error getting mock server uris", e);
                        }
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error when setting up app", e);
        }
    }

    private void setupJoinCAButtons(CustomAudienceWrapper caWrapper, EventLogManager eventLog, ActivityMainBinding binding, Uri biddingUri, Context context) {
        binding.joinShoesButton.setOnClickListener(v ->
            caWrapper.joinCa(SHOES_NAME, context.getPackageName(), AdTechIdentifier.fromString(biddingUri.getHost()), biddingUri, Uri.parse(biddingUri + "/render"), Uri.parse(biddingUri + "/daily"), Uri.parse(biddingUri + "/trusted"),
                eventLog::writeEvent));
        binding.joinShirtsButton.setOnClickListener(v ->
            caWrapper.joinCa(SHIRTS_NAME, context.getPackageName(), AdTechIdentifier.fromString(biddingUri.getHost()), biddingUri, Uri.parse(biddingUri + "/render"), Uri.parse(biddingUri + "/daily"), Uri.parse(biddingUri + "/trusted"),
                eventLog::writeEvent));
    }

    private void setupLeaveCAButtons(CustomAudienceWrapper caWrapper, EventLogManager eventLog, ActivityMainBinding binding, Uri biddingUri, Context context) {
        binding.leaveShoesButton.setOnClickListener(v ->
            caWrapper.leaveCa(SHOES_NAME, context.getPackageName(), AdTechIdentifier.fromString(biddingUri.getHost()), eventLog::writeEvent));
        binding.leaveShirtsButton.setOnClickListener(v ->
            caWrapper.leaveCa(SHIRTS_NAME, context.getPackageName(), AdTechIdentifier.fromString(biddingUri.getHost()), eventLog::writeEvent));
    }

    private void useOverrides(EventLogManager eventLog, AdSelectionWrapper adSelectionWrapper,
        CustomAudienceWrapper customAudienceWrapper, String decisionLogicJs, String biddingLogicJs,
        AdSelectionSignals trustedScoringSignals, AdSelectionSignals trustedBiddingSignals, Uri biddingUri, Context context) {
        adSelectionWrapper.overrideAdSelection(eventLog::writeEvent,decisionLogicJs, trustedScoringSignals);
        customAudienceWrapper.addCAOverride(SHOES_NAME,context.getPackageName(), AdTechIdentifier.fromString(biddingUri.getHost()), biddingLogicJs,trustedBiddingSignals,eventLog::writeEvent);
        customAudienceWrapper.addCAOverride(SHIRTS_NAME,context.getPackageName(), AdTechIdentifier.fromString(biddingUri.getHost()), biddingLogicJs,trustedBiddingSignals,eventLog::writeEvent);
    }

    private void resetOverrides(EventLogManager eventLog, AdSelectionWrapper adSelectionWrapper, CustomAudienceWrapper customAudienceWrapper) {
        adSelectionWrapper.resetAdSelectionOverrides(eventLog::writeEvent);
        customAudienceWrapper.resetCAOverrides(eventLog::writeEvent);
    }

    /**
     * Replaces the override URI in the .js files with an actual reporting URI
     */
    private String replaceReportingURI(String js, String reportingUri) {
        return js.replace("https://reporting.example.com", reportingUri);
    }

    /**
     * Gets a given intent extra or notifies the user that it is missing
     * @param intent The intent to get
     * @param eventLog An eventlog to write the error to
     * @param errorMessage the error message to write to the eventlog
     * @return The string value of the intent specified.
     */
    private String getIntentOrError(String intent, EventLogManager eventLog, String errorMessage) {
        String toReturn = getIntent().getStringExtra(intent);
        if (toReturn == null) {
            String message = String.format(errorMessage, intent);
            eventLog.writeEvent(message);
            throw new RuntimeException(message);
        }
        return toReturn;
    }

    /**
     * Gets a given intent extra or returns the given default value
     * @param intent The intent to get
     * @param defaultValue The default value to return if intent doesn't exist
     */
    private String getIntentOrDefault(String intent, String defaultValue) {
        String toReturn = getIntent().getStringExtra(intent);
        if (toReturn == null) {
            String message = String.format("No value for %s, defaulting to %s", intent, defaultValue);
            Log.w(TAG, message);
            toReturn = defaultValue;
        }
        return toReturn;
    }

    /**
     * Resolve the host of the given URI and returns an {@code AdTechIdentifier} object
     * @param uri Uri to resolve
     */
    private AdTechIdentifier resolveAdTechIdentifier(Uri uri) {
        return AdTechIdentifier.fromString(uri.getHost());
    }

    /**
     * Reads a file into a string, to be used to read the .js files into a string.
     */
    private String assetFileToString(String location) throws IOException {
        return new BufferedReader(new InputStreamReader(getApplicationContext().getAssets().open(location)))
            .lines().collect(Collectors.joining("\n"));
    }
}