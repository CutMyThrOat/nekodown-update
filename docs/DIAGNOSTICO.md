# Diagnóstico de cierres

## Informe Java local

La aplicación instala un `UncaughtExceptionHandler`. Después de un cierre Java, el siguiente informe se guarda en el directorio privado externo de la app. Incluye versión, fabricante, modelo, API y traza.

## Registro mediante ADB

```bash
adb devices
adb logcat -c
adb logcat -v threadtime AndroidRuntime:E libc:F DEBUG:F '*:S'
```

Reproducir la búsqueda mientras el registro está activo y guardar la salida. Para revisar la versión instalada:

```bash
adb shell dumpsys package com.nekodown.android
```

## Qué revisar

- `FATAL EXCEPTION`: error Java recuperable.
- `SIGSEGV`/`SIGABRT`: fallo nativo de Python, QuickJS o FFmpeg.
- `INSTALL_FAILED_UPDATE_INCOMPATIBLE`: firma distinta.
- `INSTALL_FAILED_VERSION_DOWNGRADE`: `versionCode` menor.
- Errores HTTP/yt-dlp: red, extractor o runtime JavaScript.
