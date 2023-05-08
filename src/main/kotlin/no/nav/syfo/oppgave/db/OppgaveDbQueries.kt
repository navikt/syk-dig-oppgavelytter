package no.nav.syfo.oppgave.db

import no.nav.syfo.application.db.DatabaseInterface

fun DatabaseInterface.getUlosteOppgaveCount(): Int {
    return connection.use { conn ->
        conn.prepareStatement(
            """SELECT count('any') from oppgave where ferdigstilt is null""",
        ).use { ps ->
            ps.executeQuery().use {
                if (it.next()) {
                    it.getInt(1)
                } else {
                    0
                }
            }
        }
    }
}
