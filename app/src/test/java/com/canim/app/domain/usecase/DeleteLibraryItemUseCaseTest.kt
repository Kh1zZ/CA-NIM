package com.canim.app.domain.usecase

import com.canim.app.data.model.MalUser
import com.canim.app.ui.viewmodel.FakeCanimRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DeleteLibraryItemUseCaseTest {

    private class TestDeleteRepo : FakeCanimRepository() {
        var testMalUser: MalUser = MalUser(username = "testUser", isLoggedIn = true)
        var lastDeletedAnimeId: Int? = null
        var lastDeletedMangaId: Int? = null

        override fun getMalUser(): MalUser = testMalUser

        override suspend fun deleteAnimeTracking(malId: Int): Result<Unit> {
            lastDeletedAnimeId = malId
            return Result.success(Unit)
        }

        override suspend fun deleteMangaTracking(malId: Int): Result<Unit> {
            lastDeletedMangaId = malId
            return Result.success(Unit)
        }
    }

    private lateinit var fakeRepo: TestDeleteRepo
    private lateinit var getMalUserUseCase: GetMalUserUseCase
    private lateinit var useCase: DeleteLibraryItemUseCase

    @Before
    fun setUp() {
        fakeRepo = TestDeleteRepo()
        getMalUserUseCase = GetMalUserUseCase(fakeRepo)
        useCase = DeleteLibraryItemUseCase(fakeRepo, getMalUserUseCase)
    }

    @Test
    fun testDeleteAnimeLoggedIn() = runTest {
        val result = useCase.deleteAnime(10)
        assertTrue(result.isSuccess)
        assertEquals(10, fakeRepo.lastDeletedAnimeId)
    }

    @Test
    fun testDeleteAnimeLoggedOutDoesNotCallRepo() = runTest {
        fakeRepo.testMalUser = MalUser(isLoggedIn = false)
        val result = useCase.deleteAnime(10)
        assertTrue(result.isSuccess)
        assertNull(fakeRepo.lastDeletedAnimeId)
    }

    @Test
    fun testDeleteAnimeNullMalIdDoesNotCallRepo() = runTest {
        val result = useCase.deleteAnime(null)
        assertTrue(result.isSuccess)
        assertNull(fakeRepo.lastDeletedAnimeId)
    }

    @Test
    fun testDeleteMangaLoggedIn() = runTest {
        val result = useCase.deleteManga(20)
        assertTrue(result.isSuccess)
        assertEquals(20, fakeRepo.lastDeletedMangaId)
    }

    @Test
    fun testDeleteMangaLoggedOutDoesNotCallRepo() = runTest {
        fakeRepo.testMalUser = MalUser(isLoggedIn = false)
        val result = useCase.deleteManga(20)
        assertTrue(result.isSuccess)
        assertNull(fakeRepo.lastDeletedMangaId)
    }

    @Test
    fun testInvokeOperator() = runTest {
        val resAnime = useCase(malId = 30, isAnime = true)
        assertTrue(resAnime.isSuccess)
        assertEquals(30, fakeRepo.lastDeletedAnimeId)

        val resManga = useCase(malId = 40, isAnime = false)
        assertTrue(resManga.isSuccess)
        assertEquals(40, fakeRepo.lastDeletedMangaId)
    }
}
