package com.example.protren.util

import android.content.Context
import android.database.Cursor
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object ImageUtils {

    /**
     * Wczytuje obraz z URI i kompresuje do JPG (ok. ≤ 1–2 MB).
     * Zwraca tymczasowy plik w cache, gotowy do wysyłki jako multipart.
     */
    fun prepareImageFile(
        context: Context,
        uri: Uri,
        maxSizeBytes: Long = 2_000_000L
    ): File? {
        val input = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
            ?: return null

        val originalBitmap = BitmapFactory.decodeStream(input)
        input.close()

        var quality = 95
        var byteArray: ByteArray

        do {
            val stream = ByteArrayOutputStream()
            originalBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, stream)
            byteArray = stream.toByteArray()
            quality -= 10
        } while (byteArray.size > maxSizeBytes && quality > 10)

        val tempFile = File.createTempFile("upload_", ".jpg", context.cacheDir)
        FileOutputStream(tempFile).use { fos ->
            fos.write(byteArray)
            fos.flush()
        }
        return tempFile
    }

    /** Zwraca szerokość i wysokość obrazu (użyteczne przy podglądzie). */
    fun getImageSize(context: Context, uri: Uri): Pair<Int, Int>? {
        val input = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
            ?: return null
        val bmp = BitmapFactory.decodeStream(input)
        input.close()
        return Pair(bmp.width, bmp.height)
    }

    /**
     * Tworzy listę partów Multipart pod nazwą "files" z listy URI.
     *
     * ✅ Backend: upload.array("files", 10)
     */
    fun makeGalleryParts(context: Context, uris: List<Uri>): List<MultipartBody.Part> {
        if (uris.isEmpty()) return emptyList()

        val parts = mutableListOf<MultipartBody.Part>()
        for (uri in uris) {
            val file = prepareImageFile(context, uri) ?: continue
            val mime = "image/jpeg".toMediaTypeOrNull()
            val body = file.asRequestBody(mime)

            val suggestedName = resolveDisplayName(context, uri) ?: file.name
            val safeName = if (
                suggestedName.endsWith(".jpg", true) ||
                suggestedName.endsWith(".jpeg", true)
            ) suggestedName else "$suggestedName.jpg"

            parts += MultipartBody.Part.createFormData(
                /* name     = */ "files",     // ✅ MUSI być "files"
                /* filename = */ safeName,
                /* body     = */ body
            )
        }
        return parts
    }

    /** Próbuje pobrać przyjazną nazwę pliku z ContentResolver. */
    private fun resolveDisplayName(context: Context, uri: Uri): String? {
        var name: String? = null
        var cursor: Cursor? = null
        runCatching {
            cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && it.moveToFirst()) {
                    name = it.getString(idx)
                }
            }
        }
        return name
    }
}
