package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ScanRange
import com.example.ui.theme.*

@Composable
fun SettingsScreen(viewModel: ScannerViewModel) {
    val scanRanges by viewModel.scanRanges.collectAsState()
    val portsConfig by viewModel.portsConfig.collectAsState()
    val timeoutMs by viewModel.timeoutMs.collectAsState()
    val benchDuration by viewModel.benchDurationSeconds.collectAsState()
    val jitterSamples by viewModel.jitterSamples.collectAsState()
    val maxLoss by viewModel.maxLossRatio.collectAsState()
    val samplesPerCidr by viewModel.samplePerCidr.collectAsState()
    val isEnglish by viewModel.isEnglish.collectAsState()

    var customCidrInput by remember { mutableStateOf("") }
    val context = LocalContext.current

    val isDarkTheme by viewModel.isDarkTheme.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Toolbar Title
        item {
            Column {
                Text(
                    text = Loc.t("تنظیمات اسکنر", "Scan Settings", isEnglish),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = Loc.t("پیکربندی پارامترهای شبکه، پاسخ‌ها و رنج‌های آی‌پی هدف", "Configure network timeout, samples, and target IPs", isEnglish),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Divider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
        }

        // 0. Theme settings selection card & Language toggle card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    // Theme Switch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                    .border(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                                    contentDescription = "Theme Icon",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = Loc.t("حالت شب (Night Mode)", "Night Theme", isEnglish),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isDarkTheme) Loc.t("ظاهر تیره فعال است", "Dark appearance active", isEnglish) else Loc.t("ظاهر روشن فعال است", "Light appearance active", isEnglish),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = isDarkTheme,
                            onCheckedChange = { viewModel.toggleTheme() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }

                    Divider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 16.dp))

                    // Language Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                    .border(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = "Language Icon",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = Loc.t("زبان برنامه (Application Language)", "Application Language", isEnglish),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isEnglish) "English selected" else "زبان فارسی فعال است",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = isEnglish,
                            onCheckedChange = { viewModel.toggleLanguage() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }

        // 1. Core Port & Socket variables card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = Loc.t("پارامترهای فنی اسکنر (Scanner Parameters)", "Technical Scanner Parameters", isEnglish),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )

                    // Target Ports text input
                    OutlinedTextField(
                        value = portsConfig,
                        onValueChange = { viewModel.portsConfig.value = it },
                        label = { Text(Loc.t("پورت‌های هدف اسکن (مشرک با کاما)", "Target Ports (comma separated)", isEnglish)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        singleLine = true
                    )

                    // Connect timeout field
                    OutlinedTextField(
                        value = timeoutMs.toString(),
                        onValueChange = { newValue ->
                            newValue.toIntOrNull()?.let { viewModel.timeoutMs.value = it }
                        },
                        label = { Text(Loc.t("زمان انتظار پاسخ پورت (Timeout - میلی‌ثانیه)", "Socket Connect Timeout (ms)", isEnglish)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        singleLine = true
                    )

                    // Bench duration field
                    OutlinedTextField(
                        value = benchDuration.toString(),
                        onValueChange = { newValue ->
                            newValue.toDoubleOrNull()?.let { viewModel.benchDurationSeconds.value = it }
                        },
                        label = { Text(Loc.t("حداکثر مدت تست سرعت هر آی‌پی (ثانیه)", "Max Speed Test Period per IP (s)", isEnglish)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        singleLine = true
                    )

                    // Jitter count
                    OutlinedTextField(
                        value = jitterSamples.toString(),
                        onValueChange = { newValue ->
                            newValue.toIntOrNull()?.let { viewModel.jitterSamples.value = it }
                        },
                        label = { Text(Loc.t("تعداد درخواست پینگ جیتر (Samples)", "Latency Jitter Samples count", isEnglish)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        singleLine = true
                    )

                    // Sampling count per CIDR
                    OutlinedTextField(
                        value = samplesPerCidr.toString(),
                        onValueChange = { newValue ->
                            newValue.toIntOrNull()?.let { viewModel.samplePerCidr.value = it }
                        },
                        label = { Text(Loc.t("تعداد آی‌پی برای نمونه‌برداری از هر رنج CIDR", "Sampled IPs counts per CIDR Subnet", isEnglish)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        singleLine = true
                    )
                }
            }
        }

        // 2. Custom Subnets Insertion Frame
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = Loc.t("افزودن ساب‌نت سفارشی (Add Custom IP Subnet)", "Add Custom IP Subnet", isEnglish),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = customCidrInput,
                            onValueChange = { customCidrInput = it },
                            placeholder = { Text(Loc.t("مثال: 104.16.0.0/24 یا 104.16.0.1-50", "e.g., 104.16.0.0/24 or 104.16.0.1-50", isEnglish)) },
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                focusedLabelColor = MaterialTheme.colorScheme.primary
                            ),
                            singleLine = true
                        )

                        Button(
                            onClick = {
                                val input = customCidrInput.trim()
                                if (input.isEmpty()) return@Button
                                if (!input.contains("/") && !input.contains("-") && input.split(".").size < 2) {
                                    Toast.makeText(context, Loc.t("فرمت نامعتبر است! از ساب‌نت (x.x.x.x/24)، رنج (x.x.x.x-y) یا آی‌پی استفاده کنید.", "Invalid format! Specify subnets (x.x.x.x/24), ranges (x.x.x.x-y), or clean IPs.", isEnglish), Toast.LENGTH_LONG).show()
                                    return@Button
                                }
                                viewModel.addCustomRange(input)
                                customCidrInput = ""
                                Toast.makeText(context, Loc.t("ساب‌نت یا رنج سفارشی افزوده شد!", "Custom subnet or range registered successfully!", isEnglish), Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            modifier = Modifier.height(54.dp)
                        ) {
                            Text(Loc.t("افزودن", "Add", isEnglish), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Subnets Heading
        item {
            Text(
                text = Loc.t("لیست رنج‌های ساب‌نت کلادفلر و اپراتورها", "Registered Cloudflare Subnet Collections", isEnglish),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = Loc.t("می‌توانید رنج‌های شبکه کلادفلر را غیرفعال یا فعال کنید تا گام اسکن کوتاه‌تر شود.", "Toggle subnets on or off to narrow down target scanning pools and optimize scanning time.", isEnglish),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 3. Grid of active ranges itemized
        if (scanRanges.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        } else {
            items(scanRanges, key = { it.cidr }) { range ->
                RangeItem(
                    range = range,
                    isEnglish = isEnglish,
                    onCheckedChange = { isChecked ->
                        viewModel.toggleRangeSelection(range, isChecked)
                    },
                    onDelete = {
                        viewModel.deleteRange(range)
                    }
                )
            }
        }
    }
}

@Composable
fun RangeItem(
    range: ScanRange,
    isEnglish: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(
            containerColor = if (range.isEnabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Icon tag
                Icon(
                    imageVector = if (range.isCustom) Icons.Default.AddLocationAlt else Icons.Default.Dns,
                    contentDescription = "",
                    tint = if (range.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(16.dp)
                )

                Column {
                    Text(
                        text = range.cidr,
                        fontFamily = MonospaceFontFamily,
                        fontSize = 14.sp,
                        color = if (range.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (range.isCustom) Loc.t("رنج سفارشی کاربر", "User Custom Subnet Range", isEnglish) else Loc.t("رنج عمومی رسمی کلادفلر", "Official Cloudflare Subnet", isEnglish),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Switch(
                    checked = range.isEnabled,
                    onCheckedChange = onCheckedChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                if (range.isCustom) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete range",
                            tint = DangerRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
