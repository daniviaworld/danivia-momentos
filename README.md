# DANIVIA Momentos

Álbum familiar privado de **fotos y vídeos**. Cualquiera de la familia sube desde su móvil y todos lo ven en el móvil o en la **Android TV**, con la misma app.

- Una sola app para el móvil y la Android TV (`com.daniviaworld.momentos`).
- Grupo familiar privado con código de 6 letras. La TV se vincula con un número de 6 cifras.
- Cualquier miembro sube fotos. Cada uno borra las suyas y el administrador puede borrar cualquiera.
- **Mejora automática** de cada foto antes de subirla: luz, contraste, color, dominantes y nitidez. Se ve el "antes y después".
- **Vídeos** (hasta 5 min): se reducen a 720p (~30 MB por minuto) y se mejoran (brillo, contraste, color). Los HDR se convierten a color normal para que se vean bien en cualquier tele. En diapositivas se reproducen enteros y luego sigue.
- Todo ordenado por fecha de subida, de lo más nuevo a lo más antiguo.
- 5 formas de verlas: **Cuadrícula**, **Carrusel**, **Estilo cine** (portada + filas), **Cubo 3D** que gira solo y **Diapositivas** con zoom lento.
- **Me gusta ❤️ y comentarios** en cada foto y vídeo. Se ve quién ha dado me gusta, y en estilo cine hay una fila "Las más queridas". En la tele se ven los corazones y los últimos comentarios.
- Avisos en el móvil cuando alguien sube algo nuevo, o cuando le dan me gusta o comentan **tus** fotos. Se comprueba cada ~30 minutos.
- **Se actualiza sola**: al abrirla mira si hay una versión nueva en GitHub, pregunta y se instala encima, sin Downloader y sin perder nada. También hay un botón en **Familia → Buscar actualizaciones**.

---

## Puesta en marcha (una sola vez)

### 1. Firebase

1. Entra en <https://console.firebase.google.com> y crea un proyecto llamado `danivia-momentos`.
2. **Añadir app → Android**. Paquete: `com.daniviaworld.momentos`. Descarga `google-services.json` y guárdalo.
3. **Authentication → Método de inicio de sesión → Anónimo → Habilitar**.
4. **Firestore Database → Crear base de datos**, en modo producción, ubicación `europe-southwest1 (Madrid)`.
   - En **Reglas**, borra lo que haya, pega el contenido de `firebase/firestore.rules` y pulsa **Publicar**.
5. **Storage**: Firebase pide pasar al plan **Blaze** (de pago por uso). Hay que añadir una tarjeta, pero el uso de una familia entra en la parte gratuita.
   - Al crear el bucket, elige una ubicación de **EE. UU. (us-central1)**. Así entra en la cuota gratuita "Always Free" de 5 GB, que da para unas 5.000 fotos o unos 150 minutos de vídeo. Pasado eso, el coste es de céntimos al mes.
   - Recomendado: en Google Cloud → Facturación → **Presupuestos y alertas**, crea un presupuesto de 1 € para que te avise si algo se dispara.
   - En **Reglas** de Storage, pega `firebase/storage.rules` y pulsa **Publicar**. Si Firebase pregunta si Storage puede consultar Firestore, acepta.

### 2. GitHub (compila el APK solo)

> El repositorio debe ser **público** para que las apps puedan descargar las actualizaciones. Es seguro: el `google-services.json` y la firma van como secretos y nunca se suben al código.

En el repositorio, ve a **Settings → Secrets and variables → Actions → New repository secret** y crea estos 3 secretos:

| Nombre | Valor |
|---|---|
| `GOOGLE_SERVICES_JSON` | Todo el contenido del archivo `google-services.json` |
| `KEYSTORE_BASE64` | Contenido del archivo `KEYSTORE_BASE64.txt` que te he pasado aparte |
| `KEYSTORE_PASSWORD` | Contenido del archivo `KEYSTORE_PASSWORD.txt` |

> La firma (`KEYSTORE_*`) es la "llave" de la app: con ella cada APK nuevo se instala encima del anterior sin borrar nada. Guarda esos dos archivos en un sitio seguro y no los subas al repositorio.

Después ve a **Actions → Compilar APK → Run workflow**. En unos 5 minutos aparece en **Releases** el archivo `DaniviaMomentos.apk`.

Enlace fijo a la última versión (si el repositorio es público):
`https://github.com/<usuario>/danivia-momentos/releases/latest/download/DaniviaMomentos.apk`

### 3. Instalar (sólo la primera vez)

- **Móviles**: abre el enlace anterior, descarga el APK e instálalo. Hay que permitir "instalar apps de origen desconocido".
- **Android TV**: instala la app **Downloader** desde Google Play, escribe el enlace y se instala. La app aparece en el menú de apps de la tele.
- A partir de ahí, cada vez que se suba un cambio a GitHub, la app avisará al abrirla. La primera vez Android pedirá **permitir instalar apps de esta fuente** para DANIVIA Momentos: actívalo, vuelve atrás y pulsa **Actualizar**.

---

## Uso

1. **Tú (primera vez)**: abre la app → escribe tu nombre → **Crear una familia nueva**. Ya eres administrador.
2. En **Familia** (icono de persona) verás el **código de 6 letras**. Dáselo a la familia.
3. **Los demás**: abren la app → su nombre → **Unirme a mi familia** → escriben el código.
4. **La tele**: abre la app en la TV → sale un número de 6 cifras → en un móvil ve a **Familia → Vincular una TV**, escribe el número y ya está.
5. **Subir**: botón **Subir** → eliges fotos y vídeos de la galería (hasta 50 a la vez) → **Subir**. La mejora automática viene activada. Con vídeos, deja la app abierta hasta que termine.

Consejo: haz administrador a otro adulto. Si cambias de móvil o reinstalas la app, él podrá gestionar el grupo.

### Mando de la TV
- Flechas para moverte y **OK** para abrir una foto.
- En **Carrusel**, **Cubo** y **Diapositivas**: ◀ ▶ pasan de foto (en el cubo, ▲ ▼ giran en vertical).
- Viendo un vídeo: **OK** pausa y reanuda; ◀ ▶ pasan al anterior o al siguiente.
- **Atrás** vuelve.

---

## Estructura técnica

- Kotlin + Jetpack Compose, minSdk 24, targetSdk 35.
- Firebase: Auth anónima, Firestore (datos) y Storage (fotos, miniaturas de 520 px, vídeos MP4 y su portada).
- Vídeo: Media3 Transformer (compresión + efectos) y ExoPlayer (reproducción).
- Las fotos se suben con un máximo de 2560 px en JPEG al 88 % (≈ 0,5–1 MB cada una).
- `app/src/main/java/com/daniviaworld/momentos/`
  - `data/`: Firebase (`Repo`), cola de subida, preferencias.
  - `image/`: `ImageProcessor` (leer, enderezar, reducir), `Enhancer` (mejora automática) y `VideoProcessor` (vídeos).
  - `ui/`: pantallas; `ui/views/`: los 5 modos de ver.
  - `work/`: avisos de fotos nuevas, me gusta y comentarios.
  - `update/`: autoactualización desde las Releases de GitHub.
- `firebase/`: reglas de seguridad.
- `.github/workflows/build.yml`: compilación y publicación del APK.

### Publicar en Google Play más adelante
Ya está preparada para móvil + TV (banner, `LEANBACK_LAUNCHER`, mando). Faltaría:
- Política de privacidad.
- Cumplir las normas de Google Play para apps con menores.
- Generar un `.aab` en vez de `.apk`.
