# Guía para continuar el proyecto

## Objetivo

Mantener y mejorar NekoDown Android sin romper la instalación, la firma ni las descargas.

## Componentes principales

- `MainActivity.java`: navegación entre Inicio, Cola e Historial y comprobación de Releases Android.
- `HomeFragment.java`: búsqueda YouTube, búsqueda de artistas/álbumes, muestras, calidades y preparación de descargas.
- `MediaDownloadWorker.java`: descargas en segundo plano mediante WorkManager, progreso y publicación en MediaStore.
- `AlbumDetailFragment.java`: detalle de álbum y selección de canciones.
- `QueueFragment.java`: cola, pausa/reanudación y progreso.
- `HistoryFragment.java`: historial local.
- `NekoApplication.java`: captura de excepciones Java y almacenamiento local de informes.

## Flujo de búsqueda

1. Se inicializan yt-dlp y FFmpeg.
2. Una URL se trata como elemento individual con `--no-playlist`.
3. Una búsqueda usa `ytsearch12:` y devuelve resultados paginados.
4. El modo Artistas/Álbumes consulta el catálogo musical y carga portadas.
5. La descarga se encola en WorkManager; el resultado se publica en Música/Descargas.

## Actualizaciones

`MainActivity` consulta:

```text
https://api.github.com/repos/CutMyThrOat/nekodown-update/releases?per_page=20
```

Solo considera etiquetas `android-v...` y busca el APK adjunto. Android requiere que el APK nuevo use la misma clave de firma.

## Diagnóstico

Las excepciones Java se guardan en:

```text
Android/data/com.nekodown.android/files/diagnostics/
```

Un cierre nativo de Python/FFmpeg puede requerir `adb logcat` o un tombstone del dispositivo.

## Riesgos conocidos

- No incrustar tokens de GitHub en el APK.
- No cambiar la clave de producción.
- No actualizar yt-dlp/FFmpeg automáticamente sin probar búsquedas, MP3, MP4, listas y S23.
- El servidor empaquetado de PC tiene un fallo separado pendiente: referencia `scoreEntry` sin definición.
