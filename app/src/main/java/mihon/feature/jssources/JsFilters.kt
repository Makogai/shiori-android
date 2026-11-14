package mihon.feature.jssources

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Filters cross the JavaScript boundary as JSON (`{"type": "select", "name": …, "values": […], "state": 0}`), and the
 * script copies the states back onto its own filter objects by position, so only the shape has to survive the trip.
 */
internal object JsFilters {

    fun fromJson(json: JsonArray): FilterList = FilterList(json.mapNotNull { filter(it.jsonObject) })

    fun toJson(filters: List<Filter<*>>): JsonArray = buildJsonArray { filters.forEach { add(json(it)) } }

    private fun filter(json: JsonObject): Filter<*>? {
        val name = json["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val state = json["state"]
        val values = (json["values"] as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty()
        return when (json["type"]?.jsonPrimitive?.contentOrNull) {
            "header" -> Header(name)
            "separator" -> Separator(name)
            "select" -> Select(name, values.toTypedArray(), state.int() ?: 0)
            "text" -> Text(name, (state as? JsonPrimitive)?.contentOrNull.orEmpty())
            "checkbox" -> CheckBox(name, (state as? JsonPrimitive)?.booleanOrNull ?: false)
            "tristate" -> TriState(name, state.int() ?: Filter.TriState.STATE_IGNORE)
            "group" -> Group(name, (state as? JsonArray)?.mapNotNull { filter(it.jsonObject) }.orEmpty())
            "sort" -> Sort(
                name,
                values.toTypedArray(),
                (state as? JsonObject)?.let {
                    Filter.Sort.Selection(
                        it["index"].int() ?: 0,
                        (it["ascending"] as? JsonPrimitive)?.booleanOrNull ?: false,
                    )
                },
            )
            else -> null
        }
    }

    private fun json(filter: Filter<*>): JsonObject = buildJsonObject {
        put("name", filter.name)
        when (filter) {
            is Filter.Header -> put("type", "header")
            is Filter.Separator -> put("type", "separator")
            is Filter.Select<*> -> {
                put("type", "select")
                put("values", JsonArray(filter.values.map { JsonPrimitive(it.toString()) }))
                put("state", filter.state)
            }
            is Filter.Text -> {
                put("type", "text")
                put("state", filter.state)
            }
            is Filter.CheckBox -> {
                put("type", "checkbox")
                put("state", filter.state)
            }
            is Filter.TriState -> {
                put("type", "tristate")
                put("state", filter.state)
            }
            is Filter.Group<*> -> {
                put("type", "group")
                put("state", toJson(filter.state.filterIsInstance<Filter<*>>()))
            }
            is Filter.Sort -> {
                put("type", "sort")
                put("values", JsonArray(filter.values.map(::JsonPrimitive)))
                val selection = filter.state
                put(
                    "state",
                    if (selection == null) {
                        JsonNull
                    } else {
                        buildJsonObject {
                            put("index", selection.index)
                            put("ascending", selection.ascending)
                        }
                    },
                )
            }
        }
    }

    private fun JsonElement?.int(): Int? = (this as? JsonPrimitive)?.let {
        it.intOrNull
            ?: it.contentOrNull?.toDoubleOrNull()?.toInt()
    }

    class Header(name: String) : Filter.Header(name)
    class Separator(name: String) : Filter.Separator(name)
    class Select(name: String, values: Array<String>, state: Int) : Filter.Select<String>(name, values, state)
    class Text(name: String, state: String) : Filter.Text(name, state)
    class CheckBox(name: String, state: Boolean) : Filter.CheckBox(name, state)
    class TriState(name: String, state: Int) : Filter.TriState(name, state)
    class Group(name: String, state: List<Filter<*>>) : Filter.Group<Filter<*>>(name, state)
    class Sort(name: String, values: Array<String>, state: Selection?) : Filter.Sort(name, values, state)
}
