package no.nav.syfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.prometheus.client.hotspot.DefaultExports
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import no.nav.syfo.accesstoken.AccessTokenClient
import no.nav.syfo.kafka.aiven.KafkaUtils
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.nais.isalive.naisIsAliveRoute
import no.nav.syfo.nais.isready.naisIsReadyRoute
import no.nav.syfo.nais.prometheus.naisPrometheusRoute
import no.nav.syfo.oppgave.OppgaveService
import no.nav.syfo.oppgave.client.OppgaveClient
import no.nav.syfo.oppgave.kafka.OppgaveConsumer
import no.nav.syfo.oppgave.kafka.OppgaveKafkaAivenRecord
import no.nav.syfo.oppgave.saf.SafJournalpostService
import no.nav.syfo.oppgave.saf.client.SafGraphQlClient
import no.nav.syfo.oppgave.sykdig.DigitaliseringsoppgaveKafka
import no.nav.syfo.oppgave.sykdig.SykDigProducer
import no.nav.syfo.util.kafka.JacksonKafkaDeserializer
import no.nav.syfo.util.kafka.JacksonKafkaSerializer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val logger: Logger = LoggerFactory.getLogger("no.nav.syfo.syk-dig-oppgavelytter")
val securelog: Logger = LoggerFactory.getLogger("securelog")

val objectMapper: ObjectMapper =
    ObjectMapper().apply {
        registerKotlinModule()
        registerModule(JavaTimeModule())
        configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

fun main() {
    val embeddedServer =
        embeddedServer(
            Netty,
            port = EnvironmentVariables().applicationPort,
            module = Application::module,
        )
    Runtime.getRuntime()
        .addShutdownHook(
            Thread {
                embeddedServer.stop(TimeUnit.SECONDS.toMillis(10), TimeUnit.SECONDS.toMillis(10))
            },
        )
    embeddedServer.start(true)
}

fun Application.configureRouting(
    applicationState: ApplicationState,
) {

    routing {
        naisIsAliveRoute(applicationState)
        naisIsReadyRoute(applicationState)
        naisPrometheusRoute()
    }

    install(ContentNegotiation) {
        jackson {
            registerKotlinModule()
            registerModule(JavaTimeModule())
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            logger.error("Caught exception ${cause.message}")
            securelog.error("Caught exception", cause)
            call.respond(HttpStatusCode.InternalServerError, cause.message ?: "Unknown error")
            applicationState.alive = false
            applicationState.ready = false
        }
    }
}

fun Application.module() {
    val environmentVariables = EnvironmentVariables()
    val applicationState = ApplicationState()

    environment.monitor.subscribe(ApplicationStopped) {
        applicationState.ready = false
        applicationState.alive = false
    }

    configureRouting(
        applicationState = applicationState,
    )

    val config: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
            jackson {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        HttpResponseValidator {
            handleResponseExceptionWithRequest { exception, _ ->
                when (exception) {
                    is SocketTimeoutException ->
                        throw ServiceUnavailableException(exception.message)
                }
            }
        }
        expectSuccess = true
    }
    val httpClient = HttpClient(Apache, config)

    val accessTokenClient =
        AccessTokenClient(
            aadAccessTokenUrl = environmentVariables.aadAccessTokenUrl,
            clientId = environmentVariables.clientId,
            clientSecret = environmentVariables.clientSecret,
            httpClient = httpClient,
        )

    val oppgaveClient =
        OppgaveClient(
            url = environmentVariables.oppgaveUrl,
            accessTokenClient = accessTokenClient,
            httpClient = httpClient,
            scope = environmentVariables.oppgaveScope,
        )

    val safGraphQlClient =
        SafGraphQlClient(
            httpClient = httpClient,
            basePath = "${environmentVariables.safUrl}/graphql",
            graphQlQuery =
                SafGraphQlClient::class
                    .java
                    .getResource("/graphql/findJournalpost.graphql")!!
                    .readText()
                    .replace(Regex("[\n\t]"), ""),
        )
    val safJournalpostService =
        SafJournalpostService(
            safGraphQlClient = safGraphQlClient,
            accessTokenClient = accessTokenClient,
            scope = environmentVariables.safScope,
        )

    val sykDigProducer =
        SykDigProducer(
            KafkaProducer<String, DigitaliseringsoppgaveKafka>(
                KafkaUtils.getAivenKafkaConfig("syk-dig-producer")
                    .toProducerConfig(
                        environmentVariables.applicationName,
                        valueSerializer = JacksonKafkaSerializer::class,
                    ),
            ),
            environmentVariables.sykDigTopic,
        )

    val oppgaveConsumer =
        OppgaveConsumer(
            oppgaveTopic = environmentVariables.oppgaveTopic,
            kafkaConsumer = getKafkaConsumer(),
            oppgaveService =
                OppgaveService(
                    oppgaveClient,
                    safJournalpostService,
                    sykDigProducer,
                    environmentVariables.cluster,
                ),
            applicationState = applicationState,
        )

    oppgaveConsumer.startConsumer()

    DefaultExports.initialize()
}

data class ApplicationState(
    var alive: Boolean = true,
    var ready: Boolean = true,
)

private fun getKafkaConsumer(): KafkaConsumer<String, OppgaveKafkaAivenRecord> {
    val kafkaConsumer =
        KafkaConsumer(
            KafkaUtils.getAivenKafkaConfig("oppgave-consumer")
                .also {
                    it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "none"
                    it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = 50
                }
                .toConsumerConfig(
                    "syk-dig-oppgavelytter-consumer",
                    JacksonKafkaDeserializer::class,
                ),
            StringDeserializer(),
            JacksonKafkaDeserializer(OppgaveKafkaAivenRecord::class),
        )
    return kafkaConsumer
}

class ServiceUnavailableException(message: String?) : Exception(message)
