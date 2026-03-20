package com.example.taskschedulerv3.util

import android.content.Context
import android.net.Uri
import com.example.taskschedulerv3.data.db.AppDatabase
import com.example.taskschedulerv3.data.model.*
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

object ExportImportManager {

    // ──────────── EXPORT ────────────

    /**
     * Exports all data to JSON and writes to the given URI.
     * Returns true on success.
     */
    suspend fun exportToUri(context: Context, uri: Uri): Boolean {
        return try {
            val db = AppDatabase.getInstance(context)
            val tasks = db.taskDao().getAll().first()
            val tags = db.tagDao().getAll().first()
            val crossRefs = db.taskTagCrossRefDao().getAll().first()
            val relations = db.taskRelationDao().getAllRelations()
            val photoMemos = db.photoMemoDao().getAll().first()
            val completions = db.taskCompletionDao().getAll().first()

            val root = JSONObject().apply {
                put("version", 1)
                put("exportedAt", System.currentTimeMillis())
                put("tasks", tasksToJson(tasks))
                put("tags", tagsToJson(tags))
                put("taskTagCrossRefs", crossRefsToJson(crossRefs))
                put("taskRelations", relationsToJson(relations))
                put("photoMemos", photoMemosToJson(photoMemos))
                put("taskCompletions", completionsToJson(completions))
            }

            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(root.toString(2).toByteArray(Charsets.UTF_8))
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // ──────────── IMPORT ────────────

    sealed class ImportResult {
        data class Success(val tasksImported: Int, val tagsImported: Int) : ImportResult()
        data class Error(val message: String) : ImportResult()
        object NeedsConflictResolution : ImportResult()
    }

    /**
     * Imports data from a JSON URI. Skips duplicates (same title+startDate for tasks).
     * Returns ImportResult.
     */
    suspend fun importFromUri(context: Context, uri: Uri, overwriteDuplicates: Boolean = false): ImportResult {
        return try {
            val jsonStr = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()?.readText() ?: return ImportResult.Error("ファイルを読み込めませんでした")

            val root = JSONObject(jsonStr)
            val db = AppDatabase.getInstance(context)

            // Import tags first (tasks may reference them)
            val tagsArray = root.optJSONArray("tags") ?: JSONArray()
            var tagsImported = 0
            val tagIdMap = mutableMapOf<Int, Int>() // oldId -> newId

            for (i in 0 until tagsArray.length()) {
                val obj = tagsArray.getJSONObject(i)
                val oldId = obj.getInt("id")
                val tag = Tag(
                    id = 0,
                    name = obj.getString("name"),
                    color = obj.optString("color", "#808080"),
                    level = obj.getInt("level"),
                    parentId = null, // resolve after
                    sortOrder = obj.optInt("sortOrder", 0)
                )
                val newId = db.tagDao().insert(tag).toInt()
                tagIdMap[oldId] = newId
                tagsImported++
            }

            // Fix tag parentIds
            for (i in 0 until tagsArray.length()) {
                val obj = tagsArray.getJSONObject(i)
                val oldId = obj.getInt("id")
                val oldParentId = if (obj.isNull("parentId")) null else obj.getInt("parentId")
                val newId = tagIdMap[oldId] ?: continue
                val newParentId = oldParentId?.let { tagIdMap[it] }
                if (newParentId != null) {
                    val existing = db.tagDao().getById(newId)
                    existing?.let { db.tagDao().update(it.copy(parentId = newParentId)) }
                }
            }

            // Import tasks
            val tasksArray = root.optJSONArray("tasks") ?: JSONArray()
            var tasksImported = 0
            val taskIdMap = mutableMapOf<Int, Int>() // oldId -> newId
            val existingTasks = db.taskDao().getAll().first()

            for (i in 0 until tasksArray.length()) {
                val obj = tasksArray.getJSONObject(i)
                val oldId = obj.getInt("id")
                val title = obj.getString("title")
                val startDate = obj.getString("startDate")

                // Duplicate check: same title + startDate
                val duplicate = existingTasks.find { it.title == title && it.startDate == startDate }
                if (duplicate != null && !overwriteDuplicates) {
                    taskIdMap[oldId] = duplicate.id
                    continue
                }

                val task = Task(
                    id = if (overwriteDuplicates && duplicate != null) duplicate.id else 0,
                    title = title,
                    description = obj.optString("description").ifEmpty { null },
                    startDate = startDate,
                    endDate = obj.optString("endDate").ifEmpty { null },
                    startTime = obj.optString("startTime").ifEmpty { null },
                    endTime = obj.optString("endTime").ifEmpty { null },
                    scheduleType = ScheduleType.valueOf(obj.optString("scheduleType", "NORMAL")),
                    recurrencePattern = obj.optString("recurrencePattern").takeIf { it.isNotEmpty() }
                        ?.let { runCatching { RecurrencePattern.valueOf(it) }.getOrNull() },
                    recurrenceDays = obj.optString("recurrenceDays").ifEmpty { null },
                    recurrenceEndDate = obj.optString("recurrenceEndDate").ifEmpty { null },
                    priority = obj.optInt("priority", 1),
                    isCompleted = obj.optBoolean("isCompleted", false),
                    notifyEnabled = obj.optBoolean("notifyEnabled", false),
                    notifyMinutesBefore = obj.optInt("notifyMinutesBefore", 10),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = System.currentTimeMillis()
                )
                val newId = db.taskDao().insert(task).toInt()
                taskIdMap[oldId] = newId
                tasksImported++
            }

            // Import TaskTagCrossRefs
            val crossRefsArray = root.optJSONArray("taskTagCrossRefs") ?: JSONArray()
            for (i in 0 until crossRefsArray.length()) {
                val obj = crossRefsArray.getJSONObject(i)
                val newTaskId = taskIdMap[obj.getInt("taskId")] ?: continue
                val newTagId = tagIdMap[obj.getInt("tagId")] ?: continue
                runCatching {
                    db.taskTagCrossRefDao().insert(TaskTagCrossRef(taskId = newTaskId, tagId = newTagId))
                }
            }

            // Import TaskRelations
            val relationsArray = root.optJSONArray("taskRelations") ?: JSONArray()
            for (i in 0 until relationsArray.length()) {
                val obj = relationsArray.getJSONObject(i)
                val newId1 = taskIdMap[obj.getInt("taskId1")] ?: continue
                val newId2 = taskIdMap[obj.getInt("taskId2")] ?: continue
                val t1 = minOf(newId1, newId2); val t2 = maxOf(newId1, newId2)
                runCatching { db.taskRelationDao().insert(TaskRelation(taskId1 = t1, taskId2 = t2)) }
            }

            // Import PhotoMemos (path only, no file copy)
            val photosArray = root.optJSONArray("photoMemos") ?: JSONArray()
            for (i in 0 until photosArray.length()) {
                val obj = photosArray.getJSONObject(i)
                val newTaskId = obj.optInt("taskId", 0).let { if (it == 0) null else taskIdMap[it] }
                runCatching {
                    db.photoMemoDao().insert(PhotoMemo(
                        taskId = newTaskId,
                        date = obj.getString("date"),
                        title = obj.optString("title").ifEmpty { null },
                        memo = obj.optString("memo").ifEmpty { null },
                        imagePath = obj.getString("imagePath"),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    ))
                }
            }

            // Import TaskCompletions
            val completionsArray = root.optJSONArray("taskCompletions") ?: JSONArray()
            for (i in 0 until completionsArray.length()) {
                val obj = completionsArray.getJSONObject(i)
                val newTaskId = taskIdMap[obj.getInt("taskId")] ?: continue
                runCatching {
                    db.taskCompletionDao().insert(TaskCompletion(
                        taskId = newTaskId,
                        completedDate = obj.getString("completedDate")
                    ))
                }
            }

            ImportResult.Success(tasksImported, tagsImported)
        } catch (e: Exception) {
            e.printStackTrace()
            ImportResult.Error(e.message ?: "インポートに失敗しました")
        }
    }

    // ──────────── Serializers ────────────

    private fun tasksToJson(tasks: List<Task>): JSONArray = JSONArray().also { arr ->
        tasks.forEach { t ->
            arr.put(JSONObject().apply {
                put("id", t.id); put("title", t.title)
                put("description", t.description ?: ""); put("startDate", t.startDate)
                put("endDate", t.endDate ?: ""); put("startTime", t.startTime ?: "")
                put("endTime", t.endTime ?: ""); put("scheduleType", t.scheduleType.name)
                put("recurrencePattern", t.recurrencePattern?.name ?: "")
                put("recurrenceDays", t.recurrenceDays ?: "")
                put("recurrenceEndDate", t.recurrenceEndDate ?: "")
                put("priority", t.priority); put("isCompleted", t.isCompleted)
                put("notifyEnabled", t.notifyEnabled); put("notifyMinutesBefore", t.notifyMinutesBefore)
                put("createdAt", t.createdAt); put("updatedAt", t.updatedAt)
            })
        }
    }

    private fun tagsToJson(tags: List<Tag>): JSONArray = JSONArray().also { arr ->
        tags.forEach { t ->
            arr.put(JSONObject().apply {
                put("id", t.id); put("name", t.name); put("color", t.color)
                put("level", t.level); put("parentId", t.parentId ?: JSONObject.NULL)
                put("sortOrder", t.sortOrder)
            })
        }
    }

    private fun crossRefsToJson(refs: List<TaskTagCrossRef>): JSONArray = JSONArray().also { arr ->
        refs.forEach { arr.put(JSONObject().apply { put("taskId", it.taskId); put("tagId", it.tagId) }) }
    }

    private fun relationsToJson(relations: List<TaskRelation>): JSONArray = JSONArray().also { arr ->
        relations.forEach { arr.put(JSONObject().apply { put("taskId1", it.taskId1); put("taskId2", it.taskId2) }) }
    }

    private fun photoMemosToJson(photos: List<PhotoMemo>): JSONArray = JSONArray().also { arr ->
        photos.forEach { p ->
            arr.put(JSONObject().apply {
                put("id", p.id); put("taskId", p.taskId ?: JSONObject.NULL)
                put("date", p.date); put("title", p.title ?: ""); put("memo", p.memo ?: "")
                put("imagePath", p.imagePath); put("createdAt", p.createdAt)
            })
        }
    }

    private fun completionsToJson(completions: List<TaskCompletion>): JSONArray = JSONArray().also { arr ->
        completions.forEach { arr.put(JSONObject().apply { put("taskId", it.taskId); put("completedDate", it.completedDate) }) }
    }
}
