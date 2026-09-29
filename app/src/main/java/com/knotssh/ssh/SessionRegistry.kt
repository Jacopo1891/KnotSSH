package com.knotssh.ssh

import android.content.Context
import com.knotssh.data.local.preferences.AppPreferences
import com.knotssh.di.ApplicationScope
import com.knotssh.domain.repository.CredentialRepository
import com.knotssh.domain.repository.KnownHostRepository
import com.knotssh.domain.repository.ServerRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class SessionSummary(
    val serverId: Long,
    val label: String,
    val title: String
)

class SessionLimitReached(val limit: Int) :
    IllegalStateException("Limite di $limit sessioni contemporanee raggiunto")

/**
 * Holds every live SSH session, so a session outlives the screen that opened it.
 *
 * Terminal screens attach to a session by server id; closing a screen no longer terminates the
 * connection, which is what allows several sessions to run side by side.
 */
@Singleton
class SessionRegistry @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serverRepository: ServerRepository,
    private val credentialRepository: CredentialRepository,
    private val knownHostRepository: KnownHostRepository,
    private val appPreferences: AppPreferences,
    @ApplicationScope private val scope: CoroutineScope
) {
    private val sessions = linkedMapOf<Long, SshSession>()

    private val _active = MutableStateFlow<List<SessionSummary>>(emptyList())
    val active: StateFlow<List<SessionSummary>> = _active.asStateFlow()

    @Synchronized
    fun find(serverId: Long): SshSession? = sessions[serverId]

    /** Returns the existing session for [serverId], or opens one if the limit allows it. */
    @Synchronized
    fun open(serverId: Long, limit: Int): Result<SshSession> {
        sessions[serverId]?.let { return Result.success(it) }
        if (sessions.size >= limit) return Result.failure(SessionLimitReached(limit))

        val session = SshSession(
            serverId = serverId,
            context = context,
            serverRepository = serverRepository,
            credentialRepository = credentialRepository,
            appPreferences = appPreferences,
            sshManager = SshManager(knownHostRepository),
            scope = scope,
            onFinished = ::onSessionFinished
        )
        sessions[serverId] = session
        publish()
        observeLabel(session)
        return Result.success(session)
    }

    @Synchronized
    fun close(serverId: Long) {
        sessions.remove(serverId)?.close()
        publish()
    }

    @Synchronized
    fun closeAll() {
        sessions.values.forEach { it.close() }
        sessions.clear()
        publish()
    }

    /** A session that ended by itself stops being offered for re-entry. */
    private fun onSessionFinished(serverId: Long) {
        scope.launch { close(serverId) }
    }

    private fun observeLabel(session: SshSession) {
        scope.launch {
            session.title.collect { publish() }
        }
    }

    @Synchronized
    private fun publish() {
        _active.value = sessions.values
            .filter { it.isLive }
            .map { SessionSummary(it.serverId, it.label, it.title.value) }
    }

    suspend fun sessionLimit(): Int = appPreferences.connection.first().maxSessions
}
