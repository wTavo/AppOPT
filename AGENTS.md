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
- **OBLIGATORIO** consumir las dimensiones centralizadas en `ui/theme/Dimensions.kt`:
  - `Dimensions.Spacing.*` para paddings y márgenes (`xs`, `sm`, `md`, `lg`, `xl`, `xxl`).
  - `Dimensions.Elevation.*` para sombras y planos (`cardDefault`, `cardDragging`, `modal`).
  - `Dimensions.CornerRadius.*` para formas (`small`, `medium`, `large`, `pill`).
  - `Dimensions.IconSize.*` para tamaños de iconos (`small`, `medium`, `large`, `hero`, `illustration`).

---

## 5. Reutilización de Componentes de Interfaz (*DRY Principle*)
- **PROHIBIDO** duplicar lógica de interacción o animaciones complejas entre pantallas o diálogos.
- **OBLIGATORIO** encapsular y reutilizar componentes en `ui/components/` (ej. `AppAnimatedButton` para acciones con confirmación animada a verde).

---

## 6. Centralización de Parámetros de Seguridad y Criptografía (`security/SecurityConfig.kt`)
- **PROHIBIDO** definir tiempos de expiración de portapapeles o parámetros criptográficos dispersos en el código.
- **OBLIGATORIO** declarar y consumir todas las constantes de seguridad en `security/SecurityConfig.kt` (tiempos de auto-clear de portapapeles, iteraciones PBKDF2, períodos TOTP, etc.).

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

## 9. Principio de Cero Confianza (*Zero Trust*) y Seguridad de Memoria
- Validar siempre los datos ingresados por el usuario (claves Base32, URIs, longitudes).
- Sobreescribir con ceros (*zeroize / fill('0')*) los arreglos de caracteres (`CharArray`) y bytes (`ByteArray`) que contengan secretos o contraseñas en memoria tras su uso.

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

## 14. Gestión Unificada de Diálogos Modales y Navegación Defensiva (*Single-Dialog State Machine*)
- **PROHIBIDO** superponer o apilar múltiples diálogos modales en pantalla (*Stacked Modals*).
- **OBLIGATORIO** unificar flujos encadenados (confirmaciones destructivas, modo edición, sub-pasos) dentro de un único diálogo modal dinámico con transición de contenido.
- **OBLIGATORIO** implementar navegación defensiva hacia atrás en `onDismissRequest`: presionar afuera o atrás debe revertir al estado/paso anterior antes de cerrar el modal por completo.

---

## 15. Manejo Seguro, Descriptivo y Accionable de Excepciones (*Zero-Leakage & Actionable Error Handling*)
- **PROHIBIDO** exponer mensajes técnicos internos de excepciones criptográficas, de red o de sistema (`e.message`, `e.printStackTrace()`, nombres de clases Java/C++, OpenSSL o rutas de archivos) en la interfaz de usuario, diálogos o callbacks del sistema.
- **PROHIBIDO el "antipatrón mudo" (*Swallowing Errors*):** Dejar bloques `catch` o callbacks de error con `null`, cadenas vacías o sin retroalimentación cuando el usuario requiere conocer el resultado de una acción.
- **OBLIGATORIO** capturar excepciones técnicas de bajo nivel y presentar al usuario mensajes **amigables, descriptivos y accionables** centralizados en `res/values/strings.xml` (explicando en lenguaje cotidiano qué falló y qué paso correctivo puede tomar).

---

## 16. Purga y Aislamiento de Memoria en el Ciclo de Vida (*Lifecycle-Aware Memory Purge*)
- **PROHIBIDO** mantener secretos o cachés de descifrado en memoria RAM indefinidamente cuando la aplicación entra en segundo plano.
- **OBLIGATORIO** invocar la limpieza y sobrescritura de cachés volátiles (`clearMemoryCache()`) ante transiciones a segundo plano (`Lifecycle.Event.ON_STOP`) o eventos de advertencia de memoria del sistema (`ComponentCallbacks2.onTrimMemory()`).

---

