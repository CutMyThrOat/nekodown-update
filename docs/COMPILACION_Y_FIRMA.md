# Compilación, firma y publicación

## Desarrollo

Usar JDK 17 y SDK 36. No subir `local.properties`.

```powershell
cd android
.\\gradlew.bat assembleRelease
```

## Firma

La clave de producción está fuera del repositorio, en el equipo del propietario. Nunca debe copiarse a GitHub ni enviarse a otra IA.

Variables recomendadas para CI:

- `NEKODOWN_KEYSTORE_BASE64`
- `NEKODOWN_KEYSTORE_PASSWORD`
- `NEKODOWN_KEY_ALIAS`
- `NEKODOWN_KEY_PASSWORD`

La clave pública actual puede verificarse con `apksigner verify --print-certs`. Todas las actualizaciones deben conservar el mismo certificado.

## Release

1. Aumentar `versionCode` y `versionName`.
2. Ejecutar compilación y pruebas.
3. Firmar con la clave de producción.
4. Crear una Release `android-vX.Y.Z`.
5. Adjuntar el APK.
6. Probar la actualización sobre la versión anterior.
