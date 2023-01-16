package no.nav.syfo.oppgave.saf.client.model

data class FindJournalpostResponse(
    val data: ResponseData
)

data class ResponseData(
    val journalpost: JournalpostResponse?
)

data class JournalpostResponse(
    val dokumenter: List<Dokumenter>?,
    val journalstatus: String?,
    val kanal: String
)

data class Dokumenter(
    val dokumentInfoId: String,
    val brevkode: String?,
    val dokumentvarianter: List<Dokumentvarianter>
)

data class Dokumentvarianter(
    val variantformat: String
)