## 17. Centralización de Claves de Persistencia y Alias Keystore (*Centralized Storage Keys*)
- **PROHIBIDO** declarar cadenas mágicas literales para nombres de tablas Room, claves de `SharedPreferences`, preferencias de `DataStore` o alias del `AndroidKeyStore` dispersos en la lógica de negocio o vistas.
- **OBLIGATORIO** declarar todas las claves de persistencia y alias criptográficos como constantes inmutables (`const val`) centralizadas en sus clases administradoras (`SecurityConfig.kt`, `PreferencesManager.kt` o `AppDatabase.kt`).

---

## 18. Estándar de Pruebas Unitarias de Regresión Criptográfica (*Deterministic Crypto Testing*)
- **PROHIBIDO** introducir nuevos motores de cálculo, algoritmos de derivación, parsers de URI o funciones de firma sin su correspondiente suite de pruebas unitarias automatizadas.
- **OBLIGATORIO** incluir pruebas en `app/src/test/` con 100% de cobertura en escenarios límite (*edge cases*): cadenas vacías, entradas nulas, formatos Base32 con/sin padding, alteraciones de orden y marcas de tiempo extremas.

---

## 19. Blindaje de Superficie y Prevención de Capturas (*FLAG_SECURE & App Switcher Masking*)
- **PROHIBIDO** permitir capturas de pantalla, grabaciones de video o miniaturas legibles de secretos en la vista de aplicaciones recientes (*App Switcher*).
- **OBLIGATORIO** mantener activa la bandera `WindowManager.LayoutParams.FLAG_SECURE` en la ventana principal de la aplicación para proteger la visibilidad de códigos 2FA y claves criptográficas.

---

## 20. Aislamiento de Hilos para Operaciones Criptográficas y de E/S (*Thread Confinement / Coroutine Dispatchers*)
- **PROHIBIDO** ejecutar derivación de claves PBKDF2, cifrado/descifrado AES-256-GCM o consultas a base de datos en el hilo principal (`Dispatchers.Main`).
- **OBLIGATORIO** confinar las operaciones intensivas de CPU criptográfica en `Dispatchers.Default` y las operaciones de base de datos/red en `Dispatchers.IO`, garantizando 60/120 FPS fluidos sin caídas de cuadros.

---

## 21. Inmutabilidad Estricta y Estabilidad de Parámetros en Compose (*Compose Stability & UDF*)
- **PROHIBIDO** exponer modelos mutables o clases inestables en los estados de vista que provoquen recomposiciones innecesarias durante el scroll o la actualización de códigos.
- **OBLIGATORIO** marcar las jerarquías de estado con `@Immutable` o `@Stable` y estructurar el flujo de eventos según el patrón unidireccional de datos (*UDF / MVI*).

---

## 22. Versionado de Esquemas y Compatibilidad Hacia Adelante (*Schema Versioning & Compatibility*)
- **PROHIBIDO** generar respaldos en la nube o formatos de exportación sin declarar explícitamente su versión de esquema de datos.
- **OBLIGATORIO** incluir una cabecera de versión (`version` / `schemaVersion`) en los formatos JSON de transferencia y sobre criptográfico (`CURRENT_BACKUP_VERSION`), garantizando compatibilidad hacia atrás y hacia adelante.

---

## 23. Mensajes de Control de Versiones en Español (*Spanish Git Commits*)
- **PROHIBIDO** redactar mensajes de commit de Git en inglés u otros idiomas.
- **OBLIGATORIO** escribir todos los mensajes de commit en **español**, utilizando la estructura de commits semánticos (ej. `feat: ...`, `fix: ...`, `refactor: ...`, `test: ...`, `docs: ...`) con descripciones claras y gramaticalmente correctas en español.

---

## 24. Idempotencia, Bloqueo de Doble Pulsación y Retroalimentación en Botones (*Idempotent Animated Buttons*)
- **PROHIBIDO** permitir pulsaciones múltiples o concurrentes en botones que ejecutan acciones asíncronas, mutantes o destructivas (guardado, edición, borrado, sincronización o transferencia).
- **OBLIGATORIO** utilizar `AppAnimatedButton` o banderas atómicas de bloqueo (`isProcessing`) para congelar la interactividad al primer toque, proporcionando confirmación visual inmediata (palomita blanca sobre fondo `SafeGreen` en éxito o 'X' blanca sobre fondo `UrgentRed` en fallo) y respuesta háptica semántica (`AppHaptics`).



