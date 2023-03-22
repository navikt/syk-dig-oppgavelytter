package no.nav.syfo.application.db

import java.sql.Connection

interface DatabaseInterface {
    val connection: Connection
}
