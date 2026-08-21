package com.nekodown.android;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.work.Data;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import com.bumptech.glide.Glide;
import com.nekodown.android.databinding.FragmentHomeBinding;
import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;

import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.ViewGroup;

public class HomeFragment extends Fragment {
    private FragmentHomeBinding binding;
    private final ExecutorService queue = Executors.newSingleThreadExecutor();
            private final SearchResultAdapter searchAdapter = new SearchResultAdapter(new SearchResultAdapter.Listener() {
        @Override
        public void onSelected(SearchResult result) {
            if (SearchResult.ARTIST.equals(result.kind)) {
                loadArtistAlbums(result);
            } else if (SearchResult.ALBUM.equals(result.kind)) {
                loadAlbumTracks(result);
            }
        }
        @Override
        public void onPreview(SearchResult result) {
            stopPreview();
            preparedPreview = "";
            binding.playerInclude.getRoot().setVisibility(View.VISIBLE);
            binding.playerInclude.playerTitle.setText(result.title);
            binding.playerInclude.playerArtist.setText(result.artist);
            binding.playerInclude.playerTimeCurrent.setText("0:00");
            binding.playerInclude.playerTimeTotal.setText("0:00");
            binding.playerInclude.playerSeekBar.setProgress(0);
            binding.playerInclude.playerLoading.setVisibility(View.VISIBLE);
            binding.playerInclude.playerPlayPause.setVisibility(View.GONE);
            
            if (result.thumbnail != null && !result.thumbnail.isEmpty()) {
                Glide.with(requireContext()).load(result.thumbnail).into(binding.playerInclude.playerCover);
            }
            
            preparedTarget = result.id != null && !result.id.isEmpty() && SearchResult.YOUTUBE.equals(result.kind) 
                ? "https://www.youtube.com/watch?v=" + result.id : "";
            preparedTitle = result.title;
            preparedArtist = result.artist;
            preparedAlbum = result.album != null ? result.album : "";
            preparedVersionType = detectVersionType(result.title);
            preparedTrackNumber = result.trackNumber;
            preparedYear = result.year != null ? result.year : "";
            preparedGenre = result.genre != null ? result.genre : "";

            if (SearchResult.TRACK.equals(result.kind) && result.previewUrl != null && !result.previewUrl.isEmpty()) {
                preparedPreview = result.previewUrl;
                preparedArtwork = result.thumbnail;
                prepareExtras();
            } else {
                enrichSelectedMetadata(result.thumbnail);
            }
        }
        @Override
        public void onDownload(SearchResult result) {
            preparedPreview = "";
            if (SearchResult.TRACK.equals(result.kind)) {
                quickDownload(result);
            } else {
                preparedTarget = "https://www.youtube.com/watch?v=" + result.id;
                preparedTitle = result.title;
                preparedArtist = result.artist;
                preparedVersionType = detectVersionType(result.title);
                enrichSelectedMetadataAndDownload(result.thumbnail);
            }
        }
    });
    private String preparedTarget = "";
    private String preparedTitle = "";
    private String preparedArtist = "";
    private String preparedAlbum = "";
    private String preparedYear = "";
    private String preparedGenre = "";
    private String preparedArtwork = "";
    private String preparedPreview = "";
    private int preparedTrackNumber = 0;
    private String preparedVersionType = "Oficial";
    private boolean mp4 = false;
    private boolean artistMode = false;
    private final List<String> detectedVideoQualities = new ArrayList<>();
    private final List<SearchResult> allSearchResults = new ArrayList<>();
    private int searchPage = 0;
    private String currentSearchQuery = "";
    private boolean searchHasMore = false;
    private static final int RESULTS_PER_PAGE = 12;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile int searchGeneration = 0;
    private volatile String activeSearchProcess = "";
    private String previewUrl = "";
    private MediaPlayer previewPlayer;
    private File previewFile;
    private String catalogArtistId = "";
    private String catalogArtistName = "";
    private String catalogStage = "artists";
    private String lastArtistQuery = "";
    private SearchHistoryAdapter searchHistoryAdapter;
    private final List<String> searchHistoryList = new ArrayList<>();
    private OnBackPressedCallback backPressedCallback;
    // Album overlay state
    private final List<SearchResult> albumOverlayTracks = new ArrayList<>();
    private AlbumTrackAdapter albumTrackAdapter;
    private SearchResult currentAlbumResult;
    private boolean albumAllSelected = false;

