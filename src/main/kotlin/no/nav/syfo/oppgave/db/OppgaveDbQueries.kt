package no.nav.syfo.oppgave.db

import no.nav.syfo.application.db.DatabaseInterface

fun DatabaseInterface.getUlosteOppgaveCount() {
    return connection.use { conn ->
        conn.prepareStatement(
            """select count(*) from oppgave where ferdigstilt is null"""
        ).use {
        }
    }
}
