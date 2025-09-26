package com.example.qr.ui.screens

import android.Manifest
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.qr.data.entities.EntryLog
import com.example.qr.viewmodel.QRScannerViewModel
import com.example.qr.viewmodel.ScanResult
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.journeyapps.barcodescanner.CompoundBarcodeView
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun QRScannerScreen(
    viewModel: QRScannerViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val recentEntries by viewModel.recentEntries.collectAsState()
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header with Stats
        HeaderSection(viewModel)

        Spacer(modifier = Modifier.height(16.dp))

        // Camera Permission Handling
        when {
            cameraPermissionState.status.isGranted -> {
                // Scanner Section
                ScannerSection(
                    uiState = uiState,
                    onQRCodeDetected = viewModel::processQRCode,
                    onToggleScanner = viewModel::toggleScanner
                )
            }
            cameraPermissionState.status.shouldShowRationale -> {
                CameraPermissionRationale(
                    onRequestPermission = { cameraPermissionState.launchPermissionRequest() }
                )
            }
            else -> {
                CameraPermissionRequest(
                    onRequestPermission = { cameraPermissionState.launchPermissionRequest() }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Scan Result Display
        uiState.scanResult?.let { result ->
            ScanResultCard(
                result = result,
                onDismiss = viewModel::clearScanResult
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Recent Entries
        Text(
            text = "최근 출입 기록",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(recentEntries) { entry ->
                EntryLogCard(entry = entry)
            }
        }
    }
}

@Composable
private fun HeaderSection(viewModel: QRScannerViewModel) {
    val todayStats by viewModel.todayStats.collectAsState()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "QR 스캐너",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                StatItem("오늘 입장", todayStats.totalEntries.toString())
                StatItem("오늘 퇴장", todayStats.totalExits.toString())
                StatItem("현재 체류", todayStats.currentlyInside.toString())
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun ScannerSection(
    uiState: com.example.qr.viewmodel.QRScannerUiState,
    onQRCodeDetected: (String) -> Unit,
    onToggleScanner: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "QR 코드를 스캔하세요",
                    style = MaterialTheme.typography.titleMedium
                )

                Switch(
                    checked = uiState.isScannerActive,
                    onCheckedChange = { onToggleScanner() }
                )
            }

            if (uiState.isScannerActive) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.isProcessing) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("처리 중...")
                        }
                    } else {
                        // 실제 구현에서는 CameraX 또는 ZXing 카메라 뷰를 여기에 배치
                        QRCameraPreview(
                            onQRCodeDetected = onQRCodeDetected
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Filled.Camera,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = "스캐너가 비활성화됨",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QRCameraPreview(
    onQRCodeDetected: (String) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var lastScanTime by remember { mutableStateOf(0L) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    // ZXing 뷰를 보관 (생명주기에서 resume/pause 호출용)
    val barcodeViewHolder = remember { mutableStateOf<CompoundBarcodeView?>(null) }

    if (cameraError != null) {
        // 카메라 에러 표시
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "카메라 오류",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = cameraError ?: "알 수 없는 오류",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        cameraError = null
                        barcodeViewHolder.value = null
                    }
                ) {
                    Text("다시 시도")
                }
            }
        }
        return
    }

    // 콜백 타입을 명시적으로 구현 (타입 추론 이슈 방지)
    val callback = remember {
        object : com.journeyapps.barcodescanner.BarcodeCallback {
            override fun barcodeResult(result: com.journeyapps.barcodescanner.BarcodeResult) {
                val currentTime = System.currentTimeMillis()
                // 연속 스캔 방지 (1.5초 간격)
                if (currentTime - lastScanTime > 1500) {
                    lastScanTime = currentTime
                    result.text?.let { qrCode ->
                        android.util.Log.d("QRScanner", "QR 코드 스캔됨: $qrCode")
                        onQRCodeDetected(qrCode)
                    }
                }
            }

            override fun possibleResultPoints(resultPoints: List<com.google.zxing.ResultPoint>) {
                // 스캔 포인트 시각화는 선택사항
            }
        }
    }

    // Android 뷰로 ZXing 연결 (factory는 반드시 non-null View 반환)
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            try {
                CompoundBarcodeView(context).apply {
                    // 카메라 설정
                    cameraSettings.apply {
                        requestedCameraId = -1 // 후면 카메라 사용
                        isAutoFocusEnabled = true
                        isContinuousFocusEnabled = true
                        isExposureEnabled = true
                        isMeteringEnabled = true
                    }

                    // 스캔 영역 설정
                    viewFinder.apply {
                        setLaserVisibility(false) // 레이저 라인 비활성화
                        setMaskColor(0x60000000) // 반투명 마스크
                    }

                    decodeContinuous(callback)
                }.also { createdView ->
                    barcodeViewHolder.value = createdView
                }
            } catch (e: Exception) {
                android.util.Log.e("QRScanner", "카메라 초기화 실패", e)
                cameraError = "카메라를 초기화할 수 없습니다: ${e.message}"
                // 에러 시에도 빈 뷰라도 반환해야 함
                CompoundBarcodeView(context)
            }
        }
    )

    // 생명주기와 ZXing 뷰 동기화 (resume/pause)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val view = barcodeViewHolder.value ?: return@LifecycleEventObserver
            try {
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        view.resume()
                        android.util.Log.d("QRScanner", "카메라 시작됨")
                    }
                    Lifecycle.Event.ON_PAUSE -> {
                        view.pause()
                        android.util.Log.d("QRScanner", "카메라 정지됨")
                    }
                    else -> Unit
                }
            } catch (e: Exception) {
                android.util.Log.e("QRScanner", "카메라 생명주기 처리 실패", e)
                cameraError = "카메라 제어 중 오류가 발생했습니다: ${e.message}"
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try {
                barcodeViewHolder.value?.pause()
            } catch (e: Exception) {
                android.util.Log.e("QRScanner", "카메라 정리 실패", e)
            }
        }
    }
}

