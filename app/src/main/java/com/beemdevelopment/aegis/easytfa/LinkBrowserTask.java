package com.beemdevelopment.aegis.easytfa;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.beemdevelopment.aegis.AegisApplication;
import com.beemdevelopment.aegis.R;
import com.beemdevelopment.aegis.encoding.Base64;
import com.beemdevelopment.aegis.ui.tasks.ProgressDialogTask;
import com.beemdevelopment.aegis.vault.VaultManagerException;

import org.json.JSONObject;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;

public class LinkBrowserTask extends ProgressDialogTask<LinkBrowserTask.Params, LinkBrowserTask.Result> {
    private final EasyTfaManager _manager;
    private Callback _cb;

    public LinkBrowserTask(EasyTfaManager manager, Context context, Callback cb) {
        super(context, context.getString(R.string.linking_browser));
        _manager = manager;
        _cb = cb;
    }

    @Override
    protected Result doInBackground(LinkBrowserTask.Params... args) {
        setPriority();
        LinkBrowserTask.Params params = args[0];

        try {
            JSONObject response = _manager.getApiClient().getPublicKeyForHash(params._hash);
            String publicKeyStr = response.getString("publicKey");
            String connectionId = response.getString("connectionId");

            if(!_manager.getCrypto().verifyPublicKey(publicKeyStr, params._hash)) {
                return new Result(new Exception("Public key does not match hash"));
            }

            byte[] publicKeyData = Base64.decode(publicKeyStr);
            X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyData);
            PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(publicKeySpec);

            String browserName = "Unknown Browser";

            _manager.getBrowserMessenger().linkBrowser(publicKey, connectionId, params._secret);
            _manager.addLinkedBrowser(browserName, publicKeyStr);
            return new Result(browserName);
        } catch (Exception e) {
            e.printStackTrace();
            return new Result(e);
        }

    }

    @Override
    protected void onPostExecute(Result result) {
        super.onPostExecute(result);
        _cb.onTaskFinished(result);
    }

    public static class Params {
        private String _secret;
        private String _hash;

        public Params(String secret, String hash) {
            _secret = secret;
            _hash = hash;
        }

        public String getSecret() {
            return _secret;
        }

        public String getHash() {
            return _hash;
        }
    }

    public static class Result {
        private Boolean _success;
        private String _browserName;
        private Exception _exception;

        public Result(String browserName) {
            _success = true;
            _browserName = browserName;
        }

        public Result(Exception exception) {
            _exception = exception;
            _success = false;
        }

        public String getBrowserName() {
            return _browserName;
        }

        public Boolean isSuccess() {
            return _success;
        }

        public Exception getException() {
            return _exception;
        }
    }

    public interface Callback {
        void onTaskFinished(LinkBrowserTask.Result result);
    }
}
