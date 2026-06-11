package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SavedIp
import com.example.ui.theme.*

@Composable
fun SavedIpsScreen(
    viewModel: ScannerViewModel,
    onNavigateToConfigs: () -> Unit
) {
    val savedList by viewModel.savedIps.collectAsState()
    val isEnglish by viewModel.isEnglish.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var showClearDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Toolbar header with clear action
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = Loc.t("آی‌پی‌های نشان شده", "Starred IP Addresses", isEnglish),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan
                )
                Text(
                    text = Loc.t("لیست لبه‌های طلایی کلادفلر ذخیره شده شما", "Your saved premium Cloudflare edge nodes", isEnglish),
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedSlate
                )
            }
            if (savedList.isNotEmpty()) {
                IconButton(onClick = { showClearDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Clear All Saved",
                        tint = DangerRed
                    )
                }
            }
        }

        Divider(color = CyberCardBorder.copy(alpha = 0.5f))

        // Manual IP Insertion Form Card
        var manualIp by remember { mutableStateOf("") }
        var manualPort by remember { mutableStateOf("443") }
        var manualColo by remember { mutableStateOf("MANUAL") }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = Loc.t("ثبت دستی آی‌پی تمیز", "Add Manual Clean IP", isEnglish),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = manualIp,
                        onValueChange = { manualIp = it.trim() },
                        label = { Text(Loc.t("آی‌پی (IP)", "IP Address", isEnglish), fontSize = 11.sp) },
                        modifier = Modifier.weight(1.5f),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = MonospaceFontFamily, fontSize = 13.sp)
                    )
                    OutlinedTextField(
                        value = manualPort,
                        onValueChange = { manualPort = it.trim() },
                        label = { Text(Loc.t("پورت", "Port", isEnglish), fontSize = 11.sp) },
                        modifier = Modifier.weight(0.8f),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = MonospaceFontFamily, fontSize = 13.sp)
                    )
                    OutlinedTextField(
                        value = manualColo,
                        onValueChange = { manualColo = it.trim() },
                        label = { Text(Loc.t("دیتاسنتر", "Colo", isEnglish), fontSize = 11.sp) },
                        modifier = Modifier.weight(0.9f),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = MonospaceFontFamily, fontSize = 13.sp)
                    )
                }

                Button(
                    onClick = {
                        if (manualIp.isNotEmpty()) {
                            val portInt = manualPort.toIntOrNull() ?: 443
                            viewModel.saveIp(
                                SavedIp(
                                    ip = manualIp,
                                    port = portInt,
                                    colo = manualColo,
                                    country = "Manual",
                                    ping = 100.0,
                                    dlSpeed = 10.0,
                                    ulSpeed = 1.0,
                                    jitter = 5.0,
                                    score = 100.0,
                                    timestamp = System.currentTimeMillis()
                                )
                            )
                            manualIp = ""
                            Toast.makeText(context, Loc.t("آی‌پی دستی ثبت شد و کانفیگ‌ها بازسازی شدند!", "Manual IP added and configs updated!", isEnglish), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, Loc.t("لطفاً آدرس آی‌پی معتبر وارد کنید", "Please enter a valid IP address", isEnglish), Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add IP", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(Loc.t("ذخیره و بازسازی کانفیگ‌ها", "Save & Rebuild Configs", isEnglish), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        if (savedList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.StarOutline,
                        contentDescription = "",
                        tint = MutedSlate.copy(alpha = 0.3f),
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        text = Loc.t("هیچ آی‌پی طلایی ذخیره نشده است!", "No starred IPs found!", isEnglish),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = Loc.t("پس از دریافت خروجی اسکن، روی آیکون ستاره در نتایج کلیک کنید تا لبه مورد نظر در اینجا ذخیره شود.", "After running a scan, click the star icon on any result item to save high-performance edge nodes here.", isEnglish),
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedSlate,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(savedList, key = { it.ip }) { saved ->
                    SavedIpItem(
                        saved = saved,
                        isEnglish = isEnglish,
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(saved.ip))
                            Toast.makeText(context, Loc.t("آی‌پی کپی شد", "IP copied to clipboard", isEnglish), Toast.LENGTH_SHORT).show()
                        },
                        onDelete = {
                            viewModel.deleteSavedIp(saved)
                            Toast.makeText(context, Loc.t("آی‌پی ستاره دار حذف شد", "Starred IP removed", isEnglish), Toast.LENGTH_SHORT).show()
                        },
                        onApply = {
                            viewModel.rebuildConfigs(saved.ip, saved.port)
                            Toast.makeText(context, Loc.t("بیس تمام کانفیگ‌ها با این آی‌پی بازسازی شد!", "All base configurations rebuilt with this IP!", isEnglish), Toast.LENGTH_SHORT).show()
                            onNavigateToConfigs()
                        }
                    )
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(Loc.t("پاکسازی کامل", "Clear All Starred", isEnglish), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = { Text(Loc.t("آیا مایلید تمام آی‌پی‌های ذخیره شده در بخش نشان‌شده‌ها را به کلی حذف کنید؟ این عمل غیرقابل بازگشت است.", "Are you sure you want to permanently clear all starred IP nodes? This action cannot be undone.", isEnglish)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearSavedIps()
                        showClearDialog = false
                    }
                ) {
                    Text(Loc.t("بله، حذف کن", "Yes, delete", isEnglish), color = DangerRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(Loc.t("خیر", "No", isEnglish), color = MaterialTheme.colorScheme.primary)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
fun SavedIpItem(
    saved: SavedIp,
    isEnglish: Boolean,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onApply: () -> Unit
) {
    val textGreen = if (MaterialTheme.colorScheme.background == LightBackground) Color(0xFF16A34A) else Color(0xFF34D399)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Starred",
                        tint = WarningAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = saved.ip,
                        fontFamily = MonospaceFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = ":${saved.port}",
                        fontFamily = MonospaceFontFamily,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Score Badge
                Box(
                    modifier = Modifier
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Score: ${saved.score}",
                        fontFamily = MonospaceFontFamily,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Specs details in monochrome layout grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Colo: ${saved.colo} (${saved.country})",
                        fontFamily = MonospaceFontFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = Loc.t("Ping (تأخیر): ${saved.ping.toInt()}ms (Jitter: ${saved.jitter.toInt()}ms)", "Ping: ${saved.ping.toInt()}ms (Jitter: ${saved.jitter.toInt()}ms)", isEnglish),
                        fontFamily = MonospaceFontFamily,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Download: ${saved.dlSpeed} Mbps",
                        fontFamily = MonospaceFontFamily,
                        fontSize = 12.sp,
                        color = textGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Upload: ${saved.ulSpeed} Mbps",
                        fontFamily = MonospaceFontFamily,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Divider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))

            // Lower Bar containing action triggers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onApply,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Autorenew,
                        contentDescription = "",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = Loc.t("اعمال روی کانفیگ‌ها", "Apply to Configs", isEnglish),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = onCopy,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy IP",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Unstar",
                            tint = DangerRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
