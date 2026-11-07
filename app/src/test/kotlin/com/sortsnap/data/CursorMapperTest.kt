package com.sortsnap.data

import com.google.common.truth.Truth.assertThat
import com.sortsnap.data.media.MediaStoreCursorMapper
import com.sortsnap.data.media.MediaStoreCursorMapper.BUCKET
import com.sortsnap.data.media.MediaStoreCursorMapper.DATA
import com.sortsnap.data.media.MediaStoreCursorMapper.DATE_ADDED
import com.sortsnap.data.media.MediaStoreCursorMapper.DATE_MODIFIED
import com.sortsnap.data.media.MediaStoreCursorMapper.DATE_TAKEN
import com.sortsnap.data.media.MediaStoreCursorMapper.DISPLAY_NAME
import com.sortsnap.data.media.MediaStoreCursorMapper.HEIGHT
import com.sortsnap.data.media.MediaStoreCursorMapper.ID
import com.sortsnap.data.media.MediaStoreCursorMapper.MIME
import com.sortsnap.data.media.MediaStoreCursorMapper.ORIENTATION
import com.sortsnap.data.media.MediaStoreCursorMapper.RELATIVE_PATH
import com.sortsnap.data.media.MediaStoreCursorMapper.SIZE
import com.sortsnap.data.media.MediaStoreCursorMapper.WIDTH
import com.sortsnap.data.media.RowReader
import org.junit.Test

private class FakeRow(private val values: Map<String, Any?>) : RowReader {
    override fun getLong(column: String) = (values[column] as? Number)?.toLong()
    override fun getInt(column: String) = (values[column] as? Number)?.toInt()
    override fun getString(column: String) = values[column] as? String
}

class CursorMapperTest {
    private val base = "content://media/external/images/media"

    private fun row(vararg extra: Pair<String, Any?>) = FakeRow(
        mapOf(
            ID to 42L, DISPLAY_NAME to "PXL_1.jpg", RELATIVE_PATH to "DCIM/Camera/", BUCKET to "Camera",
            DATE_TAKEN to 1_700_000_000_123L, DATE_ADDED to 1_700_000_100L, DATE_MODIFIED to 1_700_000_200L,
            WIDTH to 4000, HEIGHT to 3000, SIZE to 3_500_000L, MIME to "image/jpeg", ORIENTATION to 0,
        ) + extra.toMap(),
    )

    @Test fun mapsAllColumns() {
        val m = MediaStoreCursorMapper.map(row(), base)!!
        assertThat(m.id).isEqualTo(42L)
        assertThat(m.uri).isEqualTo("$base/42")
        assertThat(m.relativePath).isEqualTo("DCIM/Camera/")
        assertThat(m.bucket).isEqualTo("Camera")
        assertThat(m.dateTaken).isEqualTo(1_700_000_000_123L)
        assertThat(m.dateAdded).isEqualTo(1_700_000_100_000L)
        assertThat(m.dateModified).isEqualTo(1_700_000_200L)
        assertThat(m.width).isEqualTo(4000)
        assertThat(m.sizeBytes).isEqualTo(3_500_000L)
        assertThat(m.mimeType).isEqualTo("image/jpeg")
    }

    @Test fun missingIdIsSkipped() {
        assertThat(MediaStoreCursorMapper.map(row(ID to null), base)).isNull()
    }

    @Test fun dateTakenFallsBackToDateAddedThenModified() {
        assertThat(MediaStoreCursorMapper.map(row(DATE_TAKEN to null), base)!!.dateTaken).isEqualTo(1_700_000_100_000L)
        assertThat(MediaStoreCursorMapper.map(row(DATE_TAKEN to 0L, DATE_ADDED to null), base)!!.dateTaken)
            .isEqualTo(1_700_000_200_000L)
    }

    @Test fun rotatedImagesSwapDimensions() {
        val m = MediaStoreCursorMapper.map(row(ORIENTATION to 90), base)!!
        assertThat(m.width).isEqualTo(3000)
        assertThat(m.height).isEqualTo(4000)
        assertThat(MediaStoreCursorMapper.map(row(ORIENTATION to 180), base)!!.width).isEqualTo(4000)
    }

    @Test fun androidNineDerivesRelativePathFromData() {
        val m = MediaStoreCursorMapper.map(row(RELATIVE_PATH to null, DATA to "/storage/emulated/0/Pictures/Screenshots/PXL_1.jpg"), base)!!
        assertThat(m.relativePath).isEqualTo("Pictures/Screenshots/")
    }

    @Test fun sdCardDataPath() {
        assertThat(MediaStoreCursorMapper.relativePathFromData("/storage/1A2B-3C4D/DCIM/Camera/x.jpg", "x.jpg")).isEqualTo("DCIM/Camera/")
        assertThat(MediaStoreCursorMapper.relativePathFromData("/storage/emulated/0/x.jpg", "x.jpg")).isEqualTo("")
        assertThat(MediaStoreCursorMapper.relativePathFromData(null, "x.jpg")).isEqualTo("")
        assertThat(MediaStoreCursorMapper.relativePathFromData("/storage/emulated/0/WhatsApp/Media/y.jpg", "")).isEqualTo("WhatsApp/Media/")
    }

    @Test fun missingBucketFallsBackToLastFolder() {
        val m = MediaStoreCursorMapper.map(row(BUCKET to null, RELATIVE_PATH to "Pictures/Telegram/"), base)!!
        assertThat(m.bucket).isEqualTo("Telegram")
    }

    @Test fun nullsBecomeSafeDefaults() {
        val m = MediaStoreCursorMapper.map(FakeRow(mapOf(ID to 1L)), "$base/")!!
        assertThat(m.uri).isEqualTo("$base/1")
        assertThat(m.displayName).isEmpty()
        assertThat(m.width).isEqualTo(0)
        assertThat(m.mimeType).isEmpty()
    }

    @Test fun projectionUsesDataOnlyWithoutRelativePath() {
        assertThat(MediaStoreCursorMapper.projection(true).toList()).contains(RELATIVE_PATH)
        assertThat(MediaStoreCursorMapper.projection(true).toList()).doesNotContain(DATA)
        assertThat(MediaStoreCursorMapper.projection(false).toList()).contains(DATA)
    }
}
