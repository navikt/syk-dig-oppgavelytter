package no.nav.syfo.oppgave.saf.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import no.nav.syfo.log
import no.nav.syfo.oppgave.saf.client.model.FindJournalpostRequest
import no.nav.syfo.oppgave.saf.client.model.FindJournalpostResponse
import no.nav.syfo.oppgave.saf.client.model.FindJournalpostVariables

class SafGraphQlClient(
    private val httpClient: HttpClient,
    private val basePath: String,
    private val graphQlQuery: String
) {
    suspend fun findJournalpost(journalpostId: String, token: String, sporingsId: String): FindJournalpostResponse {
        val findJournalpostRequest = FindJournalpostRequest(query = graphQlQuery, variables = FindJournalpostVariables(journalpostId = journalpostId))
        try {
            return httpClient.post(basePath) {
                setBody(findJournalpostRequest)
                header(HttpHeaders.Authorization, "Bearer $token")
                header("X-Correlation-ID", sporingsId)
                header(HttpHeaders.ContentType, "application/json")
            }.body()
        } catch (e: Exception) {
            log.error("Noe gikk galt ved kall til SAF, sporingsId $sporingsId", e)
            throw e
        }
    }
}
