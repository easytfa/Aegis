package com.beemdevelopment.aegis.vault;

import android.util.Log;

import com.beemdevelopment.aegis.easytfa.VaultLinkedBrowserEntry;
import com.beemdevelopment.aegis.encoding.Base64;
import com.beemdevelopment.aegis.util.UUIDMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

public class Vault {
    private static final int VERSION = 2;
    private UUIDMap<VaultEntry> _entries = new UUIDMap<>();
    private UUIDMap<VaultLinkedBrowserEntry> _linkedBrowsers = new UUIDMap<>();

    private KeyPair _browserLinkKeypair = null;

    public JSONObject toJson() {
        try {
            JSONArray array = new JSONArray();
            for (VaultEntry e : _entries) {
                array.put(e.toJson());
            }

            JSONArray linkedBrowserArray = new JSONArray();
            for (VaultLinkedBrowserEntry e : _linkedBrowsers) {
                linkedBrowserArray.put(e.toJson());
            }

            JSONObject obj = new JSONObject();
            obj.put("version", VERSION);
            obj.put("entries", array);
            obj.put("linkedBrowsers", linkedBrowserArray);
            if(_browserLinkKeypair != null) {
                obj.put("linkPrivateKey", Base64.encode(_browserLinkKeypair.getPrivate().getEncoded()));
                obj.put("linkPublicKey", Base64.encode(_browserLinkKeypair.getPublic().getEncoded()));
            }
            return obj;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    public static Vault fromJson(JSONObject obj) throws VaultException {
        Vault vault = new Vault();
        UUIDMap<VaultEntry> entries = vault.getEntries();
        UUIDMap<VaultLinkedBrowserEntry> linkedBrowsers = vault.getLinkedBrowsers();

        try {
            int ver = obj.getInt("version");
            if (ver > VERSION) {
                throw new VaultException("Unsupported version");
            }

            if(obj.has("linkPrivateKey") &&  obj.has("linkPublicKey")) {
                try {
                    byte[] privateKeyData = Base64.decode(obj.getString("linkPrivateKey"));
                    byte[] publicKeyData = Base64.decode(obj.getString("linkPublicKey"));
                    PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(privateKeyData);
                    X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyData);
                    PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(publicKeySpec);
                    PrivateKey privateKey = KeyFactory.getInstance("RSA").generatePrivate(privateKeySpec);
                    vault._browserLinkKeypair = new KeyPair(publicKey, privateKey);
                } catch(Exception ex) {
                    Log.e( "BrowserLink","KeyPair parsing failed");
                }
            }

            JSONArray array = obj.getJSONArray("entries");
            for (int i = 0; i < array.length(); i++) {
                VaultEntry entry = VaultEntry.fromJson(array.getJSONObject(i));
                entries.add(entry);
            }

            if(obj.has("linkedBrowsers")) {
                JSONArray linkedBrowsersArray = obj.getJSONArray("linkedBrowsers");
                for (int i = 0; i < linkedBrowsersArray.length(); i++) {
                    VaultLinkedBrowserEntry entry = VaultLinkedBrowserEntry.fromJson(linkedBrowsersArray.getJSONObject(i));
                    linkedBrowsers.add(entry);
                }
            }
        } catch (VaultEntryException | JSONException e) {
            throw new VaultException(e);
        }

        return vault;
    }

    public UUIDMap<VaultEntry> getEntries() {
        return _entries;
    }

    public UUIDMap<VaultLinkedBrowserEntry> getLinkedBrowsers() {
        return _linkedBrowsers;
    }

    public KeyPair getBrowserLinkKeyPair() {
        return _browserLinkKeypair;
    }

    public void setBrowserLinkKeyPair(KeyPair keyPair) {
        _browserLinkKeypair = keyPair;
    }
}
