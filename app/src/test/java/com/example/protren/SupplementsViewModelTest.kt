package com.example.protren

import com.example.protren.data.UserPreferences
import com.example.protren.model.Supplement
import com.example.protren.repository.SupplementRepository
import com.example.protren.viewmodel.SupplementsViewModel
import com.example.protren.viewmodel.SupplementsUIState
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class SupplementsViewModelTest {

    private lateinit var viewModel: SupplementsViewModel
    private val repository: SupplementRepository = mockk()
    private val prefs: UserPreferences = mockk()
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadToday success updates state to Loaded`() = runTest {
        val mockData = listOf(Supplement(_id = "1", name = "Test"))
        coEvery { repository.getToday() } returns Response.success(mockData)


    }
}