@Composable
private fun CameraPermissionRequest(
    onRequestPermission: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "카메라 권한이 필요합니다",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )

            Text(
                text = "QR 코드를 스캔하려면 카메라 접근 권한을 허용해주세요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = onRequestPermission) {
                Text("권한 요청")
            }
        }
    }
}

@Composable
private fun CameraPermissionRationale(
    onRequestPermission: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "카메라 권한이 필요한 이유",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )

            Text(
                text = "QR 코드 스캔 기능을 사용하려면 카메라 접근 권한이 필요합니다. 권한을 허용해주시면 QR 코드를 자동으로 인식하여 출입을 기록할 수 있습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = onRequestPermission) {
                Text("권한 허용")
            }
        }
    }
}

@Composable
private fun ScanResultCard(
    result: ScanResult,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (result) {
                is ScanResult.Success -> MaterialTheme.colorScheme.primaryContainer
                is ScanResult.Error -> MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (result) {
                    is ScanResult.Success -> Icons.Default.CheckCircle
                    is ScanResult.Error -> Icons.Default.Warning
                },
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = when (result) {
                    is ScanResult.Success -> MaterialTheme.colorScheme.onPrimaryContainer
                    is ScanResult.Error -> MaterialTheme.colorScheme.onErrorContainer
                }
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (result) {
                        is ScanResult.Success -> result.message
                        is ScanResult.Error -> result.message
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = when (result) {
                        is ScanResult.Success -> MaterialTheme.colorScheme.onPrimaryContainer
                        is ScanResult.Error -> MaterialTheme.colorScheme.onErrorContainer
                    }
                )

                if (result is ScanResult.Success) {
                    Text(
                        text = "${result.user.phoneNumber} • ${if (result.entryType == "ENTER") "입장" else "퇴장"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        }
    }
}

@Composable
private fun EntryLogCard(entry: EntryLog) {
    val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Entry Type Indicator
            Surface(
                color = if (entry.entryType == "ENTER") {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                } else {
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
                },
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (entry.entryType == "ENTER") "IN" else "OUT",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (entry.entryType == "ENTER") {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.secondary
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.userName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = dateFormat.format(Date(entry.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = timeFormat.format(Date(entry.timestamp)),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}