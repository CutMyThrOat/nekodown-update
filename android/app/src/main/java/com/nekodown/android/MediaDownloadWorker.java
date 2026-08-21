package com.nekodown.android;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.mpatric.mp3agic.ID3v2;
import com.mpatric.mp3agic.ID3v24Tag;
import com.mpatric.mp3agic.Mp3File;
import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import java.io.File;
import java.io.FileInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

public class MediaDownloadWorker extends Worker {
    public static final String CHANNEL = "nekodown_downloads";

    public MediaDownloadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull @Override public Result doWork() {
        String target = getInputData().getString("target");
        String title = getInputData().getString("title");
        String artist = getInputData().getString("artist");
        String album = getInputData().getString("album");
        String year = getInputData().getString("year");
        String genre = getInputData().getString("genre");
        String artwork = getInputData().getString("artwork");
        String versionType = getInputData().getString("versionType");
        int trackNumber = getInputData().getInt("trackNumber", 0);
        String quality = getInputData().getString("quality");
        boolean video = getInputData().getBoolean("video", false);
        if (target == null || title == null) return Result.failure();
        try {
            createChannel();
            setForegroundAsync(info(title, 0, "Preparando"));
            YoutubeDL.getInstance().init(getApplicationContext());
            FFmpeg.getInstance().init(getApplicationContext());
            updateEngineIfNeeded(title);
            File root = new File(
                getApplicationContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                "NekoDown/" + getId()
            );
            if (!root.exists() && !root.mkdirs()) throw new IllegalStateException("No se pudo preparar el espacio temporal");
            String safe = title.replaceAll("[\\\\/:*?\"<>|]", "_");
            YoutubeDLRequest request = new YoutubeDLRequest(target);
            request.addOption("--no-playlist");
            request.addOption("--newline");
            request.addOption("--no-mtime");
            request.addOption("--no-warnings");
            request.addOption("--socket-timeout", "15");
            request.addOption("--retries", "3");
            request.addOption("--fragment-retries", "3");
            request.addOption("--extractor-retries", "3");
            request.addOption("--concurrent-fragments", "4");
            if (video) {
                String height = quality == null ? "" : quality.replaceAll("\\D", "");
                String selector = height.isEmpty()
                    ? "bestvideo+bestaudio/best"
                    : "bestvideo[height<=" + height + "]+bestaudio/best[height<=" + height + "]";
                request.addOption("-f", selector);
                request.addOption("--merge-output-format", "mp4");
                if ("Oficial".equals(versionType)) {
                    request.addOption("--embed-metadata");
                }
            } else {
                String bitrate = quality == null ? "320" : quality.replaceAll("\\D", "");
                request.addOption("-f", "bestaudio/best");
                request.addOption("-x");
                request.addOption("--audio-format", "mp3");
                request.addOption("--audio-quality", (bitrate.isEmpty() ? "320" : bitrate) + "K");
                request.addOption("--embed-thumbnail");
                request.addOption("--convert-thumbnails", "jpg");
                if ("Oficial".equals(versionType)) {
                    request.addOption("--embed-metadata");
                }
            }
            request.addOption("-o", new File(root, safe + ".%(ext)s").getAbsolutePath());
            YoutubeDL.getInstance().execute(request, UUID.randomUUID().toString(), (progress, eta, line) -> {
                int percent = Math.max(0, Math.min(100, progress.intValue()));
                setProgressAsync(new Data.Builder().putInt("progress", percent).putString("state", "Descargando").build());
                try {
                    setForegroundAsync(info(title, percent, eta > 0 ? "Faltan " + eta + " s" : "Descargando"));
                } catch (Exception ignored) {}
                return kotlin.Unit.INSTANCE;
            });
            File completed = new File(root, safe + (video ? ".mp4" : ".mp3"));
            if (!video) enrichMp3(completed, artist, title, album, year, genre, trackNumber, artwork);
            publish(completed, video);
            setProgressAsync(new Data.Builder().putInt("progress", 100).putString("state", "Completado").build());
            notifyDone(title);
            saveToHistory(getApplicationContext(), title, video ? "MP4" : "MP3", artwork);
            deleteTemporaryDirectory(root);
            return Result.success(new Data.Builder().putString("saved", video ? "Descargas" : "Música").build());
        } catch (Exception error) {
            Log.e("NekoDown", "Descarga fallida (intento " + (getRunAttemptCount() + 1) + ")", error);
            if (getRunAttemptCount() < 1) return Result.retry();
            return Result.failure(new Data.Builder()
                .putString("error", error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage())
                .build());
        }
    }

