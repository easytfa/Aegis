package com.beemdevelopment.aegis.browserlink;

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

public class BrowserLinkClient {

    private final RequestQueue _requestQueue;
    private AegisApplication _app;
    private final String _serverUrl;
    private BrowserLinkConfig _browserLinkConfig;

    public BrowserLinkClient(AegisApplication app, String serverUrl) {
        _app = app;
        _serverUrl = serverUrl;
        _requestQueue = Volley.newRequestQueue(_app.getApplicationContext());
    }

    public BrowserLinkConfig getConfig() throws BrowserLinkException {
        JSONObject response = request(Request.Method.GET, "/config", null);
        try {
            _browserLinkConfig = new BrowserLinkConfig(response);
            return _browserLinkConfig;
        } catch(Exception ex) {
            throw new BrowserLinkException(ex);
        }
    }

    public void registerNotificationEndpoint(String notificationEndpoint, Collection<String> browserHashes) throws BrowserLinkException {
        try {
            JSONObject requestObject = new JSONObject();
            requestObject.put("browserHashes", new JSONArray(browserHashes));
            requestObject.put("notificationEndpoint", notificationEndpoint);
            request(Request.Method.POST, "register-notification-endpoint", requestObject);
        } catch(Exception ex) {
            throw new BrowserLinkException(ex);
        }
    }

    public String getPublicKeyForHash(String hash) throws BrowserLinkException {
        try {
            JSONObject requestObject = new JSONObject();
            requestObject.put("hash", hash);
            JSONObject response = request(Request.Method.POST, "public-key-by-hash", requestObject);
            return response.getString("publicKey");
        }
        catch (Exception ex) {
            throw new  BrowserLinkException(ex);
        }
    }

    public void sendMessage(String browserHash, String encryptedMessage, JSONObject additionalData) throws BrowserLinkException {
        try {
            JSONObject requestObject = new JSONObject();
            requestObject.put("hash", browserHash);
            requestObject.put("message", encryptedMessage);
            if(additionalData != null) {
                requestObject.put("data", additionalData);
            }
            request(Request.Method.POST, "message", requestObject);
        } catch(Exception ex) {
            throw new BrowserLinkException(ex);
        }
    }

    public JSONObject getRequest(Collection<String> browserHashes) throws BrowserLinkException {
        try {
            JSONArray linkBrowserHashArray = new JSONArray(browserHashes);
            JSONObject requestObject = new JSONObject();
            requestObject.put("hashes", linkBrowserHashArray);
            return request(Request.Method.POST, "code-queries-by-hashes", requestObject);
        } catch (JSONException e) {
            throw new BrowserLinkException(e);
        }
    }

    private JSONObject request(int method, String path, JSONObject body) throws BrowserLinkException {
        try {
            RequestFuture<JSONObject> future = RequestFuture.newFuture();
            String url = _serverUrl + path;

            JsonObjectRequest request = new JsonObjectRequest(method, url, body, future, future);
            _requestQueue.add(request);

            JSONObject response = future.get(2, TimeUnit.SECONDS);
            if(!response.getBoolean("success")) {
                throw new Exception("Request failed. Success false");
            }
            return response;
        } catch (Exception ex) {
            throw new BrowserLinkException(ex);
        }
    }

    public class BrowserLinkException extends Exception {
        public BrowserLinkException(String message) {
            super(message);
        }

        public BrowserLinkException(Throwable cause) {
            super(cause);
        }
    }

    public class BrowserLinkConfig {
        final String _version;
        final Boolean _pushNotificationsSupported;
        final String _pushNotificationApiKey;
        final String _pushNotificationApplicationId;
        final String _pushNotificationProjectId;

        public BrowserLinkConfig(String _version, Boolean _pushNotificationsSupported, String _pushNotificationApiKey, String _pushNotificationApplicationId, String _pushNotificationProjectId) {
            this._version = _version;
            this._pushNotificationsSupported = _pushNotificationsSupported;
            this._pushNotificationApiKey = _pushNotificationApiKey;
            this._pushNotificationApplicationId = _pushNotificationApplicationId;
            this._pushNotificationProjectId = _pushNotificationProjectId;
        }

        public BrowserLinkConfig(JSONObject object) throws JSONException {
            _version = object.getString("version");
            JSONObject push = object.getJSONObject("push");
            _pushNotificationsSupported = object.getBoolean("supported");
            _pushNotificationApiKey = object.getString("apiKey");
            _pushNotificationApplicationId = object.getString("applicationId");
            _pushNotificationProjectId = object.getString("projectId");
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


