plugins { id("com.android.library") }
android {
    namespace = "dev.amenhancer.host.applemusic"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    buildFeatures { buildConfig = true }
    // Two dual-pane arrangements ship as separate APKs: v1 keeps lyrics on the right
    // with the cover on the far left; v2 adds the middle lyrics/controls switcher.
    flavorDimensions += "layout"
    productFlavors {
        create("v1") {
            dimension = "layout"
            buildConfigField("int", "DUAL_PANE_VARIANT", "1")
        }
        create("v2") {
            dimension = "layout"
            buildConfigField("int", "DUAL_PANE_VARIANT", "2")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    testOptions {
        unitTests.all {
            it.jvmArgs(
                "--add-opens=java.base/java.lang=ALL-UNNAMED",
                "--add-opens=java.base/java.util=ALL-UNNAMED",
                "--add-opens=java.base/java.io=ALL-UNNAMED",
                "--add-opens=java.base/java.net=ALL-UNNAMED",
                "--add-opens=java.base/java.security=ALL-UNNAMED",
                "--add-opens=java.base/java.text=ALL-UNNAMED",
                "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
                "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
            )
        }
    }
}
dependencies {
    implementation(project(":core"))
    api(project(":host-api"))
    implementation(project(":hook-runtime"))
    compileOnly("io.github.libxposed:api:102.0.0")
    compileOnly("io.github.libxposed:service:102.0.0")
    implementation("org.luckypray:dexkit:2.2.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.robolectric:robolectric:4.17")
    testRuntimeOnly("io.github.libxposed:api:102.0.0")
}
