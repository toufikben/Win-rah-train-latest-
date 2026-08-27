package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.PersistentAppLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var logText by remember { mutableStateOf("") }
    var showClearDialog by remember { mutableStateOf(false) }
    val latestSessionStatus = remember(logText) {
        logText.lineSequence()
            .filter { it.contains("SESSION_CREATE_") || it.contains("BROADCAST_ACTIVATION_FAILED") }
            .lastOrNull()
    }

    fun refreshLog() {
        logText = PersistentAppLogger.read(context)
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            refreshLog()
            delay(1_000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تشخيص بث الموقع") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع")
                    }
                },
                actions = {
                    IconButton(onClick = ::refreshLog) {
                        Icon(Icons.Default.Refresh, "تحديث السجل")
                    }
                },
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                            as ClipboardManager
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText("WinRah tracking log", logText)
                        )
                        Toast.makeText(context, "تم نسخ السجل", Toast.LENGTH_SHORT).show()
                    },
                ) { Text("نسخ") }

                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "WinRah tracking diagnostic log")
                            putExtra(Intent.EXTRA_TEXT, logText)
                        }
                        context.startActivity(
                            Intent.createChooser(shareIntent, "مشاركة سجل التشخيص")
                        )
                    },
                ) { Text("مشاركة") }

                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { showClearDialog = true },
                ) { Text("مسح") }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(12.dp),
        ) {
            Text(
                "السجل محفوظ على الهاتف فقط ولا يُرسل تلقائيًا.\nآخر تحديث تلقائي كل ثانية.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = when {
                    latestSessionStatus == null -> "آخر نتيجة للجلسة: لا توجد محاولة مسجلة"
                    latestSessionStatus.contains("FAILED") -> "آخر نتيجة للجلسة: فشل — راجع السجل الكامل"
                    latestSessionStatus.contains("SUCCESS") -> "آخر نتيجة للجلسة: نجحت الجلسة"
                    else -> "آخر نتيجة للجلسة: جارٍ التحقق"
                },
                color = when {
                    latestSessionStatus?.contains("FAILED") == true -> MaterialTheme.colorScheme.error
                    latestSessionStatus?.contains("SUCCESS") == true -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(8.dp))
            SelectionContainer {
                Text(
                    text = logText,
                    modifier = Modifier.fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(10.dp),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("مسح سجل التشخيص؟") },
            text = { Text("سيتم حذف السجل المحلي من الهاتف.") },
            confirmButton = {
                TextButton(onClick = {
                    PersistentAppLogger.clear(context)
                    refreshLog()
                    showClearDialog = false
                }) { Text("مسح") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("إلغاء") }
            },
        )
    }
}
