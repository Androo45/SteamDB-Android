# SteamDB-Android — Cambios de la versión 1.0.9

Proyecto mejorado por Lovable a partir de tu código de GitHub.
Todo el código nuevo está en `app/src/main/java/com/example/steamdb/MainActivity.kt`.

## Novedades

1. **Sección Favoritos ⭐ (vigilancia de precios)**
   - Nueva pestaña "Favoritos ⭐" en el menú lateral.
   - Añade juegos con la estrella de su ficha (o de las filas de Charts y Ofertas).
   - Fija un **precio objetivo** por juego ("Precio objetivo" → ej. 9,99).
   - Cada vez que actualizas datos (icono ⟳), la app compara precios y muestra
     avisos "▼ ¡BAJADA!" y "🎯 ¡OBJETIVO CONSEGUIDO!" en la pestaña Favoritos.
   - Los favoritos se guardan en el teléfono (SharedPreferences), sobreviven al cerrar la app.

2. **Aviso de nueva versión**
   - Al cargar datos, consulta GitHub Releases
     (`api.github.com/repos/Androo45/SteamDB-Android/releases/latest`).
   - Si existe una versión superior a la 1.0.9, muestra un banner en la portada
     con botón "Descargar" que abre el navegador en la página de Releases.

3. **Bug corregido**: el veredicto del Oráculo de Gaben mostraba `«»` vacío;
   ahora muestra el nombre del juego consultado.

4. **Versión**: `versionCode 11`, `versionName "1.0.9"` (también en el User-Agent
   y en el menú lateral).

## Cómo compilar la APK

Opción recomendada (la que ya tienes configurada):

1. Sube estos archivos a tu repositorio de GitHub (rama `main`).
2. GitHub Actions compila la APK automáticamente con el workflow
   `.github/workflows/build-apk.yml` (Build Debug APK → artefacto `SteamDB-Pocket-APK`).
3. Crea un Release con la etiqueta `v1.0.9` y adjunta la APK ahí para que el
   aviso de nueva versión funcione en los móviles de tus usuarios.

Opción local: abre la carpeta en Android Studio → Build > Build APK(s).
(Elimina o sustituye `local.properties` si lo copias a otro ordenador.)
