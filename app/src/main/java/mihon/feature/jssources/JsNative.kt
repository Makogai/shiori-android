package mihon.feature.jssources

/**
 * Loads quickjs-kt's native library before its first use. It ships renamed to libqjskt.so (see
 * scripts/repack-quickjs-kt.sh) because the app already has a libquickjs.so, zhanghai's quickjs-android, which
 * extensions use through `app.cash.quickjs`. JNI finds quickjs-kt's functions in any library the app loaded, so loading
 * it here is enough; quickjs-kt's own `loadLibrary("quickjs")` then just loads the other one.
 *
 * On the JVM (unit tests) there is no such library and quickjs-kt loads its desktop build instead.
 */
internal object JsNative {
    private val loaded by lazy {
        try {
            System.loadLibrary("qjskt")
        } catch (_: UnsatisfiedLinkError) {
            // Not Android
        }
    }

    fun ensureLoaded() = loaded
}
