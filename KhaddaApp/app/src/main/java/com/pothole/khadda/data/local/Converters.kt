package com.pothole.khadda.data.local

import androidx.room.TypeConverter
import com.pothole.khadda.model.RepairStatus
import com.pothole.khadda.model.SessionStatus
import com.pothole.khadda.model.SeverityLevel

class Converters {
    @TypeConverter
    fun fromSeverity(value: SeverityLevel): String = value.name

    @TypeConverter
    fun toSeverity(value: String): SeverityLevel =
        try { SeverityLevel.valueOf(value) } catch (e: Exception) { SeverityLevel.MEDIUM }

    @TypeConverter
    fun fromSessionStatus(value: SessionStatus): String = value.name

    @TypeConverter
    fun toSessionStatus(value: String): SessionStatus =
        try { SessionStatus.valueOf(value) } catch (e: Exception) { SessionStatus.STOPPED }

    @TypeConverter
    fun fromRepairStatus(value: RepairStatus): String = value.name

    @TypeConverter
    fun toRepairStatus(value: String): RepairStatus =
        try { RepairStatus.valueOf(value) } catch (e: Exception) { RepairStatus.REPORTED }
}
