package no.nav.syfo.oppgave.saf

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.syfo.accesstoken.AccessTokenClient
import no.nav.syfo.oppgave.saf.client.SafGraphQlClient
import no.nav.syfo.oppgave.saf.client.model.DokumentInfo
import no.nav.syfo.oppgave.saf.client.model.Dokumentvariant
import no.nav.syfo.oppgave.saf.client.model.FindJournalpostResponse
import no.nav.syfo.oppgave.saf.client.model.JournalpostResponse
import no.nav.syfo.oppgave.saf.client.model.ResponseData
import no.nav.syfo.oppgave.saf.model.DokumentMedTittel
import org.amshove.kluent.internal.assertFailsWith
import org.amshove.kluent.shouldBeEqualTo

class SafJournalpostServiceTest : FunSpec({
    val safGraphQlClient = mockk<SafGraphQlClient>()
    val accessTokenClient = mockk<AccessTokenClient>()

    val safJournalpostService = SafJournalpostService(safGraphQlClient, accessTokenClient, "scope")

    beforeEach {
        clearMocks(safGraphQlClient, accessTokenClient)
        coEvery { accessTokenClient.getAccessToken(any()) } returns "token"
    }

    context("SafJournalpostService") {
        test("Finner dokumentInfoId for PDF") {
            coEvery { safGraphQlClient.findJournalpost(any(), any(), any()) } returns FindJournalpostResponse(
                ResponseData(
                    JournalpostResponse(
                        dokumenter = listOf(
                            DokumentInfo("123", "tittel", "S055", listOf(Dokumentvariant("ARKIV"))),
                            DokumentInfo("456", "tittel", null, listOf(Dokumentvariant("ORIGINAL"))),
                        ),
                        journalstatus = "MOTTATT",
                        kanal = "ESSI",
                    ),
                ),
                emptyList(),
            )

            val dokumentInfoId = safJournalpostService.getDokumenter("jpId", "sporing")

            dokumentInfoId shouldBeEqualTo listOf(DokumentMedTittel("123", "tittel"))
        }
        test("Returnerer null hvis journalposten er journalført allerede") {
            coEvery { safGraphQlClient.findJournalpost(any(), any(), any()) } returns FindJournalpostResponse(
                ResponseData(
                    JournalpostResponse(
                        dokumenter = listOf(
                            DokumentInfo("123", "tittel", "S055", listOf(Dokumentvariant("ARKIV"))),
                            DokumentInfo("456", "tittel", null, listOf(Dokumentvariant("ORIGINAL"))),
                        ),
                        journalstatus = "FERDIGSTILT",
                        kanal = "ESSI",
                    ),
                ),
                emptyList(),
            )

            val dokumentInfoId = safJournalpostService.getDokumenter("jpId", "sporing")

            dokumentInfoId shouldBeEqualTo null
        }
        test("Kaster feil hvis dokumentliste ikke inneholder PDF") {
            coEvery { safGraphQlClient.findJournalpost(any(), any(), any()) } returns FindJournalpostResponse(
                ResponseData(
                    JournalpostResponse(
                        dokumenter = listOf(
                            DokumentInfo("123", "tittel", "S055", listOf(Dokumentvariant("SLADDET"))),
                            DokumentInfo("456", "tittel", null, listOf(Dokumentvariant("ORIGINAL"))),
                        ),
                        journalstatus = "MOTTATT",
                        kanal = "ESSI",
                    ),
                ),
                emptyList(),
            )

            assertFailsWith<RuntimeException> {
                safJournalpostService.getDokumenter("jpId", "sporing")
            }
        }
    }
})
