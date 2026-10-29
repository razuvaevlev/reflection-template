plugins {
    id("java")
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val processorSourceSet = sourceSets.create("processor") {
    java.srcDir("src/main/java")
    java.include(
        "org/example/generator/Generatable.java",
        "org/example/generator/GeneratableProcessor.java"
    )
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    add(processorSourceSet.compileOnlyConfigurationName, "com.google.auto.service:auto-service-annotations:1.1.1")
    add(processorSourceSet.annotationProcessorConfigurationName, "com.google.auto.service:auto-service:1.1.1")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

sourceSets.main {
    java.exclude(
        "org/example/generator/Generatable.java",
        "org/example/generator/GeneratableProcessor.java"
    )
    compileClasspath += processorSourceSet.output
    runtimeClasspath += processorSourceSet.output
}

sourceSets.test {
    compileClasspath += processorSourceSet.output
    runtimeClasspath += processorSourceSet.output
}

tasks.named<JavaCompile>("compileJava") {
    dependsOn(tasks.named(processorSourceSet.classesTaskName))
    options.annotationProcessorPath = files(processorSourceSet.output, processorSourceSet.compileClasspath)
}

application {
    mainClass.set("org.example.GenerateExample")
}

tasks.jar {
    from(processorSourceSet.output)
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }
}

tasks.test {
    useJUnitPlatform()
}
