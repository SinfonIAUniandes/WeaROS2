# Solución al problema de librerías nativas en Wear OS (Galaxy Watch 7)

Este documento explica paso a paso la serie de problemas y soluciones implementadas para lograr que la librería `jros2` (que utiliza Fast-DDS escrito en C++) funcionara correctamente dentro de un reloj inteligente Galaxy Watch 7 con Wear OS 5.

## El Problema Principal: "No implementation found" y "Crasheos Silenciosos"
Al intentar iniciar la comunicación ROS 2 desde la app del reloj, la aplicación crasheaba sin dejar rastro en los logs normales, o bien mostraba el error:
`Error ROS2: ExceptionInInitializerError Cause: UnsatisfiedLinkError: No implementation found...`

Esto significaba que la Máquina Virtual de Android (Dalvik) era incapaz de enlazar las funciones Java con sus implementaciones nativas en C++.

## Soluciones Implementadas

### 1. La Arquitectura Secreta de Wear OS (32-bits)
A pesar de que el procesador del Galaxy Watch 7 (Exynos W1000) es de 64 bits (`arm64-v8a`), **Google y Samsung configuran Wear OS 5 para operar en un entorno de 32 bits (`armeabi-v7a`)** para ahorrar memoria RAM y batería de forma agresiva.
* **Problema:** El script original de compilación de `jros2` (`build-android-arm64.bash`) solo generaba librerías para 64 bits. Al instalar el APK en el reloj, este buscaba la carpeta `armeabi-v7a` y, al no encontrar la librería nativa, fallaba al intentar cargarla.
* **Solución:** Se creó un nuevo script `build-android-armeabi-v7a.bash` y se modificó `cppbuild.bash` en el repositorio `jros2` para añadir soporte oficial a la compilación cruzada hacia la arquitectura `armeabi-v7a`.

### 2. El abandono de 32 bits por parte de JavaCPP
Al intentar compilar para 32 bits, nos topamos con un problema de dependencias: la librería puente `JavaCPP` (usada por `jros2`) **eliminó el soporte para Android de 32 bits (`android-arm`) a partir de la versión 1.5.10.**
* **Problema:** `jros2` estaba utilizando JavaCPP versión `1.5.11`. Al intentar compilar, Gradle fallaba porque no podía descargar el empaquetado de 32 bits desde Maven Central (ya no existe).
* **Solución:** Se hizo un "downgrade" forzado en todo el ecosistema. Se modificó `cppbuild.bash`, `jros2/android/build.gradle.kts` y `WeaROS2/app/build.gradle.kts` para utilizar **JavaCPP 1.5.9**, la última versión en la historia que dio soporte a relojes Wear OS de 32 bits. Luego se recompiló y se volvió a publicar el AAR en Maven Local.

### 3. Empaquetado `useLegacyPackaging = true`
Los relojes inteligentes tienen reglas de memoria virtual mucho más estrictas que los celulares.
* **Problema:** Por defecto, las versiones nuevas de Android Gradle Plugin intentan leer las librerías `.so` directamente desde el APK comprimido haciendo mapeo en memoria (mmap). Esto provocaba que el sistema operativo del reloj matara el proceso por falta de RAM al mapear la inmensa librería de Fast-DDS.
* **Solución:** Se añadió `useLegacyPackaging = true` en el `build.gradle.kts` de WeaROS2. Esto obliga a Android a desempaquetar y copiar físicamente los archivos `.so` al almacenamiento interno del reloj durante la instalación, evitando problemas de memoria al cargarlos.

### 4. Filtrado de ABIs (`abiFilters`)
Para evitar que Gradle empaquetara arquitecturas innecesarias, y asegurar la compatibilidad tanto con el reloj como con el emulador, se configuró el bloque de NDK en WeaROS2:
```kotlin
ndk {
    abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64")
}
```

### 5. Prevención de Crasheos Silenciosos (`Throwable`)
El crasheo silencioso original ocurría porque el código Kotlin tenía un bloque `try-catch(e: Exception)`.
* **Problema:** Los errores de bajo nivel relacionados con JNI o carga de librerías nativas (`UnsatisfiedLinkError`, `ExceptionInInitializerError`) no heredan de `Exception`, sino de `Error`. Por lo tanto, pasaban de largo el bloque catch y mataban la app.
* **Solución:** Se cambió el manejo de errores en `WearSensorBridge.kt` a `catch (t: Throwable)`. Además, se mejoró el log de pantalla para imprimir no solo el mensaje, sino la clase exacta del error y la "causa" (`t.cause`), revelando así la naturaleza real del fallo nativo.

---

### ¿Cómo compilar de nuevo en el futuro?
Si haces cambios en las interfaces de C++ en un futuro, debes:
1. Exportar el NDK correcto: `export ANDROID_NDK='.../ndk/30.0.14904198'`
2. Correr `bash build-android-arm64.bash`
3. Correr `bash build-android-armeabi-v7a.bash`
4. Publicar a Maven Local: `cd android && ../gradlew -p . publishReleasePublicationToMavenLocal`
