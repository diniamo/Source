package net.sourcebot.module.moderation.command

import com.google.common.collect.Lists
import net.dv8tion.jda.api.entities.Message
import net.sourcebot.api.command.argument.Adapter
import net.sourcebot.api.command.argument.ArgumentInfo
import net.sourcebot.api.command.argument.Arguments
import net.sourcebot.api.command.argument.OptionalArgument
import net.sourcebot.api.response.Response
import net.sourcebot.api.response.StandardInfoResponse
import net.sourcebot.api.wrapped
import net.sourcebot.api.zipAll

class HistoryCommand : ModerationRootCommand(
    "history", "Show punishment histories."
) {
    override val argumentInfo = ArgumentInfo(
        OptionalArgument("target", "The member to view history of.", "self"),
        OptionalArgument("page", "The page of history to view.", 1)
    )

    override fun execute(message: Message, args: Arguments): Response {
        val target = args.next(Adapter.member(message.guild)) ?: message.member!!
        val header = "${target.user.asTag}'s History"
        val historyList = punishmentHandler.getHistory(target)
        val reportList = punishmentHandler.getReportsAgainst(target)
        if (historyList.isEmpty() && reportList.isEmpty()) return StandardInfoResponse(
            header, "This user does not have any history."
        )
        val historyPages = Lists.partition(historyList, 5)
        val reportPages = Lists.partition(reportList, 5)
        val pages = historyPages.zipAll(reportPages)
        val pageNum = args.next(
            Adapter.int(1, pages.size, "You specified an invalid page number!")
        ) ?: 1
        val (history, reports) = pages[pageNum - 1]
        return StandardInfoResponse(
            header, "**Punishment Points:** ${punishmentHandler.getPoints(target)}"
        ).apply {
            if (history?.isNotEmpty() == true) {
                appendDescription(
                    """
                        
                        **Incidents:**
                        ${history.joinToString("\n") { "**${it.id}:** ${it.heading}: _${it.reason}_" }}
                    """.trimIndent()
                )
            }
            if (reports?.isNotEmpty() == true) {
                appendDescription(
                    """
                        
                        **Reports:**
                        ${reports.joinToString("\n") { "**${it.id}**: _${it.reason}_" }}
                    """.trimIndent()
                )
            }
            appendDescription("\nPage $pageNum / ${historyPages.size}")
        }.wrapped(target)
    }
}