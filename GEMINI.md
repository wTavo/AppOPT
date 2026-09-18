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
- **OBLIGATORIO la profundidad táctil y elevaciones perimetrales estandarizadas:**
  - Aplicar elevaciones y sombras consistentes (`Dimensions.Elevation.cardDefault`, sombras perimetrales suaves con `ambientColor` y `spotColor` sutiles) en las tarjetas de sección (`SettingsSectionCard`), tarjetas de servicio en la pantalla de inicio y controles principales (barra de búsqueda, botón de visibilidad de códigos), garantizando un contraste táctil limpio y relieve tridimensional óptimo tanto en tema claro como oscuro sin artefactos visuales.

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
- **OBLIGATORIO la Estructura Tripartita Inmutable en Diálogos (Cabecera y Botones Fijos con Cuerpo Central Scrolleable):**
  - **PROHIBIDO** permitir que el título/cabecera del modal o los botones de acción inferiores desaparezcan, se desplacen o queden ocultos fuera de la pantalla cuando el contenido interno se expande o cuando se despliega el teclado virtual (`imePadding`).
  - **OBLIGATORIO** estructurar el contenedor de cada paso o estado del modal en tres secciones independientes:
    1. **Cabecera fija superior:** Título principal de la pantalla/paso (`MaterialTheme.typography.titleLarge`) y/o controles de acción superior (botones de edición, cerrar o actualizar).
    2. **Cuerpo central scrolleable aislado:** Confinar el scroll vertical únicamente al cuerpo central mediante `Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())` (o `LazyColumn` / contenedores con `weight(1f, fill = false)`), garantizando que con contenidos breves el modal solo ocupe su altura natural (`fill = false`) y con contenidos extensos o teclado activo el scroll quede estrictamente confinado al centro.
    3. **Pie fijo inferior:** Botones de acción (`AppDialogActionButtons` o `Row(TextButton + AppAnimatedButton)`), siempre anclados y visibles en la base del diálogo.
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
- **OBLIGATORIO la simetría equitativa (50/50) en botones de diálogo (Directiva 23):**
  - **PROHIBIDO** que en diálogos de 2 botones el botón de descarte y el de acción tengan anchos dispares o calculados por contenido (`wrap_content`).
  - **OBLIGATORIO** fijar pesos simétricos (`Modifier.weight(1f)`) en ambos botones mediante `AppDialogActionButtons` o contenedores `Row` dedicados con `Arrangement.spacedBy(Dimensions.Spacing.sm)` y altura mínima estandarizada `Dimensions.ComponentHeight.buttonDefault` (50.dp).
  - En diálogos con un único botón de descarte solitario («Cerrar»), alinearlo a la derecha (`Arrangement.End`) conservando su ancho natural sin estirarlo innecesariamente.
- **OBLIGATORIO la categorización semántica y tonal de diálogos modales (`ModalTone`) con inmutabilidad geométrica:**
  - **PROHIBIDO** alterar las geometrías perimetrales (16.dp), paddings interiores (16.dp), alturas de botones (50.dp) o espaciados entre bloques (12.dp) según la categoría del diálogo. Los espaciados y dimensiones son estrictamente idénticos e inmutables en todas las categorías.
  - **OBLIGATORIO** clasificar los modales mediante el parámetro `tone: ModalTone`:
    - `ModalTone.STANDARD`: Para flujos ordinarios, altas, edición y configuración. Aplica fondo neutro `surface`, borde sutil `outlineVariant` al 15% de opacidad y velo scrim oscuro estándar.
    - `ModalTone.DESTRUCTIVE`: Para acciones críticas, irreversibles o de alto impacto (desvincular cuentas en la nube, reemplazar copias existentes de respaldo, vaciar papelera o eliminar definitivamente cuentas):
      1. Fondo de tarjeta sutilmente teñido de advertencia (`errorContainer.copy(alpha = 0.14f)` compuesto sobre `surface`).
      2. Borde perimetral neutro estándar `outlineVariant` al 15% de opacidad para consistencia geométrica sin saturación cromática.
      3. Velo de fondo desenfocado estándar neutro (Color.Black) idéntico a los modales estándar, sin tinte de advertencia.
      4. Icono centrado y título principal en color `MaterialTheme.colorScheme.error`.
      5. Botón de confirmación con esquema de color de error (`isDestructive = true` en `AppDialogActionButtons` o `AppAnimatedButton(containerColor = error)`).
