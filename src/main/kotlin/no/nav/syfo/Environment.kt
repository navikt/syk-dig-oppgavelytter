package no.nav.syfo

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "syk-dig-oppgavelytter"),
    val oppgaveTopic: String = "oppgavehandtering.oppgavehendelse-v1",
    val aadAccessTokenUrl: String = getEnvVar("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
    val clientId: String = getEnvVar("AZURE_APP_CLIENT_ID"),
    val clientSecret: String = getEnvVar("AZURE_APP_CLIENT_SECRET"),
    val oppgaveUrl: String = getEnvVar("OPPGAVE_URL"),
    val oppgaveScope: String = getEnvVar("OPPGAVE_SCOPE"),
    val safUrl: String = getEnvVar("SAF_URL"),
    val safScope: String = getEnvVar("SAF_SCOPE")
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
