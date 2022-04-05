package com.beemdevelopment.aegis.easytfa.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.beemdevelopment.aegis.R;
import com.beemdevelopment.aegis.easytfa.VaultLinkedBrowserEntry;

import java.util.List;

public class LinkedBrowserAdapter extends RecyclerView.Adapter<LinkedBrowserEntryHolder> {
    private List<VaultLinkedBrowserEntry> _linkedBrowsers;

    public LinkedBrowserAdapter(List<VaultLinkedBrowserEntry> _linkedBrowsers) {
        this._linkedBrowsers = _linkedBrowsers;
    }

    @NonNull
    @Override
    public LinkedBrowserEntryHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View view = inflater.inflate(R.layout.linked_browser_entry, parent, false);
        LinkedBrowserEntryHolder holder = new LinkedBrowserEntryHolder(view);
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull LinkedBrowserEntryHolder holder, int position) {
        VaultLinkedBrowserEntry entry = _linkedBrowsers.get(position);
        holder.setData(entry.getBrowserName(), "Never Used");
    }

    @Override
    public int getItemCount() {
        return _linkedBrowsers.size();
    }
}
