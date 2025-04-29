// Root build.gradle.kts
plugins {
    // Declaramos versiones, pero no aplicamos en el root
    id("com.android.application") version "8.1.1" apply false
    id("org.jetbrains.kotlin.android") version "1.9.0" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
}

subprojects {
    configurations.all {
        resolutionStrategy {
            // Forzamos Kotlin stdlib 1.9.0 en todos los módulos
            force("org.jetbrains.kotlin:kotlin-stdlib:1.9.0")
            force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.0")
            force("org.jetbrains.kotlin:kotlin-reflect:1.9.0")
        }
    }
}
