plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
}
// Set as appropriate for your organization
group = "org.openrewrite.recipe"
description = "Rewrite recipes which have dependencies on many other rewrite recipes or language parsers."

repositories {
    maven {
        url = uri("https://repo.gradle.org/gradle/libs-releases/")
        content {
            excludeVersionByRegex(".+", ".+", ".+-rc-?[0-9]*")
        }
    }
}

val latest = rewriteRecipe.rewriteVersion.get()
dependencies {
    compileOnly("org.projectlombok:lombok:latest.release")
    compileOnly("com.google.code.findbugs:jsr305:latest.release")
    annotationProcessor("org.projectlombok:lombok:latest.release")

    implementation(platform("org.openrewrite:rewrite-bom:$latest"))
    implementation("org.openrewrite:rewrite-docker")
    implementation("org.openrewrite:rewrite-gradle")
    implementation("org.openrewrite:rewrite-groovy")
    implementation("org.openrewrite:rewrite-hcl")
    implementation("org.openrewrite:rewrite-java")
    implementation("org.openrewrite.recipe:rewrite-java-dependencies:$latest")
    implementation("org.openrewrite:rewrite-json")
    implementation("org.openrewrite:rewrite-kotlin")
    implementation("org.openrewrite:rewrite-maven")
    implementation("org.openrewrite:rewrite-properties")
    implementation("org.openrewrite:rewrite-protobuf")
    implementation("org.openrewrite:rewrite-python")
    implementation("org.openrewrite:rewrite-toml")
    implementation("org.openrewrite:rewrite-xml")
    implementation("org.openrewrite:rewrite-yaml")
    implementation("org.openrewrite:rewrite-cobol:$latest")
    implementation("org.openrewrite:rewrite-csharp:$latest")

    testImplementation("org.openrewrite:rewrite-test")
    testRuntimeOnly("org.openrewrite:rewrite-java-21")
}
