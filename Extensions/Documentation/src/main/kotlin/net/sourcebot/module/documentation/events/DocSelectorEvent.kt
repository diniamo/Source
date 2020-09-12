package net.sourcebot.module.documentation.events

import net.dv8tion.jda.api.entities.ChannelType
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.sourcebot.api.response.ErrorResponse
import net.sourcebot.module.documentation.utility.DocResponse
import net.sourcebot.module.documentation.utility.DocSelectorStorage
import java.util.concurrent.TimeUnit

class DocSelectorEvent(private val deleteSeconds: Long) {

    fun onMessageReceived(event: MessageReceivedEvent) {
        val user = event.author
        val channelType: ChannelType = event.channelType

        val message = event.message
        val messageContent = message.contentRaw
        if (messageContent.startsWith("!")) return

        if (!DocSelectorStorage.hasSelector(user)) return
        val docStorage: DocSelectorStorage = DocSelectorStorage.getSelector(user) ?: return

        val docMessage = docStorage.message ?: return
        val cmdMessage = docStorage.cmdMessage

        val infoList = docStorage.infoList
        val jenkinsHandler = docStorage.jenkinsHandler

        if (channelType != ChannelType.PRIVATE) {
            message.delete().queue()
        }

        if (messageContent.equals("cancel", true)) {
            DocSelectorStorage.removeSelector(user)
            docMessage.delete().queue()
            return
        }

        try {
            val selectedId: Int = messageContent.toInt() - 1
            if (selectedId > infoList.size) {
                sendInvalidIdResponse(user, channelType, docMessage, cmdMessage)
                return
            }

            var docResponse = DocResponse()
            docResponse.setAuthor(jenkinsHandler.embedTitle, null, jenkinsHandler.iconUrl)

            docResponse = jenkinsHandler.createDocumentationEmbed(docResponse, infoList[selectedId])

            docMessage.editMessage(docResponse.asMessage(user)).queue()
            DocSelectorStorage.removeSelector(user)
        } catch (ex: Exception) {
            sendInvalidIdResponse(user, channelType, docMessage, cmdMessage)
        }

    }

    private fun sendInvalidIdResponse(user: User, channelType: ChannelType, docMessage: Message, cmdMessage: Message) {
        DocSelectorStorage.removeSelector(user)

        val invalidIdResponse = ErrorResponse(user.name, "You entered an invalid selection id!")

        docMessage.editMessage(invalidIdResponse.asMessage(user)).complete()
            .delete().queueAfter(deleteSeconds, TimeUnit.SECONDS)

        if (channelType != ChannelType.PRIVATE) {
            cmdMessage.delete().queueAfter(deleteSeconds, TimeUnit.SECONDS)
        }
    }


}