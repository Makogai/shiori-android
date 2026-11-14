package mihon.feature.jssources

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Jsoup elements handed to scripts as integer handles, behind the `HostElement` API of the SDK. Jsoup is what the
 * selectors were written for upstream, so `:has`, `:containsData`, `[attr~=regex]` and `abs:` work unchanged.
 */
internal class JsHtmlHandles {
    private val elements = HashMap<Int, Element>()
    private var next = 0

    @Synchronized
    fun parse(html: String, baseUri: String): Int = add(Jsoup.parse(html, baseUri))

    /** Runs element operation [op]; element results are handles, -1 when there is none. */
    fun call(op: String, handle: Int, arg: String?): Any {
        val element = get(handle)
        return when (op) {
            "select" -> element.select(arg.orEmpty()).map(::add)
            "selectFirst" -> element.selectFirst(arg.orEmpty())?.let(::add) ?: -1
            "text" -> element.text()
            "ownText" -> element.ownText()
            "wholeText" -> element.wholeText()
            "html" -> element.html()
            "outerHtml" -> element.outerHtml()
            "attr" -> element.attr(arg.orEmpty())
            "hasAttr" -> element.hasAttr(arg.orEmpty())
            "absUrl" -> element.absUrl(arg.orEmpty())
            "tagName" -> element.tagName()
            "id" -> element.id()
            "hasClass" -> element.hasClass(arg.orEmpty())
            "data" -> element.data()
            "val" -> element.`val`()
            "parent" -> element.parent()?.let(::add) ?: -1
            "children" -> element.children().map(::add)
            "nextElementSibling" -> element.nextElementSibling()?.let(::add) ?: -1
            "previousElementSibling" -> element.previousElementSibling()?.let(::add) ?: -1
            "location" -> element.ownerDocument()?.location() ?: element.baseUri()
            else -> error("Unknown HTML operation $op")
        }
    }

    @Synchronized
    fun clear() {
        elements.clear()
    }

    @Synchronized
    private fun add(element: Element): Int {
        val handle = next++
        elements[handle] = element
        return handle
    }

    @Synchronized
    private fun get(handle: Int): Element = elements[handle] ?: error("This HTML element is no longer available")
}
