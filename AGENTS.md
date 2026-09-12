# Reglas del Proyecto (AppOPT Authenticator)

Este archivo define las directivas y estándares obligatorios de desarrollo que el asistente de IA debe cumplir estrictamente en cada modificación o adición de código.

---

## 1. Escala Tipográfica Estandarizada (Material Design 3)
- **PROHIBIDO** quemar tamaños de texto directamente en los componentes (ej. `fontSize = 16.sp`).
- **OBLIGATORIO** usar la escala tipográfica centralizada en `ui/theme/Type.kt` a través de `style = MaterialTheme.typography.<rol>`:
  - `displayLarge / Medium / Small`: Para dígitos numéricos de códigos OTP (Monospace) y números de alto impacto.
  - `headlineLarge / Medium / Small`: Para títulos principales de pantalla (ej. "Bóveda bloqueada", "Authenticator").
  - `titleLarge / Medium / Small`: Para encabezados de tarjetas, diálogos, modales y barras superiores.
  - `bodyLarge / Medium / Small`: Para párrafos, descripciones, nombres de usuario y textos de lectura continua.
  - `labelLarge / Medium / Small`: Para botones, badges, indicadores y etiquetas de campos de texto.

---

## 2. Centralización y Gramática de Textos (`strings.xml`)
- **PROHIBIDO** quemar texto (*hardcoded strings*) en el código Kotlin de la interfaz o lógica.
- **OBLIGATORIO** declarar cada texto en `res/values/strings.xml` y consumirlo mediante `stringResource(R.string.<id>)` en Composables o `context.getString(R.string.<id>)`.
- **PROHIBIDO el uso de "Title Case" anglosajón:** En español, las oraciones, títulos y botones NO deben iniciar cada palabra con mayúscula (ej. INCORRECTO: "Ingresar Clave Manual", "Guardar Cambios", "Bóveda Bloqueada").
- **OBLIGATORIO el uso de "Sentence case" estándar en español:** Solo la primera letra de la oración debe ir en mayúscula (ej. CORRECTO: "Ingresar clave manual", "Guardar cambios", "Bóveda bloqueada", "Copia de seguridad y restauración"), salvo nombres propios o tras delimitadores/separadores como "Servicio / Emisor".

---

## 3. Sistema Centralizado de Animaciones y Movimiento (`ui/theme/Motion.kt`)
- **PROHIBIDO** quemar números mágicos de milisegundos (`durationMillis = 300`, `delay(750)`) o curvas de aceleración sueltas en los Composables.
- **OBLIGATORIO** consumir las duraciones (`Motion.Duration.*`), curvas (`Motion.EasingCurve.*`) y especificaciones (`Motion.Spec.*`) centralizadas en `ui/theme/Motion.kt`.

---

## 4. Sistema Centralizado de Espaciados y Dimensiones (`ui/theme/Dimensions.kt`)
- **PROHIBIDO** quemar valores arbitrarios de espaciado, elevaciones o radios de esquinas en los componentes.
- **PROHIBIDO** que en cualquier pantalla, formulario, diálogo o lista scrolleable la última sección, tarjeta o botón quede pegada al borde inferior de la pantalla o a la barra de gestos/navegación.
- **OBLIGATORIO** consumir las dimensiones centralizadas en `ui/theme/Dimensions.kt`:
  - `Dimensions.Spacing.*` para paddings y márgenes (`xs`, `sm`, `md`, `lg`, `xl`, `xxl`).
  - `Dimensions.Elevation.*` para sombras y planos (`cardDefault`, `cardDragging`, `modal`).
  - `Dimensions.CornerRadius.*` para formas (`small`, `medium`, `large`, `pill`).
  - `Dimensions.IconSize.*` para tamaños de iconos (`small`, `medium`, `large`, `hero`, `illustration`).
  - `Dimensions.ComponentSize.*` para límites y componentes estándar (`modalListMaxHeight`, `actionIconButton`, `qrCodeDisplay`, etc.).
  - `Dimensions.ComponentHeight.*` para alturas (`buttonDefault`, `buttonCompact`, `progressIndicator`).
