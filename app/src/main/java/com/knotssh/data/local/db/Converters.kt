package com.knotssh.data.local.db

import androidx.room.TypeConverter
import com.knotssh.domain.model.AuthType
import com.knotssh.domain.model.ForwardType
import com.knotssh.domain.model.PortForwardRule
import com.knotssh.domain.model.SshKeyType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromPortForwardRuleList(value: List<PortForwardRule>): String =
        json.encodeToString(value)

    @TypeConverter
    fun toPortForwardRuleList(value: String): List<PortForwardRule> =
        json.decodeFromString(value)

    @TypeConverter
    fun fromAuthType(value: AuthType): String = value.name

    @TypeConverter
    fun toAuthType(value: String): AuthType = AuthType.valueOf(value)

    @TypeConverter
    fun fromForwardType(value: ForwardType): String = value.name

    @TypeConverter
    fun toForwardType(value: String): ForwardType = ForwardType.valueOf(value)

    @TypeConverter
    fun fromSshKeyType(value: SshKeyType?): String? = value?.name

    @TypeConverter
    fun toSshKeyType(value: String?): SshKeyType? = value?.let { SshKeyType.valueOf(it) }
}