    private void updateEngineIfNeeded(String title) {
        long lastUpdate = getApplicationContext()
            .getSharedPreferences("nekodown_engine", Context.MODE_PRIVATE)
            .getLong("last_update", 0L);
        if (System.currentTimeMillis() - lastUpdate < 24L * 60L * 60L * 1000L) return;
        try {
            try {
                setForegroundAsync(info(title, 0, "Actualizando motor"));
            } catch (Exception ignored) {}
            YoutubeDL.getInstance().updateYoutubeDL(
                getApplicationContext(),
                YoutubeDL.UpdateChannel._STABLE
            );
            getApplicationContext()
                .getSharedPreferences("nekodown_engine", Context.MODE_PRIVATE)
                .edit()
                .putLong("last_update", System.currentTimeMillis())
                .apply();
            Log.i("NekoDown", "Motor yt-dlp actualizado: "
                + YoutubeDL.getInstance().versionName(getApplicationContext()));
        } catch (Exception updateError) {
            // Una caída temporal de GitHub no debe impedir que se intente usar
            // el motor ya instalado.
            Log.w("NekoDown", "No se pudo actualizar yt-dlp; se usará la versión instalada", updateError);
        }
    }

    private static void saveToHistory(Context context, String title, String format, String artwork) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("nekodown_history", Context.MODE_PRIVATE);
        String old = prefs.getString("items", "[]");
        try {
            JSONArray array = new JSONArray(old);
            JSONObject item = new JSONObject();
            item.put("title", title);
            item.put("format", format);
            item.put("artwork", artwork != null ? artwork : "");
            item.put("time", System.currentTimeMillis());
            
            // Keep at most 100 items
            if (array.length() >= 100) {
                JSONArray newArray = new JSONArray();
                for (int i = 1; i < array.length(); i++) newArray.put(array.get(i));
                array = newArray;
            }
            array.put(item);
            prefs.edit().putString("items", array.toString()).apply();
        } catch (Exception e) {
            try {
                JSONArray array = new JSONArray();
                JSONObject item = new JSONObject();
                item.put("title", title);
                item.put("format", format);
                item.put("artwork", artwork != null ? artwork : "");
                item.put("time", System.currentTimeMillis());
                array.put(item);
                prefs.edit().putString("items", array.toString()).apply();
            } catch (Exception ignored) {}
        }
    }

    private ForegroundInfo info(String title, int progress, String state) {
        NotificationCompat.Builder notification = new NotificationCompat.Builder(getApplicationContext(), CHANNEL)
            .setSmallIcon(R.drawable.ic_neko)
            .setContentTitle(title)
            .setContentText(state + " · " + progress + "%")
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, progress, false);
        return new ForegroundInfo(getId().hashCode(), notification.build());
    }

    private void notifyDone(String title) {
        NotificationCompat.Builder notification = new NotificationCompat.Builder(getApplicationContext(), CHANNEL)
            .setSmallIcon(R.drawable.ic_neko)
            .setContentTitle("Descarga completada")
            .setContentText(title)
            .setAutoCancel(true);
        ((NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE))
            .notify(getId().hashCode(), notification.build());
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "Descargas", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE))
                .createNotificationChannel(channel);
        }
    }

    private void publish(File source, boolean video) throws Exception {
        if (!source.isFile()) throw new IllegalStateException("El archivo final no fue creado");
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, source.getName());
        values.put(MediaStore.MediaColumns.MIME_TYPE, video ? "video/mp4" : "audio/mpeg");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, video ? Environment.DIRECTORY_DOWNLOADS : Environment.DIRECTORY_MUSIC);
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        Uri collection = video
            ? MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            : MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri destination = getApplicationContext().getContentResolver().insert(collection, values);
        if (destination == null) throw new IllegalStateException("Android no permitió crear el archivo");
        try (FileInputStream input = new FileInputStream(source);
             OutputStream output = getApplicationContext().getContentResolver().openOutputStream(destination)) {
            if (output == null) throw new IllegalStateException("No se pudo abrir el destino");
            byte[] buffer = new byte[128 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
        } catch (Exception error) {
            getApplicationContext().getContentResolver().delete(destination, null, null);
            throw error;
        }
        values.clear();
        values.put(MediaStore.MediaColumns.IS_PENDING, 0);
        getApplicationContext().getContentResolver().update(destination, values, null, null);
        source.delete();
    }

    private void enrichMp3(File file, String artist, String title, String album,
                           String year, String genre, int trackNumber, String artwork) {
        if (!file.isFile() || artist == null || artist.isEmpty() || title == null || title.isEmpty()) return;
        HttpURLConnection connection = null;
        String lyrics = "";
        try {
            String cleanArtist = cleanArtist(artist);
            String cleanTitle = cleanTitle(title);
            String endpoint = "https://lrclib.net/api/search?artist_name="
                + URLEncoder.encode(cleanArtist, StandardCharsets.UTF_8.name())
                + "&track_name=" + URLEncoder.encode(cleanTitle, StandardCharsets.UTF_8.name());
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setRequestProperty("User-Agent", "NekoDown-Android/1.2");
            if (connection.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8
                ));
                StringBuilder payload = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) payload.append(line);
                JSONArray results = new JSONArray(payload.toString());
                int bestScore = -1;
                for (int i = 0; i < results.length(); i++) {
                    JSONObject candidate = results.getJSONObject(i);
                    String candidateLyrics = candidate.optString("plainLyrics");
                    if (candidateLyrics.isEmpty()) continue;
                    int score = similarityScore(
                        cleanTitle, candidate.optString("trackName"),
                        cleanArtist, candidate.optString("artistName")
                    );
                    if (score > bestScore) {
                        bestScore = score;
                        lyrics = candidateLyrics;
                    }
                }
                if (bestScore < 5) lyrics = "";
            }
        } catch (Exception ignored) {
            // Los demás metadatos se conservan aunque LRCLIB no responda.
        } finally {
            if (connection != null) connection.disconnect();
        }
        try {
            Mp3File mp3 = new Mp3File(file);
            ID3v2 tag = mp3.hasId3v2Tag() ? mp3.getId3v2Tag() : new ID3v24Tag();
            tag.setTitle(title);
            tag.setArtist(artist);
            if (album != null && !album.isEmpty()) tag.setAlbum(album);
            if (year != null && !year.isEmpty()) tag.setYear(year);
            if (genre != null && !genre.isEmpty()) tag.setGenreDescription(genre);
            if (trackNumber > 0) tag.setTrack(String.valueOf(trackNumber));
            if (!lyrics.isEmpty()) tag.setLyrics(lyrics);
            byte[] cover = downloadArtwork(artwork);
            if (cover != null && cover.length > 0) tag.setAlbumImage(cover, "image/jpeg");
            mp3.setId3v2Tag(tag);
            File updated = new File(file.getParentFile(), file.getName() + ".tagged");
            mp3.save(updated.getAbsolutePath());
            if (file.delete()) updated.renameTo(file);
        } catch (Exception ignored) {
            // El audio sigue siendo válido aunque un metadato no pueda escribirse.
        }
    }

    private static byte[] downloadArtwork(String artwork) {
        if (artwork == null || artwork.isEmpty()) return null;
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(artwork).openConnection();
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(7000);
            connection.setRequestProperty("User-Agent", "NekoDown-Android/1.2");
            if (connection.getResponseCode() != 200) return null;
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[32 * 1024];
            int read;
            try (java.io.InputStream input = connection.getInputStream()) {
                while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String cleanTitle(String value) {
        return value
            .replaceAll("(?i)\\s*[\\[(].*?(official|audio|video|lyrics?|visuali[sz]er).*?[\\])]", "")
            .replaceAll("(?i)\\s*[-–—]\\s*(official|audio|video|lyrics?).*$", "")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private static String cleanArtist(String value) {
        return value
            .replaceAll("(?i)\\s*-\\s*topic$", "")
            .replaceAll("(?i)\\s*(official|vevo)$", "")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private static int similarityScore(String wantedTitle, String candidateTitle,
                                       String wantedArtist, String candidateArtist) {
        String wt = normalize(wantedTitle);
        String ct = normalize(candidateTitle);
        String wa = normalizeArtist(wantedArtist);
        String ca = normalizeArtist(candidateArtist);
        int score = 0;
        if (wt.equals(ct)) score += 4;
        else if (wt.contains(ct) || ct.contains(wt)) score += 2;
        int artistScore = 0;
        if (wa.equals(ca)) artistScore = 3;
        else if (wa.contains(ca) || ca.contains(wa)) artistScore = 1;
        if (!wa.isEmpty() && !ca.isEmpty() && artistScore == 0) return 0;
        score += artistScore;
        return score;
    }

    private static String normalizeArtist(String value) {
        return normalize(value)
            .replaceAll("\\b(feat|featuring|ft|and|the)\\b", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private static String normalize(String value) {
        return java.text.Normalizer.normalize(
            value == null ? "" : value.toLowerCase(Locale.ROOT),
            java.text.Normalizer.Form.NFD
        ).replaceAll("\\p{M}", "").replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static void deleteTemporaryDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) file.delete();
            }
        }
        directory.delete();
    }
}
