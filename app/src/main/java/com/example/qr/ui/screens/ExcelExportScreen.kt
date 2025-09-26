package com.example.qr.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.qr.data.entities.User
import com.example.qr.repository.UserRepository
import com.example.qr.viewmodel.ExcelExportViewModel
import com.example.qr.viewmodel.ExportType
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExcelExportScreen(
    viewModel: ExcelExportViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Text(
            text = "엑셀 리포트 추출",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Export Type Selection
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "추출 유형 선택",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                ExportType.values().forEach { type ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = uiState.exportType == type,
                                onClick = { viewModel.updateExportType(type) }
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = uiState.exportType == type,
                            onClick = { viewModel.updateExportType(type) }
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Column {
                            Text(
                                text = type.displayName,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = viewModel.getExportDescription(type),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Date Range Selection (for applicable export types)
        if (uiState.exportType != ExportType.USER_LIST) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "기간 설정",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Start Date
                        OutlinedButton(
                            onClick = {
                                val calendar = Calendar.getInstance()
                                calendar.timeInMillis = uiState.startDate

                                DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        val newCalendar = Calendar.getInstance()
                                        newCalendar.set(year, month, dayOfMonth)
                                        viewModel.updateStartDate(newCalendar.timeInMillis)
                                    },
                                    calendar.get(Calendar.YEAR),
                                    calendar.get(Calendar.MONTH),
                                    calendar.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.DateRange, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (uiState.startDate > 0) {
                                    dateFormat.format(Date(uiState.startDate))
                                } else {
                                    "시작 날짜"
                                }
                            )
                        }

                        // End Date
                        OutlinedButton(
                            onClick = {
                                val calendar = Calendar.getInstance()
                                calendar.timeInMillis = uiState.endDate

                                DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        val newCalendar = Calendar.getInstance()
                                        newCalendar.set(year, month, dayOfMonth, 23, 59, 59)
                                        viewModel.updateEndDate(newCalendar.timeInMillis)
                                    },
                                    calendar.get(Calendar.YEAR),
                                    calendar.get(Calendar.MONTH),
                                    calendar.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.DateRange, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (uiState.endDate > 0) {
                                    dateFormat.format(Date(uiState.endDate))
                                } else {
                                    "종료 날짜"
                                }
                            )
                        }
                    }

                    uiState.dateError?.let { error ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // User Selection (for user detail export)
        if (uiState.exportType == ExportType.USER_DETAIL) {
            UserSelectionCard(
                selectedUserId = uiState.selectedUserId,
                onUserSelected = viewModel::updateSelectedUserId
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Export Button
        Button(
            onClick = viewModel::exportData,
            enabled = !uiState.isExporting,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (uiState.isExporting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(Icons.Filled.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("엑셀 파일 생성")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Success/Error Messages
        uiState.successMessage?.let { message ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    if (uiState.exportedFile != null) {
                        Spacer(modifier = Modifier.height(12.dp))

                        // 파일 전송 섹션
                        Text(
                            text = "파일 전송 방법 선택",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // MMS 전송을 위한 전화번호 입력
                        OutlinedTextField(
                            value = uiState.recipientPhoneNumber,
                            onValueChange = viewModel::updateRecipientPhoneNumber,
                            label = { Text("전화번호 (MMS 전송용)") },
                            placeholder = { Text("010-1234-5678") },
                            leadingIcon = {
                                Icon(Icons.Default.Phone, contentDescription = null)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // 전송 버튼들
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = viewModel::sendViaMMS,
                                enabled = !uiState.isSending && uiState.recipientPhoneNumber.isNotBlank(),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (uiState.isSending) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Default.Send, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("MMS 전송")
                                }
                            }

                            Button(
                                onClick = viewModel::sendViaMessenger,
                                enabled = !uiState.isSending,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (uiState.isSending) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Default.Share, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("메신저")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 기존 일반 공유 버튼
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = viewModel::shareFile,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("일반 공유")
                            }

                            OutlinedButton(
                                onClick = viewModel::clearMessages,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("확인")
                            }
                        }

                        // 파일 크기 정보
                        uiState.exportedFile?.let { file ->
                            val fileSizeMB = file.length() / (1024 * 1024.0)
                            Text(
                                text = "파일 크기: ${String.format("%.1f", fileSizeMB)}MB ${if (fileSizeMB > 2.0) "(MMS 제한 초과)" else "(MMS 가능)"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (fileSizeMB > 2.0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        uiState.errorMessage?.let { message ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    TextButton(
                        onClick = viewModel::clearMessages,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("확인")
                    }
                }
            }
        }

        // Handle share intent
        uiState.shareIntent?.let { intent ->
            LaunchedEffect(intent) {
                context.startActivity(intent)
                viewModel.clearMessages()
            }
        }
    }
}

@Composable
private fun UserSelectionCard(
    selectedUserId: String?,
    onUserSelected: (String?) -> Unit
) {
    // 실제 구현에서는 UserRepository를 주입받아 사용자 목록을 가져와야 함
    // 여기서는 시뮬레이션용 코드

    var expanded by remember { mutableStateOf(false) }
    var users by remember { mutableStateOf<List<User>>(emptyList()) }
    val selectedUser = users.find { it.id == selectedUserId }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "사용자 선택",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // 사용자 선택 버튼 (시뮬레이션용)
            OutlinedButton(
                onClick = {
                    // 실제 구현에서는 사용자 선택 다이얼로그를 표시
                    onUserSelected("test_user_id")
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (selectedUserId != null) {
                        "사용자 선택됨"
                    } else {
                        "사용자 선택"
                    }
                )
            }

            if (selectedUserId == null) {
                Text(
                    text = "개인별 상세 리포트를 생성하려면 사용자를 선택해주세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}