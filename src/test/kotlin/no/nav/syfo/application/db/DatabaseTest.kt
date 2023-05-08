package no.nav.syfo.application.db

import io.kotest.core.spec.style.FunSpec
import no.nav.syfo.TestDB
import no.nav.syfo.oppgave.db.getUlosteOppgaveCount
import org.amshove.kluent.shouldBeEqualTo
import java.sql.Timestamp
import java.time.OffsetDateTime

class DatabaseTest : FunSpec({
    val testDb = TestDB.database

    beforeEach {
        TestDB.clearAllData()
    }

    test("get ikke ferdigstilte oppgaver should be 1") {
        insertOppgave("1", null)
        val ulosteOppgaver = testDb.getUlosteOppgaveCount()
        ulosteOppgaver shouldBeEqualTo 1
    }

    test("should get ferdigstilte ppgaver should be 0") {
        insertOppgave("1", OffsetDateTime.now())
        val ulosteOppgaver = testDb.getUlosteOppgaveCount()
        ulosteOppgaver shouldBeEqualTo 0
    }
})

private fun insertOppgave(id: String, ferdigstilt: OffsetDateTime?) {
    TestDB.database.connection.use { connection ->
        connection.prepareStatement(
            """
            INSERT INTO oppgave(oppgave_id, ferdigstilt)
            VALUES (?, ?);
        """,
        ).use { preparedStatement ->
            preparedStatement.setString(1, id)
            preparedStatement.setTimestamp(2, ferdigstilt?.let { Timestamp.from(it.toInstant()) })
            preparedStatement.executeUpdate()
        }
        connection.commit()
    }
}
