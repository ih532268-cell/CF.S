package com.example.ui

import android.widget.Toast
import androidx.compose.animation.*
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ConfigBase
import com.example.ui.theme.*

@Composable
fun ConfigManagerScreen(viewModel: ScannerViewModel) {
    val configsList by viewModel.configBases.collectAsState()
    val isEnglish by viewModel.isEnglish.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    var nameInput by remember { mutableStateOf("") }
    var uriInput by remember { mutableStateOf("") }
    var isFormExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Toolbar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = Loc.t("بازسازی کانفیگ V2Ray", "Config Rebuilder", isEnglish),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = Loc.t("کانفیگ اولیه را وارد کنید تا با آی‌پی‌های تمیز بازسازی شود", "Input base config links to rebuild with clean IPs", isEnglish),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = { isFormExpanded = !isFormExpanded },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isFormExpanded) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Icon(
                    imageVector = if (isFormExpanded) Icons.Default.Close else Icons.Default.Add,
                    contentDescription = "",
                    tint = if (isFormExpanded) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isFormExpanded) Loc.t("بستن", "Close", isEnglish) else Loc.t("افزودن بیس", "Add Base", isEnglish),
                    fontSize = 12.sp,
                    color = if (isFormExpanded) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        Divider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))

        // Expandable config creation form
        AnimatedVisibility(
            visible = isFormExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f), RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = Loc.t("افزودن لینک کانفیگ جدید پایه", "Add New Base Config URL", isEnglish),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text(Loc.t("نام برای سرور (مثال: همراه اول)", "Server Name (e.g., MCI Connection)", isEnglish)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = uriInput,
                        onValueChange = { uriInput = it },
                        label = { Text(Loc.t("لینک کانفیگ (Vless://, Vmess://, Trojan://)", "Config URL (vless://, vmess://, trojan://)", isEnglish)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        maxLines = 4
                    )

                    Button(
                        onClick = {
                            if (nameInput.trim().isEmpty() || uriInput.trim().isEmpty()) {
                                Toast.makeText(context, Loc.t("لطفا تمام فیلدها را پر کنید", "Please fill all fields", isEnglish), Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (!uriInput.startsWith("vless://") && !uriInput.startsWith("vmess://") &&
                                !uriInput.startsWith("trojan://") && !uriInput.startsWith("ss://")) {
                                Toast.makeText(context, Loc.t("پشتیبانی فقط از vless, vmess, trojan, ss", "Only vless, vmess, trojan, ss are supported", isEnglish), Toast.LENGTH_LONG).show()
                                return@Button
                            }

                            viewModel.addConfigBase(nameInput, uriInput)
                            nameInput = ""
                            uriInput = ""
                            isFormExpanded = false
                            Toast.makeText(context, Loc.t("لینک بیس با موفقیت ذخیره شد!", "Base link saved successfully!", isEnglish), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(Loc.t("ذخیره در پایگاه‌داده", "Save to Database", isEnglish), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // List View / Tutorials
        if (configsList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SettingsInputComponent,
                        contentDescription = "",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        text = Loc.t("هیچ کانفیگ پایه‌ای یافت نشد!", "No base configurations registered!", isEnglish),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = Loc.t(
                            "نحوه کارکرد:\n۱. کانفیگ مسدود شده خود را از طریق دکمه «افزودن بیس» ثبت کنید.\n۲. اسکن شبکه انجام داده و پس از پایان، روی لبه دلخواه کلیک کنید.\n۳. برنامه به طور خودکار لینک‌های شما را با آی‌پی جدید تمیز بازسازی کرده و در اختیارتان قرار می‌دهد.",
                            "How it works:\n1. Add your blocked configuration using the 'Add Base' button.\n2. Start a network scan, and after completion click on any fine-tuned edge IP.\n3. The app automatically replaces blocked endpoints with the clean IP and provides a quick copy button.",
                            isEnglish
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = if (isEnglish) TextAlign.Left else TextAlign.Right,
                        lineHeight = 22.sp
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
                items(configsList, key = { it.id }) { config ->
                    ConfigItem(
                        config = config,
                        isEnglish = isEnglish,
                        onCopyRebuilt = {
                            clipboardManager.setText(AnnotatedString(config.lastCleanUri))
                            Toast.makeText(context, Loc.t("کانفیگ تمیز بازسازی شده کپی شد!", "Rebuilt clean config copied to clipboard!", isEnglish), Toast.LENGTH_SHORT).show()
                        },
                        onCopyOriginal = {
                            clipboardManager.setText(AnnotatedString(config.rawUri))
                            Toast.makeText(context, Loc.t("لینک اصلی کپی شد", "Original raw link copied", isEnglish), Toast.LENGTH_SHORT).show()
                        },
                        onDelete = {
                            viewModel.deleteConfigBase(config)
                            Toast.makeText(context, Loc.t("از لیست حذف شد", "Removed from configurations", isEnglish), Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ConfigItem(
    config: ConfigBase,
    isEnglish: Boolean,
    onCopyRebuilt: () -> Unit,
    onCopyOriginal: () -> Unit,
    onDelete: () -> Unit
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Server Header
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
                        imageVector = Icons.Default.VpnLock,
                        contentDescription = "Config",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = config.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete",
                        tint = DangerRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Divider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))

            // Raw Base Configuration (Blocked link)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = Loc.t("لینک مسدود شده اولیه (Original Base):", "Raw Base Link (Blocked Endpoint):", isEnglish),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = config.rawUri,
                        fontFamily = MonospaceFontFamily,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onCopyOriginal,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy raw",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Divider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 4.dp))

            // Rebuilt Operating Configuration with optimal IP
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = Loc.t("لینک بازسازی شده با آی‌پی تمیز لبه:", "Rebuilt Config url with Clean Edge IP:", isEnglish),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )

                if (config.lastCleanUri.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = Loc.t("آی‌پی تمیز هنوز اعمال نشده است. اسکن کنید یا از آی‌پی نشان‌شده برای اعمال استفاده کنید.", "Clean IP not applied yet. Run a network scan or choose a starred IP result to deploy clean tunnels.", isEnglish),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, textGreen.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                            .background(textGreen.copy(alpha = 0.05f))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = config.lastCleanUri,
                            fontFamily = MonospaceFontFamily,
                            fontSize = 12.sp,
                            color = textGreen,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onCopyRebuilt,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(Loc.t("کپی لینک", "Copy Link", isEnglish), fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
