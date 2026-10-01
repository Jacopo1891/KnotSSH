package com.knotssh.data.diagnostics

/**
 * Strips everything that could identify a host, an account or key material from text that is
 * about to be written to the diagnostic log.
 *
 * The log is meant to be handed to the developers, so it errs on the side of removing too much:
 * a mangled error message is a nuisance, a leaked hostname or key is an incident.
 */
object DiagnosticsRedactor {

    private val EMAIL = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    private val USER_AT_HOST = Regex("""\b[A-Za-z0-9._%+-]+@[A-Za-z0-9._:-]+""")

    /** Base64/hex runs: private keys, host key blobs, tokens. */
    private val BLOB = Regex("""[A-Za-z0-9+/]{32,}={0,2}""")
    private val FINGERPRINT = Regex("""\b(?:SHA256|MD5):\S+""")
    private val IPV4 = Regex("""\b(?:\d{1,3}\.){3}\d{1,3}\b""")
    private val IPV6 = Regex("""\b(?:[A-Fa-f0-9]{1,4}:){2,7}[A-Fa-f0-9]{1,4}\b""")
    private val HOSTNAME = Regex("""\b(?:[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?\.)+[A-Za-z]{2,}\b""")
    private val UNIX_PATH = Regex("""(?:/[\w.@-]+){2,}/?""")

    fun redact(text: String): String = text
        .replace(FINGERPRINT, "<fingerprint>")
        .replace(EMAIL, "<email>")
        .replace(USER_AT_HOST, "<user@host>")
        .replace(BLOB, "<data>")
        .replace(IPV4, "<ip>")
        .replace(IPV6, "<ip>")
        .replace(HOSTNAME, "<host>")
        .replace(UNIX_PATH, "<path>")

    /**
     * Describes a failure without its stack trace: the exception chain alone already tells the
     * developers which layer broke.
     */
    fun describe(error: Throwable, maxCauses: Int = 3): String = buildString {
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth < maxCauses) {
            if (depth > 0) append(" <- ")
            append(current.javaClass.simpleName)
            current.message?.takeIf { it.isNotBlank() }?.let { append(": ").append(redact(it)) }
            current = current.cause
            depth++
        }
    }

    /**
     * Stack frames carry only class, method, file and line, so they are kept verbatim: redacting
     * them would destroy the only part of a crash report that is actionable.
     */
    fun stackTrace(error: Throwable, maxFrames: Int = 16): String =
        error.stackTrace.take(maxFrames).joinToString("\n") { "    at $it" }
}
