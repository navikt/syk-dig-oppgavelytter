group = "no.nav.syfo"
version = "1.0.0"

val coroutinesVersion= "1.8.1"
val jacksonVersion = "2.17.1"
val kluentVersion = "1.73"
val ktorVersion = "2.3.11"
val logbackVersion = "1.5.6"
val logstashEncoderVersion = "7.4"
val prometheusVersion = "0.16.0"
val mockkVersion = "1.13.10"
val kotlinVersion = "1.9.24"
val postgresVersion = "42.7.3"
val hikariVersion = "5.1.0"
val googlePostgresVersion = "1.18.0"
val flywayVersion = "10.12.0"
val ktfmtVersion = "0.44"
val junitJupiterVersion = "5.10.2"
val commonsCodecVersion="1.17.0"
val opentelemetryVersion = "2.3.0"
val kafkaVersion = "3.8.0"
val snappyJavaVersion = "1.1.10.6"


plugins {
    id("application")
    kotlin("jvm") version "1.9.24"
    id("com.diffplug.spotless") version "6.25.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

application {
    mainClass.set("no.nav.syfo.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
    }
}


dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:$coroutinesVersion")
    implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")
    implementation("io.prometheus:simpleclient_common:$prometheusVersion")

    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-call-id:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    constraints {
        implementation("commons-codec:commons-codec:$commonsCodecVersion") {
            because("override transient from io.ktor:ktor-client-apache")
        }
    }
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")
    implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:$opentelemetryVersion")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    implementation("org.apache.kafka:kafka_2.12:$kafkaVersion")
    constraints {
        implementation("org.xerial.snappy:snappy-java:$snappyJavaVersion") {
            because("override transient from org.apache.kafka:kafka_2.12")
        }
    }


    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("com.google.cloud.sql:postgres-socket-factory:$googlePostgresVersion") {
        exclude(group = "commons-codec", module = "commons-codec")
    }

    testImplementation("org.amshove.kluent:kluent:$kluentVersion") 
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "org.eclipse.jetty") 
    }

    testImplementation("org.junit.jupiter:junit-jupiter:$junitJupiterVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    testImplementation("org.flywaydb:flyway-core:$flywayVersion")
    testImplementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")
}

tasks {

    shadowJar {
        archiveBaseName.set("app")
        archiveClassifier.set("")
        isZip64 = true
        manifest {
            attributes(
                mapOf(
                    "Main-Class" to "no.nav.syfo.ApplicationKt",
                ),
            )
        }
    }

    test {
        useJUnitPlatform {}
        testLogging {
            events("skipped", "failed")
            showStackTraces = true
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }


    spotless {
        kotlin { ktfmt(ktfmtVersion).kotlinlangStyle() }
        check {
            dependsOn("spotlessApply")
        }
    }
}
