package net.sourcebot.module.moderation

import net.dv8tion.jda.api.entities.*
import net.sourcebot.Source
import net.sourcebot.api.configuration.ConfigurationManager
import net.sourcebot.api.database.MongoDB
import net.sourcebot.api.durationOf
import net.sourcebot.api.formatted
import net.sourcebot.api.response.Response
import net.sourcebot.api.response.StandardErrorResponse
import net.sourcebot.api.response.StandardSuccessResponse
import net.sourcebot.module.moderation.data.*
import net.sourcebot.module.moderation.data.Incident.Type
import org.bson.Document
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.pow

class PunishmentHandler(
    private val configurationManager: ConfigurationManager,
    private val mongo: MongoDB
) {
    fun clearIncident(
        guild: Guild, sender: Member, channel: TextChannel, amount: Int, reason: String
    ) = submitIncident(
        guild,
        { ClearIncident(guild, nextIncidentId(guild), sender, channel, amount, reason) },
        { ClearSuccessResponse(it.id, amount, channel.name) },
        ::ClearFailureResponse
    )

    private class ClearSuccessResponse(
        id: Long, amount: Int, channel: String
    ) : StandardSuccessResponse(
        "Clear Success (#$id)",
        "You have cleared $amount messages in channel `$channel`!"
    )

    private class ClearFailureResponse : StandardErrorResponse(
        "Clear Failure!", "Could not execute clear incident!"
    )

    fun warnIncident(
        sender: Member, member: Member, reason: String
    ): Response {
        val guild = sender.guild
        if (sender == member) return WarnFailureResponse("You may not warn yourself!")
        if (member.user.isBot) return WarnFailureResponse("You may not warn bots!")
        if (!guild.selfMember.canInteract(member)) return WarnFailureResponse(
            "I do not have permission to warn that member!"
        )
        if (!sender.canInteract(member)) return WarnFailureResponse(
            "You do not have permission to warn that member!"
        )
        return submitWarn(guild, sender, member, reason)
    }

    private fun submitWarn(
        guild: Guild,
        sender: Member,
        member: Member,
        reason: String,
        points: Double = 3.7
    ) = submitIncident(
        guild,
        { WarnIncident(nextIncidentId(guild), sender, member, reason, points) },
        { WarnSuccessResponse(it.id, it.member, it.reason) },
        { WarnFailureResponse("Could not execute warn incident!") }
    )

    private class WarnSuccessResponse(
        id: Long, warned: Member, reason: String
    ) : StandardSuccessResponse(
        "Warn Success (#$id)",
        "Warned ${warned.formatted()} for '$reason' !"
    )

    private class WarnFailureResponse(
        description: String
    ) : StandardErrorResponse("Warn Failure!", description)

    fun kickIncident(
        guild: Guild, sender: Member, member: Member, reason: String
    ): Response {
        if (sender == member) return KickFailureResponse("You may not kick yourself!")
        if (member.user.isBot) return KickFailureResponse("You may not kick bots!")
        if (!guild.selfMember.canInteract(member)) return KickFailureResponse(
            "I do not have permission to kick that member!"
        )
        if (!sender.canInteract(member)) return KickFailureResponse(
            "You do not have permission to kick that member!"
        )
        return submitKick(guild, sender, member, reason)
    }

    private fun submitKick(
        guild: Guild,
        sender: Member,
        member: Member,
        reason: String,
        points: Double = 7.4
    ) = submitIncident(guild,
        { KickIncident(nextIncidentId(guild), sender, member, reason, points) },
        { KickSuccessResponse(it.id, it.member, it.reason) },
        { KickFailureResponse("Could not execute kick incident!") }
    )

    private class KickSuccessResponse(
        id: Long, kicked: Member, reason: String
    ) : StandardSuccessResponse(
        "Kick Success (#$id)",
        "Kicked ${kicked.formatted()} for '$reason' !"
    )

    private class KickFailureResponse(
        description: String
    ) : StandardErrorResponse("Kick Failure!", description)

    fun muteIncident(
        sender: Member, member: Member, duration: Duration, reason: String
    ): Response {
        val guild = sender.guild
        if (sender == member) return MuteFailureResponse("You may not mute yourself!")
        if (member.user.isBot) return MuteFailureResponse("You may not mute bots!")
        if (!guild.selfMember.canInteract(member)) return MuteFailureResponse(
            "I do not have permission to mute that member!"
        )
        if (!sender.canInteract(member)) return MuteFailureResponse(
            "You do not have permission to mute that member!"
        )
        val muteRole = getMuteRole(guild) ?: return MuteFailureResponse(
            "The mute role has not been configured!"
        )
        return submitMute(guild, muteRole, sender, member, duration, reason)
    }

    private fun submitMute(
        guild: Guild,
        muteRole: Role,
        sender: Member,
        member: Member,
        duration: Duration,
        reason: String,
        points: Double = 10.0
    ) = submitIncident(guild,
        { MuteIncident(nextIncidentId(guild), muteRole, sender, member, duration, reason, points) },
        { MuteSuccessResponse(it.id, it.member, it.duration, it.reason) },
        { MuteFailureResponse("Could not execute mute incident!") }
    )

    private class MuteSuccessResponse(
        id: Long, muted: Member, duration: Duration, reason: String
    ) : StandardSuccessResponse(
        "Mute Success (#$id)",
        "Muted ${muted.formatted()} for '$reason' ! (${duration.formatted()})"
    )

    private class MuteFailureResponse(
        description: String
    ) : StandardErrorResponse("Mute Failure!", description)

    fun tempbanIncident(
        sender: Member, tempbanned: Member, delDays: Int, duration: Duration, reason: String
    ): Response {
        val guild = sender.guild
        if (sender == tempbanned) return TempbanFailureResponse("You may not tempban yourself!")
        if (tempbanned.user.isBot) return TempbanFailureResponse("You may not tempban bots!")
        if (!guild.selfMember.canInteract(tempbanned)) return TempbanFailureResponse(
            "I do not have permission to tempban that member!"
        )
        if (!sender.canInteract(tempbanned)) return TempbanFailureResponse(
            "You do not have permission to tempban that member!"
        )
        return submitTempban(guild, sender, tempbanned, delDays, duration, reason)
    }

    private fun submitTempban(
        guild: Guild,
        sender: Member,
        member: Member,
        delDays: Int,
        duration: Duration,
        reason: String,
        points: Double = 66.7
    ) = submitIncident(guild,
        { TempbanIncident(nextIncidentId(guild), sender, member, delDays, duration, reason, points) },
        { TempbanSuccessResponse(it.id, it.member, it.duration, it.reason) },
        { TempbanFailureResponse("Could not execute tempban incident!") }
    )

    private class TempbanSuccessResponse(
        id: Long, member: Member, duration: Duration, reason: String
    ) : StandardSuccessResponse(
        "Tempban Success (#$id)",
        "Tempbanned ${member.formatted()} for '$reason'! (${duration.formatted()})"
    )

    private class TempbanFailureResponse(
        description: String
    ) : StandardErrorResponse("Tempban Failure!", description)

    fun banIncident(
        sender: Member, member: Member, delDays: Int, reason: String
    ): Response {
        val guild = sender.guild
        if (sender == member) return BanFailureResponse("You may not ban yourself!")
        if (member.user.isBot) return BanFailureResponse("You may not ban bots!")
        if (!guild.selfMember.canInteract(member)) return BanFailureResponse(
            "I do not have permission to ban that member!"
        )
        if (!sender.canInteract(member)) return BanFailureResponse(
            "You do not have permission to ban that member!"
        )
        return submitBan(guild, sender, member, delDays, reason)
    }

    private fun submitBan(
        guild: Guild,
        sender: Member,
        member: Member,
        delDays: Int,
        reason: String,
        points: Double = 100.0
    ) = submitIncident(guild,
        { BanIncident(nextIncidentId(guild), sender, member, delDays, reason, points) },
        { BanSuccessResponse(it.id, it.member, it.reason) },
        { BanFailureResponse("Could not execute ban incident!") }
    )

    private class BanSuccessResponse(
        id: Long, member: Member, reason: String
    ) : StandardSuccessResponse(
        "Ban Success (#$id)",
        "Banned ${member.formatted()} for '$reason'!"
    )

    private class BanFailureResponse(
        description: String
    ) : StandardErrorResponse("Ban Failure!", description)

    fun unmuteIncident(
        sender: Member, member: Member, reason: String
    ): Response {
        val guild = sender.guild
        if (sender == member) return UnmuteFailureResponse("You may not unmute yourself!")
        if (member.user.isBot) return UnmuteFailureResponse("You may not unmute bots!")
        if (!guild.selfMember.canInteract(member)) return UnmuteFailureResponse(
            "I do not have permission to unmute that member!"
        )
        if (!sender.canInteract(member)) return UnmuteFailureResponse(
            "You do not have permission to unban that member!"
        )
        val muteRole = getMuteRole(guild) ?: return StandardErrorResponse(
            "No Mute Role!", "The mute role has not been configured!"
        )
        return submitIncident(guild,
            { UnmuteIncident(nextIncidentId(guild), muteRole, sender, member, reason) },
            { UnmuteSuccessResponse(it.id, it.member, it.reason) },
            { UnmuteFailureResponse("Could not execute unmute incident!") }
        )
    }

    private class UnmuteSuccessResponse(
        id: Long, member: Member, reason: String
    ) : StandardSuccessResponse(
        "Unmute Success (#$id)",
        "Unmuted ${member.formatted()} for '$reason'!"
    )

    private class UnmuteFailureResponse(
        description: String
    ) : StandardErrorResponse("Unmute Failure!", description)

    fun unbanIncident(
        sender: Member, user: String, reason: String
    ): Response {
        val guild = sender.guild
        val ban = try {
            guild.retrieveBanById(user).complete()
        } catch (err: Throwable) {
            return StandardErrorResponse("Unknown Ban!", "The specified user is not banned!")
        }
        return submitIncident(guild,
            { UnbanIncident(nextIncidentId(guild), sender, ban.user, reason) },
            { UnbanSuccessResponse(it.id, it.user, it.reason) },
            { UnbanFailureResponse("Could not execute unban incident!") }
        )
    }

    private class UnbanSuccessResponse(
        id: Long, user: User, reason: String
    ) : StandardSuccessResponse(
        "Unban Success (#$id)",
        "Unbanned ${user.formatted()} for '$reason'!"
    )

    private class UnbanFailureResponse(
        description: String
    ) : StandardErrorResponse("Unban Failure!", description)

    private val pointMap = TreeMap<Double, Pair<Type, Duration?>>().apply {
        put(3.7, Type.WARN to null)
        put(7.4, Type.KICK to null)
        put(11.1, Type.MUTE to durationOf("30m"))
        put(14.8, Type.MUTE to durationOf("45m"))
        put(18.5, Type.MUTE to durationOf("1h"))
        put(22.2, Type.MUTE to durationOf("3h"))
        put(25.9, Type.MUTE to durationOf("5h"))
        put(29.6, Type.MUTE to durationOf("7h"))
        put(33.3, Type.MUTE to durationOf("1d"))
        put(37.0, Type.MUTE to durationOf("2d"))
        put(40.7, Type.MUTE to durationOf("3d"))
        put(44.4, Type.MUTE to durationOf("4d"))
        put(48.1, Type.MUTE to durationOf("5d"))
        put(51.9, Type.MUTE to durationOf("6d"))
        put(55.6, Type.MUTE to durationOf("1w"))
        put(59.3, Type.MUTE to durationOf("2w"))
        put(63.0, Type.MUTE to durationOf("3w"))
        put(66.7, Type.TEMPBAN to durationOf("2d"))
        put(70.4, Type.TEMPBAN to durationOf("4d"))
        put(74.1, Type.TEMPBAN to durationOf("6d"))
        put(77.8, Type.TEMPBAN to durationOf("1w"))
        put(81.5, Type.TEMPBAN to durationOf("3w"))
        put(85.2, Type.TEMPBAN to durationOf("1M"))
        put(88.9, Type.TEMPBAN to durationOf("2M"))
        put(92.6, Type.TEMPBAN to durationOf("3M"))
        put(96.3, Type.TEMPBAN to durationOf("4M"))
        put(100.00, Type.BAN to null)
    }

    fun punishMember(sender: Member, target: Member, id: Int): Response {
        val guild = sender.guild
        if (sender == target) return PunishFailureResponse("You may not punish yourself!")
        if (target.user.isBot) return PunishFailureResponse("You may not punish bots!")
        if (!guild.selfMember.canInteract(target)) return PunishFailureResponse(
            "I do not have permission to punish that member!"
        )
        if (!sender.canInteract(target)) return WarnFailureResponse(
            "You do not have permission to punish that member!"
        )
        val offense = getOffenses(sender.guild)[id] ?: return StandardErrorResponse(
            "Invalid Punishment!", "There is no punishment with the ID `$id`!"
        )
        val points = getPoints(target)
        val toAdd = getPoints(offense["level"] as Int)
        val effective = points + toAdd
        val reason = "${offense["name"] as String} (Punishments vary based on your history)"
        val (type, duration) =
            (pointMap.ceilingEntry(effective) ?: pointMap.floorEntry(effective)).value
        return when (type) {
            Type.WARN -> submitWarn(guild, sender, target, reason, toAdd)
            Type.KICK -> submitKick(guild, sender, target, reason, toAdd)
            Type.MUTE -> {
                val muteRole = getMuteRole(guild) ?: return MuteFailureResponse(
                    "The mute role has not been configured!"
                )
                return submitMute(guild, muteRole, sender, target, duration!!, reason, toAdd)
            }
            Type.TEMPBAN -> submitTempban(guild, sender, target, 7, duration!!, reason, toAdd)
            Type.BAN -> submitBan(guild, sender, target, 7, reason, toAdd)
            else -> StandardErrorResponse("Unmet condition in 'when'!")
        }
    }

    private class PunishFailureResponse(
        description: String
    ) : StandardErrorResponse("Punish Failure!", description)

    fun getOffenses(
        guild: Guild
    ) = offensesCollection(guild).find().sortedBy {
        it["level"] as Int
    }.withIndex().associateByTo(
        HashMap(),
        IndexedValue<Document>::index,
        IndexedValue<Document>::value
    )

    fun addOffense(guild: Guild, level: Int, name: String): Response {
        val offenses = offensesCollection(guild)
        val offense = Document().also {
            it["level"] = level
            it["name"] = name
        }
        offenses.insertOne(offense)
        return StandardSuccessResponse(
            "Offense Added!",
            "Added Level `$level` offense: `$name`!"
        )
    }

    fun removeOffense(guild: Guild, id: Int): Response {
        val offenses = getOffenses(guild)
        val toRemove = offenses[id] ?: return StandardErrorResponse(
            "Invalid Offense!", "There is no offense with the ID `$id`!"
        )
        val level = toRemove["level"] as Int
        val name = toRemove["name"] as String
        offensesCollection(guild).deleteOne(toRemove)
        return StandardSuccessResponse(
            "Offense Removed!",
            "Removed Level `$level` offense: `$name`!"
        )
    }

    fun getPoints(level: Int) = 3.7 * 3.0.pow(level - 1.0)

    private fun offensesCollection(guild: Guild) =
        mongo.getCollection(guild.id, "offenses")

    private fun <T : ExecutableIncident> submitIncident(
        guild: Guild,
        supplier: () -> T,
        onSuccess: (T) -> StandardSuccessResponse,
        onFailure: () -> StandardErrorResponse
    ): Response {
        val logChannel = getIncidentChannel(guild) ?: return StandardErrorResponse(
            "No Log Channel!", "The incident log has not been configured!"
        )
        return try {
            val incident = supplier()
            incident.execute()
            incident.sendLog(logChannel)
            incidentCollection(guild).insertOne(incident.asDocument())
            onSuccess(incident)
        } catch (err: Throwable) {
            onFailure().apply { addField("Exception:", err.toString(), false) }
        }
    }

    private fun incidentCollection(guild: Guild) = mongo.getCollection(guild.id, "incidents")
    private fun nextIncidentId(guild: Guild) = incidentCollection(guild).countDocuments() + 1

    private fun getIncidentChannel(
        guild: Guild
    ) = configurationManager[guild].optional<String>(
        "moderation.incident-log"
    )?.let(guild::getTextChannelById)

    private fun reportCollection(guild: Guild) = mongo.getCollection(guild.id, "reports")
    private fun getReportChannel(
        guild: Guild
    ) = configurationManager[guild].optional<String>(
        "moderation.report-log"
    )?.let(guild::getTextChannelById)

    fun submitReport(
        sender: Member,
        target: Member,
        reason: String
    ): Response {
        val guild = sender.guild
        val channel = getReportChannel(guild) ?: return StandardErrorResponse(
            "No Log Channel!", "The report log has not been configured!"
        )
        val collection = reportCollection(guild)
        val id = collection.countDocuments() + 1
        Report(id, sender, target, reason).also {
            collection.insertOne(it.asDocument())
            it.send(channel)
        }
        return StandardSuccessResponse(
            "Report Submit! #$id",
            "You have submit a report against ${target.formatted()}!"
        )
    }

    private fun getMuteRole(guild: Guild) = configurationManager[guild].optional<String>(
        "moderation.mute-role"
    )?.let(guild::getRoleById)

    fun getCase(guild: Guild, id: Long): Case? =
        incidentCollection(guild).find(Document("_id", id)).first()?.let(::Case)

    fun deleteCase(guild: Guild, id: Long): Response {
        val collection = incidentCollection(guild)
        return collection.find(Document("_id", id)).first()?.let {
            collection.deleteOne(Document("_id", id))
            StandardSuccessResponse("Case Deleted!", "Case #$id has been deleted!")
        } ?: StandardErrorResponse("Invalid Case ID!", "There is no case with ID #$id!")
    }

    private fun getHistory(guild: Guild, id: String): List<Case> =
        incidentCollection(guild).find(Document("target", id))
            .map(::Case)
            .toList()

    fun getHistory(member: Member): List<Case> = getHistory(member.guild, member.id)

    fun performTasks(guilds: () -> Collection<Guild>) {
        Source.SCHEDULED_EXECUTOR_SERVICE.scheduleAtFixedRate(
            {
                guilds().forEach { guild ->
                    expireOldIncidents(guild)
                    doPointDecay(guild)
                }
            }, 0, 1, TimeUnit.SECONDS
        )
    }

    private fun expireOldIncidents(guild: Guild) {
        val reason = "The punishment has expired."
        val collection = incidentCollection(guild)
        val query = Document().apply {
            this["expiry"] = Document().apply {
                this["\$exists"] = true
                this["\$lte"] = Instant.now().toEpochMilli()
            }
            this["resolved"] = Document("\$ne", true)
        }
        collection.find(query).forEach {
            when (it["type"] as String) {
                "MUTE" -> {
                    val muted = (it["target"] as String).let(guild::getMemberById)!!
                    unmuteIncident(guild.selfMember, muted, reason)
                }
                "TEMPBAN" -> unbanIncident(guild.selfMember, it["target"] as String, reason)
            }
            collection.updateOne(it, Document("\$set", Document("resolved", true)))
        }
    }

    private fun doPointDecay(guild: Guild) = incidentCollection(guild).let { incidents ->
        incidents.find(Document("points", Document("\$exists", true))).forEach { incident ->
            val points = incident["points"] as Document
            var decay = points["decay"] as Long
            if (--decay <= 0) incidents.updateOne(incident, Document("\$unset", Document("points", "")))
            else incidents.updateOne(
                incident,
                Document("\$set", Document("points", Document("decay", decay).also {
                    it["value"] = points["value"]
                }))
            )
        }
    }

    private fun getPoints(guild: Guild, id: String): Double =
        incidentCollection(guild).find(Document().also {
            it["target"] = id
            it["points"] = Document("\$exists", true)
        }).sumByDouble {
            (it["points"] as Document)["value"] as Double
        }

    fun getPoints(member: Member) = getPoints(member.guild, member.id)
}