    @Override public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();
                        binding.searchResults.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.searchResults.setAdapter(searchAdapter);
        binding.previousPage.setOnClickListener(v -> showSearchPage(searchPage - 1));
        binding.nextPage.setOnClickListener(v -> {
            int next = searchPage + 1;
            if (next * RESULTS_PER_PAGE < allSearchResults.size()) showSearchPage(next);
            else if (searchHasMore) loadSearchPage(next);
        });
        binding.searchModeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            artistMode = checkedId == binding.artistModeButton.getId();
            binding.searchInput.setHint(artistMode
                ? "Escribe un artista o álbum…"
                : "Canción, video o URL…");
            if (!binding.searchInput.getText().toString().trim().isEmpty()) {
                prepare();
            }
        });
        
        loadSearchHistory();
        searchHistoryAdapter = new SearchHistoryAdapter(requireContext(), searchHistoryList, item -> {
            searchHistoryList.remove(item);
            if (searchHistoryAdapter != null) searchHistoryAdapter.updateData(searchHistoryList);
            SharedPreferences prefs = requireContext().getSharedPreferences("nekodown_search_history", android.content.Context.MODE_PRIVATE);
            prefs.edit().putString("history", new org.json.JSONArray(searchHistoryList).toString()).apply();
        });
        binding.searchInput.setAdapter(searchHistoryAdapter);
        binding.searchInput.setOnItemClickListener((parent, view, position, id) -> prepare());
        
        backPressedCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                if (binding.albumOverlay.getVisibility() == View.VISIBLE) {
                    hideAlbumOverlay();
                } else if (binding.catalogHeader.getVisibility() == View.VISIBLE) {
                    navigateCatalogBack();
                }
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), backPressedCallback);
        requestNotificationPermission();
        setAudioQualities();

        binding.formatGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            mp4 = checkedId == binding.mp4Button.getId();
            if (mp4) setVideoQualities(); else setAudioQualities();
        });
        binding.searchButton.setOnClickListener(v -> prepare());
        binding.catalogBackButton.setOnClickListener(v -> navigateCatalogBack());
        
        // Album overlay buttons
        binding.albumBackButton.setOnClickListener(v -> hideAlbumOverlay());
        binding.btnSelectAll.setOnClickListener(v -> {
            albumAllSelected = !albumAllSelected;
            if (albumTrackAdapter != null) albumTrackAdapter.selectAll(albumAllSelected);
            binding.btnSelectAll.setText(albumAllSelected ? "Deseleccionar todo" : "Seleccionar todo");
        });
        binding.btnDownloadAlbum.setOnClickListener(v -> {
            if (albumTrackAdapter == null) return;
            List<SearchResult> selected = albumTrackAdapter.getSelectedTracks();
            if (selected.isEmpty()) {
                Toast.makeText(requireContext(), "Selecciona al menos una canción", Toast.LENGTH_SHORT).show();
                return;
            }
            enqueueAlbumTracks(selected);
        });
        
        binding.playerInclude.playerClose.setOnClickListener(v -> {
            stopPreview();
            binding.playerInclude.getRoot().setVisibility(View.GONE);
        });
        binding.playerInclude.playerPlayPause.setOnClickListener(v -> togglePreview());
        binding.playerInclude.playerDownload.setOnClickListener(v -> {
             if (preparedTarget != null && !preparedTarget.isEmpty()) {
                  enqueueTarget(preparedTarget, preparedTitle, preparedArtist, preparedAlbum, preparedYear, preparedGenre, preparedArtwork, preparedVersionType, preparedTrackNumber);
             }
        });
        binding.playerInclude.playerSeekBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && previewPlayer != null) {
                    previewPlayer.seekTo(progress);
                    binding.playerInclude.playerTimeCurrent.setText(formatSeconds(progress / 1000));
                }
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });

        acceptSharedText(requireActivity().getIntent());
        initializeEngine();
        return root;
    }

    private void initializeEngine() {
        binding.serverStatus.setText("● Preparando motor…");
        binding.searchButton.setEnabled(false);
        binding.loading.setVisibility(View.VISIBLE);
        queue.execute(() -> {
            try {
                YoutubeDL.getInstance().init(requireActivity().getApplication());
                FFmpeg.getInstance().init(requireActivity().getApplication());
                if(getActivity()!=null) requireActivity().runOnUiThread(() -> {
                    binding.serverStatus.setText("● Listo");
                    binding.searchButton.setEnabled(true);
                    binding.loading.setVisibility(View.GONE);
                });
            } catch (Throwable error) {
                Log.e("NekoDown", "No se pudo preparar el enlace", error);
                if(getActivity()!=null) requireActivity().runOnUiThread(() -> {
                    binding.serverStatus.setText("● Error del motor");
                    binding.loading.setVisibility(View.GONE);
                    Toast.makeText(requireContext(), "No se pudo iniciar yt-dlp: " + error.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    public void onNewIntent(Intent intent) {
        //super.onNewIntent(intent);
        acceptSharedText(intent);
    }

    private void acceptSharedText(Intent intent) {
        if (Intent.ACTION_SEND.equals(intent.getAction()) && "text/plain".equals(intent.getType())) {
            String shared = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (!TextUtils.isEmpty(shared)) binding.searchInput.setText(shared);
        }
    }

    private void setAudioQualities() {
        setSpinner(Arrays.asList("Alta · 320 kbps", "Muy buena · 256 kbps", "Media · 192 kbps", "Ligera · 128 kbps"));
    }

    private void setVideoQualities() {
        setSpinner(detectedVideoQualities.isEmpty()
            ? Arrays.asList("Mejor disponible")
            : new ArrayList<>(detectedVideoQualities));
    }

    private void setSpinner(List<String> choices) {
        ArrayAdapter<String> spinner = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, choices);
        binding.qualitySpinner.setAdapter(spinner);
    }

    private void prepare() {
        String value = binding.searchInput.getText().toString().trim();
        if (value.isEmpty()) {
            binding.searchInput.setError("Escribe una canción o pega un enlace");
            return;
        }
        boolean directUrl = value.startsWith("http://") || value.startsWith("https://");
        if (!directUrl) currentSearchQuery = value;
        
        saveSearchQuery(value);
        
        int generation = ++searchGeneration;
        if (!activeSearchProcess.isEmpty()) YoutubeDL.getInstance().destroyProcessById(activeSearchProcess);
        activeSearchProcess = UUID.randomUUID().toString();
        String processId = activeSearchProcess;
        // Igual que la versión de escritorio: la primera página debe aparecer
        // rápido. Las páginas siguientes se podrán ampliar sin bloquear este
        // primer resultado.
        String requestTarget = directUrl
            ? value
            : "ytsearch12:" + value + (artistMode ? " official audio" : "");
        preparedTarget = directUrl ? value : "";
        preparedTitle = value;
        preparedArtist = "";
        preparedAlbum = "";
        preparedYear = "";
        preparedGenre = "";
        preparedArtwork = "";
        preparedPreview = "";
        preparedTrackNumber = 0;
        preparedVersionType = "Oficial";
        binding.searchResults.setVisibility(View.GONE);
        binding.searchPagination.setVisibility(View.GONE);
//         binding.resultCard.setVisibility(View.GONE);
//         binding.selectionHint.setVisibility(View.GONE);
        binding.loading.setVisibility(View.VISIBLE);
        binding.searchButton.setEnabled(false);
        mainHandler.postDelayed(() -> {
            if (generation != searchGeneration || !processId.equals(activeSearchProcess)) return;
            YoutubeDL.getInstance().destroyProcessById(processId);
            searchGeneration++;
            activeSearchProcess = "";
            binding.loading.setVisibility(View.GONE);
            binding.searchButton.setEnabled(true);
//             binding.resultTitle.setText("La búsqueda tardó demasiado");
//             binding.resultArtist.setText("Revisa tu conexión e inténtalo nuevamente.");
//             binding.resultCard.setVisibility(View.VISIBLE);
        }, 45000);
        if (artistMode && !directUrl) {
            searchArtistCatalog(value, generation);
            return;
        }
        queue.execute(() -> {
            boolean fastDirectDisplayed = false;
            try {
                // Para un enlace individual de YouTube, oEmbed entrega título,
                // autor y miniatura mucho antes que el análisis completo de
                // formatos. En MP3 eso basta para mostrar el resultado y el
                // enriquecimiento musical puede empezar de inmediato.
                if (directUrl && isYouTubeUrl(value)) {
                    HttpURLConnection quickConnection = null;
                    try {
                        String endpoint = "https://www.youtube.com/oembed?format=json&url="
                            + URLEncoder.encode(value, StandardCharsets.UTF_8.name());
                        quickConnection = (HttpURLConnection) new URL(endpoint).openConnection();
                        quickConnection.setConnectTimeout(5000);
                        quickConnection.setReadTimeout(6000);
                        quickConnection.setRequestProperty("User-Agent", "NekoDown-Android/1.2");
                        if (quickConnection.getResponseCode() == 200) {
                            JSONObject quick = readJson(quickConnection);
                            preparedTitle = quick.optString("title", value);
                            preparedArtist = quick.optString("author_name", "YouTube");
                            preparedArtwork = quick.optString("thumbnail_url");
                            preparedVersionType = detectVersionType(preparedTitle);
                            fastDirectDisplayed = true;
                            String quickArtwork = preparedArtwork;
                            if(getActivity()!=null) requireActivity().runOnUiThread(() -> {
                                if (generation != searchGeneration) return;
                                activeSearchProcess = "";
//                                 binding.resultTitle.setText(preparedTitle);
//                                 binding.resultArtist.setText(preparedArtist);
//                                 binding.resultCard.setVisibility(View.VISIBLE);
//                                 binding.selectionHint.setVisibility(View.GONE);
//                                 binding.downloadButton.setEnabled(true);
//                                 binding.downloadButton.setText(mp4 ? "Descargar MP4" : "Descargar MP3");
//                                 Glide.with(this).load(quickArtwork).centerCrop().into(binding.resultImage);
                                showPreparedMetadata();
                                enrichSelectedMetadata(quickArtwork);
                                finishSearch();
//                                 binding.resultCard.post(() ->
//                                     binding.mainScroll.smoothScrollTo(0, binding.resultCard.getTop()));
                            });
                            if (!mp4) return;
                        }
                    } catch (Exception quickError) {
                        Log.w("NekoDown", "oEmbed no respondió; se usará yt-dlp", quickError);
                    } finally {
                        if (quickConnection != null) quickConnection.disconnect();
                    }
                }
                YoutubeDLRequest request = new YoutubeDLRequest(requestTarget);
                request.addOption("--dump-single-json");
                request.addOption("--no-warnings");
                request.addOption("--socket-timeout", "12");
                request.addOption("--retries", "2");
                request.addOption("--extractor-retries", "2");
                if (directUrl) {
                    request.addOption("--no-playlist");
                } else {
                    request.addOption("--flat-playlist");
                    request.addOption("--playlist-end", "12");
                }
                String json = YoutubeDL.getInstance().execute(request, processId).getOut();
                if (generation != searchGeneration) return;
                JSONObject metadata = new JSONObject(json);
                if (!directUrl) {
                    JSONArray entries = metadata.optJSONArray("entries");
                    List<SearchResult> results = new ArrayList<>();
                    if (entries != null) {
                        for (int i = 0; i < entries.length(); i++) {
                            JSONObject entry = entries.optJSONObject(i);
                            if (entry == null) continue;
                            String id = entry.optString("id");
                            String title = entry.optString("title", "Sin título");
                            String artist = entry.optString("uploader", entry.optString("channel", "YouTube"));
                          if (!id.isEmpty()) results.add(new SearchResult(id, title, artist, thumbnailOf(entry), extractDuration(entry)));
                        }
                    }
                    if (results.isEmpty()) throw new IllegalStateException("No se encontraron canciones.");
                    if(getActivity()!=null) requireActivity().runOnUiThread(() -> {
                        if (generation != searchGeneration) return;
                        activeSearchProcess = "";
                        allSearchResults.clear();
                        allSearchResults.addAll(results);
                        searchHasMore = results.size() == RESULTS_PER_PAGE;
                        searchPage = 0;
                        showSearchPage(0);
                        binding.searchResults.setVisibility(View.VISIBLE);
                        binding.searchPagination.setVisibility(searchHasMore ? View.VISIBLE : View.GONE);
                        preparedTarget = "";
                        preparedTitle = "";
                        preparedArtist = "";
//                         binding.resultCard.setVisibility(View.GONE);
//                         binding.selectionHint.setVisibility(View.VISIBLE);
                        finishSearch();
                        binding.searchResults.post(() ->
                            binding.mainScroll.smoothScrollTo(0, binding.searchResults.getTop()));
                    });
                    return;
                }
                String id = metadata.optString("id");
                String title = metadata.optString("title", value);
                String uploader = metadata.optString("uploader", metadata.optString("channel", "YouTube"));
                String thumbnail = thumbnailOf(metadata);
                if (!TextUtils.isEmpty(id) && isYouTubeUrl(value)) {
                    preparedTarget = "https://www.youtube.com/watch?v=" + id;
                }
                preparedTitle = title;
                preparedArtist = uploader;
                preparedArtwork = thumbnail;
                preparedVersionType = detectVersionType(title);
                detectedVideoQualities.clear();
                TreeSet<Integer> heights = new TreeSet<>(Collections.reverseOrder());
                JSONArray formats = metadata.optJSONArray("formats");
                if (formats != null) {
                    for (int i = 0; i < formats.length(); i++) {
                        JSONObject format = formats.optJSONObject(i);
                        if (format == null || "none".equals(format.optString("vcodec"))) continue;
                        int height = format.optInt("height", 0);
                        if (height > 0) heights.add(height);
                    }
                }
                detectedVideoQualities.add("Mejor disponible");
                for (int height : heights) detectedVideoQualities.add(height + "p");
                if(getActivity()!=null) requireActivity().runOnUiThread(() -> {
                    if (generation != searchGeneration) return;
                    activeSearchProcess = "";
//                     binding.resultTitle.setText(preparedTitle);
//                     binding.resultArtist.setText(preparedArtist);
//                     binding.resultCard.setVisibility(View.VISIBLE);
//                     binding.selectionHint.setVisibility(View.GONE);
//                     binding.downloadButton.setEnabled(true);
//                     binding.downloadButton.setText(mp4 ? "Descargar MP4" : "Descargar MP3");
//                     Glide.with(this).load(thumbnail).centerCrop().into(binding.resultImage);
                    showPreparedMetadata();
                    enrichSelectedMetadata(thumbnail);
                    if (mp4) setVideoQualities();
                    finishSearch();
//                     binding.resultCard.post(() -> binding.mainScroll.smoothScrollTo(0, binding.resultCard.getTop()));
                });
            } catch (Throwable error) {
                if (generation != searchGeneration) return;
                Log.e("NekoDown", "No se pudo consultar yt-dlp para " + requestTarget, error);
                final boolean keepFastResult = fastDirectDisplayed;
                if(getActivity()!=null) requireActivity().runOnUiThread(() -> {
                    if (generation != searchGeneration) return;
                    activeSearchProcess = "";
                    if (keepFastResult) {
                        finishSearch();
                        return;
                    }
                    binding.loading.setVisibility(View.GONE);
                    binding.searchButton.setEnabled(true);
//                     binding.resultTitle.setText("No se pudo preparar el enlace");
//                     binding.resultArtist.setText(error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
//                     binding.resultCard.setVisibility(View.VISIBLE);
                    Toast.makeText(requireContext(), "No se pudo preparar: " + error.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
            && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
        }
    }

    private void searchArtistCatalog(String query, int generation) {
        lastArtistQuery = query;
        catalogStage = "artists";
        queue.execute(() -> {
            HttpURLConnection connection = null;
            try {
                String term = URLEncoder.encode(query, StandardCharsets.UTF_8.name());
                connection = (HttpURLConnection) new URL(
                    "https://itunes.apple.com/search?media=music&entity=album"
                        + "&attribute=artistTerm&limit=50&term=" + term
                ).openConnection();
                connection.setConnectTimeout(7000);
                connection.setReadTimeout(9000);
                connection.setRequestProperty("User-Agent", "NekoDown-Android/1.1");
                if (connection.getResponseCode() != 200) {
                    throw new IllegalStateException("El catálogo no respondió.");
                }
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8
                ));
                StringBuilder payload = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) payload.append(line);
                JSONArray entries = new JSONObject(payload.toString()).optJSONArray("results");
                List<SearchResult> results = new ArrayList<>();
                Set<String> seenArtists = new HashSet<>();
                if (entries != null) {
                    for (int i = 0; i < entries.length(); i++) {
                        JSONObject entry = entries.getJSONObject(i);
                        String artistId = entry.optString("artistId");
                        String artist = entry.optString("artistName");
                        String genre = entry.optString("primaryGenreName");
                        String artwork = largeArtwork(entry.optString("artworkUrl100"));
                        
                        if (!artistId.isEmpty() && !artist.isEmpty() && seenArtists.add(artistId)) {
                            results.add(new SearchResult(
                                artistId, artist, genre.isEmpty() ? "Artista" : genre, artwork, "",
                                SearchResult.ARTIST, "", "", "", genre, 0
                            ));
                        }
                    }
                }
                if (results.isEmpty()) throw new IllegalStateException("No se encontraron artistas.");
                if(getActivity()!=null) requireActivity().runOnUiThread(() -> {
                    if (generation != searchGeneration) return;
                    activeSearchProcess = "";
                    showCatalogResults(results, "Selecciona el artista exacto", false);
                    preparedTarget = "";
//                     binding.resultCard.setVisibility(View.GONE);
//                     binding.selectionHint.setText("Selecciona un artista para ver todos sus álbumes.");
//                     binding.selectionHint.setVisibility(View.VISIBLE);
                    finishSearch();
                    binding.searchResults.post(() ->
                        binding.mainScroll.smoothScrollTo(0, binding.searchResults.getTop()));
                });
            } catch (Exception error) {
                Log.e("NekoDown", "No se pudo consultar el catálogo", error);
                if(getActivity()!=null) requireActivity().runOnUiThread(() -> {
                    if (generation != searchGeneration) return;
                    activeSearchProcess = "";
                    finishSearch();
//                     binding.resultTitle.setText("No se pudo consultar Artista / Álbum");
//                     binding.resultArtist.setText(error.getMessage());
//                     binding.resultCard.setVisibility(View.VISIBLE);
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void loadArtistAlbums(SearchResult artistResult) {
        catalogArtistId = artistResult.id;
        catalogArtistName = artistResult.title;
        catalogStage = "albums";
        binding.loading.setVisibility(View.VISIBLE);
//         binding.resultCard.setVisibility(View.GONE);
        queue.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(
                    "https://itunes.apple.com/lookup?id=" + catalogArtistId
                        + "&entity=album&limit=200"
                ).openConnection();
                connection.setConnectTimeout(7000);
                connection.setReadTimeout(10000);
                connection.setRequestProperty("User-Agent", "NekoDown-Android/1.2");
                if (connection.getResponseCode() != 200) throw new IllegalStateException("El catálogo no respondió.");
                JSONArray entries = readJson(connection).optJSONArray("results");
                List<SearchResult> albums = new ArrayList<>();
                Set<String> seen = new HashSet<>();
                if (entries != null) {
                    for (int i = 0; i < entries.length(); i++) {
                        JSONObject entry = entries.optJSONObject(i);
                        if (entry == null || !"Album".equalsIgnoreCase(entry.optString("collectionType"))) continue;
                        String id = entry.optString("collectionId");
                        if (id.isEmpty() || !seen.add(id)) continue;
                        String year = yearOf(entry.optString("releaseDate"));
                        String genre = entry.optString("primaryGenreName");
                        String artwork = largeArtwork(entry.optString("artworkUrl100"));
                        albums.add(new SearchResult(
                            id,
                            entry.optString("collectionName", "Álbum"),
                            year + (genre.isEmpty() ? "" : " • " + genre),
                            artwork, "",
                            SearchResult.ALBUM, "", entry.optString("collectionName"),
                            year, genre, 0
                        ));
                    }
                }
                if(getActivity()!=null) requireActivity().runOnUiThread(() -> {
                    binding.loading.setVisibility(View.GONE);
                    showCatalogResults(albums, catalogArtistName + " · Álbumes", true);
                });
            } catch (Exception error) {
                if(getActivity()!=null) requireActivity().runOnUiThread(() -> {
                    binding.loading.setVisibility(View.GONE);
                    Toast.makeText(requireContext(), "No se pudieron cargar los álbumes.", Toast.LENGTH_LONG).show();
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void loadAlbumTracks(SearchResult albumResult) {
        catalogStage = "tracks";
        binding.loading.setVisibility(View.VISIBLE);
        queue.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(
                    "https://itunes.apple.com/lookup?id=" + albumResult.id + "&entity=song"
                ).openConnection();
                connection.setConnectTimeout(7000);
                connection.setReadTimeout(10000);
                connection.setRequestProperty("User-Agent", "NekoDown-Android/1.2");
                if (connection.getResponseCode() != 200) throw new IllegalStateException();
                JSONArray entries = readJson(connection).optJSONArray("results");
                List<SearchResult> tracks = new ArrayList<>();
                if (entries != null) {
                    for (int i = 0; i < entries.length(); i++) {
                        JSONObject entry = entries.optJSONObject(i);
                        if (entry == null || !"track".equalsIgnoreCase(entry.optString("wrapperType"))) continue;
                        String year = yearOf(entry.optString("releaseDate"));
                        tracks.add(new SearchResult(
                            entry.optString("trackId"),
                            entry.optString("trackName", "Canción"),
                            entry.optString("artistName", catalogArtistName),
                            largeArtwork(entry.optString("artworkUrl100")),
                            formatTrackDuration(entry.optInt("trackTimeMillis", 0)),
                            SearchResult.TRACK,
                            entry.optString("previewUrl"),
                            entry.optString("collectionName", albumResult.title),
                            year,
                            entry.optString("primaryGenreName", albumResult.genre),
                            entry.optInt("trackNumber", 0)
                        ));
                    }
                }
                if(getActivity()!=null) requireActivity().runOnUiThread(() -> {
                    binding.loading.setVisibility(View.GONE);
                    if (tracks.isEmpty()) {
                        Toast.makeText(requireContext(), "No hay canciones disponibles para este álbum.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    showAlbumOverlay(albumResult, tracks);
                });
            } catch (Exception error) {
                if(getActivity()!=null) requireActivity().runOnUiThread(() -> {
                    binding.loading.setVisibility(View.GONE);
                    Toast.makeText(requireContext(), "No se pudieron cargar las canciones.", Toast.LENGTH_LONG).show();
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void showAlbumOverlay(SearchResult albumResult, List<SearchResult> tracks) {
        currentAlbumResult = albumResult;
        albumOverlayTracks.clear();
        albumOverlayTracks.addAll(tracks);
        albumAllSelected = false;

        binding.albumTitle.setText(albumResult.title);
        binding.albumArtist.setText(albumResult.artist != null && !albumResult.artist.isEmpty()
            ? albumResult.artist : catalogArtistName);
        Glide.with(this).load(albumResult.thumbnail).centerCrop().into(binding.albumCover);
        binding.trackCount.setText(tracks.size() + " canciones");
        binding.btnSelectAll.setText("Seleccionar todo");

        albumTrackAdapter = new AlbumTrackAdapter(tracks);
        binding.albumTrackList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.albumTrackList.setAdapter(albumTrackAdapter);

        binding.mainContent.setVisibility(View.GONE);
        binding.albumOverlay.setVisibility(View.VISIBLE);
        backPressedCallback.setEnabled(true);
    }

    private void hideAlbumOverlay() {
        binding.albumOverlay.setVisibility(View.GONE);
        binding.mainContent.setVisibility(View.VISIBLE);
        backPressedCallback.setEnabled(
            binding.catalogHeader.getVisibility() == View.VISIBLE
        );
    }

    private void enqueueAlbumTracks(List<SearchResult> selected) {
        Toast.makeText(requireContext(), "Iniciando descarga de " + selected.size() + " canciones...", Toast.LENGTH_SHORT).show();
        final android.content.Context appContext = requireActivity().getApplicationContext();
        final String albumName = currentAlbumResult != null ? currentAlbumResult.title : "";

        queue.execute(() -> {
            for (SearchResult track : selected) {
                try {
                    String artistShort = track.artist.replaceAll("\\s+.*$", "");
                    String query = artistShort + " " + track.title + " official audio";
                    YoutubeDLRequest request = new YoutubeDLRequest("ytsearch1:" + query);
                    request.addOption("--dump-single-json");
                    request.addOption("--flat-playlist");
                    request.addOption("--playlist-end", "1");

                    JSONObject metadata = new JSONObject(YoutubeDL.getInstance()
                        .execute(request, UUID.randomUUID().toString()).getOut());

                    JSONArray entries = metadata.optJSONArray("entries");
                    JSONObject first = entries == null ? null : entries.optJSONObject(0);
                    String id = first == null ? "" : first.optString("id");

                    if (!id.isEmpty()) {
                        String targetUrl = "https://www.youtube.com/watch?v=" + id;

                        Data input = new Data.Builder()
                            .putString("target", targetUrl)
                            .putString("title", track.title)
                            .putString("artist", artistShort)
                            .putString("album", albumName)
                            .putString("year", track.year)
                            .putString("genre", track.genre)
                            .putString("artwork", track.thumbnail)
                            .putString("versionType", "Oficial")
                            .putInt("trackNumber", track.trackNumber)
                            .putBoolean("video", false)
                            .putString("quality", "Alta · 320 kbps")
                            .build();

                        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(MediaDownloadWorker.class)
                            .setInputData(input)
                            .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
                            .addTag("nekodown")
                            .build();

                        WorkManager.getInstance(appContext).enqueue(workRequest);

                        // Register in queue fragment so it appears in the Cola tab
                        if (getActivity() instanceof MainActivity) {
                            final String jobId = workRequest.getId().toString();
                            final String jobTitle = track.title;
                            requireActivity().runOnUiThread(() -> {
                                ((MainActivity) getActivity()).getQueueFragment().addJob(
                                    jobId, jobTitle, "MP3"
                                );
                            });
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (getActivity() != null) {
                requireActivity().runOnUiThread(() ->
                    Toast.makeText(requireContext(), "Descargas encoladas", Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    private void showCatalogResults(List<SearchResult> results, String title, boolean canGoBack) {
        allSearchResults.clear();
        allSearchResults.addAll(results);
        searchHasMore = false;
        searchPage = 0;
        if (!results.isEmpty()) showSearchPage(0);
        else searchAdapter.replace(Collections.emptyList());
        binding.searchResults.setVisibility(View.VISIBLE);
        binding.searchPagination.setVisibility(results.size() > RESULTS_PER_PAGE ? View.VISIBLE : View.GONE);
        binding.catalogHeader.setVisibility(canGoBack ? View.VISIBLE : View.GONE);
        binding.catalogTitle.setText(title);
        backPressedCallback.setEnabled(canGoBack);
    }

    private void navigateCatalogBack() {
        if ("tracks".equals(catalogStage)) {
            loadArtistAlbums(new SearchResult(
                catalogArtistId, catalogArtistName, "", "", "",
                SearchResult.ARTIST, "", "", "", "", 0
            ));
        } else if ("albums".equals(catalogStage)) {
            binding.catalogHeader.setVisibility(View.GONE);
            binding.searchResults.setVisibility(View.GONE);
            binding.searchPagination.setVisibility(View.GONE);
            backPressedCallback.setEnabled(false);
            if (!lastArtistQuery.isEmpty()) {
                prepareCatalogSearchAgain();
            }
        }
    }

    private void prepareCatalogSearchAgain() {
        int generation = ++searchGeneration;
        binding.loading.setVisibility(View.VISIBLE);
        searchArtistCatalog(lastArtistQuery, generation);
    }

    private void saveSearchQuery(String query) {
        if (query == null || query.trim().isEmpty()) return;
        query = query.trim();
        searchHistoryList.remove(query);
        searchHistoryList.add(0, query);
        if (searchHistoryList.size() > 20) {
            searchHistoryList.remove(searchHistoryList.size() - 1);
        }
        if (searchHistoryAdapter != null) searchHistoryAdapter.updateData(searchHistoryList);
        SharedPreferences prefs = requireContext().getSharedPreferences("nekodown_search_history", android.content.Context.MODE_PRIVATE);
        prefs.edit().putString("history", new org.json.JSONArray(searchHistoryList).toString()).apply();
    }

    private void loadSearchHistory() {
        SharedPreferences prefs = requireContext().getSharedPreferences("nekodown_search_history", android.content.Context.MODE_PRIVATE);
        String historyStr = prefs.getString("history", "[]");
        searchHistoryList.clear();
        try {
            JSONArray array = new JSONArray(historyStr);
            for (int i = 0; i < array.length(); i++) {
                searchHistoryList.add(array.getString(i));
            }
        } catch (Exception ignored) {}
    }

    private static JSONObject readJson(HttpURLConnection connection) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(
            connection.getInputStream(), StandardCharsets.UTF_8
        ));
        StringBuilder payload = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) payload.append(line);
        return new JSONObject(payload.toString());
    }

    private static String yearOf(String releaseDate) {
        return releaseDate != null && releaseDate.length() >= 4 ? releaseDate.substring(0, 4) : "";
    }

    private static String largeArtwork(String url) {
        return url == null ? "" : url.replace("100x100bb", "600x600bb");
    }

    

    

    

    private static boolean isYouTubeUrl(String value) {
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("youtube.com/") || lower.contains("youtu.be/");
    }

    private static String detectVersionType(String title) {
        String lower = title == null ? "" : title.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("remix")) return "Remix";
        if (lower.contains("cover") || lower.contains("versión") || lower.contains("version") || lower.contains("tribut") || lower.contains("karaoke") || lower.contains("parod")) return "Cover/Otra";
        if (lower.contains("live") || lower.contains("en vivo") || lower.contains("vivo") || lower.contains("directo") || lower.contains("concierto")) return "En vivo";
        if (lower.contains("acoustic") || lower.contains("acústic")) return "Acústica";
        if (lower.contains("sped up")) return "Sped up";
        if (lower.contains("slowed")) return "Slowed";
        if (lower.contains("nightcore")) return "Nightcore";
        if (lower.contains("instrumental")) return "Instrumental";
        return "Oficial";
    }

    private void showPreparedMetadata() {
        List<String> details = new ArrayList<>();
        if (!preparedAlbum.isEmpty()) details.add("Álbum: " + preparedAlbum);
        if (!preparedYear.isEmpty()) details.add("Año: " + preparedYear);
        if (preparedTrackNumber > 0) details.add("Pista: " + preparedTrackNumber);
        if (!preparedGenre.isEmpty()) details.add("Género: " + preparedGenre);
        if (!"Oficial".equals(preparedVersionType)) details.add("Versión: " + preparedVersionType);
//         binding.resultMetadata.setText(TextUtils.join(" · ", details));
//         binding.resultMetadata.setVisibility(details.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void enrichSelectedMetadata(String fallbackArtwork) {
        final String expectedTarget = preparedTarget;
        final String originalTitle = preparedTitle;
        final String originalArtist = preparedArtist;
        preparedArtwork = fallbackArtwork;
        if (!"Oficial".equals(preparedVersionType)) {
            showPreparedMetadata();
            prepareExtras();
            return;
        }
        queue.execute(() -> {
            HttpURLConnection connection = null;
            try {
                String[] hints = catalogHints(originalTitle, originalArtist);
                String catalogArtistHint = hints[0];
                String catalogTitleHint = hints[1];
                String query = catalogArtistHint + " " + catalogTitleHint;
                String term = URLEncoder.encode(query, StandardCharsets.UTF_8.name());
                connection = (HttpURLConnection) new URL(
                    "https://itunes.apple.com/search?media=music&entity=song&limit=10&term=" + term
                ).openConnection();
                connection.setConnectTimeout(6000);
                connection.setReadTimeout(7000);
                connection.setRequestProperty("User-Agent", "NekoDown-Android/1.2");
                if (connection.getResponseCode() != 200) throw new IllegalStateException();
                JSONArray results = readJson(connection).optJSONArray("results");
                JSONObject best = null;
                int bestScore = -1;
                if (results != null) {
                    for (int i = 0; i < results.length(); i++) {
                        JSONObject candidate = results.optJSONObject(i);
                        if (candidate == null) continue;
                        boolean isOfficial = originalTitle.toLowerCase(java.util.Locale.ROOT).contains("oficial") || originalTitle.toLowerCase(java.util.Locale.ROOT).contains("official");
                        int score = metadataScore(
                            catalogTitleHint,
                            candidate.optString("trackName"),
                            catalogArtistHint,
                            candidate.optString("artistName"),
                            isOfficial
                        );
                        if (score > bestScore) {
                            bestScore = score;
                            best = candidate;
                        }
                    }
                }
                if (best == null || bestScore < 5) throw new IllegalStateException();
                final JSONObject match = best;
                if(getActivity()!=null) requireActivity().runOnUiThread(() -> {
                    if (!expectedTarget.equals(preparedTarget)) return;
                    preparedTitle = match.optString("trackName", originalTitle);
                    preparedArtist = match.optString("artistName", originalArtist);
                    preparedAlbum = match.optString("collectionName");
                    preparedYear = yearOf(match.optString("releaseDate"));
                    preparedGenre = match.optString("primaryGenreName");
                    preparedTrackNumber = match.optInt("trackNumber", 0);
                    preparedArtwork = largeArtwork(match.optString("artworkUrl100"));
                    preparedPreview = match.optString("previewUrl");
                    if (!preparedArtwork.isEmpty()) {
                         Glide.with(this).load(preparedArtwork).into(binding.playerInclude.playerCover);
                    }
                    binding.playerInclude.playerTitle.setText(preparedTitle);
                    binding.playerInclude.playerArtist.setText(preparedArtist);
                    showPreparedMetadata();
                    prepareExtras();
                });
            } catch (Exception ignored) {
                if(getActivity()!=null) requireActivity().runOnUiThread(() -> {
                    if (!expectedTarget.equals(preparedTarget)) return;
                    showPreparedMetadata();
                    prepareExtras();
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void enrichSelectedMetadataAndDownload(String fallbackArtwork) {
        final String originalTitle = preparedTitle;
        final String originalArtist = preparedArtist;
        preparedArtwork = fallbackArtwork;
        if (!"Oficial".equals(preparedVersionType)) {
            enqueueTarget(preparedTarget, preparedTitle, preparedArtist, preparedAlbum, preparedYear, preparedGenre, preparedArtwork, preparedVersionType, preparedTrackNumber);
            return;
        }
        Toast.makeText(requireContext(), "Buscando metadatos para descargar...", Toast.LENGTH_SHORT).show();
        queue.execute(() -> {
            HttpURLConnection connection = null;
            try {
                String[] hints = catalogHints(originalTitle, originalArtist);
                String term = URLEncoder.encode(hints[0] + " " + hints[1], StandardCharsets.UTF_8.name());
                connection = (HttpURLConnection) new URL(
                    "https://itunes.apple.com/search?media=music&entity=song&limit=10&term=" + term
                ).openConnection();
                connection.setConnectTimeout(6000);
                connection.setReadTimeout(7000);
                connection.setRequestProperty("User-Agent", "NekoDown-Android/1.2");
                if (connection.getResponseCode() != 200) throw new IllegalStateException();
                JSONArray results = readJson(connection).optJSONArray("results");
                JSONObject best = null;
                int bestScore = -1;
                if (results != null) {
                    for (int i = 0; i < results.length(); i++) {
                        JSONObject candidate = results.optJSONObject(i);
                        if (candidate == null) continue;
                        boolean isOfficial = originalTitle.toLowerCase(java.util.Locale.ROOT).contains("oficial") || originalTitle.toLowerCase(java.util.Locale.ROOT).contains("official");
                        int score = metadataScore(hints[1], candidate.optString("trackName"), hints[0], candidate.optString("artistName"), isOfficial);
                        if (score > bestScore) {
                            bestScore = score;
                            best = candidate;
                        }
                    }
                }
                if (best == null || bestScore < 5) throw new IllegalStateException();
                final JSONObject match = best;
                if(getActivity()!=null) requireActivity().runOnUiThread(() -> {
                    enqueueTarget(preparedTarget, match.optString("trackName", originalTitle), match.optString("artistName", originalArtist), match.optString("collectionName"), yearOf(match.optString("releaseDate")), match.optString("primaryGenreName"), largeArtwork(match.optString("artworkUrl100")), preparedVersionType, match.optInt("trackNumber", 0));
                });
            } catch (Exception ignored) {
                if(getActivity()!=null) requireActivity().runOnUiThread(() -> {
                    enqueueTarget(preparedTarget, originalTitle, originalArtist, preparedAlbum, preparedYear, preparedGenre, preparedArtwork, preparedVersionType, preparedTrackNumber);
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void prepareExtras() {
        stopPreview();
        previewUrl = preparedPreview;
        updateFavoriteButton();
        if (!previewUrl.isEmpty()) {
             togglePreview();
        } else if (!preparedTarget.isEmpty() && isYouTubeUrl(preparedTarget)) {
             createSourcePreview();
        } else {
             if(getActivity()!=null) requireActivity().runOnUiThread(() -> {
                 binding.playerInclude.playerLoading.setVisibility(View.GONE);
                 binding.playerInclude.playerPlayPause.setVisibility(View.VISIBLE);
                 binding.playerInclude.playerTitle.setText("Muestra no disponible");
             });
        }
    }

    private static String cleanCatalogTitle(String value) {
        return (value == null ? "" : value)
            .replaceAll("(?i)[\\[(].*?(official|audio|video|lyrics?|hd|4k).*?[\\])]", "")
            .replaceAll("(?i)\\s*[-–—]\\s*(official|audio|video|lyrics?).*$", "")
            .replaceAll("\\s+", " ").trim();
    }

    private static String cleanCatalogArtist(String value) {
        return (value == null ? "" : value)
            .replaceAll("(?i)\\s*-\\s*topic$", "")
            .replaceAll("(?i)\\s*(official|vevo)$", "")
            .replaceAll("\\s+", " ").trim();
    }

    private static String[] catalogHints(String title, String uploader) {
        String cleanTitle = cleanCatalogTitle(title);
        String cleanArtist = cleanCatalogArtist(uploader);
        String[] separated = cleanTitle.split("\\s+[-–—]\\s+", 2);
        if (separated.length == 2 && !separated[0].trim().isEmpty() && !separated[1].trim().isEmpty()) {
            cleanArtist = cleanCatalogArtist(separated[0]);
            cleanTitle = cleanCatalogTitle(separated[1]);
        }
        return new String[]{cleanArtist, cleanTitle};
    }

    private static int metadataScore(String titleA, String titleB, String artistA, String artistB, boolean isOfficial) {
        String ta = normalizeMetadata(titleA);
        String tb = normalizeMetadata(titleB);
        String aa = normalizeMetadataArtist(artistA);
        String ab = normalizeMetadataArtist(artistB);
        int score = ta.equals(tb) ? 5 : (ta.contains(tb) || tb.contains(ta) ? 3 : 0);
        if (score >= 3 && isOfficial) score += 3;
        int artistScore = aa.equals(ab) ? 4 : (aa.contains(ab) || ab.contains(aa) ? 2 : 0);
        if (!aa.isEmpty() && !ab.isEmpty() && artistScore == 0 && !isOfficial) return 0;
        score += artistScore;
        return score;
    }

    private static String normalizeMetadataArtist(String value) {
        return normalizeMetadata(value)
            .replaceAll("\\b(feat|featuring|ft|and|the)\\b", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private static String normalizeMetadata(String value) {
        return java.text.Normalizer.normalize(
            value == null ? "" : value.toLowerCase(java.util.Locale.ROOT),
            java.text.Normalizer.Form.NFD
        ).replaceAll("\\p{M}", "").replaceAll("[^a-z0-9]+", " ").trim();
    }

    private Runnable updateSeekBar;

    private void startPlayerUpdater() {
        stopPlayerUpdater();
        updateSeekBar = new Runnable() {
            @Override
            public void run() {
                if (previewPlayer != null && previewPlayer.isPlaying()) {
                    int pos = previewPlayer.getCurrentPosition();
                    binding.playerInclude.playerSeekBar.setProgress(pos);
                    binding.playerInclude.playerTimeCurrent.setText(formatSeconds(pos / 1000));
                    mainHandler.postDelayed(this, 1000);
                }
            }
        };
        mainHandler.post(updateSeekBar);
    }

    private void stopPlayerUpdater() {
        if (updateSeekBar != null) {
            mainHandler.removeCallbacks(updateSeekBar);
            updateSeekBar = null;
        }
    }

    private String formatSeconds(int seconds) {
        return String.format(java.util.Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60);
    }

    private void togglePreview() {
        if (previewUrl.isEmpty()) {
            createSourcePreview();
            return;
        }
        if (previewPlayer != null && previewPlayer.isPlaying()) {
            previewPlayer.pause();
            binding.playerInclude.playerPlayPause.setImageResource(R.drawable.ic_play);
            return;
        }
        try {
            if (previewPlayer == null) {
                previewPlayer = new MediaPlayer();
                previewPlayer.setDataSource(previewUrl);
                previewPlayer.setOnPreparedListener(player -> {
                    binding.playerInclude.playerLoading.setVisibility(View.GONE);
                    binding.playerInclude.playerPlayPause.setVisibility(View.VISIBLE);
                    binding.playerInclude.playerPlayPause.setImageResource(R.drawable.ic_pause);
                    binding.playerInclude.playerSeekBar.setMax(player.getDuration());
                    binding.playerInclude.playerTimeTotal.setText(formatSeconds(player.getDuration() / 1000));
                    player.start();
                    startPlayerUpdater();
                });
                previewPlayer.setOnCompletionListener(player -> {
                    binding.playerInclude.playerPlayPause.setImageResource(R.drawable.ic_play);
                    player.seekTo(0);
                    binding.playerInclude.playerTimeCurrent.setText("0:00");
                    binding.playerInclude.playerSeekBar.setProgress(0);
                    stopPlayerUpdater();
                });
                previewPlayer.prepareAsync();
            } else {
                previewPlayer.start();
                binding.playerInclude.playerPlayPause.setImageResource(R.drawable.ic_pause);
                startPlayerUpdater();
            }
        } catch (Exception error) {
            stopPreview();
            binding.playerInclude.playerTitle.setText("Muestra no disponible");
        }
    }

    private void createSourcePreview() {
        if (preparedTarget.isEmpty() || !isYouTubeUrl(preparedTarget)) return;
        final String expectedTarget = preparedTarget;
//         binding.previewButton.setEnabled(false);
//         binding.previewButton.setText("Creando muestra…");
        queue.execute(() -> {
            try {
                File directory = new File(requireContext().getCacheDir(), "nekodown_previews");
                if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException();
                String baseName = "preview_" + Integer.toHexString(expectedTarget.hashCode());
                File base = new File(directory, baseName);
                YoutubeDLRequest request = new YoutubeDLRequest(expectedTarget);
                request.addOption("--no-playlist");
                request.addOption("-f", "bestaudio/best");
                request.addOption("--download-sections", "*00:00:20-00:00:50");
                request.addOption("--force-keyframes-at-cuts");
                request.addOption("-x");
                request.addOption("--audio-format", "mp3");
                request.addOption("--audio-quality", "128K");
                request.addOption("--socket-timeout", "12");
                request.addOption("--retries", "2");
                request.addOption("-o", base.getAbsolutePath() + ".%(ext)s");
                YoutubeDL.getInstance().execute(request, UUID.randomUUID().toString());
                File created = new File(directory, baseName + ".mp3");
                if (!created.isFile()) throw new IllegalStateException("No se creó la muestra");
                if(getActivity()!=null) requireActivity().runOnUiThread(() -> {
                    if (!expectedTarget.equals(preparedTarget)) {
                        created.delete();
                        return;
                    }
                    previewFile = created;
                    previewUrl = created.getAbsolutePath();
                    togglePreview();
                });
            } catch (Exception error) {
                Log.w("NekoDown", "No se pudo crear la muestra temporal", error);
                if(getActivity()!=null) requireActivity().runOnUiThread(() -> {
                    if (!expectedTarget.equals(preparedTarget)) return;
                    binding.playerInclude.playerLoading.setVisibility(View.GONE);
                    binding.playerInclude.playerPlayPause.setVisibility(View.VISIBLE);
                    binding.playerInclude.playerTitle.setText("Muestra no disponible");
                });
            }
        });
    }

    private void stopPreview() {
        previewUrl = "";
        stopPlayerUpdater();
        if (previewPlayer != null) {
            try { previewPlayer.release(); } catch (Exception ignored) {}
            previewPlayer = null;
        }
        if (previewFile != null) {
            previewFile.delete();
            previewFile = null;
        }
    }

    private void toggleFavorite() {
        if (preparedTarget.isEmpty()) return;
        SharedPreferences prefs = requireActivity().getSharedPreferences("nekodown_ui", android.content.Context.MODE_PRIVATE);
        Set<String> favorites = new HashSet<>(prefs.getStringSet("favorites", Collections.emptySet()));
        if (!favorites.add(preparedTarget)) favorites.remove(preparedTarget);
        prefs.edit().putStringSet("favorites", favorites).apply();
        updateFavoriteButton();
    }

    private void updateFavoriteButton() {
        boolean favorite = requireActivity().getSharedPreferences("nekodown_ui", android.content.Context.MODE_PRIVATE)
            .getStringSet("favorites", Collections.emptySet())
            .contains(preparedTarget);
//         binding.favoriteButton.setText(favorite ? "♥ Favorito" : "♡ Favorito");
    }

    private void showSearchPage(int requestedPage) {
        if (allSearchResults.isEmpty()) return;
        int pages = (allSearchResults.size() + RESULTS_PER_PAGE - 1) / RESULTS_PER_PAGE;
        searchPage = Math.max(0, Math.min(requestedPage, pages - 1));
        int start = searchPage * RESULTS_PER_PAGE;
        int end = Math.min(start + RESULTS_PER_PAGE, allSearchResults.size());
        searchAdapter.replace(new ArrayList<>(allSearchResults.subList(start, end)));
        binding.pageLabel.setText("Página " + (searchPage + 1) + " de " + pages + " · " + allSearchResults.size() + " resultados");
        binding.previousPage.setEnabled(searchPage > 0);
        binding.nextPage.setEnabled(searchPage + 1 < pages || searchHasMore);
        if (requestedPage != 0) {
            binding.searchResults.post(() -> binding.mainScroll.smoothScrollTo(0, binding.searchResults.getTop()));
        }
    }

    private void loadSearchPage(int requestedPage) {
        if (currentSearchQuery.isEmpty() || requestedPage < 1 || requestedPage > 9) return;
        int generation = ++searchGeneration;
        activeSearchProcess = UUID.randomUUID().toString();
        String processId = activeSearchProcess;
        int requestedCount = (requestedPage + 1) * RESULTS_PER_PAGE;
        binding.loading.setVisibility(View.VISIBLE);
        binding.nextPage.setEnabled(false);
        queue.execute(() -> {
            try {
                YoutubeDLRequest request = new YoutubeDLRequest(
                    "ytsearch" + requestedCount + ":" + currentSearchQuery
                        + (artistMode ? " official audio" : "")
                );
                request.addOption("--dump-single-json");
                request.addOption("--no-warnings");
                request.addOption("--flat-playlist");
                request.addOption("--playlist-end", String.valueOf(requestedCount));
                request.addOption("--socket-timeout", "12");
                request.addOption("--retries", "2");
                request.addOption("--extractor-retries", "2");
                JSONObject metadata = new JSONObject(YoutubeDL.getInstance().execute(request, processId).getOut());
                JSONArray entries = metadata.optJSONArray("entries");
                List<SearchResult> results = new ArrayList<>();
                if (entries != null) {
                    for (int i = requestedPage * RESULTS_PER_PAGE; i < entries.length(); i++) {
                        JSONObject entry = entries.optJSONObject(i);
                        if (entry == null) continue;
                        String id = entry.optString("id");
                        if (!id.isEmpty()) results.add(new SearchResult(
                            id,
                            entry.optString("title", "Sin título"),
                            entry.optString("uploader", entry.optString("channel", "YouTube")),
                            thumbnailOf(entry),
                            extractDuration(entry)
                        ));
                    }
                }
                if(getActivity()!=null) requireActivity().runOnUiThread(() -> {
                    if (generation != searchGeneration) return;
                    activeSearchProcess = "";
                    allSearchResults.addAll(results);
                    searchHasMore = results.size() == RESULTS_PER_PAGE && requestedPage < 9;
                    binding.loading.setVisibility(View.GONE);
                    if (!results.isEmpty()) showSearchPage(requestedPage);
                    else {
                        searchHasMore = false;
                        showSearchPage(searchPage);
                        Toast.makeText(requireContext(), "No hay más resultados.", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception error) {
                Log.e("NekoDown", "No se pudo cargar otra página", error);
                if(getActivity()!=null) requireActivity().runOnUiThread(() -> {
                    if (generation != searchGeneration) return;
                    activeSearchProcess = "";
                    binding.loading.setVisibility(View.GONE);
                    binding.nextPage.setEnabled(true);
                    Toast.makeText(requireContext(), "No se pudo cargar la página siguiente.", Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void finishSearch() {
        binding.loading.setVisibility(View.GONE);
        binding.searchButton.setEnabled(true);
    }

    private static String thumbnailOf(JSONObject object) {
        String thumbnail = object.optString("thumbnail");
        if (!thumbnail.isEmpty()) return thumbnail;
        JSONArray thumbnails = object.optJSONArray("thumbnails");
        if (thumbnails == null || thumbnails.length() == 0) return "";
        JSONObject last = thumbnails.optJSONObject(thumbnails.length() - 1);
        return last == null ? "" : last.optString("url");
    }

    

    

    

    

    

    

 
    private void quickDownload(SearchResult result) {
        if (SearchResult.YOUTUBE.equals(result.kind)) {
            enqueueTarget(
                "https://www.youtube.com/watch?v=" + result.id, 
                result.title, result.artist, "", "", "", result.thumbnail, "Oficial", 0
            );
        } else if (SearchResult.TRACK.equals(result.kind) || result.id.isEmpty()) {
            final int generation = searchGeneration;
            Toast.makeText(requireContext(), "Buscando fuente para descargar...", Toast.LENGTH_SHORT).show();
            queue.execute(() -> {
                try {
                    YoutubeDLRequest request = new YoutubeDLRequest(
                        "ytsearch1:" + result.artist.replaceAll("\\s+·.*$", "")
                            + " " + result.title + " official audio"
                    );
                    request.addOption("--dump-single-json");
                    request.addOption("--flat-playlist");
                    request.addOption("--playlist-end", "1");
                    request.addOption("--socket-timeout", "10");
                    org.json.JSONObject metadata = new org.json.JSONObject(YoutubeDL.getInstance()
                        .execute(request, UUID.randomUUID().toString()).getOut());
                    org.json.JSONArray entries = metadata.optJSONArray("entries");
                    org.json.JSONObject first = entries == null ? null : entries.optJSONObject(0);
                    String id = first == null ? "" : first.optString("id");
                    if (id.isEmpty()) throw new IllegalStateException("No se encontró una fuente.");
                    if(getActivity()!=null) requireActivity().runOnUiThread(() -> {
                        if (generation != searchGeneration) return;
                        enqueueTarget(
                            "https://www.youtube.com/watch?v=" + id,
                            result.title, result.artist, result.album, result.year, result.genre, result.thumbnail, "Oficial", result.trackNumber
                        );
                    });
                } catch (Exception error) {
                    if(getActivity()!=null) requireActivity().runOnUiThread(() -> {
                        if (generation != searchGeneration) return;
                        Toast.makeText(requireContext(), "No se encontró esa canción en YouTube.", Toast.LENGTH_LONG).show();
                    });
                }
            });
        }
    }

    private void enqueueTarget(String target, String title, String artist, String album, String year, String genre, String artwork, String version, int trackNumber) {
        Data input = new Data.Builder()
            .putString("target", target)
            .putString("title", title)
            .putString("artist", artist)
            .putString("album", album)
            .putString("year", year)
            .putString("genre", genre)
            .putString("artwork", artwork)
            .putString("versionType", version)
            .putInt("trackNumber", trackNumber)
            .putBoolean("video", mp4)
            .putString("quality", String.valueOf(binding.qualitySpinner.getSelectedItem()))
            .build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(MediaDownloadWorker.class)
            .setInputData(input)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build();
            
        WorkManager manager = WorkManager.getInstance(requireContext());
        manager.enqueue(request);
        
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).getQueueFragment().addJob(
                request.getId().toString(), title, mp4 ? "MP4" : "MP3"
            );
        }
        Toast.makeText(requireContext(), "Descarga iniciada", Toast.LENGTH_SHORT).show();
    }

   @Override public void onDestroy() {
        searchGeneration++;
        if (!activeSearchProcess.isEmpty()) YoutubeDL.getInstance().destroyProcessById(activeSearchProcess);
        stopPreview();
        queue.shutdownNow();
        super.onDestroy();
    }

    private String extractDuration(org.json.JSONObject entry) {
        String str = entry.optString("duration_string", "");
        if (!str.isEmpty()) return str;
        int seconds = entry.optInt("duration", 0);
        if (seconds <= 0) return "";
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        int s = seconds % 60;
        if (h > 0) return String.format(java.util.Locale.getDefault(), "%d:%02d:%02d", h, m, s);
        return String.format(java.util.Locale.getDefault(), "%d:%02d", m, s);
    }

    private String formatTrackDuration(int millis) {
        if (millis <= 0) return "";
        int seconds = millis / 1000;
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        int s = seconds % 60;
        if (h > 0) return String.format(java.util.Locale.getDefault(), "%d:%02d:%02d", h, m, s);
        return String.format(java.util.Locale.getDefault(), "%d:%02d", m, s);
    }

    // ─── Inner adapter for album track list in the overlay ───
    private static class AlbumTrackAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<AlbumTrackAdapter.VH> {
        private final List<SearchResult> tracks;
        private final boolean[] selected;

        AlbumTrackAdapter(List<SearchResult> tracks) {
            this.tracks = tracks;
            this.selected = new boolean[tracks.size()];
        }

        void selectAll(boolean select) {
            for (int i = 0; i < selected.length; i++) selected[i] = select;
            notifyDataSetChanged();
        }

        List<SearchResult> getSelectedTracks() {
            List<SearchResult> result = new ArrayList<>();
            for (int i = 0; i < tracks.size(); i++) {
                if (selected[i]) result.add(tracks.get(i));
            }
            return result;
        }

        @Override public VH onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            View v = android.view.LayoutInflater.from(parent.getContext()).inflate(R.layout.item_album_track, parent, false);
            return new VH(v);
        }

        @Override public void onBindViewHolder(VH h, int pos) {
            SearchResult t = tracks.get(pos);
            h.text.setText(t.trackNumber + ". " + t.title);
            h.checkbox.setChecked(selected[pos]);
            h.artist.setText(t.artist);

            List<String> details = new ArrayList<>();
            if (t.album != null && !t.album.isEmpty()) details.add(t.album);
            if (t.year != null && !t.year.isEmpty()) details.add(t.year);
            if (t.genre != null && !t.genre.isEmpty()) details.add(t.genre);
            if (!details.isEmpty()) { h.details.setText(String.join(" · ", details)); h.details.setVisibility(View.VISIBLE); }
            else h.details.setVisibility(View.GONE);

            if (t.duration != null && !t.duration.isEmpty()) { h.duration.setText(t.duration); h.duration.setVisibility(View.VISIBLE); }
            else h.duration.setVisibility(View.GONE);

            if (t.thumbnail == null || t.thumbnail.isEmpty()) {
                Glide.with(h.itemView).clear(h.image);
                h.image.setImageResource(R.drawable.ic_neko);
            } else {
                Glide.with(h.itemView).load(t.thumbnail).centerCrop().into(h.image);
            }

            h.itemView.setOnClickListener(v -> { selected[pos] = !selected[pos]; h.checkbox.setChecked(selected[pos]); });
            h.checkbox.setOnClickListener(v -> selected[pos] = h.checkbox.isChecked());
        }

        @Override public int getItemCount() { return tracks.size(); }

        static class VH extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            android.widget.TextView text, artist, details, duration;
            CheckBox checkbox;
            ImageView image;
            VH(View v) {
                super(v);
                text = v.findViewById(R.id.trackTitle);
                checkbox = v.findViewById(R.id.trackCheckbox);
                image = v.findViewById(R.id.trackImage);
                artist = v.findViewById(R.id.trackArtist);
                details = v.findViewById(R.id.trackDetails);
                duration = v.findViewById(R.id.trackDuration);
            }
        }
    }
}