- **OBLIGATORIO** incluir siempre un espacio o margen inferior de respiro calibrado (`Dimensions.Spacing.lg` / `Dimensions.Spacing.md` o `Spacer(modifier = Modifier.height(Dimensions.Spacing.sm))` al final del contenedor vertical) al final de cada pantalla o lista para garantizar una separación visual limpia, estética y cómoda al toque sin exceso de espacio en cualquier dispositivo.

---

## 5. Reutilización de Componentes de Interfaz (*DRY Principle*)
- **PROHIBIDO** duplicar contenedores de sección, filas de estado, animaciones complejas o sub-formularios entre pantallas o diálogos.
- **OBLIGATORIO** encapsular y reutilizar componentes modulares en `ui/components/` y sub-paquetes de componentes:
  - `SettingsSectionCard`: Para encapsular el diseño, geometría, cabeceras y paddings de las secciones de ajustes.
  - `SettingsStatusTile`: Para estandarizar las filas informativas, diagnósticos y estados del sistema.
  - `AppAnimatedButton`: Para acciones con confirmación animada e idempotencia.
  - Sub-formularios modales compartidos (ej. `DriveBackupDecryptForm`, `ServiceBrandAvatar`, `CircularTimeProgress`).

---

## 6. Centralización de Parámetros de Seguridad, Criptografía y Claves de Persistencia (*SecurityConfig & Storage Keys*)
- **PROHIBIDO** definir tiempos de expiración de portapapeles, parámetros criptográficos o cadenas literales para tablas Room, DataStore, SharedPreferences o alias Keystore dispersos en el código.
- **OBLIGATORIO** declarar y consumir todas las constantes inmutables (`const val`) en sus clases administradoras centralizadas:
  - `security/SecurityConfig.kt`: Tiempos de auto-clear de portapapeles, etiquetas de portapapeles, iteraciones PBKDF2, períodos TOTP, alias del Android Keystore, etc.
  - `PreferencesManager.kt` y `AppDatabase.kt`: Nombres de tablas, claves de persistencia y configuraciones locales.

---

## 7. Documentación y Comentarios KDoc (100% de Cobertura)
- **OBLIGATORIO** que **CADA** clase, función, método, ViewModel, repositorio y Composable incluya su bloque de documentación KDoc (`/** ... */`).
- Los comentarios deben explicar con claridad el propósito, parámetros (`@param`), valor de retorno (`@return`) y comportamiento de seguridad si aplica.

---

## 8. Arquitectura Limpia y Separación Estricta de Capas
- **Capa de Dominio (`domain/`):** Modelos inmutables y algoritmos criptográficos puros (RFC 6238 / RFC 4226 / RFC 4648) con CERO dependencias de Android.
- **Capa de Seguridad (`security/`):** Manejo de hardware seguro TEE (Android Keystore), Biometría, AppLock, Secure Clipboard y derivación PBKDF2.
- **Capa de Datos (`data/`):** Persistencia en Room, entidades cifradas y mapeo hacia modelos de dominio.
- **Capa de Presentación (`ui/`):** Jetpack Compose con ViewModels reactivos (`StateFlow`), sin lógica de negocio incrustada en la vista.

---

## 9. Principio de Cero Confianza (*Zero Trust*) y Gestión Segura de Memoria (*Memory Security & Lifecycle Purge*)
- Validar siempre los datos ingresados por el usuario (claves Base32, URIs, longitudes).
- Sobreescribir con ceros (*zeroize / fill('0')*) los arreglos de caracteres (`CharArray`) y bytes (`ByteArray`) que contengan secretos o contraseñas en memoria tras su uso.
- **PROHIBIDO** mantener secretos o cachés de descifrado en memoria RAM indefinidamente cuando la aplicación entra en segundo plano.
- **OBLIGATORIO** invocar la limpieza y sobrescritura de cachés volátiles (`clearMemoryCache()`) ante transiciones a segundo plano (`Lifecycle.Event.ON_STOP`) o eventos de advertencia de memoria del sistema (`ComponentCallbacks2.onTrimMemory()`).

---

