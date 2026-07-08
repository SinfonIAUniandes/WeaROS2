package com.jros2.wearos2.ros

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Boilerplate base for [WearSensor] implementations. Holds the identity fields, the enabled
 * toggle, and the observable message-count / status-text state so subclasses only have to
 * implement [start]/[stop] and their own domain logic, updating state through the protected
 * [_messageCount] and [_displayValue] flows.
 */
abstract class BaseWearSensor(
    final override val id: String,
    final override val name: String,
    final override val topicName: String,
    initialDisplay: String = "",
) : WearSensor {

    final override var resolvedTopicName: String = topicName

    final override val enabled = MutableStateFlow(true)

    protected val _messageCount = MutableStateFlow(0L)
    final override val messageCount: StateFlow<Long> = _messageCount

    protected val _displayValue = MutableStateFlow(initialDisplay)
    final override val displayValue: StateFlow<String> = _displayValue
}
