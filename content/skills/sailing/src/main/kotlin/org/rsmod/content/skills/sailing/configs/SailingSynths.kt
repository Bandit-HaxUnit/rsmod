package org.rsmod.content.skills.sailing.configs

import org.rsmod.game.type.synth.SynthType

typealias sailing_synths = SailingSynths

/** Synth ids taken from live helm/sail RSProx traces. */
object SailingSynths {
    val helm_enter = SynthType(internalId = 10_792, internalName = "sailing_helm_enter")
    val helm_exit = SynthType(internalId = 10_793, internalName = "sailing_helm_exit")
    val sail_raise = SynthType(internalId = 10_831, internalName = "sailing_sail_raise")
    val sail_lower = SynthType(internalId = 10_833, internalName = "sailing_sail_lower")
}