## 10. Sistema Centralizado de Respuesta Háptica (`ui/theme/Haptics.kt`)
- **PROHIBIDO** disparar vibraciones sueltas o patrones táctiles desordenados en los Composables.
- **OBLIGATORIO** consumir los métodos semánticos de `AppHaptics` (`click`, `copy`, `success`, `error`, `dragTick`) mediante `rememberAppHaptics()`.

---

## 11. Accesibilidad Universal y Semántica para Lectores de Pantalla (`util/AccessibilityUtils.kt`)
- **PROHIBIDO** dejar dígitos OTP sin formatear para síntesis de voz (*TalkBack*).
- **OBLIGATORIO** utilizar `AccessibilityUtils.toAccessibleSpokenOtp()` y definir descripciones semánticas estructuradas en las tarjetas y temporizadores.

---

## 12. Centralización de Formateadores y Localización Regional (`util/DateTimeFormatter.kt`)
- **PROHIBIDO** crear instancias sueltas de `SimpleDateFormat` con cadenas mágicas de formato en las vistas.
- **OBLIGATORIO** formatear marcas de tiempo absolutas y relativas utilizando `DateTimeFormatter`.

---

## 13. Modelado Determinístico de Estados de Pantalla (`ui/common/UiState.kt`)
- **PROHIBIDO** gestionar flujos asíncronos mediante múltiples banderas booleanas dispersas.
- **OBLIGATORIO** estructurar los estados de vista mediante la jerarquía sellada `UiState<T>` (`Idle`, `Loading`, `Success`, `Empty`, `Error`).

---

## 14. Gestión Unificada de Diálogos Modales, Navegación Defensiva y Espaciados Estándar (*Single-Dialog State Machine & Modal Design Standard*)
- **PROHIBIDO** superponer o apilar múltiples diálogos modales en pantalla (*Stacked Modals*).
- **PROHIBIDO** cerrar un diálogo modal y abrir otro diálogo separado en su lugar para flujos encadenados, restauraciones, sub-pasos o confirmaciones (antipatrón de parpadeo y desmontaje de modales).
- **PROHIBIDO** quemar espaciados o dimensiones arbitrarias dentro de los diálogos, o permitir que listas internas desborden la pantalla sin límite de altura o sin scroll vertical.
- **OBLIGATORIO** unificar flujos encadenados (confirmaciones destructivas, modo edición, sub-pasos de descifrado/restauración) dentro de un único diálogo modal dinámico mediante una máquina de estados interna con transición fluida de contenido (idéntico al patrón de `AccountDetailsDialog.kt`).
- **OBLIGATORIO el estándar uniforme de geometría y espaciados en diálogos:**
  - **Forma del modal:** `shape = RoundedCornerShape(Dimensions.CornerRadius.large)` (16.dp).
  - **Tipografía de encabezado:** `style = MaterialTheme.typography.titleLarge` (en color `onSurface` o `error` para destructivos).
  - **Espaciado vertical del contenido:** `Arrangement.spacedBy(Dimensions.Spacing.md)` (12.dp) para formularios/bloques y `Dimensions.Spacing.sm` (8.dp) para listas o subtítulos compactos.
  - **Límite y respiro de listas internas:** `heightIn(max = Dimensions.ComponentSize.modalListMaxHeight)` (320.dp) con scroll vertical (`LazyColumn` o `verticalScroll`) y `contentPadding = PaddingValues(bottom = Dimensions.Spacing.xs)`.
  - **Tarjetas y superficies internas:** `shape = RoundedCornerShape(Dimensions.CornerRadius.medium)` y padding `Dimensions.Spacing.md`.
  - **Botones interactivos y modales:** `shape = RoundedCornerShape(Dimensions.CornerRadius.medium)` con tipografía `MaterialTheme.typography.labelLarge`.
- **OBLIGATORIO el estándar uniforme de nomenclatura y posición en botones de diálogos:**
  - **PROHIBIDO el uso de la palabra «Cancelar»:** En todos los diálogos modales y alertas solo se permite el uso estandarizado de **«Cerrar»** o **«Volver»**.
  - **«Cerrar» (`R.string.action_close`):** Para el botón de descarte del estado/diálogo principal o informativos.
  - **«Volver» (`R.string.settings_drive_details_back`):** Para regresar de un sub-paso, pantalla secundaria, modo edición o abortar una confirmación/acción destructiva sin ejecutarla.
  - **Posición Material Design 3 (Ergonomía móvil):**
    - **Diálogos de 2 botones (Acción + Descarte):** El botón de salida/regreso («Cerrar» o «Volver») debe ubicarse SIEMPRE a la **izquierda** (como `TextButton`), mientras que el botón de acción afirmativa/mutante/destructiva se ubica a la **derecha** (como `Button` primario o de error).
