import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Παρέα (Firebase): ενεργό μόνο αν υπάρχει το app/google-services.json. Χωρίς αυτό, η εφαρμογή χτίζεται κανονικά.
if (file("google-services.json").exists()) apply(plugin = "com.google.gms.google-services")

// Κάθε build στο GitHub παίρνει μεγαλύτερο αριθμό, ώστε το κινητό να το δέχεται ως ενημέρωση.
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "app.motonav"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.motonav"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "0.1.$buildNumber"
    }

    // Κλειδί υπογραφής: από env vars (GitHub Secrets) ή από app/keystore.properties + app/release.keystore.
    // Τα δύο αρχεία υπάρχουν μόνο στο private repo. Ίδιο κλειδί σε κάθε build = ενημέρωση χωρίς απεγκατάσταση.
    // Χωρίς κλειδί (π.χ. fork) χρησιμοποιείται το debug κλειδί.
    val props = Properties().apply {
        val f = file("keystore.properties"); if (f.exists()) f.inputStream().use { load(it) }
    }
    val ksPath = System.getenv("MOTONAV_KEYSTORE") ?: file("release.keystore").takeIf { it.exists() }?.path
    val ksPass = System.getenv("MOTONAV_KEYSTORE_PASSWORD")?.trim()?.takeIf { it.isNotEmpty() } ?: props.getProperty("storePassword")
    val hasKey = ksPath != null && file(ksPath).exists() && ksPass != null
    // Μόνο για μία φορά: APK με το παλιό κλειδί, για να μπει από πάνω στην παλιά εφαρμογή
    // και να βγει αντίγραφο ασφαλείας πριν την αλλαγή κλειδιού.
    val legacy = System.getenv("MOTONAV_LEGACY") == "1" && file("legacy.keystore").exists() &&
        props.getProperty("legacyPassword") != null
    signingConfigs {
        if (legacy) create("legacy") {
            storeFile = file("legacy.keystore")
            storePassword = props.getProperty("legacyPassword")
            keyAlias = "motonav"
            keyPassword = props.getProperty("legacyPassword")
        }
        if (hasKey) create("own") {
            storeFile = file(ksPath!!)
            storePassword = ksPass
            keyAlias = props.getProperty("keyAlias") ?: "motonav"
            keyPassword = props.getProperty("keyPassword") ?: ksPass
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = when {
                legacy -> signingConfigs.getByName("legacy")
                hasKey -> signingConfigs.getByName("own")
                else -> signingConfigs.getByName("debug")
            }
        }
        debug {
            if (hasKey) signingConfig = signingConfigs.getByName("own")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        aidl = true
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("org.maplibre.gl:android-sdk:11.8.0")
    // Android Auto (Car App Library): app για τα πρότυπα, app-projected για την προβολή από το κινητό.
    implementation("androidx.car.app:app:1.4.0")
    implementation("androidx.car.app:app-projected:1.4.0")
    // Το παράθυρο «Ενεργοποίηση τοποθεσίας» μέσα στην εφαρμογή (χωρίς να πας στις Ρυθμίσεις).
    implementation("com.google.android.gms:play-services-location:21.3.0")
    // Παρέα: θέσεις και walkie-talkie (Firebase), QR (ZXing για τη δημιουργία, σαρωτής Google για το διάβασμα).
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-database")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")

    testImplementation("junit:junit:4.13.2")
    // Το org.json του Android είναι «άδειο» στα unit tests. Αυτή είναι η κανονική υλοποίηση.
    testImplementation("org.json:json:20240303")
}
