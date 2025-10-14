package com.streamflixreborn.streamflix.utils

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Utility class to notify ViewModels when the current provider changes
 */
object ProviderChangeNotifier {
    private val _providerChangeChannel = Channel<Unit>(Channel.CONFLATED)
    val providerChangeFlow: Flow<Unit> = _providerChangeChannel.receiveAsFlow()
    
    /**
     * Notify all listeners that the provider has changed
     */
    fun notifyProviderChanged() {
        _providerChangeChannel.trySend(Unit)
    }
}
