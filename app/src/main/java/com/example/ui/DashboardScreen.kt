package com.example.ui

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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.network.ScanResult
import com.example.ui.theme.*
import kotlinx.coroutines.launch

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
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // App Header Banner
        HeaderSection(viewModel = viewModel)

        // HighTech Interactive HUD Visualizer
        ScannerInteractiveHUD(isScanning = isScanning, viewModel = viewModel)

        // Scan Action Control Center
        ScanControlCard(
            isScanning = isScanning,
            scanState = scanState,
            viewModel = viewModel
        )

        // Live Scrolling Terminal Console
        TerminalConsole(logs = terminalLogs, isEnglish = isEnglish)

        // Top Scored Candidacy List
        CandidacyList(
            modifier = Modifier.weight(1f),
            results = liveResults,
            isScanning = isScanning,
            isEnglish = isEnglish,
            onSave = { viewModel.saveIp(it) },
            onCopy = { ip ->
                clipboardManager.setText(AnnotatedString(ip))
            },
            onRebuild = { ip, port ->
                viewModel.rebuildConfigs(ip, port)
                onNavigateToConfigs()
            }
        )
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
                    text = if (isScanning) Loc.t("اسکنر در حال کار است...", "Scanner is running...", isEnglish) else Loc.t("شروع اسکن هوشمند شبکه", "Start Smart Scan", isEnglish),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!isScanning) {
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

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!isScanning) {
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
                } else {
                    Button(
                        onClick = { viewModel.stopScanning() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Stop, contentDescription = "", tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(Loc.t("توقف اسکن (Stop)", "Stop Scan", isEnglish), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun TerminalConsole(logs: List<TerminalLog>, isEnglish: Boolean) {
    val listState = rememberLazyListState()

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
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(if (MaterialTheme.colorScheme.background == LightBackground) ElectricBlue else NeonCyan, RoundedCornerShape(4.dp))
                )
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
fun CandidacyList(
    modifier: Modifier = Modifier,
    results: List<ScanResult>,
    isScanning: Boolean,
    isEnglish: Boolean,
    onSave: (ScanResult) -> Unit,
    onCopy: (String) -> Unit,
    onRebuild: (String, Int) -> Unit
) {
    Card(
        modifier = modifier
            .border(1.dp, CyberCardBorder.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = Loc.t("جدول رده‌بندی لبه‌های تمیز کشف شده", "Clean Edges Leaderboard", isEnglish),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = Loc.t("کاندیداها بر طبق کمترین پکت لاس، پینگ و در نهایت بیشترین سرعت دانلود رتبه‌گیری می‌شوند.", "Candidates are ranked based on lowest packet loss, latency, and highest download speed.", isEnglish),
                style = MaterialTheme.typography.bodySmall,
                color = MutedSlate,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (results.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
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
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = if (isScanning) Loc.t("در حال یافتن بهترین آی‌پی در میان رنج‌های لبه...", "Discovering top performance IPs across edge subnets...", isEnglish) else Loc.t("جدول کاندیداها خالی است. اسکن را آغاز کنید.", "Candidacy array is empty. Warm up edge scanners to fill.", isEnglish),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedSlate,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    itemsIndexed(results) { index, item ->
                        CandidacyItem(
                            rank = index + 1,
                            res = item,
                            onSave = { onSave(item) },
                            onCopy = { onCopy(item.ip) },
                            onRebuild = { onRebuild(item.ip, item.port) }
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
    val radarRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RadarRotation"
    )
    val blinkAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BlinkAlpha"
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

            // Radar elements (Concentric rings)
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            val maxRadarRadius = size.height * 0.45f
            
            val radarThemeColor = if (isScanning) SuccessGreen else MutedSlate.copy(alpha = 0.3f)

            // 3 Concentric rings
            drawCircle(
                color = radarThemeColor.copy(alpha = 0.05f),
                radius = maxRadarRadius * 0.35f,
                center = Offset(centerX, centerY),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
            )
            drawCircle(
                color = radarThemeColor.copy(alpha = 0.08f),
                radius = maxRadarRadius * 0.7f,
                center = Offset(centerX, centerY),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
            )
            drawCircle(
                color = radarThemeColor.copy(alpha = 0.12f),
                radius = maxRadarRadius,
                center = Offset(centerX, centerY),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
            )

            // Radar crosshairs (Horizontal and vertical line segments)
            drawLine(
                color = radarThemeColor.copy(alpha = 0.1f),
                start = Offset(centerX - maxRadarRadius - 10f, centerY),
                end = Offset(centerX + maxRadarRadius + 10f, centerY),
                strokeWidth = 1f
            )
            drawLine(
                color = radarThemeColor.copy(alpha = 0.1f),
                start = Offset(centerX, centerY - maxRadarRadius - 10f),
                end = Offset(centerX, centerY + maxRadarRadius + 10f),
                strokeWidth = 1f
            )

            // Dynamic rotating radar beam
            if (isScanning) {
                val angleRad = Math.toRadians(radarRotation.toDouble())
                val endX = centerX + maxRadarRadius * Math.cos(angleRad).toFloat()
                val endY = centerY + maxRadarRadius * Math.sin(angleRad).toFloat()

                // Radar beam line
                drawLine(
                    color = SuccessGreen.copy(alpha = 0.6f),
                    start = Offset(centerX, centerY),
                    end = Offset(endX, endY),
                    strokeWidth = 1.5.dp.toPx()
                )

                // Pulse core
                drawCircle(
                    color = SuccessGreen.copy(alpha = 0.15f * blinkAlpha),
                    radius = maxRadarRadius,
                    center = Offset(centerX, centerY)
                )
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
