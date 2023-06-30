package no.nav.syfo.metrics

import io.prometheus.client.Histogram

const val METRICS_NS = "syk-dig-oppgavelytter"

val HTTP_HISTOGRAM: Histogram =
    Histogram.Builder()
        .namespace(METRICS_NS)
        .labelNames("path")
        .name("requests_duration_seconds")
        .help("http requests durations for incoming requests in seconds")
        .register()
