package no.nav.syfo.oppgave

import no.nav.syfo.log
import no.nav.syfo.oppgave.client.OppdaterOppgaveRequest
import no.nav.syfo.oppgave.client.OppgaveClient
import no.nav.syfo.oppgave.client.OppgaveResponse
import no.nav.syfo.oppgave.saf.SafJournalpostService
import no.nav.syfo.oppgave.sykdig.DigitaliseringsoppgaveKafka
import no.nav.syfo.oppgave.sykdig.SykDigProducer
import java.util.UUID

const val NAV_OPPFOLGNING_UTLAND = "0393"

class OppgaveService(
    private val oppgaveClient: OppgaveClient,
    private val safJournalpostService: SafJournalpostService,
    private val sykDigProducer: SykDigProducer,
    private val cluster: String,
) {
    suspend fun handleOppgave(oppgaveId: Long, fnr: String) {
        val sporingsId = UUID.randomUUID().toString()
        val oppgave = oppgaveClient.hentOppgave(oppgaveId = oppgaveId, sporingsId = sporingsId)

        if ((oppgave.gjelderUtenlandskSykmeldingFraRina() || oppgave.gjelderUtenlandskSykmeldingFraNAVNO()) &&
            !oppgave.journalpostId.isNullOrEmpty()
        ) {
            log.info("Oppgave med id $oppgaveId og journalpostId ${oppgave.journalpostId} gjelder utenlandsk sykmelding, sporingsId $sporingsId")

            log.info("Utenlandsk sykmelding: OppgaveId $oppgaveId, journalpostId ${oppgave.journalpostId}")
            if (oppgave.erTildeltNavOppfolgningUtlang() || cluster == "dev-gcp") {
                val dokumenter =
                    safJournalpostService.getDokumenter(journalpostId = oppgave.journalpostId, sporingsId = sporingsId, source = setSoruce(oppgave))
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
                            source = setSoruce(oppgave),
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

    private fun OppgaveResponse.gjelderUtenlandskSykmeldingFraNAVNO(): Boolean {
        return ferdigstiltTidspunkt.isNullOrEmpty() && behandlesAvApplikasjon == null &&
            tema == "SYK" && behandlingstype == "ae0106" && behandlingstema.isNullOrEmpty() &&
            oppgavetype == "JFR"
    }

    private fun setSoruce(oppgave: OppgaveResponse): String {
        return if (oppgave.gjelderUtenlandskSykmeldingFraRina()) {
            "rina"
        } else if (oppgave.gjelderUtenlandskSykmeldingFraNAVNO()) {
            "navno"
        } else {
            throw RuntimeException("Ukjent type kilde")
        }
    }

    private fun OppgaveResponse.erTildeltNavOppfolgningUtlang() = tildeltEnhetsnr == NAV_OPPFOLGNING_UTLAND
}
