package com.mnemo.app.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.mnemo.app.feature.chat.ChatViewModel
import com.mnemo.app.feature.memory.MemoriesViewModel
import com.mnemo.app.ui.home.HomeViewModel

class MnemoViewModelFactory(
    private val container: AppContainer
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(ChatViewModel::class.java) -> {
                ChatViewModel(
                    assistantEngine = container.assistantEngine,
                    memoryRepository = container.memoryRepository
                ) as T
            }
            modelClass.isAssignableFrom(MemoriesViewModel::class.java) -> {
                MemoriesViewModel(
                    memoryRepository = container.memoryRepository
                ) as T
            }
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> {
                HomeViewModel(
                    memoryRepository = container.memoryRepository
                ) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
