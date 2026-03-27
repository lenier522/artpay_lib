# ArtPay Android SDK

Librería Android en Kotlin para gestionar la verificación de licencias **Art-Pay** mediante archivos `.lic`, incluyendo selección de archivo, validación y comunicación con backend.

---

## 🚀 Características

* 📂 Selector de archivos `.lic` integrado
* 🔐 Verificación segura contra backend Art-Pay
* ✅ Validación automática de:

  * Package Name
  * Tipo de licencia (Basic, Pro, Enterprise)
* ⚡ Manejo de errores y feedback con Snackbar
* 🧠 Flujo completo listo para usar (plug & play)

---

## 📦 Instalación (JitPack)

### 1. Agregar JitPack

En tu `settings.gradle`:

```gradle
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

---

### 2. Agregar dependencia

```gradle
implementation("com.github.lenier522:artpaylib:1.1.5")
```

---

---

## 🧠 Uso

### 1. Inicializar en tu Activity

```kotlin
private val artPayManager = ArtPayManager(this)

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    artPayManager.register() // ⚠️ obligatorio
}
```

---

### 2. Ejecutar el flujo de verificación

```kotlin
artPayManager.handlePayment(
    rootView = binding.root,
    tierName = "Pro",
    displayName = "Pro",
    onSuccess = { result ->
        println("Licencia activada: ${result.productName}")
    },
    onError = { error ->
        println("Error: $error")
    }
)
```

---

## 📱 Ejemplo completo

```kotlin
binding.btnActivatePro.setOnClickListener {
    artPayManager.handlePayment(
        rootView = binding.root,
        tierName = "Pro",
        displayName = "Pro",
        onSuccess = { result ->
            updateLicense(result)
        },
        onError = { error ->
            println(error)
        }
    )
}
```

---

## 📊 Resultado

```kotlin
data class ArtPayVerificationResult(
    val success: Boolean,
    val errorMessage: String?,
    val productToken: String?,
    val productName: String?,
    val packageName: String?,
    val accessExpiresAt: Date?
)
```

---

## 🔒 Validaciones incluidas

La librería valida automáticamente:

* ✔ La licencia pertenece a la app actual
* ✔ El tipo de licencia coincide con el solicitado
* ✔ Compatibilidad de nombres (ej: enterprise ↔ empresarial)

---

## ⚠️ Requisitos

* Android SDK 24+
* Kotlin
* Conexión a internet
* Backend Art-Pay activo

---

---

## 📌 Notas importantes

* `register()` DEBE llamarse en `onCreate()`
* La IP del backend debe ser accesible desde el dispositivo
* Para dispositivos físicos, usa la IP local correcta (no localhost)

---

## 📄 Licencia

MIT License

---

## 👨‍💻 Autor

Lenier Cruz

---

## ⭐ Contribuciones

Las contribuciones son bienvenidas. Puedes abrir issues o pull requests.
