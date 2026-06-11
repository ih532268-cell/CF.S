package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.ConfigManagerScreen
import com.example.ui.DashboardScreen
import com.example.ui.SavedIpsScreen
import com.example.ui.ScannerViewModel
import com.example.ui.SettingsScreen
import com.example.ui.theme.CyberBlack
import com.example.ui.theme.CyberCharcoal
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NeonCyan

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState

import com.example.ui.Loc

enum class AppTab {
    DASHBOARD, SAVED_IPS, CONFIG_MANAGER, SETTINGS
}

class MainActivity : ComponentActivity() {

    private val viewModel: ScannerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val isDarkTheme by viewModel.isDarkTheme.collectAsState()
            val isEnglish by viewModel.isEnglish.collectAsState()
            MyApplicationTheme(darkTheme = isDarkTheme) {
                var currentTab by remember { mutableStateOf(AppTab.DASHBOARD) }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        MainBottomNavigation(
                            selectedTab = currentTab,
                            onTabSelected = { currentTab = it },
                            isEnglish = isEnglish
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(innerPadding)
                    ) {
                        when (currentTab) {
                            AppTab.DASHBOARD -> DashboardScreen(
                                viewModel = viewModel,
                                onNavigateToConfigs = { currentTab = AppTab.CONFIG_MANAGER }
                            )
                            AppTab.SAVED_IPS -> SavedIpsScreen(
                                viewModel = viewModel,
                                onNavigateToConfigs = { currentTab = AppTab.CONFIG_MANAGER }
                            )
                            AppTab.CONFIG_MANAGER -> ConfigManagerScreen(
                                viewModel = viewModel
                            )
                            AppTab.SETTINGS -> SettingsScreen(
                                viewModel = viewModel
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MainBottomNavigation(
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    isEnglish: Boolean
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            selected = selectedTab == AppTab.DASHBOARD,
            onClick = { onTabSelected(AppTab.DASHBOARD) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = "Dashboard",
                    modifier = Modifier.size(22.dp)
                )
            },
            label = {
                Text(
                    text = Loc.t("اسکنر", "Scanner", isEnglish),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primary,
                unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        )

        NavigationBarItem(
            selected = selectedTab == AppTab.SAVED_IPS,
            onClick = { onTabSelected(AppTab.SAVED_IPS) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Saved IPs",
                    modifier = Modifier.size(22.dp)
                )
            },
            label = {
                Text(
                    text = Loc.t("نشان شده‌ها", "Starred", isEnglish),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primary,
                unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        )

        NavigationBarItem(
            selected = selectedTab == AppTab.CONFIG_MANAGER,
            onClick = { onTabSelected(AppTab.CONFIG_MANAGER) },
            icon = {
                Icon(
                    imageVector = Icons.Default.VpnLock,
                    contentDescription = "Configs",
                    modifier = Modifier.size(24.dp)
                )
            },
            label = {
                Text(
                    text = Loc.t("کانفیگ‌ها", "Configs", isEnglish),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primary,
                unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        )

        NavigationBarItem(
            selected = selectedTab == AppTab.SETTINGS,
            onClick = { onTabSelected(AppTab.SETTINGS) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    modifier = Modifier.size(22.dp)
                )
            },
            label = {
                Text(
                    text = Loc.t("تنظیمات", "Settings", isEnglish),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primary,
                unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        )
    }
}
