package no.nav.syfo.oppgave.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import no.nav.syfo.accesstoken.AccessTokenClient
import java.time.LocalDateTime

class OppgaveClient(
    private val url: String,
    private val accessTokenClient: AccessTokenClient,
    private val httpClient: HttpClient,
    private val scope: String
) {
    suspend fun hentOppgave(oppgaveId: Long, sporingsId: String): OppgaveResponse {
        return httpClient.get("$url/$oppgaveId") {
            val token = accessTokenClient.getAccessToken(scope)
            header("Authorization", "Bearer $token")
            header("X-Correlation-ID", sporingsId)
        }.body<OppgaveResponse>()
    }
}

data class OppgaveResponse(
    val journalpostId: String?,
    val behandlesAvApplikasjon: String?,
    val tema: String,
    val behandlingstema: String?,
    val oppgavetype: String,
    val behandlingstype: String?,
    val versjon: Int,
    val metadata: Map<String, String?>?,
    val ferdigstiltTidspunkt: LocalDateTime?
)