- **OBLIGATORIO** implementar navegación defensiva hacia atrás en `onDismissRequest`: presionar afuera o el botón atrás del sistema debe revertir al estado/paso anterior antes de cerrar el modal por completo.
- **OBLIGATORIO la jerarquía de animaciones en transiciones de diálogos (*Fade Through & Directional SizeTransform*):**
  - **PROHIBIDO** animar simultáneamente opacidad y tamaño sin desfase temporal o con resortes sin amortiguación que causen recortes progresivos, compresión de layouts, desborde inferior o temblor visual en componentes rígidos (ej. tarjetas OTP o listas).
  - **PROHIBIDO** usar `animateContentSize` en contenedores externos que envuelven `Crossfade` o transiciones asíncronas de contenido.
  - **OBLIGATORIO** orquestar las transiciones de pasos y sub-estados dentro del diálogo mediante `AnimatedContent` consumiendo `Motion.Spec.dialogStepContentTransform()`:
    1. **Fase de salida:** El contenido saliente se desvanece por completo a 0% de opacidad (`fadeOut`, 75ms) manteniendo el tamaño fijo del contenedor.
    2. **Fase de ajuste dimensional:** En expansión, el contenedor salta instantáneamente al tamaño final (`snap(delayMillis = 75)`) mientras la opacidad es cero; en contracción, el contenedor anima su altura suavemente tras la desaparición del contenido.
    3. **Fase de entrada:** El nuevo contenido entra suavemente (`fadeIn`, 150ms) con 75ms de retardo en un contenedor que ya tiene su dimensión final 100% garantizada y sin desbordes.
    4. **Sin recorte invasivo:** Fijar `clip = false` en `SizeTransform` para preservar radios de curvatura y sombras intactas en cada fotograma.

---

## 15. Manejo Seguro, Descriptivo y Accionable de Excepciones (*Zero-Leakage & Actionable Error Handling*)
- **PROHIBIDO** exponer mensajes técnicos internos de excepciones criptográficas, de red o de sistema (`e.message`, `e.printStackTrace()`, nombres de clases Java/C++, OpenSSL o rutas de archivos) en la interfaz de usuario, diálogos o callbacks del sistema.
- **PROHIBIDO el "antipatrón mudo" (*Swallowing Errors*):** Dejar bloques `catch` o callbacks de error con `null`, cadenas vacías o sin retroalimentación cuando el usuario requiere conocer el resultado de una acción.
- **OBLIGATORIO** capturar excepciones técnicas de bajo nivel y presentar al usuario mensajes **amigables, descriptivos y accionables** centralizados en `res/values/strings.xml` (explicando en lenguaje cotidiano qué falló y qué paso correctivo puede tomar).
- **OBLIGATORIO el manejo resiliente de tasa de solicitudes API (HTTP 429 / Rate Limiting):** En todas las integraciones de red y nube (como Google Drive API), capturar respuestas de saturación de cuota o límite de peticiones (HTTP 429 / 503) y presentar mensajes amigables y accionables de reintento (`R.string.settings_drive_error_rate_limited`), evitando desbordes o fallos silenciosos.

---

## 16. Estándar de Pruebas Unitarias de Regresión Criptográfica e Instalación Automática (*Testing & Automated Device Installation*)
- **PROHIBIDO** introducir nuevos motores de cálculo, algoritmos de derivación, parsers de URI o funciones de firma sin su correspondiente suite de pruebas unitarias automatizadas.
- **OBLIGATORIO** incluir pruebas en `app/src/test/` con 100% de cobertura en escenarios límite (*edge cases*): cadenas vacías, entradas nulas, formatos Base32 con/sin padding, alteraciones de orden y marcas de tiempo extremas.
- **OBLIGATORIO** tras finalizar las modificaciones de código y validar las pruebas unitarias con `./gradlew testDebugUnitTest`, ejecutar `./gradlew installRelease` para compilar e instalar automáticamente el APK en los dispositivos o emuladores conectados.

