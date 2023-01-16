package no.nav.syfo.oppgave

import no.nav.syfo.log
import no.nav.syfo.oppgave.client.OppgaveClient
import no.nav.syfo.oppgave.client.OppgaveResponse
import no.nav.syfo.oppgave.saf.SafJournalpostService
import java.util.UUID

class OppgaveService(
    private val oppgaveClient: OppgaveClient,
    private val safJournalpostService: SafJournalpostService
) {
    suspend fun handleOppgave(oppgaveId: Long, fnr: String) {
        val sporingsId = UUID.randomUUID().toString()
        val oppgave = oppgaveClient.hentOppgave(oppgaveId = oppgaveId, sporingsId = sporingsId)

        if (oppgave.gjelderUtenlandskSykmeldingFraRina() && oppgave.journalpostId != null) {
            log.info("Oppgave med id $oppgaveId  og journalpostId ${oppgave.journalpostId} gjelder utenlandsk sykmelding fra Rina, sporingsId $sporingsId")
            val dokumentInfoId = safJournalpostService.getDokumentInfoId(journalpostId = oppgave.journalpostId, sporingsId = sporingsId)

            log.info("Utenlandsk sykmelding fra Rina: OppgaveId $oppgaveId, journalpostId ${oppgave.journalpostId}, dokumentInfoId $dokumentInfoId")
        }
    }

    fun OppgaveResponse.gjelderUtenlandskSykmeldingFraRina(): Boolean {
        return ferdigstiltTidspunkt == null && behandlesAvApplikasjon == null &&
            tema == "SYM" && behandlingstype == "ae0106" && behandlingstema.isNullOrEmpty() &&
            oppgavetype == "JFR" && opprettetAv == "9999" && metadata?.get("RINA_SAKID") != null
    }
}
