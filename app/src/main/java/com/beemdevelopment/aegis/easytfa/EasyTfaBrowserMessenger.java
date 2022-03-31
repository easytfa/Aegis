package com.beemdevelopment.aegis.easytfa;

import android.util.Log;

import com.beemdevelopment.aegis.encoding.Base64;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;

public class EasyTfaBrowserMessenger {
    private final EasyTfaApiClient _apiClient;
    private final EasyTfaCrypto _crypto;

    public EasyTfaBrowserMessenger(EasyTfaApiClient apiClient, EasyTfaCrypto crypto) {
        _apiClient = apiClient;
        _crypto = crypto;
    }

    private String createEncryptedMessage(PublicKey browserPublicKey, String type, JSONObject content) throws JSONException {
        if(!content.has("type")) {
            content.put("type", type);
        }
        String stringToEncrypt = content.toString();
        return _crypto.encrypt(browserPublicKey, stringToEncrypt);
    }

    public void linkBrowser(PublicKey publicKey, String hash, String secret) throws JSONException, EasyTfaException {
        String encodedLocalPublicKey = _crypto.getEncodedPublicKey();

        JSONObject objectToEncrypt = new JSONObject();
        objectToEncrypt.put("secret", secret);
        objectToEncrypt.put("appPublicKeyHash", _crypto.hashKey(encodedLocalPublicKey));
        String encryptedString = createEncryptedMessage(publicKey, "link", objectToEncrypt);

        JSONObject dataObject = new JSONObject();
        dataObject.put("appPublicKey", encodedLocalPublicKey);

        _apiClient.sendMessage(hash, encryptedString, dataObject);
    }

    public void sendCode(VaultLinkedBrowserEntry entry, String totpUrl, String oneTimePad, String code) {
        try {
            byte[] codeBytes = code.getBytes(StandardCharsets.UTF_8);
            byte[] oneTimePadBytes = Base64.decode(oneTimePad);
            int length = Math.min(codeBytes.length, oneTimePadBytes.length);
            for (int i = 0; i < length; i++) {
                codeBytes[i] ^= oneTimePadBytes[i];
            }

            byte[] publicKeyData = Base64.decode(entry.getBrowserPublicKey());
            X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyData);
            PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(publicKeySpec);

            JSONObject browserMessage = new JSONObject();
            browserMessage.put("url", totpUrl);
            browserMessage.put("code", Base64.encode(codeBytes));
            String encryptedMessage = createEncryptedMessage(publicKey, "code", browserMessage);

            _apiClient.sendMessage(entry.getBrowserPublicKeyHash(), encryptedMessage, null);
        } catch (Exception ex) {
            Log.e("EasyTFA", "Could not send code", ex);
        }
    }
}
