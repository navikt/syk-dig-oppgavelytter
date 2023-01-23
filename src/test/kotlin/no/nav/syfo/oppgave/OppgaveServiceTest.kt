package no.nav.syfo.oppgave

import io.kotest.core.spec.style.FunSpec
import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import no.nav.syfo.oppgave.client.OppgaveClient
import no.nav.syfo.oppgave.client.OppgaveResponse
import no.nav.syfo.oppgave.saf.SafJournalpostService
import no.nav.syfo.oppgave.sykdig.SykDigProducer

class OppgaveServiceTest : FunSpec({
    val oppgaveClient = mockk<OppgaveClient>()
    val safJournalpostService = mockk<SafJournalpostService>()
    val sykDigProducer = mockk<SykDigProducer>()

    val oppgaveService = OppgaveService(oppgaveClient, safJournalpostService, sykDigProducer, "dev-gcp")

    beforeEach {
        clearMocks(oppgaveClient, safJournalpostService)
        coEvery { safJournalpostService.getDokumentInfoId(any(), any()) } returns "123"
        coEvery { sykDigProducer.send(any(), any()) } just Runs
    }

    context("OppgaveService") {
        test("Henter dokumentInfoId for utenlandsk sykmelding-oppgave som kommer fra Rina") {
            coEvery { oppgaveClient.hentOppgave(any(), any()) } returns OppgaveResponse(
                journalpostId = "5566",
                behandlesAvApplikasjon = null,
                tema = "SYM",
                behandlingstema = null,
                oppgavetype = "JFR",
                behandlingstype = "ae0106",
                versjon = 1,
                metadata = mapOf("RINA_SAKID" to "111"),
                ferdigstiltTidspunkt = null
            )

            oppgaveService.handleOppgave(1L, "fnr")

            coVerify { safJournalpostService.getDokumentInfoId("5566", any()) }
            coVerify {
                sykDigProducer.send(
                    any(),
                    match {
                        it.oppgaveId == "1" && it.journalpostId == "5566" &&
                            it.fnr == "fnr" && it.dokumentInfoId == "123" && it.type == "UTLAND"
                    }
                )
            }
        }

        test("Henter ikke dokumentInfoId for utenlandsk sykmelding-oppgave med behandlesAvApplikasjon SMD") {
            coEvery { oppgaveClient.hentOppgave(any(), any()) } returns OppgaveResponse(
                journalpostId = "5566",
                behandlesAvApplikasjon = "SMD",
                tema = "SYM",
                behandlingstema = null,
                oppgavetype = "JFR",
                behandlingstype = "ae0106",
                versjon = 1,
                metadata = mapOf("RINA_SAKID" to "111"),
                ferdigstiltTidspunkt = null
            )

            oppgaveService.handleOppgave(1L, "fnr")

            coVerify(exactly = 0) { safJournalpostService.getDokumentInfoId(any(), any()) }
        }

        test("Henter ikke dokumentInfoId for ferdigstilt utenlandsk sykmelding-oppgave som kommer fra Rina") {
            coEvery { oppgaveClient.hentOppgave(any(), any()) } returns OppgaveResponse(
                journalpostId = "5566",
                behandlesAvApplikasjon = null,
                tema = "SYM",
                behandlingstema = null,
                oppgavetype = "JFR",
                behandlingstype = "ae0106",
                versjon = 1,
                metadata = mapOf("RINA_SAKID" to "111"),
                ferdigstiltTidspunkt = "2023-01-18T09:55:15.729+01:00"
            )

            oppgaveService.handleOppgave(1L, "fnr")

            coVerify(exactly = 0) { safJournalpostService.getDokumentInfoId(any(), any()) }
        }
    }
})
