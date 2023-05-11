package no.nav.syfo.oppgave.saf

import no.nav.syfo.accesstoken.AccessTokenClient
import no.nav.syfo.log
import no.nav.syfo.oppgave.saf.client.SafGraphQlClient
import no.nav.syfo.oppgave.saf.client.model.DokumentInfo
import no.nav.syfo.oppgave.saf.client.model.JournalpostResponse
import no.nav.syfo.oppgave.saf.model.DokumentMedTittel

class SafJournalpostService(
    private val safGraphQlClient: SafGraphQlClient,
    private val accessTokenClient: AccessTokenClient,
    private val scope: String,
) {
    suspend fun getDokumenter(
        journalpostId: String,
        sporingsId: String,
        source: String,
    ): List<DokumentMedTittel>? {
        val journalpost = safGraphQlClient.findJournalpost(
            journalpostId = journalpostId,
            token = accessTokenClient.getAccessToken(scope),
            sporingsId = sporingsId,
        )
        journalpost.errors?.forEach {
            log.error("Feil ved henting av journalpost med id $journalpostId fra SAF: ${it.message}")
        }
        if (journalpost.data == null) {
            log.error("Mottatt journalføringsoppgave for journalpost som ikke finnes: journalpostId: $journalpostId, $sporingsId")
            throw RuntimeException("Mottatt journalføringsoppgave for journalpost som ikke finnes")
        }

        journalpost.data.journalpost?.let {
            if (it.kanal != "EESSI" && source == "rina") {
                log.warn("Journalpost med id $journalpostId har ikke forventet mottakskanal: ${it.kanal}, $sporingsId")
            }
            if (it.dokumenter?.any { it.brevkode == "S055" } == false && source == "rina") {
                log.warn("Journalpost med id $journalpostId har ingen dokumenter med forventet brevkode, $sporingsId")
            }

            if (erIkkeJournalfort(it)) {
                return finnDokumentInfoIdForSykmeldingPdfListe(it.dokumenter, sporingsId)
            } else {
                log.warn("Journalpost med id $journalpostId er allerede journalført, sporingsId $sporingsId")
                return null
            }
        }
        log.warn("Fant ikke journalpost med id $journalpostId, $sporingsId")
        return null
    }

    private fun erIkkeJournalfort(journalpostResponse: JournalpostResponse): Boolean {
        return journalpostResponse.journalstatus?.let {
            it.equals("MOTTATT", true) || it.equals("FEILREGISTRERT", true)
        } ?: false
    }

    private fun finnDokumentInfoIdForSykmeldingPdfListe(
        dokumentListe: List<DokumentInfo>?,
        sporingsId: String,
    ): List<DokumentMedTittel> {
        val dokumenter = dokumentListe?.filter { dokument ->
            dokument.dokumentvarianter?.any {
                it.variantformat == "ARKIV"
            } == true
        }?.map { DokumentMedTittel(it.dokumentInfoId, it.tittel) }

        if (dokumenter.isNullOrEmpty()) {
            log.error("Fant ikke PDF-dokument for sykmelding, $sporingsId")
            throw RuntimeException("Journalpost mangler PDF, $sporingsId")
        }

        return dokumenter
    }
}
