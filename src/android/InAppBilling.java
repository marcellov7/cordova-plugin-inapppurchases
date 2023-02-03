
// Copyright (c) 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

/**
 *
 * Modifications: Alex Disler (alexdisler.com)
 * github.com/alexdisler/cordova-plugin-inapppurchase
 *
 */

/** cordova-plugin-inapppurchases MIT © 2023 cozycode.ca **/

package com.alexdisler_github_cozycode.inapppurchases;
//import android.util.Log;
import java.io.OutputStreamWriter;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaWebView;


import com.android.billingclient.api.BillingClient;
import com.alexdisler_github_cozycode.inapppurchases.IabNext;

//import com.alexdisler_github_cozycode.inapppurchases.IabHelper.OnConsumeFinishedListener;
//import com.alexdisler_github_cozycode.inapppurchases.IabHelper.OnAcknowledgeFinishedListener;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

public class InAppBilling extends CordovaPlugin {
    
    boolean Extra_Debug_Logging_Enabled = false; //set to false for app store, asks for more permissions set these in the androidManfiest.xml too
 //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    
    
    public static final int BILLING_API_VERSION = 5;
    protected static final String TAG = "google.payments";

    public static final int OK = 0;
    public static final int INVALID_ARGUMENTS = -1;
    public static final int UNABLE_TO_INITIALIZE = -2;
    public static final int BILLING_NOT_INITIALIZED = -3;
    public static final int UNKNOWN_ERROR = -4;
    public static final int USER_CANCELLED = -5;
    public static final int BAD_RESPONSE_FROM_SERVER = -6;
    public static final int VERIFICATION_FAILED = -7;
    public static final int ITEM_UNAVAILABLE = -8;
    public static final int ITEM_ALREADY_OWNED = -9;
    public static final int ITEM_NOT_OWNED = -10;
    public static final int CONSUME_FAILED = -11;
    public static final int GOOGLE_PLAY_KEY_ERROR = -12;

    public static final int PURCHASE_PURCHASED = 0;
    public static final int PURCHASE_CANCELLED = 1;
    public static final int PURCHASE_REFUNDED = 2;
    
    public static final String API_GET_ALL_PRODUCT_INFO = "billingGetAllProductInfo";
    public static final String API_GET_PURCHASES = "billingGetPurchases";
    public static final String API_RESTORE_PURCHASES = "billingRestorePurchases";
    public static final String API_PURCHASE = "billingPurchase";
    public static final String API_COMPLETE_PURCHASE = "billingCompletePurchase";
    public static final String[] API_ALL_FUNCS = {API_GET_ALL_PRODUCT_INFO, API_GET_PURCHASES, API_RESTORE_PURCHASES, API_PURCHASE, API_COMPLETE_PURCHASE};
    
    protected IabHelper iabHelper = null;
    protected IabInventory iabHelperInventory = new IabInventory();
    boolean billingInitialized = false;
    AtomicInteger orderSerial = new AtomicInteger(0);
    
    private JSONObject manifestObject = null;

