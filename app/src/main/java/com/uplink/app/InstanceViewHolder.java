package com.uplink.app;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class InstanceViewHolder extends RecyclerView.ViewHolder {
    private final TextView tvId;

    public InstanceViewHolder(@NonNull View itemView) {
        super(itemView);
        tvId = itemView.findViewById(R.id.tvId);
    }

    public void bind(String id) {
        tvId.setText(id);
    }
}
