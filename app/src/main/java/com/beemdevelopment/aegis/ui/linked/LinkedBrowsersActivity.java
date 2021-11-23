package com.beemdevelopment.aegis.ui.linked;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.beemdevelopment.aegis.R;
import com.beemdevelopment.aegis.ui.AegisActivity;
import com.beemdevelopment.aegis.ui.ScannerActivity;
import com.beemdevelopment.aegis.ui.dialogs.Dialogs;
import com.beemdevelopment.aegis.ui.tasks.LinkBrowserTask;
import com.beemdevelopment.aegis.vault.VaultLinkedBrowserEntry;
import com.beemdevelopment.aegis.vault.VaultManagerException;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class LinkedBrowsersActivity extends AegisActivity {

    private static final int CODE_SCANNER = 1;
    private ArrayList<VaultLinkedBrowserEntry> entries;
    private LinkedBrowserAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_linked_browsers);
        setSupportActionBar(findViewById(R.id.toolbar));

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(v -> {
            Intent intent = new Intent(this, ScannerActivity.class);
            intent.putExtra("browserLink", true);
            startActivityForResult(intent, CODE_SCANNER);
        });

        entries = new ArrayList<>();
        adapter = new LinkedBrowserAdapter(entries);

        updateLinkedBrowserList();

        RecyclerView entriesView = findViewById(R.id.linked_browser_list);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        entriesView.setLayoutManager(layoutManager);
        entriesView.setAdapter(adapter);
        entriesView.setNestedScrollingEnabled(false);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
       if (resultCode != RESULT_OK) {
            return;
        }

        switch (requestCode) {
            case CODE_SCANNER:
                onScanResult(data);
                break;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    private void updateLinkedBrowserList() {
        entries.clear();
        entries.addAll(getApp().getVaultManager().getLinkedBrowsers().getValues());
        adapter.notifyDataSetChanged();
    }

    private void onScanResult(Intent data) {
        String secret = data.getStringExtra("secret");
        String hash = data.getStringExtra("hash");

        LinkBrowserTask task = new LinkBrowserTask(getApp(), this, (LinkBrowserTask.Result result) -> {
            if(result.isSuccess()) {
                createNewBrowserLinkEntry(result);
            } else {
                Dialogs.showErrorDialog(this, R.string.linking_browser_error, result.getException());
            }
        });

        LinkBrowserTask.Params params = new LinkBrowserTask.Params(secret, hash);
        task.execute(this.getLifecycle(), params);
    }

    private void createNewBrowserLinkEntry(LinkBrowserTask.Result result) {
        for (VaultLinkedBrowserEntry linkedBrowser: getApp().getVaultManager().getLinkedBrowsers()) {
            if(linkedBrowser.getBrowserPublicKey().equals(result.getPubKeyBrowser()))
                return;
        }

        VaultLinkedBrowserEntry linkedBrowser = new VaultLinkedBrowserEntry(result.getBrowserName(), result.getPubKeyBrowser());
        getApp().getVaultManager().getLinkedBrowsers().add(linkedBrowser);
        try {
            getApp().getVaultManager().save(true);
            Toast.makeText(this.getApplicationContext(), "Linked browser", Toast.LENGTH_SHORT).show();
        }
        catch(VaultManagerException ex) {
            Toast.makeText(this.getApplicationContext(), "Could not save vault", Toast.LENGTH_SHORT).show();
        }
        updateLinkedBrowserList();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                break;
            default:
                return super.onOptionsItemSelected(item);
        }

        return true;
    }

}
