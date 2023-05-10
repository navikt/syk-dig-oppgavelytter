package no.nav.syfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.apache.Apache
import io.ktor.client.engine.apache.ApacheEngineConfig
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.jackson.jackson
import io.prometheus.client.hotspot.DefaultExports
import no.nav.syfo.accesstoken.AccessTokenClient
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.application.db.Database
import no.nav.syfo.application.exception.ServiceUnavailableException
import no.nav.syfo.kafka.aiven.KafkaUtils
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
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
import java.net.SocketTimeoutException

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.syk-dig-oppgavelytter")
val securelog: Logger = LoggerFactory.getLogger("securelog")

val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
}

fun main() {
    val env = Environment()
    val database = Database(env)

    DefaultExports.initialize()
    val applicationState = ApplicationState()
    val applicationEngine = createApplicationEngine(
        env,
        applicationState,
    )
    val applicationServer = ApplicationServer(applicationEngine, applicationState)

    val config: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        install(ContentNegotiation) {
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
                    is SocketTimeoutException -> throw ServiceUnavailableException(exception.message)
                }
            }
        }
        expectSuccess = true
    }
    val httpClient = HttpClient(Apache, config)

    val accessTokenClient = AccessTokenClient(
        aadAccessTokenUrl = env.aadAccessTokenUrl,
        clientId = env.clientId,
        clientSecret = env.clientSecret,
        httpClient = httpClient,
    )

    val oppgaveClient = OppgaveClient(
        url = env.oppgaveUrl,
        accessTokenClient = accessTokenClient,
        httpClient = httpClient,
        scope = env.oppgaveScope,
    )

    val safGraphQlClient = SafGraphQlClient(
        httpClient = httpClient,
        basePath = "${env.safUrl}/graphql",
        graphQlQuery = SafGraphQlClient::class.java.getResource("/graphql/findJournalpost.graphql")!!.readText().replace(Regex("[\n\t]"), ""),
    )
    val safJournalpostService = SafJournalpostService(
        safGraphQlClient = safGraphQlClient,
        accessTokenClient = accessTokenClient,
        scope = env.safScope,
    )

    val sykDigProducer = SykDigProducer(
        KafkaProducer<String, DigitaliseringsoppgaveKafka>(
            KafkaUtils.getAivenKafkaConfig()
                .toProducerConfig(env.applicationName, valueSerializer = JacksonKafkaSerializer::class),
        ),
        env.sykDigTopic,
    )

    val oppgaveConsumer = OppgaveConsumer(
        oppgaveTopic = env.oppgaveTopic,
        kafkaConsumer = getKafkaConsumer(),
        oppgaveService = OppgaveService(oppgaveClient, safJournalpostService, sykDigProducer, database, env.cluster),
        applicationState = applicationState,
    )
    oppgaveConsumer.startConsumer()

    applicationServer.start()
}

private fun getKafkaConsumer(): KafkaConsumer<String, OppgaveKafkaAivenRecord> {
    val kafkaConsumer = KafkaConsumer(
        KafkaUtils.getAivenKafkaConfig().also {
            it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
            it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = 50
        }.toConsumerConfig("syk-dig-oppgavelytter-consumer", JacksonKafkaDeserializer::class),
        StringDeserializer(),
        JacksonKafkaDeserializer(OppgaveKafkaAivenRecord::class),
    )
    return kafkaConsumer
}
