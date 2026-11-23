package com.mnemo.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mnemo.app.feature.memory.MemoryCard
import com.mnemo.app.ui.theme.Emerald500
import com.mnemo.app.ui.theme.Indigo500
import com.mnemo.app.ui.theme.Sky400

data class FeatureCardItem(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val tag: String,
    val accentColor: Color,
    val destination: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onNavigateToChat: () -> Unit,
    onNavigateToMemories: () -> Unit,
    onNavigateToDiagnostics: () -> Unit,
    onDeleteRecentMemory: (String) -> Unit = {}
) {
    val features = listOf(
        FeatureCardItem(
            title = "Assistant Chat",
            description = "Natural language queries, intent routing, and source-verified answers on local device data.",
            icon = Icons.Default.Assistant,
            tag = "Phase 1: Loki & Pravee",
            accentColor = Sky400,
            destination = "chat"
        ),
        FeatureCardItem(
            title = "Encrypted Memories",
            description = "Explore indexed memories, add new items, or search full-text across encrypted records.",
            icon = Icons.Default.Psychology,
            tag = "Phase 1: Nikhi & Pravee",
            accentColor = Emerald500,
            destination = "memories"
        ),
        FeatureCardItem(
            title = "Contacts Integration",
            description = "Ask questions like \"Find Rahul's phone number\" with zero network transmission.",
            icon = Icons.Default.Contacts,
            tag = "Phase 1: Dadu",
            accentColor = Indigo500,
            destination = "chat"
        ),
        FeatureCardItem(
            title = "Calendar Schedule",
            description = "Ask \"What is on my schedule today?\" to inspect upcoming events completely offline.",
            icon = Icons.Default.CalendarMonth,
            tag = "Phase 1: Dadu",
            accentColor = Emerald500,
            destination = "chat"
        ),
        FeatureCardItem(
            title = "Notifications & Messages",
            description = "Query local notifications and messages without any cloud telemetry.",
            icon = Icons.Default.Message,
            tag = "Phase 1: Dadu",
            accentColor = Color(0xFFA855F7),
            destination = "chat"
        ),
        FeatureCardItem(
            title = "Offline Documents & Files",
            description = "Search local files and PDFs without cloud processing.",
            icon = Icons.Default.Folder,
            tag = "Phase 1: Dadu",
            accentColor = Color(0xFFF59E0B),
            destination = "chat"
        ),
        FeatureCardItem(
            title = "Privacy & Diagnostics",
            description = "Audit hardware Keystore encryption, verified lack of INTERNET permission, and storage status.",
            icon = Icons.Default.Lock,
            tag = "Phase 1: Nikhi",
            accentColor = Color(0xFFEC4899),
            destination = "diagnostics"
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Mnemo",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToDiagnostics) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Diagnostics",
                            tint = Emerald500
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        text = "Mnemo",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Private On-Device AI Assistant",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    PrivacyBadge()
                }
            }

            // Quick Action Card: Start Chat
            item {
                HeroChatActionCard(
                    onStartChat = onNavigateToChat,
                    totalMemories = uiState.totalMemoriesCount,
                    onViewMemories = onNavigateToMemories
                )
            }

            // Recent memories preview if any exist
            if (uiState.recentMemories.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent Encrypted Memories",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        OutlinedButton(onClick = onNavigateToMemories) {
                            Text("View All (${uiState.totalMemoriesCount})")
                        }
                    }
                }

                items(uiState.recentMemories, key = { it.id }) { memory ->
                    MemoryCard(
                        item = memory,
                        onDeleteClick = onDeleteRecentMemory
                    )
                }
            }

            // Feature Sections
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Capabilities & Modules",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            items(features) { feature ->
                FeaturePlaceholderCard(
                    item = feature,
                    onClick = {
                        when (feature.destination) {
                            "chat" -> onNavigateToChat()
                            "memories" -> onNavigateToMemories()
                            "diagnostics" -> onNavigateToDiagnostics()
                            else -> onNavigateToChat()
                        }
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                TeamRosterCard()
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun HeroChatActionCard(
    onStartChat: () -> Unit,
    totalMemories: Int,
    onViewMemories: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Sky400.copy(alpha = 0.2f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Chat,
                            contentDescription = null,
                            tint = Sky400,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Ask Mnemo",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$totalMemories local memories ready to query",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Ask questions, query offline contacts and calendars, or say \"Remember: <note>\" to store private thoughts.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onStartChat,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Assistant,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open Chat")
                }

                OutlinedButton(
                    onClick = onViewMemories,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Memories")
                }
            }
        }
    }
}

@Composable
private fun PrivacyBadge() {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(Emerald500, CircleShape)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Offline-First • Zero Cloud APIs • Encrypted Local Storage",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FeaturePlaceholderCard(
    item: FeatureCardItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(18.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = item.accentColor.copy(alpha = 0.15f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = item.accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = item.tag,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Navigate",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun TeamRosterCard() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Four-Member Development Team Architecture",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "• Loki — AI & Intelligence Lead (:intelligence)\n" +
                       "• Pravee — Android UI & Presentation Lead (:app)\n" +
                       "• Dadu — Android System Connectors Lead (:connectors)\n" +
                       "• Nikhi — Database & Security Lead (:core:database, :core:security)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
