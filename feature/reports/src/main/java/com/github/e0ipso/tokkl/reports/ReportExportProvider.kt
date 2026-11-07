package com.github.e0ipso.tokkl.reports

import android.net.Uri
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import androidx.core.content.FileProvider
import com.github.e0ipso.tokkl.time.MILLIS_PER_SECOND
import java.io.FileNotFoundException

/** FileProvider's sole configured root is completed exports. Every grant is read-only. */
class ReportExportProvider : FileProvider() {
    override fun getType(uri: Uri): String {
        super.getType(uri) // Validate that the URI is inside the configured root.
        return "text/csv"
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Exports are read-only")
        val descriptor = super.openFile(uri, mode)
            ?: throw FileNotFoundException("Cannot read export")
        var handedOver = false
        try {
            val stat = try {
                Os.fstat(descriptor.fileDescriptor)
            } catch (_: ErrnoException) {
                throw FileNotFoundException("Cannot read export")
            }
            if (System.currentTimeMillis() - stat.st_mtime * MILLIS_PER_SECOND >= ReportExportFiles.MAX_AGE_MILLIS ||
                stat.st_size > ReportExportFiles.MAX_BYTES
            ) {
                throw FileNotFoundException("Export expired")
            }
            handedOver = true
            return descriptor
        } finally {
            if (!handedOver) descriptor.close()
        }
    }
}
