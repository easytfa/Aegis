package com.beemdevelopment.aegis.vault;

import com.beemdevelopment.aegis.util.UUIDMap;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

public class VaultLinkedBrowserEntry extends UUIDMap.Value {
    private String _browserName = "";
    private String _browserPublicKey = "";

    private VaultLinkedBrowserEntry(UUID uuid, String browserName, String browserPublicKey) {
        super(uuid);
        _browserName = browserName;
        _browserPublicKey = browserPublicKey;
    }

    public VaultLinkedBrowserEntry(String browserName, String browserPublicKey) {
        super();
        _browserName = browserName;
        _browserPublicKey = browserPublicKey;
    }

    public JSONObject toJson() {
        JSONObject obj = new JSONObject();

        try {
            obj.put("uuid", getUUID().toString());
            obj.put("name", _browserName);
            obj.put("publicKey", _browserPublicKey);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        return obj;
    }

    public static VaultLinkedBrowserEntry fromJson(JSONObject obj) throws VaultEntryException {
        try {
            // if there is no uuid, generate a new one
            UUID uuid;
            if (!obj.has("uuid")) {
                uuid = UUID.randomUUID();
            } else {
                uuid = UUID.fromString(obj.getString("uuid"));
            }

            String publicKey = obj.getString("publicKey");
            String browserName = obj.getString("name");
            VaultLinkedBrowserEntry entry = new VaultLinkedBrowserEntry(uuid, browserName, publicKey);

            return entry;
        } catch (JSONException e) {
            throw new VaultEntryException(e);
        }
    }

    public String getBrowserName() {
        return _browserName;
    }
    public String getBrowserPublicKey() {
        return _browserPublicKey;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof VaultLinkedBrowserEntry)) {
            return false;
        }

        VaultLinkedBrowserEntry entry = (VaultLinkedBrowserEntry) o;
        return super.equals(entry) && equivalates(entry);
    }

    /**
     * Reports whether this entry is equivalent to the given entry. The UUIDs of these
     * entries are ignored during the comparison, so they are not necessarily the same
     * instance.
     */
    public boolean equivalates(VaultLinkedBrowserEntry entry) {
        return getBrowserName().equals(entry.getBrowserName())
                && getBrowserPublicKey().equals(entry.getBrowserPublicKey());
    }

    /**
     * Reports whether this entry has its values set to the defaults.
     */
    public boolean isDefault() {
        return equivalates(getDefault());
    }

    public static VaultLinkedBrowserEntry getDefault() {
        try {
            return new VaultLinkedBrowserEntry("", "");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
