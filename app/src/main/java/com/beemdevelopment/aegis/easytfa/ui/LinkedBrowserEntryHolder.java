package com.beemdevelopment.aegis.easytfa.ui;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.beemdevelopment.aegis.R;

public class LinkedBrowserEntryHolder extends RecyclerView.ViewHolder {

    TextView browserNameView;
    TextView lastUsedView;

    public LinkedBrowserEntryHolder(@NonNull View itemView) {
        super(itemView);

        browserNameView = itemView.findViewById(R.id.linked_browser_name);
        lastUsedView = itemView.findViewById(R.id.linked_browser_last_used);
    }

    public void setData(String name, String lastUsed) {
        browserNameView.setText(name);
        lastUsedView.setText(lastUsed);
    }
}
