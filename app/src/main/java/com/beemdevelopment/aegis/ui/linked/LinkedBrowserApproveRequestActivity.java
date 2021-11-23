package com.beemdevelopment.aegis.ui.linked;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.amulyakhare.textdrawable.TextDrawable;
import com.beemdevelopment.aegis.R;
import com.beemdevelopment.aegis.helpers.IconViewHelper;
import com.beemdevelopment.aegis.helpers.TextDrawableHelper;
import com.beemdevelopment.aegis.ui.AegisActivity;
import com.beemdevelopment.aegis.ui.ScannerActivity;
import com.beemdevelopment.aegis.ui.dialogs.Dialogs;
import com.beemdevelopment.aegis.ui.glide.IconLoader;
import com.beemdevelopment.aegis.ui.tasks.LinkBrowserTask;
import com.beemdevelopment.aegis.vault.VaultEntry;
import com.beemdevelopment.aegis.vault.VaultLinkedBrowserEntry;
import com.beemdevelopment.aegis.vault.VaultManagerException;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.UUID;

public class LinkedBrowserApproveRequestActivity extends AegisActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browser_request_approve);

        Intent intent = getIntent();
        UUID entryUUID = (UUID) intent.getSerializableExtra("entryUUID");
        VaultEntry entry = getApp().getVaultManager().getEntryByUUID(entryUUID);

        ImageView imageView = findViewById(R.id.profile_icon);
        if (entry.hasIcon()) {
            IconViewHelper.setLayerType(imageView, entry.getIconType());
            Glide.with(this)
                    .asDrawable()
                    .load(entry)
                    .set(IconLoader.ICON_TYPE, entry.getIconType())
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(false)
                    .into(imageView);
        } else {
            TextDrawable drawable = TextDrawableHelper.generate(entry.getIssuer(), entry.getName(), imageView);
            imageView.setImageDrawable(drawable);
        }

        TextView textView = findViewById(R.id.profile_account_name);
        textView.setText(entry.getIssuer());

        Button approveButton = findViewById(R.id.approveButton);
        approveButton.setOnClickListener(l -> {
            setApproval(true);
        });

        Button denyButton = findViewById(R.id.denyButton);
        denyButton.setOnClickListener(l -> {
            setApproval(false);
        });
    }

    private void setApproval(boolean approval) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra("approved", false);
        setResult(RESULT_OK, resultIntent);
        finish();
    }
}
