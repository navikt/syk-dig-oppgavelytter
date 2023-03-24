package no.nav.syfo.oppgave

import io.kotest.core.spec.style.FunSpec
import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import no.nav.syfo.application.db.DatabaseInterface
import no.nav.syfo.oppgave.client.OppgaveClient
import no.nav.syfo.oppgave.client.OppgaveResponse
import no.nav.syfo.oppgave.db.getUlosteOppgaveCount
import no.nav.syfo.oppgave.saf.SafJournalpostService
import no.nav.syfo.oppgave.saf.model.DokumentMedTittel
import no.nav.syfo.oppgave.sykdig.SykDigProducer

class OppgaveServiceTest : FunSpec({
    val oppgaveClient = mockk<OppgaveClient>()
    val safJournalpostService = mockk<SafJournalpostService>()
    val sykDigProducer = mockk<SykDigProducer>()
    val database = mockk<DatabaseInterface>()
    mockkStatic(DatabaseInterface::getUlosteOppgaveCount)

    val oppgaveService = OppgaveService(oppgaveClient, safJournalpostService, sykDigProducer, database, "prod-gcp")

    beforeEach {
        clearMocks(oppgaveClient, safJournalpostService, sykDigProducer, database)
        coEvery { safJournalpostService.getDokumenter(any(), any()) } returns listOf(
            DokumentMedTittel("123", "123dokument"),
            DokumentMedTittel("456", "456dokument")
        )
        coEvery { sykDigProducer.send(any(), any()) } just Runs
        coEvery { database.getUlosteOppgaveCount() } returns 1
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
                ferdigstiltTidspunkt = null,
                tildeltEnhetsnr = NAV_OPPFOLGNING_UTLAND
            )
            coEvery { oppgaveClient.oppdaterOppgave(any(), any()) } just Runs
            oppgaveService.handleOppgave(1L, "fnr")

            coVerify { safJournalpostService.getDokumenter("5566", any()) }
            coVerify { oppgaveClient.oppdaterOppgave(match { it.id == 1 && it.versjon == 1 && it.behandlesAvApplikasjon == "SMD" }, any()) }
            coVerify {
                sykDigProducer.send(
                    any(),
                    match {
                        it.oppgaveId == "1" && it.journalpostId == "5566" &&
                            it.fnr == "fnr" && it.dokumentInfoId == "123" && it.type == "UTLAND" && it.dokumenter == listOf(
                            DokumentMedTittel("123", "123dokument"),
                            DokumentMedTittel("456", "456dokument")
                        )
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
                ferdigstiltTidspunkt = null,
                tildeltEnhetsnr = NAV_OPPFOLGNING_UTLAND
            )

            oppgaveService.handleOppgave(1L, "fnr")

            coVerify(exactly = 0) { safJournalpostService.getDokumenter(any(), any()) }
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
                ferdigstiltTidspunkt = "2023-01-18T09:55:15.729+01:00",
                tildeltEnhetsnr = NAV_OPPFOLGNING_UTLAND
            )

            oppgaveService.handleOppgave(1L, "fnr")

            coVerify(exactly = 0) { safJournalpostService.getDokumenter(any(), any()) }
        }

        test("Sender ikke til syk dig om uløste oppgaver er 10 eller mer") {
            coEvery { oppgaveClient.hentOppgave(any(), any()) } returns OppgaveResponse(
                journalpostId = "5566",
                behandlesAvApplikasjon = null,
                tema = "SYM",
                behandlingstema = null,
                oppgavetype = "JFR",
                behandlingstype = "ae0106",
                versjon = 1,
                metadata = mapOf("RINA_SAKID" to "111"),
                ferdigstiltTidspunkt = null,
                tildeltEnhetsnr = NAV_OPPFOLGNING_UTLAND
            )
            coEvery { database.getUlosteOppgaveCount() } returns 11
            coEvery { oppgaveClient.oppdaterOppgave(any(), any()) } just Runs
            oppgaveService.handleOppgave(1L, "fnr")

            coVerify(exactly = 0) { safJournalpostService.getDokumenter("5566", any()) }
            coVerify(exactly = 0) { oppgaveClient.oppdaterOppgave(match { it.id == 1 && it.versjon == 1 && it.behandlesAvApplikasjon == "SMD" }, any()) }
        }

        test("Sender sykmelding til syk-dig i prod-gcp") {
            val oppgavesServiceProd = OppgaveService(oppgaveClient, safJournalpostService, sykDigProducer, database, "prod-gcp")
            coEvery { oppgaveClient.oppdaterOppgave(any(), any()) } just Runs
            coEvery { oppgaveClient.hentOppgave(any(), any()) } returns OppgaveResponse(
                journalpostId = "5566",
                behandlesAvApplikasjon = null,
                tema = "SYM",
                behandlingstema = null,
                oppgavetype = "JFR",
                behandlingstype = "ae0106",
                versjon = 1,
                metadata = mapOf("RINA_SAKID" to "111"),
                ferdigstiltTidspunkt = null,
                tildeltEnhetsnr = NAV_OPPFOLGNING_UTLAND
            )

            oppgavesServiceProd.handleOppgave(1L, "fnr")

            coVerify { safJournalpostService.getDokumenter("5566", any()) }
            coVerify(exactly = 1) { oppgaveClient.oppdaterOppgave(any(), any()) }
            coVerify(exactly = 1) { sykDigProducer.send(any(), any()) }
        }
    }
})
