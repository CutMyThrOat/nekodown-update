package com.nekodown.android;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryFragment extends Fragment {

    private RecyclerView historyList;
    private View clearHistoryButton;
    private HistoryAdapter adapter;
    private List<HistoryItem> items = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_history, container, false);
        historyList = view.findViewById(R.id.historyList);
        clearHistoryButton = view.findViewById(R.id.clearHistoryButton);

        historyList.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new HistoryAdapter();
        historyList.setAdapter(adapter);

        clearHistoryButton.setOnClickListener(v -> {
            if (getContext() != null) {
                getContext().getSharedPreferences("nekodown_history", Context.MODE_PRIVATE)
                        .edit().clear().apply();
                loadHistory();
            }
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadHistory();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            loadHistory();
        }
    }

    private void loadHistory() {
        if (getContext() == null) return;
        items.clear();
        String json = getContext().getSharedPreferences("nekodown_history", Context.MODE_PRIVATE)
                .getString("items", "[]");
        try {
            JSONArray array = new JSONArray(json);
            // Read backwards to show newest first
            for (int i = array.length() - 1; i >= 0; i--) {
                JSONObject obj = array.getJSONObject(i);
                items.add(new HistoryItem(
                        obj.optString("title", "Desconocido"),
                        obj.optString("format", ""),
                        obj.optString("artwork", ""),
                        obj.optLong("time", 0)
                ));
            }
        } catch (Exception ignored) {}
        adapter.notifyDataSetChanged();
    }

    private static class HistoryItem {
        String title;
        String format;
        String artwork;
        long time;

        HistoryItem(String title, String format, String artwork, long time) {
            this.title = title;
            this.format = format;
            this.artwork = artwork;
            this.time = time;
        }
    }

    private class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

        private SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            HistoryItem item = items.get(position);
            holder.title.setText(item.title);
            holder.format.setText(item.format);
            holder.time.setText(format.format(new Date(item.time)));

            if (item.artwork != null && !item.artwork.isEmpty()) {
                Glide.with(HistoryFragment.this).load(item.artwork).into(holder.image);
            } else {
                holder.image.setImageResource(0); // or a placeholder
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView image;
            TextView title;
            TextView format;
            TextView time;

            ViewHolder(View itemView) {
                super(itemView);
                image = itemView.findViewById(R.id.historyImage);
                title = itemView.findViewById(R.id.historyTitle);
                format = itemView.findViewById(R.id.historyFormat);
                time = itemView.findViewById(R.id.historyTime);
            }
        }
    }
}
