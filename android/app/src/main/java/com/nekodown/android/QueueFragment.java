package com.nekodown.android;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class QueueFragment extends Fragment {
    private final List<DownloadItem> jobs = new ArrayList<>();
    private final DownloadAdapter adapter = new DownloadAdapter(this::cancelJob);
    private RecyclerView queueList;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_queue, container, false);
        queueList = view.findViewById(R.id.queueList);
        queueList.setLayoutManager(new LinearLayoutManager(requireContext()));
        queueList.setAdapter(adapter);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        restoreWorkQueue();
    }

    public void addJob(String id, String title, String subtitle) {
        jobs.add(0, new DownloadItem(id, title, subtitle, 0, "En espera"));
        adapter.replace(jobs);
        saveWorkQueue();
        observeWork(UUID.fromString(id));
    }

    private void observeWork(UUID id) {
        WorkManager.getInstance(requireContext()).getWorkInfoByIdLiveData(id).observe(getViewLifecycleOwner(), info -> {
            if (info == null) return;
            int index = findJob(id.toString());
            if (index < 0) return;
            DownloadItem old = jobs.get(index);
            int progress = info.getProgress().getInt("progress", old.progress);
            String state;
            if (info.getState() == WorkInfo.State.ENQUEUED || info.getState() == WorkInfo.State.BLOCKED) {
                state = "En espera";
            } else if (info.getState() == WorkInfo.State.RUNNING) {
                state = info.getProgress().getString("state");
                if (state == null) state = "Preparando";
            } else if (info.getState() == WorkInfo.State.SUCCEEDED) {
                progress = 100;
                state = "Completado · " + info.getOutputData().getString("saved");
            } else if (info.getState() == WorkInfo.State.FAILED) {
                String detail = info.getOutputData().getString("error");
                state = "Error" + (detail == null ? "" : ": " + detail);
            } else {
                state = "Cancelado";
            }
            DownloadItem updated = new DownloadItem(old.id, old.title, old.subtitle, progress, state);
            jobs.set(index, updated);
            adapter.updateItem(index, updated);
            saveWorkQueue();
        });
    }

    private int findJob(String id) {
        for (int i = 0; i < jobs.size(); i++) if (jobs.get(i).id.equals(id)) return i;
        return -1;
    }

    private void cancelJob(DownloadItem item) {
        try {
            WorkManager.getInstance(requireContext()).cancelWorkById(UUID.fromString(item.id));
        } catch (IllegalArgumentException ignored) {}
    }

    private void saveWorkQueue() {
        JSONArray array = new JSONArray();
        try {
            for (DownloadItem item : jobs) {
                JSONObject object = new JSONObject();
                object.put("id", item.id);
                object.put("title", item.title);
                object.put("subtitle", item.subtitle);
                object.put("progress", item.progress);
                object.put("state", item.state);
                array.put(object);
            }
        } catch (Exception ignored) {}
        if (getActivity() != null) {
            requireActivity().getSharedPreferences("nekodown_ui", android.content.Context.MODE_PRIVATE)
                .edit().putString("queue", array.toString()).apply();
        }
    }

    private void restoreWorkQueue() {
        if (getActivity() == null) return;
        String stored = requireActivity().getSharedPreferences("nekodown_ui", android.content.Context.MODE_PRIVATE)
            .getString("queue", "[]");
        try {
            JSONArray array = new JSONArray(stored);
            jobs.clear();
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) continue;
                String id = object.optString("id");
                if (id.isEmpty()) continue;
                jobs.add(new DownloadItem(
                    id,
                    object.optString("title", "Desconocido"),
                    object.optString("subtitle", ""),
                    object.optInt("progress", 0),
                    object.optString("state", "Consultando estado")
                ));
                observeWork(UUID.fromString(id));
            }
        } catch (Exception error) {
            Log.w("NekoDown", "No se pudo restaurar la cola", error);
        }
        adapter.replace(jobs);
    }
}