- **OBLIGATORIO la gestión determinista del velo de desenfoque (*Zero-Ghost Blur Lifecycle*):**
  - **PROHIBIDO** gobernar el desenfoque de fondo mediante banderas booleanas aisladas o contadores sueltos que puedan quedar huérfanos al desmontarse diálogos o ante navegaciones defensivas del sistema (*Sticky / Orphan Blur*).
  - **OBLIGATORIO** registrar cada modal en `ModalOverlayController` mediante un identificador único en su composición (`registerModal(id)` / `unregisterModal(id)`) o consumir `LocalModalDismissHandler`, garantizando que el desenfoque solo esté activo mientras exista al menos un diálogo modal visible y se limpie atómicamente de inmediato al cerrarse el último modal.
- **OBLIGATORIO** implementar navegación defensiva hacia atrás en `onDismissRequest`: presionar afuera o el botón atrás del sistema debe revertir al estado/paso anterior antes de cerrar el modal por completo.
- **OBLIGATORIO la jerarquía de animaciones en transiciones de diálogos (*Unified Monolithic Dynamic Island Morphing*):**
  - **PROHIBIDO** fragmentar diálogos modales interactivos en múltiples ranuras animadas independientes (`title`, `text`, `confirmButton`) que compitan entre sí y provoquen colisiones de layout o desbordes en la parte inferior.
  - **PROHIBIDO** animar escala sobre árboles de texto (`scaleIn`/`scaleOut`) que causen vibración en fuentes monoespaciadas o códigos OTP.
  - **PROHIBIDO** usar `animateContentSize` en contenedores externos que envuelven `Crossfade` o transiciones asíncronas de contenido.
  - **PROHIBIDO** usar `AlertDialog` nativo (`WRAP_CONTENT`) para flujos multietapa con cambio dinámico de tamaño: la ventana flotante del sistema operativo Android re-centra su marco físico en cada fotograma vía IPC con `WindowManagerService`, provocando desincronización y temblor.
  - **OBLIGATORIO** utilizar `AppModalDialog` para todos los diálogos de múltiples pasos: su ventana fija de pantalla completa (`usePlatformDefaultWidth = false`) elimina toda interferencia de Android OS y permite que la tarjeta visual (`Surface`) mute sus dimensiones de forma 100% líquida en Compose GPU.
  - **OBLIGATORIO** unificar todo el diálogo (título, cuerpo y botones `AppDialogActionButtons`) dentro de un único contenedor monolítico animado (`AnimatedContent`), consumiendo `Motion.Spec.dialogStepContentTransform()`:
    1. **Fase de salida fluida:** El contenido saliente se desvanece de inmediato (`fadeOut`, 90ms). Sin desplazamiento vectorial.
    2. **Fase de ajuste dimensional líquido:** La tarjeta modal muta sus dimensiones suavemente en Compose GPU con física de resortes elásticos sin rebote (`spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)`).
    3. **Fase de entrada suave:** El nuevo contenido emerge suavemente (`fadeIn`, 180ms con 70ms de retardo). Sin desplazamiento vectorial.
    4. **Sin recorte invasivo:** Fijar `clip = false` en `SizeTransform` para preservar radios de curvatura de 16.dp y sombras intactas en cada fotograma.


---

## 15. Manejo Seguro, Descriptivo y Accionable de Excepciones (*Zero-Leakage & Actionable Error Handling*)
- **PROHIBIDO** exponer mensajes técnicos internos de excepciones criptográficas, de red o de sistema (`e.message`, `e.printStackTrace()`, nombres de clases Java/C++, OpenSSL o rutas de archivos) en la interfaz de usuario, diálogos o callbacks del sistema.
- **PROHIBIDO el "antipatrón mudo" (*Swallowing Errors*):** Dejar bloques `catch` o callbacks de error con `null`, cadenas vacías o sin retroalimentación cuando el usuario requiere conocer el resultado de una acción.
- **OBLIGATORIO** capturar excepciones técnicas de bajo nivel y presentar al usuario mensajes **amigables, descriptivos y accionables** centralizados en `res/values/strings.xml` (explicando en lenguaje cotidiano qué falló y qué paso correctivo puede tomar).
- **OBLIGATORIO el manejo resiliente de tasa de solicitudes API (HTTP 429 / Rate Limiting):** En todas las integraciones de red y nube (como Google Drive API), capturar respuestas de saturación de cuota o límite de peticiones (HTTP 429 / 503) y presentar mensajes amigables y accionables de reintento (`R.string.settings_drive_error_rate_limited`), evitando desbordes o fallos silenciosos.

---

## 16. Estándar de la Pirámide de Pruebas e Instalación Automática (*Testing Pyramid & Automated Device Installation*)
- **PROHIBIDO** introducir nuevos motores de cálculo, algoritmos de derivación, parsers de URI, funciones de firma o flujos de usuario críticos sin su correspondiente cobertura en la pirámide de pruebas.
- **OBLIGATORIO la Base de la Pirámide (Pruebas Unitarias Rápidas en `app/src/test/`):**
  - Cobertura estricta al 100% en escenarios límite (*edge cases*): cadenas vacías, entradas nulas, formatos Base32 con/sin padding, alteraciones de orden, sanitización homoglífica y marcas de tiempo extremas.
  - Aislamiento absoluto de Android Framework y ejecución ultra rápida en JVM sin emulador.
