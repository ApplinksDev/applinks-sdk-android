package android.net

import android.os.Parcel
import java.net.URI
import java.net.URLDecoder

/**
 * A Uri for JVM unit tests, where android.net.Uri.parse returns null. It's in
 * android.net to reach Uri's package-private constructor, and reads only what
 * the middlewares read: scheme, host, path and query parameters.
 */
class TestUri(private val value: String) : Uri() {
    private val uri = URI(value)

    override fun toString(): String = value
    override fun getScheme(): String? = uri.scheme
    override fun getHost(): String? = uri.host
    override fun getPath(): String? = uri.path
    override fun getQuery(): String? = uri.query
    override fun getEncodedQuery(): String? = uri.rawQuery

    private val params: List<Pair<String, String>> =
        uri.rawQuery?.split("&")?.filter { it.isNotEmpty() }?.map {
            val (key, param) = (it.split("=", limit = 2) + "").take(2)
            URLDecoder.decode(key, "UTF-8") to URLDecoder.decode(param, "UTF-8")
        } ?: emptyList()

    override fun getQueryParameterNames(): Set<String> = params.map { it.first }.toCollection(LinkedHashSet())
    override fun getQueryParameter(key: String): String? = params.firstOrNull { it.first == key }?.second

    override fun isHierarchical(): Boolean = true
    override fun isRelative(): Boolean = uri.scheme == null
    override fun getSchemeSpecificPart(): String? = uri.schemeSpecificPart
    override fun getEncodedSchemeSpecificPart(): String? = uri.rawSchemeSpecificPart
    override fun getAuthority(): String? = uri.authority
    override fun getEncodedAuthority(): String? = uri.rawAuthority
    override fun getUserInfo(): String? = uri.userInfo
    override fun getEncodedUserInfo(): String? = uri.rawUserInfo
    override fun getPort(): Int = uri.port
    override fun getEncodedPath(): String? = uri.rawPath
    override fun getFragment(): String? = uri.fragment
    override fun getEncodedFragment(): String? = uri.rawFragment
    override fun getPathSegments(): List<String> = uri.path.orEmpty().split("/").filter { it.isNotEmpty() }
    override fun getLastPathSegment(): String? = pathSegments.lastOrNull()
    override fun buildUpon(): Builder = throw UnsupportedOperationException()
    override fun describeContents(): Int = 0
    override fun writeToParcel(dest: Parcel, flags: Int) = throw UnsupportedOperationException()
}
