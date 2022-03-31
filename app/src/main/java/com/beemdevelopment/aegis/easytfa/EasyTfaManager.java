package com.beemdevelopment.aegis.easytfa;

import android.content.Intent;
import android.os.AsyncTask;
import android.util.Log;

import com.beemdevelopment.aegis.AegisApplication;
import com.beemdevelopment.aegis.easytfa.ui.LinkedBrowserApproveRequestActivity;
import com.beemdevelopment.aegis.vault.VaultEntry;
import com.beemdevelopment.aegis.vault.VaultManagerException;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class EasyTfaManager {
    private AegisApplication _app;
    private FirebaseApp _firebaseApp;
    private String _notificationEndpointToken;
    private EasyTfaApiClient _easyTfaApiClient;
    private EasyTfaCrypto _easyTfaCrypto;
    private EasyTfaBrowserMessenger _browserMessenger;

    public EasyTfaManager(AegisApplication app) {
        _app = app;
    }

    public void initialize() {
        try {
            if(_easyTfaCrypto == null) {
                _easyTfaCrypto = new EasyTfaCrypto(_app.getVaultManager());
                _easyTfaApiClient = new EasyTfaApiClient(_app, _app.getPreferences().getEasyTfaServerUrl());
                _browserMessenger = new EasyTfaBrowserMessenger(_easyTfaApiClient, _easyTfaCrypto);
                EasyTfaApiClient.EasyTfaConfig config = _easyTfaApiClient.getConfig();
                if (config.pushNotificationsSupported() && _firebaseApp == null) {
                    AsyncTask.execute(() -> initializeFirebase(config));
                }
            }

            checkForNewRequest();
        } catch(Exception ex) {
            Log.e("EasyTFA", "Initialization failed", ex);
        }
    }

    //region Firebase
    private FirebaseMessaging getFirebaseMessaging() {
        return _firebaseApp.get(FirebaseMessaging.class);
    }

    private void initializeFirebase(EasyTfaApiClient.EasyTfaConfig config) {

        FirebaseOptions fbOptions = new FirebaseOptions.Builder()
                .setApiKey(config.getPushNotificationApiKey())
                .setApplicationId(config.getPushNotificationApplicationId())
                .setProjectId(config.getPushNotificationProjectId())
                .build();
        _firebaseApp = FirebaseApp.initializeApp(_app.getApplicationContext(), fbOptions);

        getFirebaseMessaging().setAutoInitEnabled(true);
        getFirebaseMessaging().getToken().addOnCompleteListener(task -> {
            if(!task.isSuccessful()) {
                Log.e("BrowserLink", "Could not fetch token from firebase");
                return;
            }

            String token = task.getResult();
            _notificationEndpointToken = token;
            Log.i("BrowserLink", "FCMToken is: " + token);

            try {
                Collection<VaultLinkedBrowserEntry> values = _app.getVaultManager().getLinkedBrowsers().getValues();

                ArrayList<String> browserHashes = new ArrayList<String>();
                for (VaultLinkedBrowserEntry entry : values) {
                    browserHashes.add(entry.getBrowserPublicKeyHash());
                }

                // TODO - find a better solution for this if possible
                AsyncTask.execute(() -> {
                    try {
                        _easyTfaApiClient.registerNotificationEndpoint(_notificationEndpointToken, browserHashes);
                    } catch (EasyTfaException e) {
                        e.printStackTrace();
                    }
                });
            } catch(Exception ex) {
                Log.e("BrowserLink", "Endpoint registration failed", ex);
            }
        });
    }
    //endregion

    public void checkForNewRequest() {
        Collection<VaultLinkedBrowserEntry> linkedBrowsers = _app.getVaultManager().getLinkedBrowsers().getValues();
        List<String> linkedBrowserHashes = linkedBrowsers.stream().map(VaultLinkedBrowserEntry::getBrowserPublicKeyHash).collect(Collectors.toList());

        try {
            JSONObject response = _easyTfaApiClient.getRequest(linkedBrowserHashes);
            if (response.isNull("message")) {
                return;
            }

            String encryptedMessage = response.getString("message");
            String decryptedMessage = _easyTfaCrypto.decrypt(encryptedMessage);

            JSONObject messageJson = new JSONObject(decryptedMessage);

            String action = messageJson.getString("action");

            switch (action) {
                case "query-code":
                    String browserPubKeyHash = messageJson.getString("hash");
                    String oneTimePad = messageJson.getString("oneTimePad");
                    String checksum = messageJson.getString("checksum");
                    String queryUrl = messageJson.getString("url");

                    handleCodeRequest(new URL(queryUrl), browserPubKeyHash, oneTimePad, checksum);
                    break;
            }

        } catch (Exception ex) {
            Log.e("EasyTFA", "Could not parse new request", ex);
        }
    }

    public void handleCodeRequest(URL url, String browserPubKeyHash, String oneTimePad, String checksum) {
        VaultEntry entry = getEntryForUrl(url);
        if(entry == null) {
            // TODO: Show entry selection dialog
        }
        Intent intent = new Intent(this._app.getApplicationContext(), LinkedBrowserApproveRequestActivity.class);
        intent.putExtra("entryUUID", entry.getUUID());
        intent.putExtra("url", url.toString());
        intent.putExtra("browserPubKeyHash", browserPubKeyHash);
        intent.putExtra("oneTimePad", oneTimePad);
        intent.putExtra("checksum", checksum);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // TODO - what does this actually do? (it works though)
        _app.startActivity(intent);
    }

    public VaultEntry getEntryForUrl(URL url) {
        for (VaultEntry entry : _app.getVaultManager().getEntries()) {
            if(entry.getBrowserLinkUrls().contains(url.toString()) || entry.getNote().contains(url.toString())) // TODO - this currently also checks the note
                return entry;
        }
        return null;
    }

    //region Linked Browser
    /**
     * Gets a linked browser from the store
     * @param publicKeyHash Hash of the public key of the browser
     */
    public VaultLinkedBrowserEntry getLinkedBrowser(String publicKeyHash) {
        for(VaultLinkedBrowserEntry entry: _app.getVaultManager().getLinkedBrowsers()) {
            if(entry.getBrowserPublicKeyHash().equals(publicKeyHash))
                return entry;
        }
        return null;
    }

    /**
     * Adds a linked browser to the vault
     * @param browserName Name of the browser
     * @param publicKey Public key of the browser
     * @throws VaultManagerException
     */
    public void addLinkedBrowser(String browserName, String publicKey) throws VaultManagerException{
        for (VaultLinkedBrowserEntry linkedBrowser: _app.getVaultManager().getLinkedBrowsers()) {
            if(linkedBrowser.getBrowserPublicKey().equals(publicKey))
                return;
        }

        VaultLinkedBrowserEntry linkedBrowser = new VaultLinkedBrowserEntry(browserName, publicKey);
        _app.getVaultManager().getLinkedBrowsers().add(linkedBrowser);
        _app.getVaultManager().save(true);
    }

    /**
     * Removes a linked browser from the vault
     * @param publicKeyHash Hash of the public key of the browser
     * @throws VaultManagerException
     */
    public void removeLinkedBrowser(String publicKeyHash) throws VaultManagerException{
        VaultLinkedBrowserEntry entry = null;
        for (VaultLinkedBrowserEntry linkedBrowser: _app.getVaultManager().getLinkedBrowsers()) {
            if(linkedBrowser.getBrowserPublicKeyHash().equals(publicKeyHash)) {
                entry = linkedBrowser;
                break;
            }
        }

        if(entry != null) {
            _app.getVaultManager().getLinkedBrowsers().remove(entry);
            _app.getVaultManager().save(true);
        }
    }
    //endregion

    public EasyTfaApiClient getApiClient() {
        return _easyTfaApiClient;
    }

    public EasyTfaCrypto getCrypto() {
        return _easyTfaCrypto;
    }

    public EasyTfaBrowserMessenger getBrowserMessenger() {
        return _browserMessenger;
    }
}
