package com.sortsnap.data.db

import androidx.room.TypeConverter
import com.sortsnap.classifier.Category

class Converters {
    @TypeConverter fun fromCategory(c: Category?): String? = c?.name
    @TypeConverter fun toCategory(s: String?): Category? = Category.fromNameOrNull(s)
}
