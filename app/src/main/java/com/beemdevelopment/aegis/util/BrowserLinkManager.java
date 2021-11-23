package com.beemdevelopment.aegis.util;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.RequestFuture;
import com.android.volley.toolbox.Volley;
import com.beemdevelopment.aegis.AegisApplication;
import com.beemdevelopment.aegis.encoding.Base64;
import com.beemdevelopment.aegis.encoding.Hex;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

public class BrowserLinkManager {
    private AegisApplication _app;
    private String _serverAddress = "http://10.0.2.2:3000/";

    public BrowserLinkManager(AegisApplication app) {
        _app = app;
    }

    public void generateLocalKeypair() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(4096);
            KeyPair keyPair = kpg.generateKeyPair();
            _app.getVaultManager().setBrowserLinkKeyPair(keyPair);
        } catch(Exception ex) {}
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
        Volley.newRequestQueue(_app.getApplicationContext()).add(request);

        JSONObject response = future.get(10, TimeUnit.SECONDS);
        return response.getString("publicKey");
    }

    public Boolean linkBrowser(PublicKey publicKey, String hash, String secret) throws JSONException, InterruptedException, ExecutionException, TimeoutException {
        RequestFuture<JSONObject> future = RequestFuture.newFuture();
        String url = _serverAddress + "link";

        JSONObject objectToEncrypt = new JSONObject();
        objectToEncrypt.put("secret", secret);
        objectToEncrypt.put("publicKey", getEncodedLocalPublicKey());
        String stringToEncrypt = objectToEncrypt.toString();
        String encryptedString = encryptWithPublicKey(stringToEncrypt, publicKey);

        JSONObject requestObject = new JSONObject();
        requestObject.put("hash", hash);
        requestObject.put("message", encryptedString);

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, requestObject, future, future);
        Volley.newRequestQueue(_app.getApplicationContext()).add(request);

        JSONObject response = future.get(10, TimeUnit.SECONDS);
        return response.getBoolean("success");
    }

    public Boolean verifyPublicKey(String encodedPublicKey, String hash) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String hashedKey = Hex.encode(digest.digest(encodedPublicKey.getBytes(StandardCharsets.UTF_8)));
            return hashedKey.equals(hash);
        } catch(Exception ex) {
            return false;
        }
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
}
