package jp.co.bitz.spritekit

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri

/**
 * The host app's `assets/` folder — this library's stand-in for Apple's main bundle, which
 * `SKTexture(imageNamed:)` reads from without being handed anything. Holds the application
 * [Context] that [SKBundleInitializer] captures at app start-up (and every [SKView] refreshes).
 */
internal object SKBundle {
    @Volatile
    var context: Context? = null

    fun requireContext(): Context =
        checkNotNull(context) {
            "SpriteKit has no application Context yet. It is captured at app start-up by " +
                "SKBundleInitializer, declared in this library's manifest; if a host app removed " +
                "that provider, create an SKView before loading named resources."
        }
}

/**
 * Captures the application [Context] into [SKBundle] when the host app's process starts —
 * Android creates every declared [ContentProvider] before `Application.onCreate`. The same
 * mechanism `androidx.startup` uses, without the dependency. Serves no data.
 */
public class SKBundleInitializer : ContentProvider() {
    override fun onCreate(): Boolean {
        SKBundle.context = context?.applicationContext
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
