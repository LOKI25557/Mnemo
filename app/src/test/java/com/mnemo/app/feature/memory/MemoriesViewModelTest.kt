package com.mnemo.app.feature.memory

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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MemoriesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeMemoryRepository
    private lateinit var viewModel: MemoriesViewModel

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

        override suspend fun deleteBySource(sourceType: DataSourceType): Int {
            val before = items.size
            items.removeAll { it.sourceType == sourceType }
            countFlow.value = items.size
            return before - items.size
        }

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
        viewModel = MemoriesViewModel(fakeRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialStateEmpty() = runTest(testDispatcher) {
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(0, state.memories.size)
        assertEquals(0, state.totalCount)
    }

    @Test
    fun testAddMemoryUpdatesStateAndRepository() = runTest(testDispatcher) {
        viewModel.addMemory(
            title = "WiFi Password",
            content = "HomeNetwork: 12345678",
            sourceType = DataSourceType.MESSAGE
        )
        advanceUntilIdle()

        assertEquals(1, fakeRepository.items.size)
        assertEquals(1, viewModel.uiState.value.memories.size)
        assertEquals("WiFi Password", viewModel.uiState.value.memories[0].title)
        assertNotNull(viewModel.uiState.value.userNotification)
    }

    @Test
    fun testDeleteMemoryRemovesFromRepository() = runTest(testDispatcher) {
        viewModel.addMemory(
            title = "Item to delete",
            content = "Some content",
            sourceType = DataSourceType.FILE
        )
        advanceUntilIdle()

        val id = fakeRepository.items[0].id
        viewModel.deleteMemory(id)
        advanceUntilIdle()

        assertEquals(0, fakeRepository.items.size)
        assertEquals(0, viewModel.uiState.value.memories.size)
    }

    @Test
    fun testSearchFiltersMemories() = runTest(testDispatcher) {
        viewModel.addMemory("Apple Notes", "Grocery list", DataSourceType.MESSAGE)
        viewModel.addMemory("Banana Bread", "Recipe instructions", DataSourceType.FILE)
        advanceUntilIdle()

        viewModel.onSearchQueryChanged("Apple")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.memories.size)
        assertEquals("Apple Notes", state.memories[0].title)
    }

    @Test
    fun testSeedDemoDataCreatesClearlyIdentifiedSampleData() = runTest(testDispatcher) {
        viewModel.seedDemoData()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(3, state.memories.size)
        state.memories.forEach { memory ->
            assertTrue(memory.title.contains("Sample Data"))
        }
    }
}