- **OBLIGATORIO la Cúspide de la Pirámide (Pruebas Instrumentadas de UI en `app/src/androidTest/`):**
  - Validar interacciones críticas de Compose (altas de cuenta, diálogos modales, renderizado de códigos y estados vacíos) en entorno Android real o emulado.
  - Usar obligatoriamente `createAndroidComposeRule<TestActivity>()` donde `TestActivity` esté configurada para pruebas desatendidas (`setShowWhenLocked(true)`, `setTurnScreenOn(true)` y `FLAG_KEEP_SCREEN_ON`), garantizando que la ejecución no falle si el dispositivo físico está bloqueado o con pantalla apagada.
- **OBLIGATORIO la Verificación y Despliegue Automatizado:**
  - Tras finalizar las modificaciones de código, validar primero la suite de pruebas con `./gradlew testDebugUnitTest`.
  - Ejecutar `./gradlew installRelease` para compilar con optimización R8 e instalar automáticamente el APK en los dispositivos o emuladores conectados.

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

---

## 26. Blindaje Antifraude, Prevención de Ingeniería Social y Sanitización Óptica (*Anti-Spoofing & Pre-Commit Verification*)
- **PROHIBIDO** persistir directamente cuentas escaneadas por QR o importadas sin antes permitir que el usuario verifique conscientemente la identidad visual del emisor y la cuenta.
- **PROHIBIDO** permitir caracteres invisibles, espacios de ancho cero (*zero-width characters*) o códigos de control Unicode en los campos de emisor (*issuer*) y nombre de cuenta (*label*).
- **PROHIBIDO** silenciar o ignorar colisiones de homóglifos o scripts mixtos (ej. caracteres cirílicos idénticos visualmente a caracteres latinos como 'а', 'о', 'р') diseñados para suplantar marcas legítimas (*IDN Homograph Attack*).
- **OBLIGATORIO sanitización y análisis antifraude previo al commit (`SecurityAnalysisUtils`):**
  1. **Sanitización de invisibles:** Eliminar caracteres de formato Unicode invisibles (`\u200B` a `\u200F`, `\uFEFF`, etc.) antes de validar o persistir.
  2. **Detección de scripts mixtos:** Alertar al usuario de forma explícita si el emisor o cuenta mezcla alfabetos dispares para imitar marcas reconocidas.
  3. **Detección de colisiones y duplicados:** Advertir visualmente si ya existe una cuenta con el mismo emisor y usuario en la bóveda antes de sobrescribir o crear duplicados involuntarios.
  4. **Pre-Commit Security Card:** Mostrar en el diálogo de escaneo (`QrScannerDialog`) una tarjeta de verificación previa con el avatar de marca oficial, emisor, usuario, tipo de OTP y advertencias de seguridad antes del guardado definitivo.

---

## 27. Desacoplamiento de Fases de Renderizado y Confinamiento CPU vs. GPU (*Rendering Pipeline & GPU Hardware Confinement*)
- **PROHIBIDO** ejecutar transformaciones geométricas animadas (escalas, rotaciones, traslaciones) u opacidades variables animadas forzando las fases de Composición (*Composition*) o Medición (*Layout / Measure pass*) mediante modificadores dependientes de recomposición o cambios de tamaño de contenedor (ej. `Modifier.fillMaxSize(animatedFraction)`, `Modifier.width(animatedDp)`, o lecturas de `animate*AsState` en el cuerpo del Composable sin diferir a la fase de dibujo).
- **PROHIBIDO** delegar a la GPU tareas de cómputo algorítmico, criptografía, derivación de claves, formateo de texto, parsers de URI, o filtrado de colecciones (responsabilidad exclusiva del procesador CPU en hilos de fondo).
- **PROHIBIDO** mantener capas de renderizado fuera de pantalla (*offscreen buffers / RenderEffects*) activas en la GPU de forma estática o innecesaria cuando no haya contenido que requiera el efecto (ej. `Modifier.blur(0.dp)` continuo durante la navegación ordinaria).
- **PROHIBIDO** apilar capas redundantes de desenfoque compitiendo entre el WindowManager nativo del sistema operativo y Compose Skia GPU (ej. invocar `FLAG_BLUR_BEHIND` IPC al mismo tiempo que `Modifier.blur()` en el NavHost).
- **OBLIGATORIO Confinamiento estricto a la GPU (Fase de Dibujo / RenderNode en RenderThread):**
  1. **Transformaciones Geométricas Animadas:** Toda animación de escala (`scaleX`, `scaleY`), rotación (`rotationZ`) o traslación (`translationX`, `translationY`) debe diferirse a la fase de Draw consumiendo la lambda de `Modifier.graphicsLayer { ... }` o `drawWithContent`.
  2. **Opacidades y Scrims Animados:** Animar la opacidad en la GPU mediante `graphicsLayer { alpha = ... }` en lugar de recomponer con `Color.copy(alpha = ...)`.
  3. **Multiplicación de Matrices por Hardware:** Aprovechar la capacidad nativa de la GPU para matrices 2D/3D sin invalidar ni re-medir la geometría de la tarjeta o pantalla en cada fotograma.
  4. **Activación Condicional de Shaders:** Condicionar la aplicación de `Modifier.blur()` para que solo exista cuando el radio sea estrictamente mayor a cero (`modalBlurRadius > Dimensions.Spacing.none`), liberando el ancho de banda de memoria GPU durante el uso ordinario.
