package com.nekodown.android;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.nekodown.android.databinding.ItemSearchResultBinding;
import java.util.ArrayList;
import java.util.List;

public class SearchResultAdapter extends RecyclerView.Adapter<SearchResultAdapter.Holder> {
    public interface Listener { 
        void onSelected(SearchResult result); 
        void onPreview(SearchResult result);
        void onDownload(SearchResult result);
    }
    private final List<SearchResult> items = new ArrayList<>();
    private final Listener listener;

    public SearchResultAdapter(Listener listener) {
        this.listener = listener;
    }

    public void replace(List<SearchResult> next) {
        items.clear();
        items.addAll(next);
        notifyDataSetChanged();
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemSearchResultBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        SearchResult item = items.get(position);
        holder.binding.searchTitle.setText(item.title);
        holder.binding.searchArtist.setText(item.artist);
        
        // Formatear detalles
        List<String> details = new ArrayList<>();
        if (item.album != null && !item.album.isEmpty()) details.add(item.album);
        if (item.year != null && !item.year.isEmpty()) details.add(item.year);
        if (item.genre != null && !item.genre.isEmpty()) details.add(item.genre);
        
        if (!details.isEmpty()) {
            holder.binding.searchDetails.setText(String.join(" · ", details));
            holder.binding.searchDetails.setVisibility(View.VISIBLE);
        } else {
            holder.binding.searchDetails.setVisibility(View.GONE);
        }

        if (item.thumbnail == null || item.thumbnail.isEmpty()) {
            Glide.with(holder.itemView).clear(holder.binding.searchImage);
            if (SearchResult.ALBUM.equals(item.kind) || SearchResult.ARTIST.equals(item.kind)) {
                holder.binding.searchImage.setImageDrawable(null);
                holder.binding.searchImage.setBackgroundResource(R.drawable.bg_card); // Fondo simple sin gato
            } else {
                holder.binding.searchImage.setImageResource(R.drawable.ic_neko);
                holder.binding.searchImage.setBackgroundResource(0);
            }
        } else {
            holder.binding.searchImage.setBackgroundResource(0);
            Glide.with(holder.itemView).load(item.thumbnail).centerCrop().into(holder.binding.searchImage);
        }
        
        if (item.duration != null && !item.duration.isEmpty()) {
            holder.binding.searchDuration.setText(item.duration);
            holder.binding.searchDuration.setVisibility(View.VISIBLE);
        } else {
            holder.binding.searchDuration.setVisibility(View.GONE);
        }
        
        // Mostrar acciones rápidas solo para canciones o videos de youtube (no para artistas ni álbumes enteros)
        if (SearchResult.TRACK.equals(item.kind) || SearchResult.YOUTUBE.equals(item.kind) || item.kind == null || item.kind.isEmpty()) {
            holder.binding.actionContainer.setVisibility(View.VISIBLE);
            if (SearchResult.YOUTUBE.equals(item.kind) || item.previewUrl == null || item.previewUrl.isEmpty()) {
                holder.binding.btnPreview.setVisibility(View.GONE);
            } else {
                holder.binding.btnPreview.setVisibility(View.VISIBLE);
            }
        } else {
            holder.binding.actionContainer.setVisibility(View.GONE);
        }
        
        // Clic en la imagen también respeta el comportamiento general
        holder.binding.searchImage.setOnClickListener(v -> {
            if (SearchResult.ARTIST.equals(item.kind) || SearchResult.ALBUM.equals(item.kind)) {
                listener.onSelected(item);
            } else {
                listener.onPreview(item);
            }
        });

        // Clic en todo el elemento abre detalles SOLO para álbumes/artistas. Las canciones lanzan preview.
        holder.itemView.setOnClickListener(v -> {
            if (SearchResult.ARTIST.equals(item.kind) || SearchResult.ALBUM.equals(item.kind)) {
                listener.onSelected(item);
            } else {
                listener.onPreview(item);
            }
        });
        
        holder.binding.btnPreview.setVisibility(View.GONE); // Usamos el reproductor inferior
        holder.binding.btnDownloadQuick.setOnClickListener(v -> listener.onDownload(item));
    }

    @Override public int getItemCount() { return items.size(); }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemSearchResultBinding binding;
        Holder(ItemSearchResultBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
