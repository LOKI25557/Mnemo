package com.mnemo.app.feature.chat

import com.mnemo.core.model.DataSourceType
import com.mnemo.core.model.MemoryItem
import com.mnemo.core.model.repository.MemoryRepository
import com.mnemo.intelligence.AssistantEngine
import com.mnemo.intelligence.intent.RuleBasedCommandParser
import com.mnemo.intelligence.retrieval.RetrievalOrchestrator
import com.mnemo.intelligence.router.DefaultToolRouter
import com.mnemo.intelligence.router.ToolRegistry
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeMemoryRepository
    private lateinit var assistantEngine: AssistantEngine
    private lateinit var viewModel: ChatViewModel

    class FakeMemoryRepository : MemoryRepository {
        val items = mutableListOf<MemoryItem>()
        private val countFlow = MutableStateFlow(0)

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

        val parser = RuleBasedCommandParser()
        val registry = ToolRegistry()
        val router = DefaultToolRouter(registry)
        val orchestrator = RetrievalOrchestrator(fakeRepository)
        assistantEngine = AssistantEngine(parser, router, orchestrator)

        viewModel = ChatViewModel(assistantEngine, fakeRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialStateHasWelcomeMessage() {
        val state = viewModel.uiState.value
        assertEquals(1, state.messages.size)
        assertEquals(MessageSender.ASSISTANT, state.messages[0].sender)
        assertTrue(state.messages[0].text.contains("Mnemo"))
        assertFalse(state.isLoading)
    }

    @Test
    fun testInputTextChanges() {
        viewModel.onInputTextChanged("Hello Mnemo")
        assertEquals("Hello Mnemo", viewModel.uiState.value.inputText)
    }

    @Test
    fun testGreetingReceivesGenuineAssistantResponse() = runTest(testDispatcher) {
        viewModel.onInputTextChanged("Hello Mnemo.")
        viewModel.sendMessage()
        advanceUntilIdle()

        val messages = viewModel.uiState.value.messages
        assertEquals(3, messages.size)
        assertEquals(MessageSender.USER, messages[1].sender)
        assertEquals("Hello Mnemo.", messages[1].text)
        assertEquals(MessageSender.ASSISTANT, messages[2].sender)
        assertTrue(messages[2].text.contains("offline", ignoreCase = true) || messages[2].text.contains("private", ignoreCase = true))
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun testRequiredDemoSequenceSaveAndRetrieve() = runTest(testDispatcher) {
        // Step 1: User says "Remember that our project presentation is tomorrow."
        viewModel.onInputTextChanged("Remember that our project presentation is tomorrow.")
        viewModel.sendMessage()
        advanceUntilIdle()

        // Confirmation emitted only after save operation succeeds
        assertEquals(1, fakeRepository.items.size)
        assertTrue(fakeRepository.items[0].content.contains("project presentation is tomorrow"))

        val saveResponse = viewModel.uiState.value.messages.last()
        assertEquals(MessageSender.ASSISTANT, saveResponse.sender)
        assertTrue(saveResponse.text.contains("Memory saved", ignoreCase = true))
        assertTrue(saveResponse.sources.isNotEmpty())

        // Step 2: User says "Find my project presentation."
        viewModel.onInputTextChanged("Find my project presentation.")
        viewModel.sendMessage()
        advanceUntilIdle()

        val retrieveResponse = viewModel.uiState.value.messages.last()
        assertEquals(MessageSender.ASSISTANT, retrieveResponse.sender)
        assertTrue(retrieveResponse.text.contains("project presentation", ignoreCase = true))
        assertTrue(retrieveResponse.sources.isNotEmpty())
        assertEquals(fakeRepository.items[0].title, retrieveResponse.sources[0].title)
    }

    @Test
    fun testSendNormalMessageInvokesAssistantEngine() = runTest(testDispatcher) {
        viewModel.onInputTextChanged("Who is the team?")
        viewModel.sendMessage()

        // Input should be cleared immediately
        assertEquals("", viewModel.uiState.value.inputText)

        advanceUntilIdle()

        val messages = viewModel.uiState.value.messages
        assertEquals(3, messages.size)
        assertEquals(MessageSender.USER, messages[1].sender)
        assertEquals("Who is the team?", messages[1].text)
        assertEquals(MessageSender.ASSISTANT, messages[2].sender)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun testSaveDirectMemoryThenRetrieveViaAssistant() = runTest(testDispatcher) {
        // Step 1: Save memory
        viewModel.saveDirectMemory(
            title = "Flight Reservation",
            content = "Flight to San Francisco is UA240 on November 30"
        )
        advanceUntilIdle()

        assertEquals(1, fakeRepository.items.size)

        // Step 2: Query assistant about the memory
        viewModel.onInputTextChanged("Flight reservation")
        viewModel.sendMessage()
        advanceUntilIdle()

        val messages = viewModel.uiState.value.messages
        val response = messages.last()
        assertEquals(MessageSender.ASSISTANT, response.sender)
        assertTrue(response.text.contains("UA240") || response.text.contains("Flight Reservation"))
        assertTrue(response.sources.isNotEmpty())
    }

    @Test
    fun testClearChatResetsToSingleWelcome() {
        viewModel.onInputTextChanged("test")
        viewModel.sendMessage()
        viewModel.clearChat()

        val messages = viewModel.uiState.value.messages
        assertEquals(1, messages.size)
        assertTrue(messages[0].text.contains("cleared"))
    }
}
