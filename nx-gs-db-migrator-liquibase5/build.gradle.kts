plugins {
    `java-library`
    `maven-publish`
    signing
}

version = findProperty("${project.name}.version") as String? ?: "0.1.0"

java {
    withSourcesJar()
    withJavadocJar()
}

// Liquibase 5 needs Java 17; this module is the only adapter artifact above the Java 8 baseline.
tasks.withType<JavaCompile> {
    options.release.set(17)
    options.compilerArgs.addAll(listOf("-Xlint:deprecation"))
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":nx-gs-adapter-api"))
    implementation(project(":nx-gs-db-migrations"))
    compileOnly(libs.liquibase.core)

    testImplementation(libs.liquibase.core)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.mariadb)
    testImplementation(libs.testcontainers.mysql)
    testRuntimeOnly(libs.postgresql)
    testRuntimeOnly(libs.mariadb.java.client)
    testRuntimeOnly(libs.mysql.connector)
}

tasks.test {
    useJUnitPlatform {
        findProperty("excludeTags")?.toString()?.let { excludeTags(it) }
    }
}

tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:-missing", "-quiet")
}

publishing {
    repositories {
        maven {
            name = "staging"
            url = uri(layout.buildDirectory.dir("staging-deploy"))
        }
    }
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "nx-gs-db-migrator-liquibase5"

            pom {
                name.set("nx-gs-db-migrator-liquibase5")
                description.set("Liquibase 5 SchemaMigrator for the L2NX game-server adapter; the host brings liquibase-core 5.x (Java 17+)")
                url.set("https://github.com/nexuslabsio/nx-gs-adapter")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }

                developers {
                    developer {
                        id.set("n1rmata")
                        name.set("Kiryl Valiushka")
                        email.set("kiryl.valiushka@gmail.com")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/nexuslabsio/nx-gs-adapter.git")
                    developerConnection.set("scm:git:ssh://github.com:nexuslabsio/nx-gs-adapter.git")
                    url.set("https://github.com/nexuslabsio/nx-gs-adapter")
                }
            }
        }
    }
}

signing {
    val signingKey = findProperty("signingKey") as String?
    val signingPassword = findProperty("signingPassword") as String?
    if (signingKey != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
    }
    isRequired = signingKey != null
    sign(publishing.publications["maven"])
}
