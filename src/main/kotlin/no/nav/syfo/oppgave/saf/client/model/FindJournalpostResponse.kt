package no.nav.syfo.oppgave.saf.client.model

data class FindJournalpostResponse(
    val data: ResponseData?
)

data class ResponseData(
    val journalpost: JournalpostResponse?
)

data class JournalpostResponse(
    val dokumenter: List<DokumentInfo>?,
    val journalstatus: String?,
    val kanal: String
)

data class DokumentInfo(
    val dokumentInfoId: String,
    val brevkode: String?,
    val dokumentvarianter: List<Dokumentvariant>?
)

data class Dokumentvariant(
    val variantformat: String
)
