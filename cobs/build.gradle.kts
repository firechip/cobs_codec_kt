import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("maven-publish")
}

group = "dev.firechip"
version = "1.0.0"

android {
    namespace = "dev.firechip.cobs"
    compileSdk = 35

    defaultConfig {
        minSdk = 21
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.all {
            it.useJUnit()
        }
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

publishing {
    publications {
        register<MavenPublication>("release") {
            afterEvaluate {
                from(components["release"])
            }
            groupId = "dev.firechip"
            artifactId = "cobs_codec"
            version = project.version.toString()
            pom {
                name = "cobs_codec"
                description =
                    "Pure-Kotlin Consistent Overhead Byte Stuffing (COBS) and " +
                    "COBS/R for Android."
                url = "https://github.com/firechip/cobs_codec_kt"
                licenses {
                    license {
                        name = "MIT"
                        url =
                            "https://github.com/firechip/cobs_codec_kt/blob/main/LICENSE"
                    }
                }
                developers {
                    developer {
                        id = "ajsb85"
                        name = "Alexander Salas Bastidas"
                        email = "ajsb85@firechip.dev"
                    }
                }
                scm {
                    url = "https://github.com/firechip/cobs_codec_kt"
                    connection =
                        "scm:git:https://github.com/firechip/cobs_codec_kt.git"
                    developerConnection =
                        "scm:git:ssh://git@github.com/firechip/cobs_codec_kt.git"
                }
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/firechip/cobs_codec_kt")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                    ?: providers.gradleProperty("gpr.user").orNull
                password = System.getenv("GITHUB_TOKEN")
                    ?: providers.gradleProperty("gpr.key").orNull
            }
        }
    }
}
