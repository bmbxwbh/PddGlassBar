import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = libs.versions.namespace.get()
    compileSdk {
        version = release(libs.versions.compileSdk.get().toInt()) {
            minorApiLevel = libs.versions.compileSdkMinor.get().toInt()
        }
    }

    defaultConfig {
        applicationId = libs.versions.namespace.get()
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 10
        versionName = "1.5.0"
    }

    // 与 WeKit 相同的双入口布局:
    //  standard: libxposed 入口 (META-INF/xposed/*)   legacy: de.robv 入口 (assets/xposed_init)
    flavorDimensions += "entrypoint"
    productFlavors {
        create("standard") { dimension = "entrypoint" }
        create("legacy") { dimension = "entrypoint" }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.jdk.get().toInt())
        targetCompatibility = JavaVersion.toVersion(libs.versions.jdk.get().toInt())
    }

    packaging {
        resources.excludes += listOf(
            "kotlin/**",
            "**.bin",
            "META-INF/INDEX.LIST",
            "kotlin-tooling-metadata.json",
        )
        resources.merges += listOf("META-INF/xposed/*")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        getByName("release") {
            // CI 产物开箱即装(调试签名); 正式分发时替换为自有签名
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(libs.versions.jdk.get()))
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
        )
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.savedstate)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.annotation)

    compileOnly(libs.jetbrains.annotations)

    implementation(libs.miuix.blur)
    implementation(libs.miuix.shader)
    implementation(libs.symbols.outlined)
    implementation(libs.symbols.filled)

    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)
    compileOnly(libs.legacyxposed.api)
}