---

## 17. Blindaje de Superficie y Prevención de Capturas (*FLAG_SECURE & App Switcher Masking*)
- **PROHIBIDO** permitir capturas de pantalla, grabaciones de video o miniaturas legibles de secretos en la vista de aplicaciones recientes (*App Switcher*).
- **OBLIGATORIO** mantener activa la bandera `WindowManager.LayoutParams.FLAG_SECURE` en la ventana principal de la aplicación para proteger la visibilidad de códigos 2FA y claves criptográficas.

---

## 18. Aislamiento de Hilos para Operaciones Criptográficas y de E/S (*Thread Confinement / Coroutine Dispatchers*)
- **PROHIBIDO** ejecutar derivación de claves PBKDF2, cifrado/descifrado AES-256-GCM o consultas a base de datos en el hilo principal (`Dispatchers.Main`).
- **OBLIGATORIO** confinar las operaciones intensivas de CPU criptográfica en `Dispatchers.Default` y las operaciones de base de datos/red en `Dispatchers.IO`, garantizando 60/120 FPS fluidos sin caídas de cuadros.

---

## 19. Inmutabilidad Estricta y Estabilidad de Parámetros en Compose (*Compose Stability & UDF*)
- **PROHIBIDO** exponer modelos mutables o clases inestables en los estados de vista que provoquen recomposiciones innecesarias durante el scroll o la actualización de códigos.
- **OBLIGATORIO** marcar las jerarquías de estado con `@Immutable` o `@Stable` y estructurar el flujo de eventos según el patrón unidireccional de datos (*UDF / MVI*).

---

## 20. Versionado de Esquemas y Compatibilidad Hacia Adelante (*Schema Versioning & Compatibility*)
- **PROHIBIDO** generar respaldos en la nube o formatos de exportación sin declarar explícitamente su versión de esquema de datos.
- **OBLIGATORIO** incluir una cabecera de versión (`version` / `schemaVersion`) en los formatos JSON de transferencia y sobre criptográfico (`CURRENT_BACKUP_VERSION`), garantizando compatibilidad hacia atrás y hacia adelante.

---

## 21. Mensajes de Control de Versiones en Español (*Spanish Git Commits*)
- **PROHIBIDO** redactar mensajes de commit de Git en inglés u otros idiomas.
- **OBLIGATORIO** escribir todos los mensajes de commit en **español**, utilizando la estructura de commits semánticos (ej. `feat: ...`, `fix: ...`, `refactor: ...`, `test: ...`, `docs: ...`) con descripciones claras y gramaticalmente correctas en español.

---

## 22. Idempotencia, Bloqueo de Doble Pulsación y Retroalimentación en Botones (*Idempotent Animated Buttons*)
- **PROHIBIDO** permitir pulsaciones múltiples o concurrentes en botones que ejecutan acciones asíncronas, mutantes o destructivas (guardado, edición, borrado, sincronización o transferencia).
- **OBLIGATORIO** utilizar `AppAnimatedButton` o banderas atómicas de bloqueo (`isProcessing`) para congelar la interactividad al primer toque, proporcionando confirmación visual inmediata (palomita blanca sobre fondo `SafeGreen` en éxito o 'X' blanca sobre fondo `UrgentRed` en fallo) y respuesta háptica semántica (`AppHaptics`).

---

