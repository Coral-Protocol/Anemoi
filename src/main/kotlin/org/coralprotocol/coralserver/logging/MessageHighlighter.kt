package org.coralprotocol.coralserver.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.pattern.color.ANSIConstants
import ch.qos.logback.core.pattern.color.ForegroundCompositeConverterBase

class MessageHighlighter : ForegroundCompositeConverterBase<ILoggingEvent?>() {
    override fun getForegroundColorCode(event: ILoggingEvent?): String {
        if (event != null) {
            return when (event.level.toInt()) {
                Level.ERROR_INT -> ANSIConstants.RED_FG
                Level.WARN_INT -> ANSIConstants.YELLOW_FG
                Level.INFO_INT -> ANSIConstants.DEFAULT_FG
                else -> ANSIConstants.DEFAULT_FG
            }
        }

        return ANSIConstants.DEFAULT_FG
    }
}