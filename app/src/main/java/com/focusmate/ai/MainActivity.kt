package com.focusmate.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusmate.ai.data.FocusRepository
import com.focusmate.ai.theme.FocusCyan
import com.focusmate.ai.theme.FocusMateAITheme
import com.focusmate.ai.ui.dashboard.DashboardScreen
import com.focusmate.ai.ui.goals.GoalsScreen
import com.focusmate.ai.ui.history.HistoryScreen
import com.focusmate.ai.ui.permissions.PermissionsScreen
import com.focusmate.ai.ui.testing.AccuracyTestingScreen

data class NavItem(
    val title: String,
    val iconEmoji: String,
    val index: Int
)

class MainActivity : ComponentActivity() {

    private lateinit var repository: FocusRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = FocusRepository.getInstance(applicationContext)

        enableEdgeToEdge()
        setContent {
            FocusMateAITheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAppScaffold(repository = repository)
                }
            }
        }
    }
}

@Composable
fun MainAppScaffold(repository: FocusRepository) {
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }

    val navItems = listOf(
        NavItem("Dashboard", "🏠", 0),
        NavItem("Goals", "🎯", 1),
        NavItem("History", "📊", 2),
        NavItem("Testing", "🧪", 3),
        NavItem("Privacy", "🛡️", 4)
    )

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 8.dp
            ) {
                navItems.forEach { item ->
                    val isSelected = selectedIndex == item.index
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedIndex = item.index },
                        icon = {
                            Text(
                                text = item.iconEmoji,
                                fontSize = 18.sp
                            )
                        },
                        label = {
                            Text(
                                text = item.title,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) FocusCyan else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        BoxModifierContent(
            selectedIndex = selectedIndex,
            repository = repository,
            onNavigateTab = { selectedIndex = it },
            modifier = Modifier.padding(innerPadding)
        )
    }
}

@Composable
fun BoxModifierContent(
    selectedIndex: Int,
    repository: FocusRepository,
    onNavigateTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    when (selectedIndex) {
        0 -> DashboardScreen(
            repository = repository,
            onNavigateToGoals = { onNavigateTab(1) },
            onNavigateToTesting = { onNavigateTab(3) },
            onNavigateToPermissions = { onNavigateTab(4) },
            modifier = modifier
        )
        1 -> GoalsScreen(
            repository = repository,
            modifier = modifier
        )
        2 -> HistoryScreen(
            repository = repository,
            modifier = modifier
        )
        3 -> AccuracyTestingScreen(
            repository = repository,
            modifier = modifier
        )
        4 -> PermissionsScreen(
            repository = repository,
            modifier = modifier
        )
    }
}