    /**
     * Load manifest file from assets by given path.
     * In Cordova manifest file should be placed on path 'www/manifest.json'.
     * In Capacitor the path should be 'public/manifest.json'
     * @param path should contain path of manifest.json file (without '/manifest.json').
     * @return InputStream if manifest file is loaded, null otherwise.
     */
    private JSONObject getManifestContents() {
        if (manifestObject != null) return manifestObject;
        InputStream is;
        try {
            is = getManifestFileInputStream("www");
            if (is == null) {
                is = getManifestFileInputStream("public");
            }
            if (is != null) {
                Scanner s = new Scanner(is).useDelimiter("\\A");
                String manifestString = s.hasNext() ? s.next() : "";
                if (iabHelper != null) iabHelper.logInfo("manifest:" + manifestString);
                manifestObject = new JSONObject(manifestString);
            } else {
                manifestObject = null;
            }
        } catch (JSONException e) {
            if (iabHelper != null) iabHelper.logInfo("Unable to parse manifest file:" + e.toString());
            manifestObject = null;
        }
        return manifestObject;
    }
    private InputStream getManifestFileInputStream(String path) {
        InputStream inputStream = null;
        Context context = this.cordova.getActivity();
        try {
            inputStream = context.getAssets().open(path + "/manifest.json");
        } catch (IOException e) {
            if (iabHelper != null) iabHelper.logInfo("Can not load manifest file on path: " + path + "/manifest.json");
        }
        return inputStream;
    }
    protected String getBase64EncodedPublicKey() {
        return getBase64EncodedPublicKey(null);
    }
    protected String getBase64EncodedPublicKey(IabNext next){
        return getBase64EncodedPublicKey(next, false);
    }
    protected String getBase64EncodedPublicKey(IabNext next, boolean tryDecodeKey) {
        //if (iabHelper != null) iabHelper.logInfo("Reading Google Play Billing Key");
        JSONObject manifestObject = getManifestContents();
        if (manifestObject != null) {
            try {
                String key = manifestObject.optString("play_store_key");
            } catch (Exception e){
                String err = "ERROR reading Google Play Store Key from manifest.json: "+e;
                if (next != null) next.OnError(false,GOOGLE_PLAY_KEY_ERROR,err);
                if (iabHelper != null) iabHelper.logError(err);
                return null;
            }
            if (tryDecodeKey){
                try {
                    String key = manifestObject.optString("play_store_key");
                    IabSecurity.generatePublicKey(key);
                    if (IabSecurity.hasDecodeFailMessage()){
                        String err = IabSecurity.getFailDecodeMessage();
                        if (iabHelper != null) iabHelper.logError(err);
                        //if (next != null) next.OnError(false,err,GOOGLE_PLAY_KEY_ERROR);
                    }
                } catch (Exception e){
                    String err = "ERROR converting Google Play Store Key from manifest.json: "+e;
                    if (next != null) next.OnError(false,GOOGLE_PLAY_KEY_ERROR,err);
                    if (iabHelper != null) iabHelper.logError(err);
                }
            }
            return manifestObject.optString("play_store_key");
        }
        return null;
    }
    protected boolean debugManifestFailure(IabNext next){
        JSONObject manifestObject = getManifestContents();
        if (manifestObject == null){
            if (next != null) next.callbackContext.error(makeError("Billing cannot be initialized: could not find www/manifest.json file with Google Play Billing Key", UNABLE_TO_INITIALIZE));
            return true;
        }
        String base64EncodedPublicKey = getBase64EncodedPublicKey(next,true);
        if (base64EncodedPublicKey == null) {
            if (next != null) next.callbackContext.error(makeError("Billing cannot be initialized: extracting Android billing key from www/manifest.json file", UNABLE_TO_INITIALIZE));
            return true;
        }
        return false;
    }
    
