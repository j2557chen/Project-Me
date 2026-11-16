package com.example.myfitnessapp

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class AutoBackupTest {
    @Test
    fun onlyTheNewestSevenBackupsAreKept() {
        val names = (1..10).map { backupFileName(LocalDate.of(2026, 11, it)) } + listOf("notes.txt", "photo.zip")
        assertEquals(
            listOf(1, 2, 3).map { backupFileName(LocalDate.of(2026, 11, it)) }.reversed(),
            backupsToDelete(names.shuffled())
        )
    }

    @Test
    fun fewerThanSevenAreAllKept() {
        assertEquals(emptyList<String>(), backupsToDelete(listOf(backupFileName(LocalDate.of(2026, 11, 1)))))
    }

    @Test
    fun backupNamesCarryTheirDate() {
        assertEquals(LocalDate.of(2026, 11, 10), backupDateOf("ProjectMe-backup-2026-11-10.zip"))
        // Backups from before the rename still count.
        assertEquals(LocalDate.of(2026, 11, 10), backupDateOf("MyFitnessApp-backup-2026-11-10.zip"))
        assertEquals(LocalDate.of(2026, 11, 10), backupDateOf("MyFitnessApp-backup-2026-11-10 (1).zip"))
        assertEquals(null, backupDateOf("photo.zip"))
    }
}
