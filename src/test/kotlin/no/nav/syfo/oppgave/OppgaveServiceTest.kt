package no.nav.syfo.oppgave

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.syfo.oppgave.client.OppgaveClient
import no.nav.syfo.oppgave.client.OppgaveResponse
import no.nav.syfo.oppgave.saf.SafJournalpostService

class OppgaveServiceTest : FunSpec({
    val oppgaveClient = mockk<OppgaveClient>()
    val safJournalpostService = mockk<SafJournalpostService>()

    val oppgaveService = OppgaveService(oppgaveClient, safJournalpostService)

    beforeEach {
        clearMocks(oppgaveClient, safJournalpostService)
        coEvery { safJournalpostService.getDokumentInfoId(any(), any()) } returns "123"
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
