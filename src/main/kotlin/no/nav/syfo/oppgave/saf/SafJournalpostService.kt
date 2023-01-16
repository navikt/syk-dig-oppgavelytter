package no.nav.syfo.oppgave.saf

import no.nav.syfo.accesstoken.AccessTokenClient
import no.nav.syfo.log
import no.nav.syfo.oppgave.saf.client.SafGraphQlClient
import no.nav.syfo.oppgave.saf.client.model.Dokumenter
import no.nav.syfo.oppgave.saf.client.model.JournalpostResponse

class SafJournalpostService(
    private val safGraphQlClient: SafGraphQlClient,
    private val accessTokenClient: AccessTokenClient,
    private val scope: String
) {
    suspend fun getDokumentInfoId(
        journalpostId: String,
        sporingsId: String
    ): String? {
        val journalpost = safGraphQlClient.findJournalpost(
            journalpostId = journalpostId,
            token = accessTokenClient.getAccessToken(scope),
            sporingsId = sporingsId
        )

        journalpost.data.journalpost?.let {
            if (it.kanal != "EESSI") {
                log.warn("Journalpost med id $journalpostId har ikke forventet mottakskanal: ${it.kanal}, $sporingsId")
            }
            if (it.dokumenter?.any { it.brevkode == "S055" } == false) {
                log.warn("Journalpost med id $journalpostId har ingen dokumenter med forventet brevkode, $sporingsId")
            }

            if (erIkkeJournalfort(it)) {
                return finnDokumentInfoIdForSykmeldingPdf(it.dokumenter, sporingsId)
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

    private fun finnDokumentInfoIdForSykmeldingPdf(dokumentListe: List<Dokumenter>?, sporingsId: String): String {
        dokumentListe?.forEach { dokument ->
            dokument.dokumentvarianter.forEach {
                if (it.variantformat == "ARKIV") {
                    return dokument.dokumentInfoId
                }
            }
        }
        log.error("Fant ikke PDF-dokument for sykmelding, $sporingsId")
        throw RuntimeException("Journalpost mangler PDF, $sporingsId")
    }
}