    /**
     * Error Handling
     */
    protected JSONObject makeError(String message) {
        return makeError(message, null, null, null);
    }
    protected JSONObject makeError(String message, Integer resultCode) {
        return makeError(message, resultCode, null, null);
    }
    protected JSONObject makeError(String message, Integer resultCode, IabResult result) {
        return makeError(message, resultCode, result.getMessage(), result.getResponse());
    }
    protected JSONObject makeError(String message, Integer resultCode, String text, Integer response) {
        if (resultCode == INVALID_ARGUMENTS) message = "INVALID_ARGUMENTS "+message;
        else if (resultCode == UNABLE_TO_INITIALIZE) message = "UNABLE_TO_INITIALIZE "+message;
        else if (resultCode == BILLING_NOT_INITIALIZED) message = "BILLING_NOT_INITIALIZED "+message;
        else if (resultCode == UNKNOWN_ERROR) message = "UNKNOWN_ERROR "+message;
        else if (resultCode == USER_CANCELLED) message = "USER_CANCELLED "+message;
        else if (resultCode == BAD_RESPONSE_FROM_SERVER) message = "BAD_RESPONSE_FROM_SERVER "+message;
        else if (resultCode == VERIFICATION_FAILED) message = "VERIFICATION_FAILED "+message;
        else if (resultCode == ITEM_UNAVAILABLE) message = "ITEM_UNAVAILABLE "+message;
        else if (resultCode == ITEM_ALREADY_OWNED) message = "ITEM_ALREADY_OWNED "+message;
        else if (resultCode == ITEM_NOT_OWNED) message = "ITEM_NOT_OWNED "+message;
        else if (resultCode == CONSUME_FAILED) message = "CONSUME_FAILED "+message;
        else if (resultCode == GOOGLE_PLAY_KEY_ERROR) message = "GOOGLE_PLAY_KEY_ERROR "+message;
        if (message != null) {
            if (iabHelper != null) iabHelper.logInfo("Error: " + message);
        }
        JSONObject error = new JSONObject();
        try {
            if (resultCode != null) error.put("code", (int)resultCode);
            if (message != null) error.put("message", message);
            if (text != null) error.put("iabText", text);
            if (response != null) error.put("responseCode", response);
        } catch (JSONException e) {
            if (iabHelper != null) iabHelper.logError("ERROR: while creating InAppBilling error "+e.toString());
        }
        return error;
    }
    private List<String> convertJsonArrayToList(JSONArray jsonArray) throws JSONException {
        List<String> list = new ArrayList<String>();
        for (int i=0; i<jsonArray.length(); i++) {
            list.add( jsonArray.getString(i) );
        }
        return list;
    }
    private int stringArrayIndex(String[] arr, String a){
        for (int i = 0; i < arr.length; i++) {
            if (arr[i].equals(a)){
                return i;
            }
        }
        return -1;
    }

    
    /**
     * Cordova plugin
     */
    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        initializeBillingHelper();
    }
    @Override
    public boolean execute(String action, final JSONArray args, final CallbackContext callbackContext) {
        try {
            if (iabHelper != null) iabHelper.logInfo(TAG+ " "+"executing "+ action+" with "+Integer.toString(args.length())+" arguments");
            int funcNum = stringArrayIndex(API_ALL_FUNCS, action);
            if (funcNum == -1){
                callbackContext.error(makeError("Invalid API Request: "+action, INVALID_ARGUMENTS));
                return false;
            }
            return startBillingAll(new IabNext(this, this.cordova.getActivity(), callbackContext, args, action){
                public void OnNext(){ if (inAppBilling.iabHelper != null) inAppBilling.iabHelper.logWarning("shouldn't be here"); } });
        } catch (Exception ex){
            callbackContext.error(makeError("UNKNOWN_ERROR: "+ex, UNKNOWN_ERROR));
            return false;
        }
    }
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (!iabHelper.handleActivityResult(requestCode, resultCode, intent)) {
            super.onActivityResult(requestCode, resultCode, intent);
        }
    }
    @Override
    public void onDestroy() {
        if (iabHelper != null) iabHelper.dispose();
        iabHelper = null;
        billingInitialized = false;
    }
    
    /**
     * Billing calls
     **/
    //IabHelper
    protected boolean initializeBillingHelper() {
        if (iabHelper != null) {
            if (iabHelper != null) iabHelper.logInfo("Billing already initialized");
            return true;
        }
        Context context = this.cordova.getActivity();
        String base64EncodedPublicKey = getBase64EncodedPublicKey();
        if (base64EncodedPublicKey != null) {
            iabHelper = new IabHelper(context, base64EncodedPublicKey, Extra_Debug_Logging_Enabled);
            if (Extra_Debug_Logging_Enabled) iabHelper.logInfo("ENABLED EXTRA LOGGING");
            billingInitialized = false;
            return true;
        }
        if (iabHelper != null) iabHelper.logInfo("Unable to initialize billing");
        return false;
    }
    //start Google Play BillingClient
    private boolean startBillingAll(IabNext next){
        try {
            //initialize and call
            return initializeBillingClient(new IabNext(next){
                public void OnNext(){
                    try {
                        iabHelper.flagStartAsync(action); //end async only after this
                    } catch (IllegalStateException ex){
                        next.callbackContext.error(makeError("ERROR TOO MANY REQUESTS: only one billing operation is permitted at a time. "+ex, UNKNOWN_ERROR));
                    } catch (Exception ex){
                        if (iabHelper != null) iabHelper.flagEndAsync();
                        next.callbackContext.error(makeError("UNKNOWN_ERROR: started initializing billing "+ex, UNKNOWN_ERROR));
                    }
                    try {
                        if (API_GET_ALL_PRODUCT_INFO.equals(action)) {
                            inAppBilling.getAllProductInfo(thisNext);
                        } else if (API_GET_PURCHASES.equals(action)) {
                            inAppBilling.getPurchases(thisNext);
                        } else if (API_RESTORE_PURCHASES.equals(action)) {
                            inAppBilling.restorePurchases(thisNext);
                        } else if (API_PURCHASE.equals(action)) {
                            inAppBilling.purchase(thisNext);
                        } else if (API_COMPLETE_PURCHASE.equals(action)) {
                            inAppBilling.completePurchase(thisNext);
                        } else {
                            if (iabHelper != null) iabHelper.flagEndAsync();
                            callbackContext.error(inAppBilling.makeError("Unhandled API Request: "+action, INVALID_ARGUMENTS));
                        }
                    } catch (Exception ex){
                        if (iabHelper != null) iabHelper.flagEndAsync();
                        next.callbackContext.error(makeError("UNKNOWN_ERROR: started initializing billing "+ex, UNKNOWN_ERROR));
                    }
                }});
        } catch (Exception ex){
            next.callbackContext.error(makeError("UNKNOWN_ERROR: started initializing billing "+ex, UNKNOWN_ERROR));
            return false;
        }
    }
    //initialize billing client
    private boolean initializeBillingClient(IabNext next){
        try {
            if (billingInitialized) {
                if (iabHelper != null) iabHelper.logInfo("Billing already initialized");
                next.OnNext();
                return true;
            } else if (iabHelper == null) {
                if (debugManifestFailure(next)) return false;
                String base64EncodedPublicKey = getBase64EncodedPublicKey(next);
                try {
                    new IabHelper(next.activityContext, base64EncodedPublicKey, Extra_Debug_Logging_Enabled);
                } catch (Exception ex){
                    if (next != null) next.callbackContext.error(makeError("Billing cannot be initialized: "+ex, UNABLE_TO_INITIALIZE));
                    return false;
                }
                next.callbackContext.error(makeError("Billing cannot be initialized", UNABLE_TO_INITIALIZE));
                return false;
            } else {
                iabHelper.initializeBillingClientAsync(new IabNext(next) {
                    public void OnNext(IabResult result) {
                        try {
                            if (!result.isSuccess()) {
                                mNext.callbackContext.error(makeError("Unable to initialize billing: " + result.toString(), UNABLE_TO_INITIALIZE, result));
                            } else {
                                if (iabHelper != null) iabHelper.logInfo("Billing initialized");
                                billingInitialized = true;
                                mNext.OnNext(); //callbackContext.success();
                            }
                        } catch (Exception ex){
                            next.callbackContext.error(makeError("UNKNOWN_ERROR: "+ex, UNKNOWN_ERROR));
                            if (iabHelper != null) iabHelper.flagEndAsync();
                        }
                    }
                });
            }
        } catch (Exception ex){
            next.callbackContext.error(makeError("UNKNOWN_ERROR: initializing billing "+ex, UNKNOWN_ERROR));
            return false;
        }
        return true;
    }
    //get product information
    private void getBillingProductInfo(IabNext next){
        try {
            if (iabHelper == null || !billingInitialized) {
                next.callbackContext.error(makeError("Billing is not initialized", BILLING_NOT_INITIALIZED));
                if (iabHelper != null) iabHelper.flagEndAsync();
                return;
            }
            //iabHelper.logInfo("Getting Product Details");
            iabHelper.getProductDetailsAsync(next);
        } catch (Exception ex){
            if (iabHelper != null) iabHelper.flagEndAsync();
            next.callbackContext.error(makeError("UNKNOWN_ERROR: "+ex, UNKNOWN_ERROR));
        }
    }
    //get purchase information
    private void getBillingPurchaseInfo(IabNext next){
        try {
            if (iabHelper == null || !billingInitialized) {
                if (iabHelper != null) iabHelper.flagEndAsync();
                next.callbackContext.error(makeError("Billing is not initialized", BILLING_NOT_INITIALIZED));
                return;
            }
            //iabHelper.logInfo("Getting Purchases");
            iabHelper.restorePurchasesAsync(next);
        } catch (Exception ex){
            next.callbackContext.error(makeError("UNKNOWN_ERROR: "+ex, UNKNOWN_ERROR));
            if (iabHelper != null) iabHelper.flagEndAsync();
        }
    }
    //make a purchase
    private void doBillingPurchase(IabNext next){
        try {
            if (iabHelper == null || !billingInitialized) {
                if (iabHelper != null) iabHelper.flagEndAsync();
                next.callbackContext.error(makeError("Billing is not initialized", BILLING_NOT_INITIALIZED));
                return;
            }
            String purchaseProductId = next.getArgsProductId(true);
            if (!iabHelperInventory.hasDetails(purchaseProductId)){
                iabHelper.logInfo("Getting Product Details for "+purchaseProductId);
                getBillingProductInfo(new IabNext(next){
                    public void OnNext(IabResult result){ //, IabInventory inv){
                        try {
                            if (thisNext.checkResultFail(result)) return;
                            thisNext.inAppBilling.iabHelper.logInfo("Purchasing "+purchaseProductId);
                            thisNext.inAppBilling.iabHelper.launchBillingFlowAsync(next, purchaseProductId);
                        } catch (Exception ex){
                            if (thisNext.inAppBilling.iabHelper != null) thisNext.inAppBilling.iabHelper.flagEndAsync();
                            this.callbackContext.error(makeError("UNKNOWN_ERROR: "+ex, UNKNOWN_ERROR));
                        }
                    }
                });
            } else {
                iabHelper.logInfo("Purchasing "+purchaseProductId);
                iabHelper.launchBillingFlowAsync(next, purchaseProductId);
            }
        } catch (Exception ex){
            next.callbackContext.error(makeError("UNKNOWN_ERROR: "+ex, UNKNOWN_ERROR));
            if (iabHelper != null) iabHelper.flagEndAsync();
        }
    }
    //complete a purchase - consume+/acknowledge
    private void doBillingCompletePurchase(IabNext next){
        try {
            if (iabHelper == null || !billingInitialized) {
                if (iabHelper != null) iabHelper.flagEndAsync();
                next.callbackContext.error(makeError("Billing is not initialized", BILLING_NOT_INITIALIZED));
                return;
            }
            String purchaseProductId = next.getArgsProductId(true);
            if (!iabHelperInventory.hasDetails(purchaseProductId)){
                iabHelper.logInfo("Getting Product Details for "+purchaseProductId);
                getBillingProductInfo(new IabNext(next){
                    public void OnNext(IabResult result){ //, IabInventory inv){
                        try {
                            if (thisNext.checkResultFail(result)) return;
                            thisNext.inAppBilling.iabHelper.logInfo("Completing Purchase "+purchaseProductId);
                            thisNext.inAppBilling.iabHelper.launchPurchaseCompletionAsync(next, purchaseProductId);
                        } catch (Exception ex){
                            if (thisNext.inAppBilling.iabHelper != null) thisNext.inAppBilling.iabHelper.flagEndAsync();
                            this.callbackContext.error(makeError("UNKNOWN_ERROR: "+ex, UNKNOWN_ERROR));
                        }
                    }
                });
            } else {
                iabHelper.logInfo("Completing Purchase "+purchaseProductId);
                iabHelper.launchPurchaseCompletionAsync(next, purchaseProductId);
            }
        } catch (Exception ex){
            next.callbackContext.error(makeError("UNKNOWN_ERROR: "+ex, UNKNOWN_ERROR));
            if (iabHelper != null) iabHelper.flagEndAsync();
        }
    }
    
    /**
     * Plugin Billing API
     **/
    /* Get All Product Info - from argument list of product ids */
    protected void getAllProductInfo(IabNext prev) {
        iabHelper.logInfo("getAllProductInfo");
        getBillingProductInfo(new IabNext(prev){
            public void OnNext(IabResult result){ //, IabInventory inv){
                try {
                    if (thisNext.checkResultFail(result)) return;
                    try {
                        JSONArray productDetailsJSONArray = iabHelperInventory.getAllKnownProductDetailsJSON();
                        iabHelper.logInfo(iabHelperInventory.toString());
                        if (iabHelper != null) iabHelper.flagEndAsync();
                        this.callbackContext.success(productDetailsJSONArray);
                    } catch (JSONException e) {
                        if (iabHelper != null) iabHelper.flagEndAsync();
                        this.callbackContext.error("JSON parse error: "+e.getMessage());
                    }
                    iabHelper.logInfo("SENT READ RESPONSE:");
                } catch (Exception ex){
                    if (iabHelper != null) iabHelper.flagEndAsync();
                    this.callbackContext.error(makeError("UNKNOWN_ERROR: "+ex, UNKNOWN_ERROR));
                }
            }
        });
    }
    
    /* Restore purchases - get purchase details */
    protected void restorePurchases(IabNext prev){
        iabHelper.logInfo("restorePurchases");
        getBillingPurchaseInfo(new IabNext(prev){
            public void OnNext(IabResult result){ //, IabInventory inv){
                try {
                    if (thisNext.checkResultFail(result)) return;
                    if (debugManifestFailure(thisNext)) return;
                    try {
                        JSONArray purchasesJSONArray = iabHelperInventory.getAllOwnedPurchasesJSON();
                        if (this.inAppBilling.iabHelper != null) this.inAppBilling.iabHelper.logInfo(iabHelperInventory.toString());
                        if (iabHelper != null) iabHelper.flagEndAsync();
                        this.callbackContext.success(purchasesJSONArray);
                    } catch (JSONException e) {
                        if (iabHelper != null) iabHelper.flagEndAsync();
                        this.callbackContext.error("JSON parse error: "+e.getMessage());
                    }
                } catch (Exception ex){
                    this.callbackContext.error(makeError("UNKNOWN_ERROR: "+ex, UNKNOWN_ERROR));
                    if (iabHelper != null) iabHelper.flagEndAsync();
                }
            }
        });
    }
    
    /* Get purchases - get purchase details */
    protected void getPurchases(IabNext prev){
        iabHelper.logInfo("getPurchases");
        //same in android - can ask to not log in??
        restorePurchases(prev);
    }
    
    /* Purchase - make a purchase of a product id */
    protected void purchase(IabNext prev){
        iabHelper.logInfo("purchase");
        String purchaseProductId = prev.getArgsProductId(true);
        if (debugManifestFailure(prev)) return;
        getBillingProductInfo(new IabNext(prev){
            public void OnNext(IabResult result){ //, IabInventory inv){
                try {
                    if (thisNext.checkResultFail(result)) return;
                    IabNext buyNext = new IabNext(thisNext){
                        public void OnNext(IabResult result, IabInventory newInv){
                            try {
                                if (thisNext.checkResultFail(result)) return;
                                String purchaseProductId = thisNext.getArgsProductId(true);
                                thisNext.inAppBilling.iabHelperInventory.addInventory(newInv);
                                thisNext.inAppBilling.iabHelper.logInfo(TAG + " bought "+ purchaseProductId);
                                if (this.inAppBilling.iabHelper != null) this.inAppBilling.iabHelper.logInfo(iabHelperInventory.toString());
                                JSONObject purchaseJSONObject = thisNext.inAppBilling.iabHelperInventory.getPurchase(purchaseProductId).getDetailsJSON();
                                //thisNext.inAppBilling.iabHelper.logInfo(purchaseJSONObject.toString());
                                if (thisNext.inAppBilling.iabHelper != null) thisNext.inAppBilling.iabHelper.flagEndAsync();
                                this.callbackContext.success(purchaseJSONObject);
                            } catch (Exception ex){
                                thisNext.callbackContext.error(makeError("UNKNOWN_ERROR: "+ex, UNKNOWN_ERROR));
                                if (iabHelper != null) iabHelper.flagEndAsync();
                            }
                        }
                    };
                    //thisNext.inAppBilling.iabHelper.logInfo("purchasing "+purchaseProductId);
                    doBillingPurchase(buyNext);
                    //if (iabHelper != null) iabHelper.flagEndAsync();
                    //this.callbackContext.success(purchasesJSONArray);
                } catch (Exception ex){
                    this.callbackContext.error(makeError("UNKNOWN_ERROR: "+ex, UNKNOWN_ERROR));
                    if (iabHelper != null) iabHelper.flagEndAsync();
                }
            }
        });
    }
    
    /* Complete purchase - complete a purchase of a product id */
    protected void completePurchase(IabNext prev){
        iabHelper.logInfo("complete purchase");
        if (debugManifestFailure(prev)) return;
        String purchaseProductId = prev.getArgsProductId(true);
        getBillingProductInfo(new IabNext(prev){
            public void OnNext(IabResult result){ //, IabInventory inv){
                try {
                    if (thisNext.checkResultFail(result)) return;
                    IabNext completeNext = new IabNext(thisNext){
                        public void OnNext(IabResult result){
                            try {
                                if (thisNext.checkResultFail(result)) return;
                                String purchaseProductId = thisNext.getArgsProductId(true);
                                thisNext.inAppBilling.iabHelper.logInfo(TAG + " completed "+ purchaseProductId);
                                if (this.inAppBilling.iabHelper != null) this.inAppBilling.iabHelper.logInfo(iabHelperInventory.toString());
                                JSONObject purchaseJSONObject = thisNext.inAppBilling.iabHelperInventory.getPurchase(purchaseProductId).getDetailsJSON();
                                //thisNext.inAppBilling.iabHelper.logInfo(purchaseJSONObject.toString());
                                if (thisNext.inAppBilling.iabHelper != null) thisNext.inAppBilling.iabHelper.flagEndAsync();
                                this.callbackContext.success(purchaseJSONObject);
                            } catch (Exception ex){
                                thisNext.callbackContext.error(makeError("UNKNOWN_ERROR: "+ex, UNKNOWN_ERROR));
                                if (iabHelper != null) iabHelper.flagEndAsync();
                            }
                        }
                    };
                    if (iabHelperInventory.hasPurchase(purchaseProductId)){
                        boolean consumable = completeNext.getArgsConsumable(true);
                        thisNext.inAppBilling.iabHelper.logInfo("conumeable: "+Boolean.toString(consumable));
                        iabHelperInventory.getPurchase(purchaseProductId).setIsConsumable(consumable);
                    }
                    doBillingCompletePurchase(completeNext);
                } catch (Exception ex){
                    this.callbackContext.error(makeError("UNKNOWN_ERROR: "+ex, UNKNOWN_ERROR));
                    if (iabHelper != null) iabHelper.flagEndAsync();
                }
            }
        });
    }
}