## 23. Simetría de Controles, Visibilidad Completa y Adaptación Dinámica de Textos (*Dynamic Sizing, Button Symmetry & Zero Clipping*)
- **PROHIBIDO** que botones adyacentes o agrupados en una misma fila tengan alturas dispares, deformaciones visuales o anchos asimétricos debido a textos con diferentes longitudes.
- **PROHIBIDO** el recorte, corte vertical o truncamiento de texto (*text clipping / ellipsis indeseado*) en cualquier contenedor (botones, tarjetas, modales, encabezados o listas) por forzar `maxLines = 1` o alturas fijas cuando el texto no quepa en una sola línea.
- **OBLIGATORIO** fijar pesos simétricos (`Modifier.weight(1f)`) en filas de botones adyacentes para que compartan equitativamente el ancho disponible.
- **OBLIGATORIO** sincronizar la altura de botones y contenedores hermanos utilizando `Modifier.height(IntrinsicSize.Min)` en la fila y `Modifier.fillMaxHeight().heightIn(min = Dimensions.ComponentHeight.buttonDefault)` (50.dp o 36.dp) en los botones, de modo que si un control requiere más altura por salto de línea, todos los controles adyacentes se adapten dinámicamente a la misma altura exacta.
- **OBLIGATORIO** diseñar textos con flujo natural (`wrapContentHeight()`, `textAlign = TextAlign.Center`, `PaddingValues` calibrados), garantizando legibilidad total, simetría y cero truncamiento.

---

## 24. Ámbito de Estados: Centralización Compartida vs. Aislamiento Local (*Shared Managers vs. Local UI State Scope*)
- **PROHIBIDO** centralizar en Managers o Singletons globales estados efímeros que solo pertenecen a una pantalla, formulario o diálogo particular (ej. visibilidad de contraseña, pestañas de modales, textos en edición, selecciones transitorias o animaciones).
- **PROHIBIDO** duplicar o mantener cálculos aislados en múltiples ViewModels para estados transversales del sistema que requieren una única fuente de verdad (ej. estado de sincronización en la nube, temporizadores de portapapeles, sesión de proveedores o estado de bloqueo biométrico).
- **OBLIGATORIO definir el ámbito del estado según su ciclo de vida y alcance:**
  - **Estado Compartido / Global (`data/cloud/`, `security/`, `domain/repository/`):**
    - Debe implementarse mediante `Manager`, `Repository` o `Coordinator` como fuente única de verdad cuando:
      1. Múltiples pantallas o procesos en segundo plano (como `WorkManager`) observan o mutan el mismo dato de forma concurrente (ej. `CloudVaultSyncManager`, `SecureClipboardManager`, `AppLockManager`).
      2. El ciclo de vida de la tarea o dato supera la permanencia de la pantalla activa (ej. cuenta regresiva de limpieza de portapapeles, sincronización en progreso).
      3. Gestiona recursos o hardware exclusivos del sistema (Keystore, biometría, conectividad).
  - **Estado Local / Aislado (`ViewModel` / `UiState` / `remember`):**
    - Debe confinarse estrictamente al `ViewModel` o Composable cuando:
      1. Es transaccional o descartable al cerrar la pantalla/diálogo (ej. opciones de cuestionario BIP-39, filtros temporales de búsqueda).
      2. Modela entradas del usuario en curso antes de confirmarse (ej. campos de texto en formularios de alta/edición).
      3. Controla elementos puramente visuales, animaciones o coordenadas de renderizado (`isMenuOpen`, `isDragging`, `rememberScrollState`).

---

## 25. Resiliencia Fuera de Línea y Degradación Elegante de Permisos (*Offline-First Resilience & Permission Degradation*)
- **PROHIBIDO** condicionar o bloquear la funcionalidad básica de la aplicación (generación de códigos TOTP/HOTP, adición manual, exportación offline o gestión de papelera) a la disponibilidad de conexión a internet o a la concesión de permisos opcionales del sistema.
- **PROHIBIDO** provocar cierres forzosos (*crashes*) o bloqueos en la interfaz cuando el usuario deniegue permisos en tiempo de ejecución (Cámara, Notificaciones, Batería o Alarmas exactas).
- **OBLIGATORIO diseñar bajo el principio Offline-First:**
  1. La base de datos local y los algoritmos criptográficos son completamente autónomos y funcionan al 100% sin red.
  2. Las operaciones en la nube (Google Drive) deben ejecutarse de forma asíncrona y no bloqueante, manejando estados de desconexión sin degradar la experiencia local.
  3. Ante la denegación de permisos de cámara o notificaciones, presentar vistas informativas y alternativas claras (ej. entrada manual de clave o enlaces directos a los ajustes del sistema) sin interrumpir el flujo del usuario.

