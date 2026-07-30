package org.rsmod.api.net.rsprot.provider

import com.github.michaelbull.logging.InlineLogger
import java.lang.Exception
import net.rsprot.protocol.api.suppliers.WorldEntityInfoSupplier
import net.rsprot.protocol.game.outgoing.info.worldentityinfo.WorldEntityAvatarExceptionHandler

object WorldEntityProvider {
    fun provide(): WorldEntityInfoSupplier {
        return WorldEntityInfoSupplier(ExceptionHandler)
    }

    private object ExceptionHandler : WorldEntityAvatarExceptionHandler {
        private val logger = InlineLogger()

        override fun exceptionCaught(index: Int, exception: Exception) {
            logger.error(exception) { "Error during world entity avatar computation: index=$index" }
        }
    }
}
