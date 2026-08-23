# NekoDown Android

Aplicación Android para buscar y descargar audio/vídeo usando `youtubedl-android` (yt-dlp + FFmpeg), con búsqueda de YouTube, catálogo de artistas/álbumes, cola de descargas, historial y reproductor de muestras.

## Estado actual

- Versión publicada: `1.6.2`.
- Paquete: `com.nekodown.android`.
- Android mínimo: API 26.
- Arquitectura incluida: `arm64-v8a` (compatible con Galaxy S23 Ultra).
- Release: https://github.com/CutMyThrOat/nekodown-update/releases/tag/android-v1.6.2
- Repositorio: https://github.com/CutMyThrOat/nekodown-update

## Estructura

```text
.
├── android/                 # Proyecto Gradle Android
│   ├── app/src/main/        # Código Java, layouts, iconos y temas
│   ├── gradle/              # Gradle Wrapper
│   ├── build.gradle.kts
│   └── settings.gradle.kts
├── .github/workflows/       # Compilación automática en GitHub Actions
├── .github/dependabot.yml   # Actualizaciones propuestas de dependencias
└── docs/                    # Documentación técnica y mantenimiento
```

## Compilación local

Requisitos: Android Studio/SDK, JDK 17 y Android SDK 36.

```bash
cd android
./gradlew assembleRelease
```

En Windows se puede usar `gradlew.bat assembleRelease`. El APK generado queda en `android/app/build/outputs/apk/release/`.

## Notas importantes

- No se incluyen claves `.jks`, `local.properties`, APKs ni tokens en Git.
- Las versiones instalables deben firmarse con la misma clave de producción.
- El comprobador de actualizaciones consulta Releases con etiqueta `android-vX.Y.Z`.
- El diagnóstico de excepciones se guarda localmente; no se envían datos automáticamente a un servidor.

Consulta `docs/AI_HANDOFF.md` si otra persona o IA debe continuar el proyecto.
