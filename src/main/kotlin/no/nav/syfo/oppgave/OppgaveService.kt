package no.nav.syfo.oppgave

import no.nav.syfo.log
import no.nav.syfo.oppgave.client.OppdaterOppgaveRequest
import no.nav.syfo.oppgave.client.OppgaveClient
import no.nav.syfo.oppgave.client.OppgaveResponse
import no.nav.syfo.oppgave.saf.SafJournalpostService
import no.nav.syfo.oppgave.sykdig.DigitaliseringsoppgaveKafka
import no.nav.syfo.oppgave.sykdig.SykDigProducer
import java.util.UUID

class OppgaveService(
    private val oppgaveClient: OppgaveClient,
    private val safJournalpostService: SafJournalpostService,
    private val sykDigProducer: SykDigProducer,
    private val cluster: String
) {
    suspend fun handleOppgave(oppgaveId: Long, fnr: String) {
        val sporingsId = UUID.randomUUID().toString()
        val oppgave = oppgaveClient.hentOppgave(oppgaveId = oppgaveId, sporingsId = sporingsId)

        if (oppgave.gjelderUtenlandskSykmeldingFraRina() && !oppgave.journalpostId.isNullOrEmpty()) {
            log.info("Oppgave med id $oppgaveId  og journalpostId ${oppgave.journalpostId} gjelder utenlandsk sykmelding fra Rina, sporingsId $sporingsId")
            val dokumenter = safJournalpostService.getDokumenter(journalpostId = oppgave.journalpostId, sporingsId = sporingsId)

            log.info("Utenlandsk sykmelding fra Rina: OppgaveId $oppgaveId, journalpostId ${oppgave.journalpostId}, dokumenter $dokumenter")

            if (cluster == "dev-gcp" && dokumenter != null) {
                oppgaveClient.oppdaterOppgave(
                    OppdaterOppgaveRequest(
                        id = oppgaveId.toInt(),
                        behandlesAvApplikasjon = "SMD",
                        versjon = oppgave.versjon
                    ),
                    sporingsId
                )
                sykDigProducer.send(
                    sporingsId,
                    DigitaliseringsoppgaveKafka(
                        oppgaveId = oppgaveId.toString(),
                        fnr = fnr,
                        journalpostId = oppgave.journalpostId,
                        dokumentInfoId = dokumenter.first(),
                        type = "UTLAND",
                        dokumenter = dokumenter
                    )
                )
                log.info("Sendt sykmelding til syk-dig for oppgaveId $oppgaveId, sporingsId $sporingsId")
            }
        }
    }

    private fun OppgaveResponse.gjelderUtenlandskSykmeldingFraRina(): Boolean {
        return ferdigstiltTidspunkt.isNullOrEmpty() && behandlesAvApplikasjon == null &&
            tema == "SYM" && behandlingstype == "ae0106" && behandlingstema.isNullOrEmpty() &&
            oppgavetype == "JFR" && metadata?.get("RINA_SAKID") != null
    }
}
