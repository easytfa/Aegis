package com.beemdevelopment.aegis.easytfa;

import android.content.Intent;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;
import com.beemdevelopment.aegis.AegisApplication;
import com.beemdevelopment.aegis.encoding.Base64;
import com.beemdevelopment.aegis.encoding.Hex;
import com.beemdevelopment.aegis.easytfa.ui.LinkedBrowserApproveRequestActivity;
import com.beemdevelopment.aegis.vault.VaultEntry;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

public class EasyTfaManager {
    private AegisApplication _app;
    private String _serverAddress = "https://eu-relay1.easytfa.com/";
    private RequestQueue _requestQueue;
    private FirebaseApp _firebaseApp;
    private String _notificationEndpointToken;
    private EasyTfaApiClient _easyTfaApiClient;

    public EasyTfaManager(AegisApplication app) {
        _app = app;
        _requestQueue = Volley.newRequestQueue(_app.getApplicationContext());

        _easyTfaApiClient = new EasyTfaApiClient(_app, _serverAddress);
    }

    public void initialize() {
        try {
            EasyTfaApiClient.EasyTfaConfig config = _easyTfaApiClient.getConfig();
            if(config.pushNotificationsSupported() && _firebaseApp == null) {
                AsyncTask.execute(() -> initializeFirebase(config));
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

                _easyTfaApiClient.registerNotificationEndpoint(_notificationEndpointToken, browserHashes);
            } catch(Exception ex) {
                Log.e("BrowserLink", "Endpoint registration failed");
            }
        });
    }
    //endregion

    //region Key Stuff
    public void generateLocalKeypair() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(4096);
            KeyPair keyPair = kpg.generateKeyPair();
            _app.getVaultManager().setBrowserLinkKeyPair(keyPair);
        } catch (Exception ex) {
        }
    }

    public String getEncodedLocalPublicKey() {
        return Base64.encode(_app.getVaultManager().getBrowserLinkKeypair().getPublic().getEncoded());
    }

    public String hashKey(String encodedKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Hex.encode(digest.digest(encodedKey.getBytes(StandardCharsets.UTF_8)));
        } catch(Exception ex) {
            return null;
        }
    }

    public Boolean verifyPublicKey(String encodedPublicKey, String hash) {
        return hash.equals(hashKey(encodedPublicKey));
    }

    public String encryptWithPublicKey(String data, PublicKey key) {
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
            OAEPParameterSpec oaepParams = new OAEPParameterSpec("SHA-256", "MGF1", new MGF1ParameterSpec("SHA-256"), PSource.PSpecified.DEFAULT);
            cipher.init(Cipher.ENCRYPT_MODE, key, oaepParams);
            byte[] encrypted = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.encode(encrypted);
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public String decryptWithPrivateKey(String data) throws Exception {
        KeyPair localKeyPair = _app.getVaultManager().getBrowserLinkKeypair();
        if (localKeyPair == null)
            throw new Exception("Local KeyPair not generated");

        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
            OAEPParameterSpec oaepParams = new OAEPParameterSpec("SHA-256", "MGF1", new MGF1ParameterSpec("SHA-256"), PSource.PSpecified.DEFAULT);
            cipher.init(Cipher.DECRYPT_MODE, localKeyPair.getPrivate(), oaepParams);
            byte[] decrypted = cipher.doFinal(Base64.decode(data));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }
    //endregion

    public String requestPublicKeyForHash(String hash) throws EasyTfaException {
        return _easyTfaApiClient.getPublicKeyForHash(hash);
    }

    public void linkBrowser(PublicKey publicKey, String hash, String secret) throws JSONException, EasyTfaException {
        String encodedLocalPublicKey = getEncodedLocalPublicKey();

        JSONObject objectToEncrypt = new JSONObject();
        objectToEncrypt.put("secret", secret);
        objectToEncrypt.put("appPublicKeyHash", hashKey(encodedLocalPublicKey));
        String encryptedString = createEncryptedMessage(publicKey, "link", objectToEncrypt);

        JSONObject dataObject = new JSONObject();
        dataObject.put("appPublicKey", encodedLocalPublicKey);

        _easyTfaApiClient.sendMessage(hash, encryptedString, dataObject);
    }

    private String createEncryptedMessage(PublicKey browserPublicKey, String type, JSONObject content) throws JSONException {
        if(!content.has("type")) {
            content.put("type", type);
        }
        String stringToEncrypt = content.toString();
        return encryptWithPublicKey(stringToEncrypt, browserPublicKey);
    }

    public void checkForNewRequest() {
        Collection<VaultLinkedBrowserEntry> linkedBrowsers = _app.getVaultManager().getLinkedBrowsers().getValues();
        List<String> linkedBrowserHashes = linkedBrowsers.stream().map(VaultLinkedBrowserEntry::getBrowserPublicKeyHash).collect(Collectors.toList());

        try {
            JSONObject response = _easyTfaApiClient.getRequest(linkedBrowserHashes);
            if (response.isNull("message")) {
                return;
            }

            String encryptedMessage = response.getString("message");
            String decryptedMessage = decryptWithPrivateKey(encryptedMessage);

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

    public VaultLinkedBrowserEntry getEntryByPubKeyHash(String hash) {
        for(VaultLinkedBrowserEntry entry: _app.getVaultManager().getLinkedBrowsers()) {
            if(entry.getBrowserPublicKeyHash().equals(hash))
                return entry;
        }
        return null;
    }

    public void sendCode(VaultLinkedBrowserEntry entry, String totpUrl, String oneTimePad, String code) {
        try {
            byte[] codeBytes = code.getBytes(StandardCharsets.UTF_8);
            byte[] oneTimePadBytes = Base64.decode(oneTimePad);
            int length = Math.min(codeBytes.length, oneTimePadBytes.length);
            for(int i = 0; i < length; i++) {
                codeBytes[i] ^= oneTimePadBytes[i];
            }

            byte[] publicKeyData = Base64.decode(entry.getBrowserPublicKey());
            X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyData);
            PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(publicKeySpec);

            JSONObject browserMessage = new JSONObject();
            browserMessage.put("url", totpUrl);
            browserMessage.put("code", Base64.encode(codeBytes));
            String encryptedMessage = createEncryptedMessage(publicKey, "code", browserMessage);

            _easyTfaApiClient.sendMessage(entry.getBrowserPublicKeyHash(), encryptedMessage, null);
        } catch (Exception ex) {
            Log.e("EasyTFA", "Could not send code", ex);
        }
    }

}
