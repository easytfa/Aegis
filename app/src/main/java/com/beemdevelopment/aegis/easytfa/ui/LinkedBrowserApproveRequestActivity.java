package com.beemdevelopment.aegis.easytfa.ui;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.amulyakhare.textdrawable.TextDrawable;
import com.beemdevelopment.aegis.R;
import com.beemdevelopment.aegis.helpers.IconViewHelper;
import com.beemdevelopment.aegis.helpers.TextDrawableHelper;
import com.beemdevelopment.aegis.helpers.UiRefresher;
import com.beemdevelopment.aegis.otp.OtpInfo;
import com.beemdevelopment.aegis.otp.SteamInfo;
import com.beemdevelopment.aegis.otp.TotpInfo;
import com.beemdevelopment.aegis.ui.AegisActivity;
import com.beemdevelopment.aegis.ui.glide.IconLoader;
import com.beemdevelopment.aegis.ui.views.TotpProgressBar;
import com.beemdevelopment.aegis.vault.VaultEntry;
import com.beemdevelopment.aegis.easytfa.VaultLinkedBrowserEntry;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.util.UUID;

public class LinkedBrowserApproveRequestActivity extends AegisActivity {

    private UiRefresher _refresher;
    private TotpProgressBar _progressBar;
    private TextView _issuerTextView;
    private TextView _profileCode;
    private TextView _checksumTextView;
    private VaultEntry _entry;
    private int _codeGroupSize = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browser_request_approve);

        Intent intent = getIntent();
        UUID entryUUID = (UUID) intent.getSerializableExtra("entryUUID");
        String browserPubKeyHash = intent.getStringExtra("browserPubKeyHash");
        String url = intent.getStringExtra("url");
        String oneTimePad = intent.getStringExtra("oneTimePad");
        String checksum = intent.getStringExtra("checksum");

        _entry = getApp().getVaultManager().getEntryByUUID(entryUUID);

        ImageView imageView = findViewById(R.id.profile_icon);
        if (_entry.hasIcon()) {
            IconViewHelper.setLayerType(imageView, _entry.getIconType());
            Glide.with(this)
                    .asDrawable()
                    .load(_entry)
                    .set(IconLoader.ICON_TYPE, _entry.getIconType())
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(false)
                    .into(imageView);
        } else {
            TextDrawable drawable = TextDrawableHelper.generate(_entry.getIssuer(), _entry.getName(), imageView);
            imageView.setImageDrawable(drawable);
        }

        _progressBar = findViewById(R.id.progressBar);

        _profileCode = findViewById(R.id.profile_code);
        _issuerTextView = findViewById(R.id.profile_issuer);
        _issuerTextView.setText(_entry.getIssuer());
        _checksumTextView = findViewById(R.id.profile_checksum);
        _checksumTextView.setText(checksum);

        Button approveButton = findViewById(R.id.approveButton);
        approveButton.setOnClickListener(l -> {

            VaultLinkedBrowserEntry linkedBrowserEntry = getApp().getEasyTfaManager().getEntryByPubKeyHash(browserPubKeyHash);
            AsyncTask.execute(() -> getApp().getEasyTfaManager().sendCode(linkedBrowserEntry, url, oneTimePad, _entry.getInfo().getOtp()));
            ;

            setApproval(true);
        });

        Button denyButton = findViewById(R.id.denyButton);
        denyButton.setOnClickListener(l -> {
            setApproval(false);
        });

        _refresher = new UiRefresher(new UiRefresher.Listener() {
            @Override
            public void onRefresh() {
                updateCode();
            }

            @Override
            public long getMillisTillNextRefresh() {
                return ((TotpInfo) _entry.getInfo()).getMillisTillNextRotation();
            }
        });

        startRefreshLoop();
    }

    public void refresh() {
        _progressBar.restart();
        updateCode();
    }

    private void updateCode() {
        OtpInfo info = _entry.getInfo();

        String otp = info.getOtp();
        if (!(info instanceof SteamInfo)) {
            otp = formatCode(otp);
        }

        _profileCode.setText(otp);
    }

    private String formatCode(String code) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < code.length(); i++) {
            if (i != 0 && i % _codeGroupSize == 0) {
                sb.append(" ");
            }
            sb.append(code.charAt(i));
        }
        code = sb.toString();

        return code;
    }

    public void startRefreshLoop() {
        _refresher.start();
        _progressBar.start();
    }

    public void stopRefreshLoop() {
        _refresher.stop();
        _progressBar.stop();
    }

    private void setApproval(boolean approval) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra("approved", false);
        setResult(RESULT_OK, resultIntent);
        finish();
    }
}
