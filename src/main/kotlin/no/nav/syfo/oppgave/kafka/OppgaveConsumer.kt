package no.nav.syfo.oppgave.kafka

import java.time.Duration
import no.nav.syfo.logger
import no.nav.syfo.oppgave.OppgaveService
import org.apache.kafka.clients.consumer.KafkaConsumer

class OppgaveConsumer(
    private val oppgaveTopic: String,
    private val kafkaConsumer: KafkaConsumer<String, OppgaveKafkaAivenRecord>,
    private val oppgaveService: OppgaveService,
    private val applicationState: no.nav.syfo.ApplicationState,
) {

    fun startConsumer() {
        logger.info("Oppaveconsumer turned off")
    }

    private suspend fun consume() {
        while (applicationState.ready) {
            val records = kafkaConsumer.poll(Duration.ofSeconds(1)).mapNotNull { it.value() }
            if (records.isNotEmpty()) {
                records
                    .filter {
                        it.hendelse.hendelsestype == Hendelsestype.OPPGAVE_OPPRETTET &&
                            (it.oppgave.kategorisering.tema == "SYM" ||
                                it.oppgave.kategorisering.tema == "SYK") &&
                            it.oppgave.kategorisering.behandlingstype == "ae0106" &&
                            it.oppgave.kategorisering.oppgavetype == "JFR" &&
                            it.oppgave.bruker != null &&
                            it.oppgave.bruker.identType == IdentType.FOLKEREGISTERIDENT
                    }
                    .forEach { oppgaveKafkaAivenRecord ->
                        oppgaveService.handleOppgave(
                            oppgaveKafkaAivenRecord.oppgave.oppgaveId,
                            oppgaveKafkaAivenRecord.oppgave.bruker!!.ident
                        )
                    }
            }
        }
    }
}
