plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    `maven-publish`
}

android {
    namespace = "jp.co.bitz.spritekit.compose"
    compileSdk = 34

    defaultConfig {
        minSdk = 24

        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = false
        }
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    // Resolved relative to this module's own parent project rather than a hardcoded absolute
    // path (":spritekit"), so this dependency resolves correctly both in this repo's own
    // standalone build (flat ":spritekit") and when a host app embeds this repo as a git
    // submodule nested under a grouping project name, e.g. ":SpriteKit:spritekit" per this
    // repo's own README "Usage as a git submodule" — the same nested shape
    // https://github.com/bitzgroup/GKSKBridge already depends on, so both can be embedded
    // together in one host app's settings.gradle.kts.
    val parentPath = project.parent?.path
    val spritekitPath = if (parentPath == null || parentPath == ":") ":spritekit" else "$parentPath:spritekit"
    api(project(spritekitPath))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.lifecycle.runtime.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit)
}

detekt {
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = project.property("GROUP") as String
                artifactId = "spritekit-compose"
                version = project.property("VERSION_NAME") as String

                pom {
                    name.set("SpriteKit for Android — Compose")
                    description.set("Jetpack Compose wrapper for SpriteKit for Android's SKView.")
                    url.set("https://github.com/bitzgroup/SpriteKit")
                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://github.com/bitzgroup/SpriteKit/blob/main/LICENSE")
                        }
                    }
                }
            }
        }
    }
}
