package no.nav.syfo.oppgave.sykdig

data class DigitaliseringsoppgaveKafka(
    val oppgaveId: String,
    val fnr: String,
    val journalpostId: String,
    val dokumentInfoId: String,
    val type: String,
    val dokumenter: List<String>
)
