package com.example.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.network.ScanResult
import com.example.ui.theme.*
import kotlinx.coroutines.launch

private fun getLeaderboardTxt(results: List<ScanResult>, isEnglish: Boolean): String {
    val sb = java.lang.StringBuilder()
    sb.append(if (isEnglish) "--- Cloudflare Clean Edge Leaderboard ---\n" else "--- جدول رده‌بندی لبه‌های تمیز کلادفلر ---\n")
    sb.append(java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()) + "\n\n")
    sb.append(String.format("%-4s | %-18s | %-5s | %-4s | %-7s | %-12s | %-5s\n", "Rank", "IP", "Port", "Colo", "Ping", "Down Speed", "Score"))
    sb.append("-------------------------------------------------------------------------\n")
    results.forEachIndexed { idx, res ->
        sb.append(String.format("#%-3d | %-18s | %-5d | %-4s | %-5dms | %-8.2fMbps | %.1f\n", 
            idx + 1, res.ip, res.port, res.colo, res.medianLatency.toInt(), res.downloadSpeed, res.score))
    }
    return sb.toString()
}

@Composable
fun DashboardScreen(
    viewModel: ScannerViewModel,
    onNavigateToConfigs: () -> Unit
) {
    val isScanning by viewModel.isScanning.collectAsState()
    val scanState by viewModel.scanState.collectAsState()
    val terminalLogs by viewModel.terminalLogs.collectAsState()
    val liveResults by viewModel.liveResults.collectAsState()
    val isEnglish by viewModel.isEnglish.collectAsState()
    val scanTimestamp by viewModel.scanTimestamp.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // App Header Banner
        item {
            HeaderSection(viewModel = viewModel)
        }

        // HighTech Interactive HUD Visualizer
        item {
            ScannerInteractiveHUD(isScanning = isScanning, viewModel = viewModel)
        }

        // Scan Action Control Center
        item {
            val isPaused by viewModel.isPaused.collectAsState()
            ScanControlCard(
                isScanning = isScanning,
                isPaused = isPaused,
                scanState = scanState,
                viewModel = viewModel
            )
        }

        // Live Scrolling Terminal Console
        item {
            TerminalConsole(logs = terminalLogs, isEnglish = isEnglish)
        }

        // Top Scored Leaderboard Table Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberCardBorder.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = Loc.t("جدول رده‌بندی لبه‌های تمیز کشف شده", "Clean Edges Leaderboard", isEnglish),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (scanTimestamp.isNotEmpty()) {
                                Text(
                                    text = Loc.t("آخرین اسکن: $scanTimestamp", "Last Scan: $scanTimestamp", isEnglish),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFFF9800),
                                    fontFamily = MonospaceFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }

                        // Export Leaderboard Table as TXT File Share Trigger
                        if (liveResults.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    val txtData = getLeaderboardTxt(liveResults, isEnglish)
                                    clipboardManager.setText(AnnotatedString(txtData))
                                    Toast.makeText(context, Loc.t("جدول متنی در حافظه کپی شد", "Text table copied to clipboard", isEnglish), Toast.LENGTH_SHORT).show()

                                    try {
                                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(android.content.Intent.EXTRA_TEXT, txtData)
                                        }
                                        context.startActivity(android.content.Intent.createChooser(shareIntent, Loc.t("خروجی جدول رده‌بندی", "Export Leaderboard", isEnglish)))
                                    } catch (ex: Exception) {
                                        ex.printStackTrace()
                                    }
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Export TXT Table",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = Loc.t("کاندیداها بر طبق کمترین پکت لاس، پینگ و در نهایت بیشترین سرعت دانلود رتبه‌گیری می‌شوند.", "Candidates are ranked based on lowest packet loss, latency, and highest download speed.", isEnglish),
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedSlate,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )

                    if (liveResults.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudQueue,
                                    contentDescription = "",
                                    tint = MutedSlate.copy(alpha = 0.5f),
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = if (isScanning) Loc.t("در حال یافتن بهترین آی‌پی در میان رنج‌های لبه...", "Discovering top performance IPs across edge subnets...", isEnglish) else Loc.t("جدول کاندیداها خالی است. اسکن را آغاز کنید.", "Candidacy array is empty. Warm up edge scanners to fill.", isEnglish),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MutedSlate,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            liveResults.forEachIndexed { index, item ->
                                CandidacyItem(
                                    rank = index + 1,
                                    res = item,
                                    onSave = { viewModel.saveIp(item) },
                                    onCopy = {
                                        clipboardManager.setText(AnnotatedString(item.ip))
                                        Toast.makeText(context, Loc.t("آی‌پی کپی شد", "IP copied to clipboard", isEnglish), Toast.LENGTH_SHORT).show()
                                    },
                                    onRebuild = {
                                        viewModel.rebuildConfigs(item.ip, item.port)
                                        Toast.makeText(context, Loc.t("تمام کانفیگ‌ها با این آی‌پی بازسازی شد!", "All configurations rebuilt with this IP!", isEnglish), Toast.LENGTH_SHORT).show()
                                        onNavigateToConfigs()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HeaderSection(viewModel: ScannerViewModel) {
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()
    val isEnglish by viewModel.isEnglish.collectAsState()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyberCardBorder.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CenterFocusStrong,
                        contentDescription = "Radar Icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column {
                    Text(
                        text = Loc.t("اسکنر هوشمند شبکه", "Smart Network Scanner", isEnglish),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "CLOUDFLARE SCANNER",
                        fontFamily = MonospaceFontFamily,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Language Switch Button
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                        .border(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                        .size(32.dp)
                        .clickable { viewModel.toggleLanguage() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isEnglish) "FA" else "EN",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Theme switch button
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                        .border(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                        .size(32.dp)
                        .clickable { viewModel.toggleTheme() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "Toggle Theme Icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ScanControlCard(
    isScanning: Boolean,
    isPaused: Boolean,
    scanState: ScanState,
    viewModel: ScannerViewModel
) {
    val ignoreCert by viewModel.ignoreCert.collectAsState()
    val portsConfig by viewModel.portsConfig.collectAsState()
    val isEnglish by viewModel.isEnglish.collectAsState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isScanning) Loc.t("اسکنر در حال کار است...", "Scanner is running...", isEnglish)
                           else if (isPaused) Loc.t("اسکنر متوقف شده است", "Scanner is paused", isEnglish)
                           else Loc.t("شروع اسکن هوشمند شبکه", "Start Smart Scan", isEnglish),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!isScanning && !isPaused) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Checkbox(
                            checked = ignoreCert,
                            onCheckedChange = { viewModel.ignoreCert.value = it },
                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                        )
                        Text(
                            text = Loc.t("بای‌پس SSL", "Bypass SSL", isEnglish),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Real-time progress visualizer
            AnimatedContent(
                targetState = scanState,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "ScanStateDetails"
            ) { state ->
                when (state) {
                    is ScanState.Idle -> {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = Loc.t("پورت‌های هدف: $portsConfig", "Target Ports: $portsConfig", isEnglish),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = MonospaceFontFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = Loc.t("ابزار خودکار ارزیابی رنج‌های آی‌پی کلادفلر و بازسازی کانفیگ‌ها.", "Automated tool for scoring Cloudflare IPs and rebuilding configs.", isEnglish),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    is ScanState.Stage1PortScan -> {
                        val progress = if (state.total > 0) state.checked.toFloat() / state.total else 0f
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = Loc.t("مرحله ۱: جستجوی پورت‌های فعال", "Stage 1: Active Port Scanning", isEnglish),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${state.checked}/${state.total}",
                                    fontFamily = MonospaceFontFamily,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Text(
                                text = Loc.t("آی‌پی فعال کشف شده: ${state.foundActive}", "Active IPs discovered: ${state.foundActive}", isEnglish),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    is ScanState.Stage2Benchmark -> {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = Loc.t("مرحله ۲: عیارسنجی پهنای باند و پایداری", "Stage 2: Bandwidth & Stability Test", isEnglish),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = WarningAmber
                                )
                                Text(
                                    text = "${state.indexed}/${state.total}",
                                    fontFamily = MonospaceFontFamily,
                                    color = WarningAmber,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            LinearProgressIndicator(
                                progress = { if (state.total > 0) state.indexed.toFloat() / state.total else 0f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = WarningAmber,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            if (state.currentIp.isNotEmpty()) {
                                Text(
                                    text = Loc.t("آی‌پی در حال آزمایش: ${state.currentIp}", "Active test IP: ${state.currentIp}", isEnglish),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = MonospaceFontFamily,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    is ScanState.Finished -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Done",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = Loc.t("اسکن با موفقیت به پایان رسید!", "Scanning finished successfully!", isEnglish),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Action Buttons (Dynamic Pause/Resume controls)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isScanning) {
                    // 1. Active Scan Running -> Show Pause & Stop buttons
                    Button(
                        onClick = { viewModel.pauseScanning() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = WarningAmber),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Pause, contentDescription = "", tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(Loc.t("توقف موقت", "Pause", isEnglish), color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { viewModel.stopScanning() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Stop, contentDescription = "", tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(Loc.t("لغو اسکن", "Stop", isEnglish), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                } else if (isPaused) {
                    // 2. Scan is Paused -> Show Resume & Reset/Clear buttons
                    Button(
                        onClick = { viewModel.resumeScanning() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "", tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(Loc.t("ادامه‌ی اسکن", "Resume", isEnglish), color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { viewModel.stopScanning() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "", tint = MaterialTheme.colorScheme.onSecondary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(Loc.t("انصراف و ریست", "Reset", isEnglish), color = MaterialTheme.colorScheme.onSecondary, fontWeight = FontWeight.Bold)
                    }
                } else {
                    // 3. Fully Idle Standby -> Show standard Start Scan button
                    Button(
                        onClick = { viewModel.startScanning() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "", tint = MaterialTheme.colorScheme.onPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(Loc.t("شروع اسکن شبکه (Start)", "Start Scan", isEnglish), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun TerminalConsole(logs: List<TerminalLog>, isEnglish: Boolean) {
    val listState = rememberLazyListState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    // Auto-scroll to lowest log item
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = TerminalDark)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Title
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = Loc.t("ترمینال مانیتورینگ اسکن", "TERMINAL SCANNER MONITOR", isEnglish),
                    fontFamily = MonospaceFontFamily,
                    fontSize = 11.sp,
                    color = if (MaterialTheme.colorScheme.background == LightBackground) ElectricBlue else NeonCyan,
                    fontWeight = FontWeight.Bold
                )
                
                // Orange Circular Icon & Copy Label Trigger
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clickable {
                            val logsText = logs.joinToString("\n") { "[${it.timestamp}] ${if (isEnglish) it.enText else it.faText}" }
                            clipboardManager.setText(AnnotatedString(logsText))
                            Toast.makeText(context, Loc.t("تمامی لاگ‌های ترمینال کپی شدند!", "All terminal logs copied to clipboard!", isEnglish), Toast.LENGTH_SHORT).show()
                        }
                        .background(Color(0xFFE65100).copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = Loc.t("کپی لاگ", "Copy Logs", isEnglish),
                        fontSize = 9.sp,
                        color = Color(0xFFFF9800),
                        fontWeight = FontWeight.Bold,
                        fontFamily = MonospaceFontFamily
                    )
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(Color(0xFFFF9800), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy logs",
                            tint = Color.White,
                            modifier = Modifier.size(9.dp)
                        )
                    }
                }
            }

            Divider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), modifier = Modifier.padding(bottom = 8.dp))

            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (logs.isEmpty()) {
                    item {
                        Text(
                            text = Loc.t("در انتظار دستور... دکمه شروع اسکن را فشار دهید.", "Awaiting instructions... Press 'Start Scan' to begin.", isEnglish),
                            fontFamily = MonospaceFontFamily,
                            fontSize = 11.sp,
                            color = MutedSlate
                        )
                    }
                } else {
                    itemsIndexed(logs) { _, log ->
                        val text = if (isEnglish) log.enText else log.faText
                        val color = when {
                            text.contains("🟢") || text.contains("Success") || text.contains("موفق") || text.contains("نشده") -> if (MaterialTheme.colorScheme.background == LightBackground) Color(0xFF15803D) else NeonCyan
                            text.contains("❌") || text.contains("خطا") || text.contains("Rejected") || text.contains("رد شد") -> DangerRed
                            text.contains("⚙️") || text.contains("Analyzing") || text.contains("ارزیابی") || text.contains("Profiling") || text.contains("📊") -> WarningAmber
                            else -> MutedSlate
                        }
                        Text(
                            text = "[${log.timestamp}] $text",
                            fontFamily = MonospaceFontFamily,
                            fontSize = 12.sp,
                            color = color,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}



@Composable
fun CandidacyItem(
    rank: Int,
    res: ScanResult,
    onSave: () -> Unit,
    onCopy: () -> Unit,
    onRebuild: () -> Unit
) {
    var saved by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Rank Badge
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(
                        color = when (rank) {
                            1 -> WarningAmber
                            2 -> MutedSlate
                            3 -> ElectricPurple.copy(alpha = 0.5f)
                            else -> CyberCardBorder
                        },
                        shape = RoundedCornerShape(14.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "#$rank",
                    fontFamily = MonospaceFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = if (rank <= 2) CyberBlack else MaterialTheme.colorScheme.onSurface
                )
            }

            // Central IP Metrics Details
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = res.ip,
                        fontFamily = MonospaceFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = ":${res.port}",
                        fontFamily = MonospaceFontFamily,
                        fontSize = 12.sp,
                        color = MutedSlate
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val textGreen = if (MaterialTheme.colorScheme.background == LightBackground) Color(0xFF16A34A) else Color(0xFF34D399)
                    val textAmber = if (MaterialTheme.colorScheme.background == LightBackground) Color(0xFFD97706) else WarningAmber

                    Text(
                        text = "Colo: ${res.colo}",
                        fontFamily = MonospaceFontFamily,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Ping: ${res.medianLatency.toInt()}ms",
                        fontFamily = MonospaceFontFamily,
                        fontSize = 11.sp,
                        color = if (res.medianLatency <= 120) textGreen else textAmber
                    )
                    Text(
                        text = "↓ ${res.downloadSpeed} Mbps",
                        fontFamily = MonospaceFontFamily,
                        fontSize = 11.sp,
                        color = textGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Quick Score & Actions Flow
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Score: ${res.score}",
                        fontFamily = MonospaceFontFamily,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = onCopy,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy IP",
                            tint = MutedSlate,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            onSave()
                            saved = true
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (saved) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Save to favorites",
                            tint = if (saved) WarningAmber else MutedSlate,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onRebuild,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Autorenew,
                            contentDescription = "Apply to base configs",
                            tint = SuccessGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ScannerInteractiveHUD(isScanning: Boolean, viewModel: ScannerViewModel) {
    val isEnglish by viewModel.isEnglish.collectAsState()
    val context = LocalContext.current
    val logoBitmap = remember(context) {
        try {
            context.assets.open("ic_cf_logo.webp").use { inputStream ->
                android.graphics.BitmapFactory.decodeStream(inputStream)?.asImageBitmap()
            }
        } catch (e: Exception) {
            null
        }
    }
    val infiniteTransition = rememberInfiniteTransition(label = "ScanSweep")
    val sweepPosition by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SweepPosition"
    )
    val logoRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "LogoRotation"
    )
    val logoScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LogoScale"
    )
    val logoAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LogoAlpha"
    )

    val gridColorTheme = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Holographic Background Grid
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gridSpacing = 16.dp.toPx()
            val gridColor = gridColorTheme
            var y = 0f
            while (y < size.height) {
                drawLine(gridColor, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = 1f)
                y += gridSpacing
            }
            var x = 0f
            while (x < size.width) {
                drawLine(gridColor, start = Offset(x, 0f), end = Offset(x, size.height), strokeWidth = 1f)
                x += gridSpacing
            }

            // High-Tech Corner Brackets
            val padding = 16.dp.toPx()
            val length = 12.dp.toPx()
            val bracketColor = if (isScanning) SuccessGreen else MutedSlate.copy(alpha = 0.4f)
            val stroke = 2.dp.toPx()

            // Top-Left
            drawLine(bracketColor, start = Offset(padding, padding), end = Offset(padding + length, padding), strokeWidth = stroke)
            drawLine(bracketColor, start = Offset(padding, padding), end = Offset(padding, padding + length), strokeWidth = stroke)

            // Top-Right
            drawLine(bracketColor, start = Offset(size.width - padding, padding), end = Offset(size.width - padding - length, padding), strokeWidth = stroke)
            drawLine(bracketColor, start = Offset(size.width - padding, padding), end = Offset(size.width - padding, padding + length), strokeWidth = stroke)

            // Bottom-Left
            drawLine(bracketColor, start = Offset(padding, size.height - padding), end = Offset(padding + length, size.height - padding), strokeWidth = stroke)
            drawLine(bracketColor, start = Offset(padding, size.height - padding), end = Offset(padding, size.height - padding - length), strokeWidth = stroke)

            // Bottom-Right
            drawLine(bracketColor, start = Offset(size.width - padding, size.height - padding), end = Offset(size.width - padding - length, size.height - padding), strokeWidth = stroke)
            drawLine(bracketColor, start = Offset(size.width - padding, size.height - padding), end = Offset(size.width - padding, size.height - padding - length), strokeWidth = stroke)

            // Dynamic Scanning Line sweep with radial glow
            if (isScanning) {
                val lineY = padding + (size.height - padding * 2) * sweepPosition
                drawLine(
                    color = SuccessGreen.copy(alpha = 0.8f),
                    start = Offset(padding, lineY),
                    end = Offset(size.width - padding, lineY),
                    strokeWidth = 2.dp.toPx()
                )
                
                // Sweep shader drop
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(SuccessGreen.copy(alpha = 0.12f), Color.Transparent),
                        startY = lineY - 24.dp.toPx(),
                        endY = lineY
                    ),
                    topLeft = Offset(padding, lineY - 24.dp.toPx()),
                    size = Size(size.width - padding * 2, 24.dp.toPx())
                )
            }
        }

        // Center spinning / breathing logo representing the scanner animation using modern asset loading with 100% safety fallback!
        if (logoBitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = logoBitmap,
                contentDescription = "CF Scanner App Logo Animation",
                modifier = Modifier
                    .size(72.dp)
                    .align(Alignment.Center)
                    .graphicsLayer {
                        rotationZ = if (isScanning) logoRotation else 0f
                        scaleX = if (isScanning) logoScale else 1.0f
                        scaleY = if (isScanning) logoScale else 1.0f
                        alpha = if (isScanning) logoAlpha else 1.0f
                    }
            )
        } else {
            androidx.compose.material3.Icon(
                imageVector = androidx.compose.material.icons.Icons.Default.CloudQueue,
                contentDescription = "CF Scanner Fallback Logo",
                tint = if (isScanning) SuccessGreen else MutedSlate,
                modifier = Modifier
                    .size(72.dp)
                    .align(Alignment.Center)
                    .graphicsLayer {
                        rotationZ = if (isScanning) logoRotation else 0f
                        scaleX = if (isScanning) logoScale else 1.0f
                        scaleY = if (isScanning) logoScale else 1.0f
                        alpha = if (isScanning) logoAlpha else 1.0f
                    }
            )
        }

        val hudGreen = if (MaterialTheme.colorScheme.background == LightBackground) ElectricBlue else SuccessGreen

        // Live Mode Pill
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(20.dp))
                .border(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        color = if (isScanning) hudGreen else Color.Gray,
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
            )
            Text(
                text = if (isScanning) Loc.t("اسکن فعال", "Scanning", isEnglish) else Loc.t("آماده به کار", "Standby", isEnglish),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Tech Info Panel (HUD details)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                .border(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = "STATUS: " + (if (isScanning) "ANALYZING..." else "STANDBY"),
                fontFamily = MonospaceFontFamily,
                fontSize = 9.sp,
                color = if (isScanning) hudGreen else MutedSlate,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "BOUNDS: [SSL_LOCK=BYPASS]",
                fontFamily = MonospaceFontFamily,
                fontSize = 8.sp,
                color = MutedSlate
            )
            Text(
                text = "DPI_LOCK: 300_HQ",
                fontFamily = MonospaceFontFamily,
                fontSize = 8.sp,
                color = MutedSlate
            )
        }
    }
}
