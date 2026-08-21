package com.nekodown.android;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.nekodown.android.databinding.FragmentAlbumDetailBinding;

import java.util.ArrayList;
import java.util.List;
import com.nekodown.android.MediaDownloadWorker;

public class AlbumDetailFragment extends Fragment {
    private FragmentAlbumDetailBinding binding;
    private SearchResult album;
    private List<SearchResult> tracks = new ArrayList<>();
    private AlbumTrackAdapter adapter;
    private boolean allSelected = false;

    public AlbumDetailFragment(SearchResult album, List<SearchResult> tracks) {
        this.album = album;
        this.tracks = tracks;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentAlbumDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.albumTitle.setText(album.title);
        binding.albumArtist.setText(album.artist);
        Glide.with(this).load(album.thumbnail).centerCrop().into(binding.albumCover);
        binding.trackCount.setText(tracks.size() + " canciones");

        adapter = new AlbumTrackAdapter(tracks);
        binding.trackList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.trackList.setAdapter(adapter);

        binding.btnSelectAll.setOnClickListener(v -> {
            allSelected = !allSelected;
            adapter.selectAll(allSelected);
            binding.btnSelectAll.setText(allSelected ? "Deseleccionar todo" : "Seleccionar todo");
        });

        binding.btnDownloadAlbum.setOnClickListener(v -> {
            List<SearchResult> selected = adapter.getSelectedTracks();
            if (selected.isEmpty()) {
                Toast.makeText(requireContext(), "Selecciona al menos una canción", Toast.LENGTH_SHORT).show();
                return;
            }
            // Add selected tracks to download queue
            // Since this involves WorkManager and YoutubeDL, it should ideally be integrated with HomeFragment's queue manager
            // Or we can just enqueue them directly
            enqueueTracks(selected);
        });
    }

    private void enqueueTracks(List<SearchResult> selected) {
        Toast.makeText(requireContext(), "Iniciando descarga de " + selected.size() + " canciones...", Toast.LENGTH_SHORT).show();
        final android.content.Context appContext = requireActivity().getApplicationContext();
        // Directly enqueue each track for download without extra threading
        for (SearchResult track : selected) {
            try {
                String query = track.artist.replaceAll("\\s+.*$", "") + " " + track.title + " official audio";
                com.yausername.youtubedl_android.YoutubeDLRequest request = new com.yausername.youtubedl_android.YoutubeDLRequest("ytsearch1:" + query);
                request.addOption("--dump-single-json");
                request.addOption("--flat-playlist");
                request.addOption("--playlist-end", "1");
                
                org.json.JSONObject metadata = new org.json.JSONObject(com.yausername.youtubedl_android.YoutubeDL.getInstance()
                    .execute(request, java.util.UUID.randomUUID().toString()).getOut());
                
                org.json.JSONArray entries = metadata.optJSONArray("entries");
                org.json.JSONObject first = entries == null ? null : entries.optJSONObject(0);
                String id = first == null ? "" : first.optString("id");
                
                if (!id.isEmpty()) {
                    String targetUrl = "https://www.youtube.com/watch?v=" + id;
                    
                    androidx.work.Data input = new androidx.work.Data.Builder()
                        .putString("target", targetUrl)
                        .putString("title", track.title)
                        .putString("artist", track.artist.replaceAll("\\s+.*$", ""))
                        .putString("album", album.title)
                        .putString("year", track.year)
                        .putString("genre", track.genre)
                        .putString("artwork", track.thumbnail)
                        .putString("versionType", "Oficial")
                        .putInt("trackNumber", track.trackNumber)
                        .putBoolean("video", false)
                        .putString("quality", "Alta · 320 kbps")
                        .build();

                    androidx.work.OneTimeWorkRequest workRequest = new androidx.work.OneTimeWorkRequest.Builder(MediaDownloadWorker.class)
                        .setInputData(input)
                        .setConstraints(new androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
                        .setBackoffCriteria(androidx.work.BackoffPolicy.LINEAR, 10, java.util.concurrent.TimeUnit.SECONDS)
                        .addTag("nekodown")
                        .build();

                    androidx.work.WorkManager.getInstance(appContext).enqueue(workRequest);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        // Go back to the main search //requireActivity().getSupportFragmentManager().popBackStack();
    }

    private static class AlbumTrackAdapter extends RecyclerView.Adapter<AlbumTrackAdapter.ViewHolder> {
        private final List<SearchResult> tracks;
        private final boolean[] selected;

        public AlbumTrackAdapter(List<SearchResult> tracks) {
            this.tracks = tracks;
            this.selected = new boolean[tracks.size()];
            // Default to unselected (false)
            for (int i = 0; i < selected.length; i++) {
                selected[i] = false;
            }
        }

        public void selectAll(boolean select) {
            for (int i = 0; i < selected.length; i++) {
                selected[i] = select;
            }
            notifyDataSetChanged();
        }

        public List<SearchResult> getSelectedTracks() {
            List<SearchResult> result = new ArrayList<>();
            for (int i = 0; i < tracks.size(); i++) {
                if (selected[i]) result.add(tracks.get(i));
            }
            return result;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_album_track, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            SearchResult track = tracks.get(position);
            holder.text.setText(track.trackNumber + ". " + track.title);
            holder.checkbox.setChecked(selected[position]);
            
            holder.artist.setText(track.artist);
            
            List<String> details = new ArrayList<>();
            if (track.album != null && !track.album.isEmpty()) details.add(track.album);
            if (track.year != null && !track.year.isEmpty()) details.add(track.year);
            if (track.genre != null && !track.genre.isEmpty()) details.add(track.genre);
            
            if (!details.isEmpty()) {
                holder.details.setText(String.join(" · ", details));
                holder.details.setVisibility(View.VISIBLE);
            } else {
                holder.details.setVisibility(View.GONE);
            }
            
            if (track.duration != null && !track.duration.isEmpty()) {
                holder.duration.setText(track.duration);
                holder.duration.setVisibility(View.VISIBLE);
            } else {
                holder.duration.setVisibility(View.GONE);
            }
            
            if (track.thumbnail == null || track.thumbnail.isEmpty()) {
                Glide.with(holder.itemView).clear(holder.image);
                holder.image.setImageResource(R.drawable.ic_neko);
                holder.image.setBackgroundResource(0);
            } else {
                holder.image.setBackgroundResource(0);
                Glide.with(holder.itemView).load(track.thumbnail).centerCrop().into(holder.image);
            }
            
            holder.itemView.setOnClickListener(v -> {
                selected[position] = !selected[position];
                holder.checkbox.setChecked(selected[position]);
            });
            holder.checkbox.setOnClickListener(v -> {
                selected[position] = holder.checkbox.isChecked();
            });
        }

        @Override
        public int getItemCount() {
            return tracks.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView text;
            CheckBox checkbox;
            ImageView image;
            TextView artist;
            TextView details;
            TextView duration;
            
            ViewHolder(View itemView) {
                super(itemView);
                text = itemView.findViewById(R.id.trackTitle);
                checkbox = itemView.findViewById(R.id.trackCheckbox);
                image = itemView.findViewById(R.id.trackImage);
                artist = itemView.findViewById(R.id.trackArtist);
                details = itemView.findViewById(R.id.trackDetails);
                duration = itemView.findViewById(R.id.trackDuration);
            }
        }
    }
}
