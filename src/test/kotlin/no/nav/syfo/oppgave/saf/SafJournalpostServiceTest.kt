package no.nav.syfo.oppgave.saf

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.syfo.accesstoken.AccessTokenClient
import no.nav.syfo.oppgave.saf.client.SafGraphQlClient
import no.nav.syfo.oppgave.saf.client.model.Dokumenter
import no.nav.syfo.oppgave.saf.client.model.Dokumentvarianter
import no.nav.syfo.oppgave.saf.client.model.FindJournalpostResponse
import no.nav.syfo.oppgave.saf.client.model.JournalpostResponse
import no.nav.syfo.oppgave.saf.client.model.ResponseData
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
                            Dokumenter("123", "S055", listOf(Dokumentvarianter("ARKIV"))),
                            Dokumenter("456", null, listOf(Dokumentvarianter("ORIGINAL")))
                        ),
                        journalstatus = "MOTTATT",
                        kanal = "ESSI"
                    )
                )
            )

            val dokumentInfoId = safJournalpostService.getDokumentInfoId("jpId", "sporing")

            dokumentInfoId shouldBeEqualTo "123"
        }
        test("Returnerer null hvis journalposten er journalført allerede") {
            coEvery { safGraphQlClient.findJournalpost(any(), any(), any()) } returns FindJournalpostResponse(
                ResponseData(
                    JournalpostResponse(
                        dokumenter = listOf(
                            Dokumenter("123", "S055", listOf(Dokumentvarianter("ARKIV"))),
                            Dokumenter("456", null, listOf(Dokumentvarianter("ORIGINAL")))
                        ),
                        journalstatus = "FERDIGSTILT",
                        kanal = "ESSI"
                    )
                )
            )

            val dokumentInfoId = safJournalpostService.getDokumentInfoId("jpId", "sporing")

            dokumentInfoId shouldBeEqualTo null
        }
        test("Kaster feil hvis dokumentliste ikke inneholder PDF") {
            coEvery { safGraphQlClient.findJournalpost(any(), any(), any()) } returns FindJournalpostResponse(
                ResponseData(
                    JournalpostResponse(
                        dokumenter = listOf(
                            Dokumenter("123", null, listOf(Dokumentvarianter("SLADDET"))),
                            Dokumenter("456", null, listOf(Dokumentvarianter("ORIGINAL")))
                        ),
                        journalstatus = "MOTTATT",
                        kanal = "ESSI"
                    )
                )
            )

            assertFailsWith<RuntimeException> {
                safJournalpostService.getDokumentInfoId("jpId", "sporing")
            }
        }
    }
})
