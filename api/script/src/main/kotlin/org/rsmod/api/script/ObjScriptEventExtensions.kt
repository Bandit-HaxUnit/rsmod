package org.rsmod.api.script

import org.rsmod.api.player.events.interact.ObjContentEvents
import org.rsmod.api.player.events.interact.ObjEvents
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.events.EventBus
import org.rsmod.game.type.content.ContentGroupType
import org.rsmod.game.type.obj.ObjType
import org.rsmod.plugin.scripts.ScriptContext

public fun ScriptContext.onOpObj1(
    type: ObjType,
    action: suspend ProtectedAccess.(ObjEvents.Op1) -> Unit,
): Unit = onProtectedEvent(type.id, action)

public fun ScriptContext.onOpObj2(
    type: ObjType,
    action: suspend ProtectedAccess.(ObjEvents.Op2) -> Unit,
): Unit = onProtectedEvent(type.id, action)

public fun ScriptContext.onOpObj3(
    type: ObjType,
    action: suspend ProtectedAccess.(ObjEvents.Op3) -> Unit,
): Unit = onProtectedEvent(type.id, action)

public fun ScriptContext.onOpObj4(
    type: ObjType,
    action: suspend ProtectedAccess.(ObjEvents.Op4) -> Unit,
): Unit = onProtectedEvent(type.id, action)

public fun ScriptContext.onOpObj5(
    type: ObjType,
    action: suspend ProtectedAccess.(ObjEvents.Op5) -> Unit,
): Unit = onProtectedEvent(type.id, action)

public fun ScriptContext.onOpObj1Subop(
    type: ObjType,
    subop: Int,
    action: suspend ProtectedAccess.(ObjEvents.SubOp1) -> Unit,
): Unit = onProtectedEvent(EventBus.composeLongKey(type.id, subop), action)

public fun ScriptContext.onOpObj2Subop(
    type: ObjType,
    subop: Int,
    action: suspend ProtectedAccess.(ObjEvents.SubOp2) -> Unit,
): Unit = onProtectedEvent(EventBus.composeLongKey(type.id, subop), action)

public fun ScriptContext.onOpObj3Subop(
    type: ObjType,
    subop: Int,
    action: suspend ProtectedAccess.(ObjEvents.SubOp3) -> Unit,
): Unit = onProtectedEvent(EventBus.composeLongKey(type.id, subop), action)

public fun ScriptContext.onOpObj4Subop(
    type: ObjType,
    subop: Int,
    action: suspend ProtectedAccess.(ObjEvents.SubOp4) -> Unit,
): Unit = onProtectedEvent(EventBus.composeLongKey(type.id, subop), action)

public fun ScriptContext.onOpObj5Subop(
    type: ObjType,
    subop: Int,
    action: suspend ProtectedAccess.(ObjEvents.SubOp5) -> Unit,
): Unit = onProtectedEvent(EventBus.composeLongKey(type.id, subop), action)

public fun ScriptContext.onOpObj1(
    content: ContentGroupType,
    action: suspend ProtectedAccess.(ObjContentEvents.Op1) -> Unit,
): Unit = onProtectedEvent(content.id, action)

public fun ScriptContext.onOpObj2(
    content: ContentGroupType,
    action: suspend ProtectedAccess.(ObjContentEvents.Op2) -> Unit,
): Unit = onProtectedEvent(content.id, action)

public fun ScriptContext.onOpObj3(
    content: ContentGroupType,
    action: suspend ProtectedAccess.(ObjContentEvents.Op3) -> Unit,
): Unit = onProtectedEvent(content.id, action)

public fun ScriptContext.onOpObj4(
    content: ContentGroupType,
    action: suspend ProtectedAccess.(ObjContentEvents.Op4) -> Unit,
): Unit = onProtectedEvent(content.id, action)

public fun ScriptContext.onOpObj5(
    content: ContentGroupType,
    action: suspend ProtectedAccess.(ObjContentEvents.Op5) -> Unit,
): Unit = onProtectedEvent(content.id, action)

public fun ScriptContext.onOpObj1Subop(
    content: ContentGroupType,
    subop: Int,
    action: suspend ProtectedAccess.(ObjContentEvents.SubOp1) -> Unit,
): Unit = onProtectedEvent(EventBus.composeLongKey(content.id, subop), action)

public fun ScriptContext.onOpObj2Subop(
    content: ContentGroupType,
    subop: Int,
    action: suspend ProtectedAccess.(ObjContentEvents.SubOp2) -> Unit,
): Unit = onProtectedEvent(EventBus.composeLongKey(content.id, subop), action)

public fun ScriptContext.onOpObj3Subop(
    content: ContentGroupType,
    subop: Int,
    action: suspend ProtectedAccess.(ObjContentEvents.SubOp3) -> Unit,
): Unit = onProtectedEvent(EventBus.composeLongKey(content.id, subop), action)

public fun ScriptContext.onOpObj4Subop(
    content: ContentGroupType,
    subop: Int,
    action: suspend ProtectedAccess.(ObjContentEvents.SubOp4) -> Unit,
): Unit = onProtectedEvent(EventBus.composeLongKey(content.id, subop), action)

public fun ScriptContext.onOpObj5Subop(
    content: ContentGroupType,
    subop: Int,
    action: suspend ProtectedAccess.(ObjContentEvents.SubOp5) -> Unit,
): Unit = onProtectedEvent(EventBus.composeLongKey(content.id, subop), action)