- **OBLIGATORIO Confinamiento estricto al Procesador (CPU):**
  1. **Cómputo Criptográfico:** Derivación PBKDF2, AES-256-GCM, cálculo TOTP/HOTP y huellas SHA-256 deben ejecutarse en `Dispatchers.Default` (Directiva 18).
  2. **Estructura y Medición de Layout (*Measure Pass*):** Medir y componer los elementos de la interfaz **una sola vez** al inicio, delegando su animación subsiguiente al RenderNode.
  3. **Lógica de Estado y Persistencia:** ViewModels, Room, DataStore, SharedPreferences, diff incremental de listas y verificación síncrona de permisos en memoria (`ContextCompat.checkSelfPermission`) deben ejecutarse en la CPU.
- **OBLIGATORIO Criterio de Decisión Taxonómico para Nuevas Funcionalidades (Árbol de Decisión CPU vs. GPU):**
  - Ante cualquier nueva adición, refactorización o componente visual en el proyecto, clasificar el flujo bajo esta regla binaria:
    1. **Asignación OBLIGATORIA a la GPU (Fase de Draw / RenderNode / Hardware Layer):**
       - **Pregunta clave:** *«¿La operación solo altera la apariencia visual (matrices de transformación, píxeles, colores, opacidades, sombras, recorte o shaders) sin alterar el espacio estructural ni desplazar a los elementos hermanos en el layout?»*
       - **Ejemplos mandatorios:**
         - Animaciones de modales, popups y diálogos (escala, desvanecimiento, morphing).
         - Gestos táctiles de arrastre (*Drag & Drop*) y deslizamiento (*Swipe-to-Dismiss*): animar `translationX` / `translationY` en `graphicsLayer`.
         - Rotaciones de flechas, iconos e indicadores de carga (`rotationZ`).
         - Arcos de progreso temporal, temporizadores TOTP y trazos de `Canvas` (`drawArc`, `drawCircle`).
         - Efectos de desenfoque de fondo (*Skia Blur*), viñeteado y tinte de scrims.
         - Recorte de bordes redondeados y elevaciones de sombras dinámicas (`clip = true`, `shadowElevation`).
    2. **Asignación OBLIGATORIA al Procesador (CPU):**
       - **Pregunta clave:** *«¿La operación requiere procesamiento lógico/algorítmico, lectura de almacenamiento/red/memoria, o altera la cantidad, orden o dimensiones intrínsecas que afectan el flujo de otros elementos?»*
       - **Sub-confinamiento estricto de hilos en CPU:**
         - **CPU — Hilo Principal (`Dispatchers.Main`):**
           - Composición declarativa inicial de la UI y maquetación estructural (*Measure / Placement pass*).
           - Despacho y recepción de eventos de toque del usuario.
           - Instanciación de ViewModels y renderizado del primer frame.
         - **CPU — Hilos de Cómputo Algorítmico (`Dispatchers.Default`):**
           - Generación de contraseñas de un solo uso (TOTP RFC 6238 / HOTP RFC 4226).
           - Cifrado/descifrado AES-256-GCM y derivación PBKDF2.
           - Decodificación y análisis de imágenes / frames de cámara para códigos QR (CameraX / ML Kit / ZXing).
           - Generación de kits de emergencia y serialización/deserialización de PDFs o esquemas JSON.
           - Cálculo de huellas digitales criptográficas (SHA-256) y comparaciones diferenciales complejas en listas.
         - **CPU — Hilos de Entrada/Salida (`Dispatchers.IO`):**
           - Lectura, escritura y migraciones en base de datos local SQLite / Room.
           - Persistencia en `SharedPreferences` y `DataStore`.
           - Comunicaciones HTTP REST con Google Drive API.
           - Operaciones de lectura y escritura en almacenamiento local de archivos.
