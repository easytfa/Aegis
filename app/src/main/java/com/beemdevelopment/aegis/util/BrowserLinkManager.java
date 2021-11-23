package com.beemdevelopment.aegis.util;

import android.content.Intent;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.RequestFuture;
import com.android.volley.toolbox.Volley;
import com.beemdevelopment.aegis.AegisApplication;
import com.beemdevelopment.aegis.encoding.Base64;
import com.beemdevelopment.aegis.encoding.Hex;
import com.beemdevelopment.aegis.ui.linked.LinkedBrowserApproveRequestActivity;
import com.beemdevelopment.aegis.vault.VaultEntry;
import com.beemdevelopment.aegis.vault.VaultLinkedBrowserEntry;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

public class BrowserLinkManager {
    private AegisApplication _app;
    private String _serverAddress = "https://easytfa.genemon.at/";
    private RequestQueue _requestQueue;

    public BrowserLinkManager(AegisApplication app) {
        _app = app;
        _requestQueue = Volley.newRequestQueue(_app.getApplicationContext());
    }

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

    public String requestPublicKeyForHash(String hash) throws JSONException, InterruptedException, ExecutionException, TimeoutException {
        RequestFuture<JSONObject> future = RequestFuture.newFuture();
        String url = _serverAddress + "public-key-by-hash";
        JSONObject requestObject = new JSONObject();
        requestObject.put("hash", hash);
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, requestObject, future, future);
        _requestQueue.add(request);

        JSONObject response = future.get(10, TimeUnit.SECONDS);
        return response.getString("publicKey");
    }

    public Boolean linkBrowser(PublicKey publicKey, String hash, String secret) throws JSONException, InterruptedException, ExecutionException, TimeoutException {
        String encodedLocalPublicKey = getEncodedLocalPublicKey();
        RequestFuture<JSONObject> future = RequestFuture.newFuture();
        String url = _serverAddress + "link";

        JSONObject objectToEncrypt = new JSONObject();
        objectToEncrypt.put("secret", secret);
        objectToEncrypt.put("appPublicKeyHash", hashKey(encodedLocalPublicKey));
        String stringToEncrypt = objectToEncrypt.toString();
        String encryptedString = encryptWithPublicKey(stringToEncrypt, publicKey);

        JSONObject requestObject = new JSONObject();
        requestObject.put("hash", hash);
        requestObject.put("message", encryptedString);
        requestObject.put("appPublicKey", encodedLocalPublicKey);

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, requestObject, future, future);
        _requestQueue.add(request);

        JSONObject response = future.get(10, TimeUnit.SECONDS);
        return response.getBoolean("success");
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

    public void checkForNewRequest() {
        Collection<VaultLinkedBrowserEntry> linkedBrowsers = _app.getVaultManager().getLinkedBrowsers().getValues();
        List<String> linkedBrowserHashes = linkedBrowsers.stream().map(VaultLinkedBrowserEntry::getBrowserPublicKeyHash).collect(Collectors.toList());

        try {
            RequestFuture<JSONObject> future = RequestFuture.newFuture();
            String url = _serverAddress + "code-queries-by-hashes";

            JSONArray linkBrowserHashArray = new JSONArray(linkedBrowserHashes);
            JSONObject requestObject = new JSONObject();
            requestObject.put("hashes", linkBrowserHashArray);
            JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, requestObject, future, future);
            _requestQueue.add(request);

            JSONObject response = future.get(10, TimeUnit.SECONDS);
            if (response.isNull("message")) {
                return;
            }

            String encryptedMessage = response.getString("message");
            String decryptedMessage = decryptWithPrivateKey(encryptedMessage);
            JSONObject messageJson = new JSONObject(decryptedMessage);
            String action = messageJson.getString("action");

            switch (action) {
                case "query-code":
                    String queryUrl = messageJson.getString("url");
                    handleCodeRequest(new URL(queryUrl));
                    break;
            }

        } catch (Exception ex) {
            Log.e("BrowserLink", "Something failed", ex);
        }
    }

    public void handleCodeRequest(URL url) {
        VaultEntry entry = getEntryForUrl(url);
        if(entry == null) {
            // TODO: Show entry selection dialog
        }
        Intent intent = new Intent(this._app.getApplicationContext(), LinkedBrowserApproveRequestActivity.class);
        intent.putExtra("entryUUID", entry.getUUID());
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
}
