package no.nav.syfo.oppgave

import io.opentelemetry.instrumentation.annotations.SpanAttribute
import io.opentelemetry.instrumentation.annotations.WithSpan
import java.util.UUID
import no.nav.syfo.logger
import no.nav.syfo.oppgave.client.OppdaterOppgaveRequest
import no.nav.syfo.oppgave.client.OppgaveClient
import no.nav.syfo.oppgave.client.OppgaveResponse
import no.nav.syfo.oppgave.saf.SafJournalpostService
import no.nav.syfo.oppgave.sykdig.DigitaliseringsoppgaveKafka
import no.nav.syfo.oppgave.sykdig.SykDigProducer

const val NAV_OPPFOLGNING_UTLAND = "0393"

class OppgaveService(
    private val oppgaveClient: OppgaveClient,
    private val safJournalpostService: SafJournalpostService,
    private val sykDigProducer: SykDigProducer,
    private val cluster: String,
) {

    @WithSpan
    suspend fun handleOppgave(@SpanAttribute oppgaveId: Long, fnr: String) {
        logger.info("Slått av handleOppgave syk-dig-oppgavelytter")
    }

    private fun OppgaveResponse.gjelderUtenlandskSykmeldingFraRina(): Boolean {
        return ferdigstiltTidspunkt.isNullOrEmpty() &&
            behandlesAvApplikasjon == null &&
            tema == "SYM" &&
            behandlingstype == "ae0106" &&
            behandlingstema.isNullOrEmpty() &&
            oppgavetype == "JFR" &&
            metadata?.get("RINA_SAKID") != null
    }

    private fun OppgaveResponse.gjelderUtenlandskSykmeldingFraNAVNO(): Boolean {
        return ferdigstiltTidspunkt.isNullOrEmpty() &&
            behandlesAvApplikasjon == null &&
            tema == "SYK" &&
            behandlingstype == "ae0106" &&
            behandlingstema.isNullOrEmpty() &&
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

    private fun OppgaveResponse.erTildeltNavOppfolgningUtlang() =
        tildeltEnhetsnr == NAV_OPPFOLGNING_UTLAND
}
