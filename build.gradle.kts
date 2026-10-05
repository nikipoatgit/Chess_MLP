plugins {
    id("java")
    id("application")
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "com.chessmind"
version = "1.0-SNAPSHOT"

application {
    mainClass.set("com.chessmind.Main")
}

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

javafx {
    version = "21.0.2"
    modules = listOf("javafx.controls")
}

dependencies {
    // Deep Learning 4 Java
    implementation("org.deeplearning4j:deeplearning4j-core:1.0.0-M2.1")

    // Use the CPU-native backend by default so the app also runs without CUDA.
    implementation("org.nd4j:nd4j-native-platform:1.0.0-M2.1")
    implementation("org.slf4j:slf4j-simple:2.0.12")

    // Chess Engine & Rules
    implementation("com.github.bhlangonijr:chesslib:1.3.3")

    // Testing
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
