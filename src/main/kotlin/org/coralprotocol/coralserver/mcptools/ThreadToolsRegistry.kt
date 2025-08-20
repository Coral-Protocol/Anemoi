package org.coralprotocol.coralserver.mcptools

import org.coralprotocol.coralserver.server.CoralAgentIndividualMcp

/**
 * Extension function to add all thread-based tools to a server.
 */
fun CoralAgentIndividualMcp.addThreadTools() {
    addListAgentsTool()
    addCreateThreadTool()
    addAddParticipantTool()
    addRemoveParticipantTool()
    addCloseThreadTool()
    addSendMessageTool()
    addWaitForMentionsTool()
    addWaitForAgentMessagesTool()
}
