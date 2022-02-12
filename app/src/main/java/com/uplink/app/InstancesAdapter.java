package com.uplink.app;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class InstancesAdapter extends RecyclerView.Adapter<InstanceViewHolder> {
    private List<String> ids;

    public void updateInstancesList(List<String> ids) {
        this.ids = ids;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public InstanceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new InstanceViewHolder(
                LayoutInflater.from(parent.getContext()).inflate(
                        R.layout.item_instance,
                        parent,
                        false
                )
        );
    }

    @Override
    public void onBindViewHolder(@NonNull InstanceViewHolder holder, int position) {
        holder.bind(ids.get(position));
    }

    @Override
    public int getItemCount() {
        return ids != null ? ids.size() : 0;
    }
}
