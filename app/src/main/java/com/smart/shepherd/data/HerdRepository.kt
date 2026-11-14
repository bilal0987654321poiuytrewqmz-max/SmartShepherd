package com.smart.shepherd.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class SheepIdentity(val id: String, val label: String)

class HerdRepository(context: Context) {
    private val prefs = context.getSharedPreferences("herd_local_db", Context.MODE_PRIVATE)

    fun all(): List<SheepIdentity> {
        val array = JSONArray(prefs.getString("identities", "[]"))
        return (0 until array.length()).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            SheepIdentity(item.optString("id"), item.optString("label"))
        }
    }

    // Manual, explicit registration only. A future verified Re-ID model may call this
    // after a user-approved identity workflow.
    fun register(label: String): SheepIdentity {
        val list = all().toMutableList()
        val next = (list.size + 1).toString().padStart(4, '0')
        val identity = SheepIdentity("SH-$next", label)
        list.add(identity)
        val array = JSONArray()
        list.forEach { array.put(JSONObject().put("id", it.id).put("label", it.label)) }
        prefs.edit().putString("identities", array.toString()).apply()
        return identity
    }
}
