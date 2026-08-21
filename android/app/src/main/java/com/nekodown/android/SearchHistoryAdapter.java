package com.nekodown.android;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class SearchHistoryAdapter extends ArrayAdapter<String> {

    private final List<String> historyList;
    private final List<String> filteredList;
    private final Listener listener;

    public interface Listener {
        void onDeleteClick(String item);
    }

    public SearchHistoryAdapter(@NonNull Context context, @NonNull List<String> historyList, Listener listener) {
        super(context, R.layout.item_search_history, historyList);
        this.historyList = new ArrayList<>(historyList);
        this.filteredList = new ArrayList<>(historyList);
        this.listener = listener;
    }

    public void updateData(List<String> newData) {
        historyList.clear();
        historyList.addAll(newData);
        getFilter().filter(""); // Trigger re-filter to show updated items
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_search_history, parent, false);
        }

        String item = getItem(position);
        TextView text = convertView.findViewById(R.id.historyText);
        ImageButton deleteBtn = convertView.findViewById(R.id.historyDelete);

        text.setText(item);

        deleteBtn.setOnClickListener(v -> {
            if (listener != null) listener.onDeleteClick(item);
        });

        return convertView;
    }
    
    @NonNull
    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults results = new FilterResults();
                List<String> suggestions = new ArrayList<>();
                if (constraint == null || constraint.length() == 0) {
                    suggestions.addAll(historyList);
                } else {
                    String filterPattern = constraint.toString().toLowerCase().trim();
                    for (String item : historyList) {
                        if (item.toLowerCase().contains(filterPattern)) {
                            suggestions.add(item);
                        }
                    }
                }
                results.values = suggestions;
                results.count = suggestions.size();
                return results;
            }

            @Override
            @SuppressWarnings("unchecked")
            protected void publishResults(CharSequence constraint, FilterResults results) {
                clear();
                if (results != null && results.count > 0) {
                    addAll((List<String>) results.values);
                    notifyDataSetChanged();
                } else {
                    notifyDataSetInvalidated();
                }
            }
        };
    }
}
