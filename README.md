# ArtPay Android SDK

Librería Android en Kotlin para gestionar la verificación de licencias **Art-Pay** mediante archivos `.lic`, incluyendo selección de archivo, validación y comunicación segura con el backend a través de la **Billetera Art-Pay**.

---

## 🚀 Características

* 📂 Selector de archivos `.lic` integrado
* 🔐 Verificación segura y autenticada contra el backend Art-Pay
* 🤝 **Integración total con Billetera Art-Pay** (uso de Content Providers para validación JWT sin interrupciones)
* ✅ Validación automática de:
  * Package Name
  * Tipo de licencia (Basic, Pro, Enterprise)
  * Identidad del usuario propietario de la licencia
* ⚡ Manejo de errores y feedback con Snackbar
* 🧠 Flujo completo listo para usar (plug & play)

---

## 📦 Instalación (JitPack)

### 1. Agregar JitPack

En tu `settings.gradle` (o `settings.gradle.kts`):

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
    tierName = "Pro", // Nombre exacto del producto en el backend
    displayName = "Pro", // Nombre a mostrar en la UI
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
            // Guardar localmente el estado de la licencia...
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

La librería valida automáticamente en conjunto con el backend:

* ✔ La licencia pertenece a la app actual (`packageName` coincide)
* ✔ El tipo de licencia coincide con la solicitada
* ✔ Compatibilidad de nombres (ej: enterprise ↔ empresarial)
* ✔ **NUEVO (v1.1.5):** El usuario que intenta validar es el verdadero dueño legítimo usando validación vía JWT de la Billetera Art-Pay.

---

## ⚠️ Requisitos

* Android SDK 24+
* Kotlin
* Conexión a internet
* **Aplicación Billetera Art-Pay instalada y con sesión iniciada**

---

## 📌 Notas importantes

* `register()` DEBE llamarse en `onCreate()`
* **Seguridad Mejorada:** La validación se realiza de forma segura obteniendo el token de identidad directamente de la Billetera Art-Pay mediante un Content Provider (`content://cu.lenier.billetera_artpay.provider/license`). Si el usuario no tiene la billetera o no ha iniciado sesión, la validación será rechazada por seguridad.
* La IP del backend debe ser accesible desde el dispositivo (para desarrollo local).

---

## 📄 Licencia

MIT License

---

## 👨‍💻 Autor

Lenier Cruz

---

## ⭐ Contribuciones

Las contribuciones son bienvenidas. Puedes abrir issues o pull requests.
