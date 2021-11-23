package com.beemdevelopment.aegis.ui.tasks;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import com.beemdevelopment.aegis.AegisApplication;
import com.beemdevelopment.aegis.R;
import com.beemdevelopment.aegis.crypto.CryptoUtils;
import com.beemdevelopment.aegis.crypto.SCryptParameters;
import com.beemdevelopment.aegis.encoding.Base64;
import com.beemdevelopment.aegis.vault.VaultLinkedBrowserEntry;
import com.beemdevelopment.aegis.vault.slots.PasswordSlot;

import org.json.JSONException;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import javax.crypto.SecretKey;

public class LinkBrowserTask extends ProgressDialogTask<LinkBrowserTask.Params, LinkBrowserTask.Result> {
    private Callback _cb;
    private AegisApplication _app;

    public LinkBrowserTask(AegisApplication app, Context context, Callback cb) {
        super(context, context.getString(R.string.linking_browser));
        _app = app;
        _cb = cb;
    }

    @Override
    protected Result doInBackground(LinkBrowserTask.Params... args) {
        setPriority();
        LinkBrowserTask.Params params = args[0];

        if(_app.getVaultManager().getBrowserLinkKeypair() == null) {
            _app.getBrowserLinkManager().generateLocalKeypair();
            Log.i("BrowserLink", "Keypair null, generating new keypair");
        }

        try {
            String publicKeyStr = _app.getBrowserLinkManager().requestPublicKeyForHash(params._hash);

            if(!_app.getBrowserLinkManager().verifyPublicKey(publicKeyStr, params._hash)) {
                return new Result(new Exception("Public key does not match hash"));
            }

            byte[] publicKeyData = Base64.decode(publicKeyStr);
            X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyData);
            PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(publicKeySpec);

            Boolean success = _app.getBrowserLinkManager().linkBrowser(publicKey, params._hash, params._secret);
            return new Result(publicKeyStr, "Chrome Something");
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
        private String _pubKeyBrowser;
        private String _browserName;
        private Exception _exception;

        public Result(String pubKeyBrowser, String browserName) {
            _success = true;
            _pubKeyBrowser = pubKeyBrowser;
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

        public String getPubKeyBrowser() {
            return _pubKeyBrowser;
        }
    }

    public interface Callback {
        void onTaskFinished(LinkBrowserTask.Result result);
    }
}
