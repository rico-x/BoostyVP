package io.github.ricox.boostyvp.core.ui.paging

import androidx.paging.PagingSource
import io.github.ricox.boostyvp.core.model.Page
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class CursorPagingSourceTest {
    @Test fun `a repeated cursor becomes a retryable load error`() = runTest {
        val source = CursorPagingSource { _: String? -> Page(listOf("post"), "same", false) }
        val result = source.load(PagingSource.LoadParams.Append("same", 20, false))
        assertTrue(result is PagingSource.LoadResult.Error)
    }

    @Test(expected = CancellationException::class)
    fun `cancellation propagates to the paging collector`() = runTest {
        val source = CursorPagingSource<String> { throw CancellationException() }
        source.load(PagingSource.LoadParams.Refresh(null, 20, false))
        Unit
    }
}
