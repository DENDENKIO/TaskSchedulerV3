package com.example.taskschedulerv3.ui.recurring

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 繰り返し予定の追加・編集シート (第7弾)
 *
 * 3種のパターンをサポート:
 *  - EVERY_N_DAYS   : N日ごと → recurrenceDays = "N"
 *  - WEEKLY_MULTI   : 曜日複数 → recurrenceDays = "1,3,5" (月=1,火=2,...,日=7)
 *  - MONTHLY_DATES  : 毎月日付複数 → recurrenceDays = "5,20,28"
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringTaskEditorSheet(
    initialTitle: String = "",
    initialStartDate: String = "",
    initialPattern: String = "EVERY_N_DAYS",
    initialDays: String = "1",
    onSave: (title: String, startDate: String, pattern: String, days: String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var startDate by remember { mutableStateOf(initialStartDate) }
    var selectedPattern by remember { mutableStateOf(initialPattern) }
    var daysInput by remember { mutableStateOf(initialDays) }

    // 曜日チェックボックス用 state (日=0, 月=1, ... 土=6 → Room はその月の曜日番号 1-7)
    val dayOfWeekLabels = listOf("月", "火", "水", "木", "金", "土", "日")
    val dayOfWeekNumbers = listOf(1, 2, 3, 4, 5, 6, 7)
    val selectedWeekdays = remember {
        mutableStateListOf<Int>().also { list ->
            initialDays.split(",").mapNotNull { it.trim().toIntOrNull() }.forEach { list.add(it) }
        }
    }

    // 毎月日付選択 (1-31)
    val selectedMonthDays = remember {
        mutableStateListOf<Int>().also { list ->
            initialDays.split(",").mapNotNull { it.trim().toIntOrNull() }.forEach { list.add(it) }
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                if (initialTitle.isBlank()) "繰り返し予定を追加" else "繰り返し予定を編集",
                style = MaterialTheme.typography.titleLarge
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("予定名") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = startDate,
                onValueChange = { startDate = it },
                label = { Text("開始日 (yyyy-MM-dd)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(java.time.LocalDate.now().toString()) }
            )

            // パターン選択
            Text("繰り返しルール", style = MaterialTheme.typography.labelLarge)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    "EVERY_N_DAYS" to "N日ごと",
                    "WEEKLY_MULTI" to "曜日を複数指定",
                    "MONTHLY_DATES" to "毎月の日付を指定"
                ).forEach { (pattern, label) ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(
                            selected = selectedPattern == pattern,
                            onClick = {
                                selectedPattern = pattern
                                // パターン切り替え時に入力をリセット
                                daysInput = "1"
                                selectedWeekdays.clear()
                                selectedMonthDays.clear()
                            }
                        )
                        Text(label)
                    }
                }
            }

            // パターン別 詳細入力
            when (selectedPattern) {
                "EVERY_N_DAYS" -> {
                    OutlinedTextField(
                        value = daysInput,
                        onValueChange = { daysInput = it.filter { c -> c.isDigit() } },
                        label = { Text("間隔（日数）") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                "WEEKLY_MULTI" -> {
                    Text("曜日を選択", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        dayOfWeekLabels.forEachIndexed { idx, lbl ->
                            val num = dayOfWeekNumbers[idx]
                            FilterChip(
                                selected = num in selectedWeekdays,
                                onClick = {
                                    if (num in selectedWeekdays) selectedWeekdays.remove(num)
                                    else selectedWeekdays.add(num)
                                },
                                label = { Text(lbl) }
                            )
                        }
                    }
                }
                "MONTHLY_DATES" -> {
                    Text("日付を選択 (複数可)", style = MaterialTheme.typography.labelMedium)
                    Column {
                        (1..31).chunked(7).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                row.forEach { day ->
                                    FilterChip(
                                        selected = day in selectedMonthDays,
                                        onClick = {
                                            if (day in selectedMonthDays) selectedMonthDays.remove(day)
                                            else selectedMonthDays.add(day)
                                        },
                                        label = { Text("$day", style = MaterialTheme.typography.labelSmall) },
                                        modifier = Modifier.size(38.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 保存ボタン
            val canSave = title.isNotBlank() && when (selectedPattern) {
                "EVERY_N_DAYS" -> daysInput.toIntOrNull() != null && daysInput.toInt() > 0
                "WEEKLY_MULTI" -> selectedWeekdays.isNotEmpty()
                "MONTHLY_DATES" -> selectedMonthDays.isNotEmpty()
                else -> false
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("キャンセル")
                }
                Button(
                    onClick = {
                        val finalDays = when (selectedPattern) {
                            "EVERY_N_DAYS" -> daysInput.trim()
                            "WEEKLY_MULTI" -> selectedWeekdays.sorted().joinToString(",")
                            "MONTHLY_DATES" -> selectedMonthDays.sorted().joinToString(",")
                            else -> daysInput
                        }
                        val finalStart = startDate.ifBlank { java.time.LocalDate.now().toString() }
                        onSave(title, finalStart, selectedPattern, finalDays)
                    },
                    enabled = canSave,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("保存")
                }
            }
        }
    }
}
