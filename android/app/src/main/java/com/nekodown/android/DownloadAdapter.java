package com.nekodown.android;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.nekodown.android.databinding.ItemDownloadBinding;
import java.util.ArrayList;
import java.util.List;

public class DownloadAdapter extends RecyclerView.Adapter<DownloadAdapter.Holder> {
    public interface Listener { void onCancel(DownloadItem item); }
    private final List<DownloadItem> items = new ArrayList<>();
    private final Listener listener;

    public DownloadAdapter(Listener listener) {
        this.listener = listener;
    }

    public void replace(List<DownloadItem> next) {
        items.clear();
        items.addAll(next);
        notifyDataSetChanged();
    }

    public void updateItem(int position, DownloadItem item) {
        if (position >= 0 && position < items.size()) {
            items.set(position, item);
            notifyItemChanged(position);
        }
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemDownloadBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        DownloadItem item = items.get(position);
        holder.binding.itemTitle.setText(item.title);
        holder.binding.itemSubtitle.setText(item.subtitle);
        holder.binding.itemProgress.setProgress(item.progress);
        holder.binding.itemPercent.setText(item.progress + "%");
        holder.binding.itemState.setText(item.state);
        boolean active = !item.state.startsWith("Completado")
            && !item.state.startsWith("Error")
            && !item.state.startsWith("Cancelado");
        holder.binding.itemCancel.setVisibility(active ? android.view.View.VISIBLE : android.view.View.GONE);
        holder.binding.itemCancel.setOnClickListener(v -> listener.onCancel(item));
    }

    @Override public int getItemCount() { return items.size(); }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemDownloadBinding binding;
        Holder(ItemDownloadBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
