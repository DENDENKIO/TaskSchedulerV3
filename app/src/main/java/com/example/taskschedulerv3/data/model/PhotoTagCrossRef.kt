package com.example.taskschedulerv3.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "photo_tag_cross_ref",
    primaryKeys = ["photoId", "tagId"],
    foreignKeys = [
        ForeignKey(entity = PhotoMemo::class, parentColumns = ["id"], childColumns = ["photoId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Tag::class,       parentColumns = ["id"], childColumns = ["tagId"],   onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("tagId")]
)
data class PhotoTagCrossRef(
    val photoId: Int,
    val tagId: Int
)
