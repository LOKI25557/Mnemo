package com.mnemo.app.ui.home

import com.mnemo.core.model.DataSourceType
import com.mnemo.core.model.MemoryItem
import com.mnemo.core.model.repository.MemoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeMemoryRepository
    private lateinit var viewModel: HomeViewModel

    class FakeMemoryRepository : MemoryRepository {
        val items = mutableListOf<MemoryItem>()
        val countFlow = MutableStateFlow(0)

        override suspend fun insert(item: MemoryItem) {
            items.removeAll { it.id == item.id }
            items.add(item)
            countFlow.value = items.size
        }

        override suspend fun insertAll(newItems: List<MemoryItem>) {
            newItems.forEach { insert(it) }
        }

        override suspend fun search(query: String, limit: Int): List<MemoryItem> {
            if (query.isBlank()) return items.take(limit)
            return items.filter {
                it.title.contains(query, ignoreCase = true) ||
                        it.content.contains(query, ignoreCase = true)
            }.take(limit)
        }

        override suspend fun getById(id: String): MemoryItem? = items.find { it.id == id }

        override suspend fun getBySource(sourceType: DataSourceType, limit: Int): List<MemoryItem> {
            return items.filter { it.sourceType == sourceType }.take(limit)
        }

        override suspend fun deleteById(id: String): Boolean {
            val removed = items.removeIf { it.id == id }
            countFlow.value = items.size
            return removed
        }

        override suspend fun deleteBySource(sourceType: DataSourceType): Int = 0
        override suspend fun deleteAll() {
            items.clear()
            countFlow.value = 0
        }
        override suspend fun count(): Int = items.size
        override fun observeCount(): Flow<Int> = countFlow
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeMemoryRepository()
        viewModel = HomeViewModel(fakeRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialCountZero() = runTest(testDispatcher) {
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.totalMemoriesCount)
        assertEquals(0, viewModel.uiState.value.recentMemories.size)
    }

    @Test
    fun testMemoryCountUpdatesWhenRepositoryChanges() = runTest(testDispatcher) {
        fakeRepository.insert(
            MemoryItem(
                id = "item1",
                sourceType = DataSourceType.MESSAGE,
                title = "Note 1",
                content = "Test content"
            )
        )
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.totalMemoriesCount)
        assertEquals(1, viewModel.uiState.value.recentMemories.size)
    }

    @Test
    fun testDeleteMemoryRemovesItem() = runTest(testDispatcher) {
        fakeRepository.insert(
            MemoryItem(
                id = "item1",
                sourceType = DataSourceType.MESSAGE,
                title = "Note 1",
                content = "Test content"
            )
        )
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.totalMemoriesCount)

        viewModel.deleteMemory("item1")
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.totalMemoriesCount)
        assertEquals(0, viewModel.uiState.value.recentMemories.size)
    }
}
