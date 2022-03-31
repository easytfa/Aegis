package com.beemdevelopment.aegis.easytfa;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.RequestFuture;
import com.android.volley.toolbox.Volley;
import com.beemdevelopment.aegis.AegisApplication;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

public class EasyTfaApiClient {

    private final RequestQueue _requestQueue;
    private final AegisApplication _app;
    private final String _serverUrl;
    private EasyTfaConfig _easyTfaConfig;

    public EasyTfaApiClient(AegisApplication app, String serverUrl) {
        _app = app;
        _serverUrl = serverUrl;
        _requestQueue = Volley.newRequestQueue(_app.getApplicationContext());
    }

    public EasyTfaConfig getConfig() throws EasyTfaException {
        JSONObject response = apiRequest(Request.Method.GET, "config", null);
        try {
            _easyTfaConfig = new EasyTfaConfig(response);
            return _easyTfaConfig;
        } catch(Exception ex) {
            throw new EasyTfaException(ex);
        }
    }

    public void registerNotificationEndpoint(String notificationEndpoint, Collection<String> browserHashes) throws EasyTfaException {
        try {
            JSONObject requestObject = new JSONObject();
            requestObject.put("browserHashes", new JSONArray(browserHashes));
            requestObject.put("notificationEndpoint", notificationEndpoint);
            apiRequest(Request.Method.POST, "register-notification-endpoint", requestObject);
        } catch(Exception ex) {
            throw new EasyTfaException(ex);
        }
    }

    public String getPublicKeyForHash(String hash) throws EasyTfaException {
        try {
            JSONObject requestObject = new JSONObject();
            requestObject.put("hash", hash);
            JSONObject response = apiRequest(Request.Method.POST, "public-key-by-hash", requestObject);
            return response.getString("publicKey");
        }
        catch (Exception ex) {
            throw new EasyTfaException(ex);
        }
    }

    public void sendMessage(String browserHash, String encryptedMessage, JSONObject additionalData) throws EasyTfaException {
        try {
            JSONObject requestObject = new JSONObject();
            requestObject.put("hash", browserHash);
            requestObject.put("message", encryptedMessage);
            if(additionalData != null) {
                requestObject.put("data", additionalData);
            }
            apiRequest(Request.Method.POST, "message", requestObject);
        } catch(Exception ex) {
            throw new EasyTfaException(ex);
        }
    }

    public JSONObject getRequest(Collection<String> browserHashes) throws EasyTfaException {
        try {
            JSONArray linkBrowserHashArray = new JSONArray(browserHashes);
            JSONObject requestObject = new JSONObject();
            requestObject.put("hashes", linkBrowserHashArray);
            return apiRequest(Request.Method.POST, "code-queries-by-hashes", requestObject);
        } catch (JSONException e) {
            throw new EasyTfaException(e);
        }
    }

    private JSONObject apiRequest(int method, String path, JSONObject body) throws EasyTfaException {
        try {
            RequestFuture<JSONObject> future = RequestFuture.newFuture();
            String url = _serverUrl + path;

            JsonObjectRequest request = new JsonObjectRequest(method, url, body, future, future);
            _requestQueue.add(request);

            JSONObject response = future.get(5, TimeUnit.SECONDS);
            if(!response.getBoolean("success")) {
                throw new Exception("Request failed. Success false");
            }
            return response;
        } catch (Exception ex) {
            throw new EasyTfaException(ex);
        }
    }

    public class EasyTfaConfig {
        final String _version;
        final Boolean _pushNotificationsSupported;
        final String _pushNotificationApiKey;
        final String _pushNotificationApplicationId;
        final String _pushNotificationProjectId;

        public EasyTfaConfig(String _version, Boolean _pushNotificationsSupported, String _pushNotificationApiKey, String _pushNotificationApplicationId, String _pushNotificationProjectId) {
            this._version = _version;
            this._pushNotificationsSupported = _pushNotificationsSupported;
            this._pushNotificationApiKey = _pushNotificationApiKey;
            this._pushNotificationApplicationId = _pushNotificationApplicationId;
            this._pushNotificationProjectId = _pushNotificationProjectId;
        }

        public EasyTfaConfig(JSONObject object) throws JSONException {
            _version = object.getString("version");
            JSONObject push = object.getJSONObject("push");
            _pushNotificationsSupported = push.getBoolean("supported");
            _pushNotificationApiKey = push.getString("apiKey");
            _pushNotificationApplicationId = push.getString("applicationId");
            _pushNotificationProjectId = push.getString("projectId");
        }

        public String getVersion() {
            return _version;
        }

        public Boolean pushNotificationsSupported() {
            return _pushNotificationsSupported;
        }

        public String getPushNotificationApiKey() {
            return _pushNotificationApiKey;
        }

        public String getPushNotificationApplicationId() {
            return _pushNotificationApplicationId;
        }

        public String getPushNotificationProjectId() {
            return _pushNotificationProjectId;
        }
    }
}


