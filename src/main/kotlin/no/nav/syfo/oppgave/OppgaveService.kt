package no.nav.syfo.oppgave

import no.nav.syfo.application.db.DatabaseInterface
import no.nav.syfo.log
import no.nav.syfo.objectMapper
import no.nav.syfo.oppgave.client.OppdaterOppgaveRequest
import no.nav.syfo.oppgave.client.OppgaveClient
import no.nav.syfo.oppgave.client.OppgaveResponse
import no.nav.syfo.oppgave.db.getUlosteOppgaveCount
import no.nav.syfo.oppgave.saf.SafJournalpostService
import no.nav.syfo.oppgave.sykdig.DigitaliseringsoppgaveKafka
import no.nav.syfo.oppgave.sykdig.SykDigProducer
import no.nav.syfo.securelog
import java.util.UUID

const val NAV_OPPFOLGNING_UTLAND = "0393"
private const val ULOSTE_OPPGAVER_LIMIT = 10
class OppgaveService(
    private val oppgaveClient: OppgaveClient,
    private val safJournalpostService: SafJournalpostService,
    private val sykDigProducer: SykDigProducer,
    private val database: DatabaseInterface,
    private val cluster: String,
) {
    suspend fun handleOppgave(oppgaveId: Long, fnr: String) {
        val sporingsId = UUID.randomUUID().toString()
        val oppgave = oppgaveClient.hentOppgave(oppgaveId = oppgaveId, sporingsId = sporingsId)

        securelog.info("oppgave info: ${objectMapper.writeValueAsString(oppgave)}")

        if (oppgave.gjelderUtenlandskSykmeldingFraRina() && !oppgave.journalpostId.isNullOrEmpty()) {
            log.info("Oppgave med id $oppgaveId og journalpostId ${oppgave.journalpostId} gjelder utenlandsk sykmelding fra Rina, sporingsId $sporingsId")

            log.info("Utenlandsk sykmelding fra Rina: OppgaveId $oppgaveId, journalpostId ${oppgave.journalpostId}")
            if (oppgave.erTildeltNavOppfolgningUtlang() || cluster == "dev-gcp") {
                val ulosteOppgaver = database.getUlosteOppgaveCount()
                log.info("uløste oppgaver $ulosteOppgaver, limit $ULOSTE_OPPGAVER_LIMIT")
                if (ULOSTE_OPPGAVER_LIMIT < ulosteOppgaver && cluster != "dev-gcp") {
                    log.info("Uløste oppgaver er større enn limit, sender ikke til syk-dig")
                    return
                }
                val dokumenter = safJournalpostService.getDokumenter(journalpostId = oppgave.journalpostId, sporingsId = sporingsId)
                if (dokumenter != null) {
                    oppgaveClient.oppdaterOppgave(
                        OppdaterOppgaveRequest(
                            id = oppgaveId.toInt(),
                            behandlesAvApplikasjon = "SMD",
                            versjon = oppgave.versjon,
                        ),
                        sporingsId,
                    )
                    sykDigProducer.send(
                        sporingsId,
                        DigitaliseringsoppgaveKafka(
                            oppgaveId = oppgaveId.toString(),
                            fnr = fnr,
                            journalpostId = oppgave.journalpostId,
                            dokumentInfoId = dokumenter.first().dokumentInfoId,
                            type = "UTLAND",
                            dokumenter = dokumenter,
                        ),
                    )
                    log.info("Sendt sykmelding til syk-dig for oppgaveId $oppgaveId, sporingsId $sporingsId")
                } else {
                    log.warn("Oppgaven $oppgaveId har ikke dokumenter, hopper over")
                }
            } else {
                log.warn("Oppgaven $oppgaveId er ikke tildelt $NAV_OPPFOLGNING_UTLAND")
            }
        }
    }

    private fun OppgaveResponse.gjelderUtenlandskSykmeldingFraRina(): Boolean {
        return ferdigstiltTidspunkt.isNullOrEmpty() && behandlesAvApplikasjon == null &&
            tema == "SYM" && behandlingstype == "ae0106" && behandlingstema.isNullOrEmpty() &&
            oppgavetype == "JFR" && metadata?.get("RINA_SAKID") != null
    }

    private fun OppgaveResponse.erTildeltNavOppfolgningUtlang() = tildeltEnhetsnr == NAV_OPPFOLGNING_UTLAND
}
