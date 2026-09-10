package com.example.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.OcrRecognitionEngine
import com.example.model.BrainKnowledgeEntry
import com.example.model.ReplySettings
import com.example.ui.theme.AccentBlue
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.AccentPurple
import com.example.ui.theme.AccentYellow
import com.example.ui.theme.CrimsonDark
import com.example.ui.theme.CrimsonLight
import com.example.ui.theme.CrimsonPrimary
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkCard
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkCardElevated
import com.example.ui.theme.DarkSurfaceCard
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.TechBlue
import com.example.ui.theme.TechGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextWhite
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private val DEFAULT_CATEGORIES = listOf(
    "Super Sus Quiz",
    "Personal Facts",
    "Work Info",
    "Gaming Trivia",
    "General Knowledge"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BrainTab(
    settings: ReplySettings,
    onAddEntry: (BrainKnowledgeEntry) -> Unit,
    onUpdateEntry: (BrainKnowledgeEntry) -> Unit,
    onDeleteEntry: (String) -> Unit,
    onToggleEntry: (String, Boolean) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("All") }
    var isOcrRunning by remember { mutableStateOf(false) }

    // Dialog state for adding/editing entries
    var showEditDialog by remember { mutableStateOf(false) }
    var editingEntryId by remember { mutableStateOf<String?>(null) }
    var dialogTitle by remember { mutableStateOf("") }
    var dialogCategory by remember { mutableStateOf("General Knowledge") }
    var dialogContent by remember { mutableStateOf("") }
    var dialogSource by remember { mutableStateOf("MANUAL") }

    // Delete confirmation dialog
    var deleteCandidateId by remember { mutableStateOf<String?>(null) }

    // Photo picker for OCR extraction
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                isOcrRunning = true
                try {
                    val ocrResult = OcrRecognitionEngine.recognizeTextFromUri(context, uri)
                    val extracted = ocrResult.rawText.trim()
                    isOcrRunning = false
                    if (extracted.isNotBlank()) {
                        // Infer reasonable title and category from OCR text
                        val lines = extracted.lines().map { it.trim() }.filter { it.isNotBlank() }
                        val suggestedTitle = lines.firstOrNull()?.take(40) ?: "Screenshot Notes"
                        val lower = extracted.lowercase()
                        val suggestedCategory = when {
                            lower.contains("super sus") || lower.contains("impostor") || lower.contains("quiz") -> "Super Sus Quiz"
                            lower.contains("meeting") || lower.contains("project") || lower.contains("client") -> "Work Info"
                            else -> "Screenshot Notes"
                        }

                        editingEntryId = null
                        dialogTitle = suggestedTitle
                        dialogCategory = suggestedCategory
                        dialogContent = extracted
                        dialogSource = "SCREENSHOT_OCR"
                        showEditDialog = true
                    } else {
                        Toast.makeText(context, "No text detected in selected screenshot", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    isOcrRunning = false
                    Toast.makeText(context, "OCR failed: ${e.localizedMessage ?: "Unknown error"}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val entries = settings.brainEntries

    // Collect all unique categories
    val availableCategories = remember(entries) {
        val unique = (DEFAULT_CATEGORIES + entries.map { it.category }).distinct()
        listOf("All") + unique
    }

    // Filter entries by search and category
    val filteredEntries = remember(entries, searchQuery, selectedCategoryFilter) {
        entries.filter { entry ->
            val matchesCategory = (selectedCategoryFilter == "All" || entry.category.equals(selectedCategoryFilter, ignoreCase = true))
            val query = searchQuery.trim().lowercase()
            val matchesQuery = query.isBlank() ||
                entry.title.lowercase().contains(query) ||
                entry.content.lowercase().contains(query) ||
                entry.category.lowercase().contains(query)
            matchesCategory && matchesQuery
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Brain Header & Mission Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(CrimsonPrimary.copy(alpha = 0.15f))
                                .border(1.dp, CrimsonPrimary.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = null,
                                tint = CrimsonPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Brain Knowledge Base",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextWhite
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(TechBlue.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${entries.count { it.isEnabled }} active",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TechBlue
                                    )
                                }
                            }
                            Text(
                                text = "AI automatically searches your saved knowledge when generating replies",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                maxLines = 2
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Action buttons: Add Manually & Add Screenshot OCR
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                editingEntryId = null
                                dialogTitle = ""
                                dialogCategory = if (selectedCategoryFilter != "All") selectedCategoryFilter else "General Knowledge"
                                dialogContent = ""
                                dialogSource = "MANUAL"
                                showEditDialog = true
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .testTag("add_knowledge_manual_button"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Add Note",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }

                        Button(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .testTag("add_knowledge_screenshot_button"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DarkCardElevated
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, TechBlue.copy(alpha = 0.6f)),
                            enabled = !isOcrRunning
                        ) {
                            if (isOcrRunning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = TechBlue
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Reading...",
                                    fontSize = 12.sp,
                                    color = TechBlue
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = null,
                                    tint = TechBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Add Screenshot",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TechBlue
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Search & Category Filters
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Search brain notes, answers, categories...",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search",
                                    tint = TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkSurfaceCard,
                        unfocusedContainerColor = DarkSurfaceCard,
                        focusedBorderColor = CrimsonPrimary,
                        unfocusedBorderColor = DarkCardBorder,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("brain_search_field")
                )

                // Category Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    availableCategories.forEach { category ->
                        val isSelected = selectedCategoryFilter == category
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategoryFilter = category },
                            label = {
                                Text(
                                    text = category,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = DarkSurfaceCard,
                                labelColor = TextSecondary,
                                selectedContainerColor = CrimsonPrimary.copy(alpha = 0.2f),
                                selectedLabelColor = CrimsonLight
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = DarkCardBorder,
                                selectedBorderColor = CrimsonPrimary
                            )
                        )
                    }
                }
            }
        }

        // 3. Knowledge Entries List or Empty State
        if (entries.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(CrimsonPrimary.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = CrimsonPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Text(
                            text = "Your Brain is Empty",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite
                        )

                        Text(
                            text = "Add quiz answers, game trivia, work information, or personal preferences. When questions appear on your screen, the AI will use this knowledge to give exact answers!",
                            fontSize = 12.5.sp,
                            color = TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Quick Starter Templates:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TechBlue
                        )

                        // Starter quick buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    editingEntryId = null
                                    dialogTitle = "Super Sus Trivia & Roles"
                                    dialogCategory = "Super Sus Quiz"
                                    dialogContent = "Q: What does Spacecrew win condition require?\nA: Complete all tasks or eject all Impostors.\n\nQ: How many impostors in standard match?\nA: Usually 2 impostors."
                                    dialogSource = "MANUAL"
                                    showEditDialog = true
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TechGreen),
                                border = androidx.compose.foundation.BorderStroke(1.dp, TechGreen.copy(alpha = 0.5f))
                            ) {
                                Text("Super Sus Quiz", fontSize = 11.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    editingEntryId = null
                                    dialogTitle = "Personal FAQ & Preferences"
                                    dialogCategory = "Personal Facts"
                                    dialogContent = "My favorite food: Spicy Biryani\nMy coffee order: Oat milk latte, no sugar\nMy timezone: GMT+5:30\nMy role: Lead Developer"
                                    dialogSource = "MANUAL"
                                    showEditDialog = true
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentPurple),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AccentPurple.copy(alpha = 0.5f))
                            ) {
                                Text("Personal Facts", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        } else if (filteredEntries.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No knowledge entries matching \"$searchQuery\"",
                            fontSize = 13.sp,
                            color = TextMuted
                        )
                    }
                }
            }
        } else {
            items(filteredEntries, key = { it.id }) { entry ->
                BrainEntryCard(
                    entry = entry,
                    onEdit = {
                        editingEntryId = entry.id
                        dialogTitle = entry.title
                        dialogCategory = entry.category
                        dialogContent = entry.content
                        dialogSource = entry.source
                        showEditDialog = true
                    },
                    onDelete = { deleteCandidateId = entry.id },
                    onToggle = { isEnabled -> onToggleEntry(entry.id, isEnabled) }
                )
            }
        }
    }

    // 4. Add / Edit Knowledge Entry Dialog
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            containerColor = DarkSurfaceCard,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (dialogSource == "SCREENSHOT_OCR") Icons.Default.AddPhotoAlternate else Icons.Default.Psychology,
                        contentDescription = null,
                        tint = CrimsonPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (editingEntryId == null) "New Knowledge Entry" else "Edit Knowledge Entry",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Title Field
                    OutlinedTextField(
                        value = dialogTitle,
                        onValueChange = { dialogTitle = it },
                        label = { Text("Title / Question / Topic", color = TextSecondary) },
                        placeholder = { Text("e.g. Super Sus Tasks or My Contact Info", color = TextMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DarkCard,
                            unfocusedContainerColor = DarkCard,
                            focusedBorderColor = CrimsonPrimary,
                            unfocusedBorderColor = DarkCardBorder,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("brain_dialog_title_field")
                    )

                    // Category Field with suggestion chips
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = dialogCategory,
                            onValueChange = { dialogCategory = it },
                            label = { Text("Category / Tag", color = TextSecondary) },
                            placeholder = { Text("e.g. Super Sus Quiz, Personal Facts", color = TextMuted) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = DarkCard,
                                unfocusedContainerColor = DarkCard,
                                focusedBorderColor = CrimsonPrimary,
                                unfocusedBorderColor = DarkCardBorder,
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("brain_dialog_category_field")
                        )

                        // Quick category suggestions
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            DEFAULT_CATEGORIES.take(4).forEach { cat ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(DarkCardElevated)
                                        .border(0.5.dp, DarkCardBorder, RoundedCornerShape(6.dp))
                                        .clickable { dialogCategory = cat }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(text = cat, fontSize = 10.5.sp, color = TechBlue)
                                }
                            }
                        }
                    }

                    // Content Field
                    Column {
                        OutlinedTextField(
                            value = dialogContent,
                            onValueChange = { dialogContent = it },
                            label = { Text("Facts / Answers / Notes", color = TextSecondary) },
                            placeholder = { Text("Type facts, answers, or notes the AI should know...", color = TextMuted) },
                            minLines = 4,
                            maxLines = 10,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = DarkCard,
                                unfocusedContainerColor = DarkCard,
                                focusedBorderColor = CrimsonPrimary,
                                unfocusedBorderColor = DarkCardBorder,
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("brain_dialog_content_field")
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Source: ${if (dialogSource == "SCREENSHOT_OCR") "Screenshot OCR" else "Manual Entry"}",
                                fontSize = 10.sp,
                                color = TextMuted
                            )
                            Text(
                                text = "${dialogContent.length} chars",
                                fontSize = 10.sp,
                                color = TextMuted
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanTitle = dialogTitle.trim()
                        val cleanContent = dialogContent.trim()
                        val cleanCat = dialogCategory.trim().ifBlank { "General Knowledge" }

                        if (cleanContent.isBlank()) {
                            Toast.makeText(context, "Please enter content/facts for this entry", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val finalTitle = cleanTitle.ifBlank {
                            cleanContent.lines().firstOrNull()?.take(35) ?: "Knowledge Note"
                        }

                        val currentId = editingEntryId
                        if (currentId != null) {
                            val existing = entries.firstOrNull { it.id == currentId }
                            val updated = (existing ?: BrainKnowledgeEntry(
                                id = currentId,
                                title = finalTitle,
                                content = cleanContent,
                                category = cleanCat
                            )).copy(
                                title = finalTitle,
                                content = cleanContent,
                                category = cleanCat,
                                timestamp = System.currentTimeMillis()
                            )
                            onUpdateEntry(updated)
                            Toast.makeText(context, "Knowledge updated", Toast.LENGTH_SHORT).show()
                        } else {
                            val newEntry = BrainKnowledgeEntry(
                                id = UUID.randomUUID().toString(),
                                title = finalTitle,
                                content = cleanContent,
                                category = cleanCat,
                                source = dialogSource,
                                isEnabled = true,
                                timestamp = System.currentTimeMillis()
                            )
                            onAddEntry(newEntry)
                            Toast.makeText(context, "Knowledge saved to Brain", Toast.LENGTH_SHORT).show()
                        }
                        showEditDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                    modifier = Modifier.testTag("brain_save_button")
                ) {
                    Text("Save to Brain", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // 5. Delete Confirmation Dialog
    if (deleteCandidateId != null) {
        val entryToDelete = entries.firstOrNull { it.id == deleteCandidateId }
        AlertDialog(
            onDismissRequest = { deleteCandidateId = null },
            containerColor = DarkSurfaceCard,
            title = {
                Text(
                    text = "Delete Knowledge Entry?",
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete \"${entryToDelete?.title ?: "this entry"}\"? The AI will no longer use this information.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        deleteCandidateId?.let { onDeleteEntry(it) }
                        deleteCandidateId = null
                        Toast.makeText(context, "Entry deleted", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidateId = null }) {
                    Text("Keep", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun BrainEntryCard(
    entry: BrainKnowledgeEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val categoryColor = remember(entry.category) {
        val lower = entry.category.lowercase()
        when {
            lower.contains("super sus") || lower.contains("quiz") -> TechGreen
            lower.contains("personal") -> AccentPurple
            lower.contains("work") -> TechBlue
            lower.contains("trivia") -> AccentYellow
            else -> CrimsonLight
        }
    }

    val dateFormatter = remember {
        SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (entry.isEnabled) DarkSurfaceCard else DarkCard.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (entry.isEnabled) DarkCardBorder else DarkCardBorder.copy(alpha = 0.4f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("brain_entry_${entry.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Category badge, source badge, toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Category chip
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(categoryColor.copy(alpha = 0.15f))
                            .border(0.5.dp, categoryColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = entry.category,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = categoryColor
                        )
                    }

                    // Source indicator
                    if (entry.source == "SCREENSHOT_OCR") {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(TechBlue.copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "OCR",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TechBlue
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (entry.isEnabled) "Active" else "Disabled",
                        fontSize = 10.sp,
                        color = if (entry.isEnabled) TechGreen else TextMuted,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Switch(
                        checked = entry.isEnabled,
                        onCheckedChange = onToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = TechGreen,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = DarkCardBorder
                        ),
                        modifier = Modifier.size(width = 36.dp, height = 22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Title
            Text(
                text = entry.title,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (entry.isEnabled) TextWhite else TextMuted,
                maxLines = if (expanded) 3 else 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Content Preview / Expanded
            Text(
                text = entry.content,
                fontSize = 12.5.sp,
                color = if (entry.isEnabled) TextSecondary else TextMuted.copy(alpha = 0.7f),
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 17.sp,
                modifier = Modifier.clickable { expanded = !expanded }
            )

            if (entry.content.lines().size > 3 || entry.content.length > 120) {
                Text(
                    text = if (expanded) "Show less" else "Read more...",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = CrimsonLight,
                    modifier = Modifier
                        .clickable { expanded = !expanded }
                        .padding(top = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Footer: Timestamp and Action Buttons (Edit, Delete)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Saved ${dateFormatter.format(Date(entry.timestamp))}",
                    fontSize = 10.sp,
                    color = TextMuted
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit entry",
                            tint = TechBlue,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete entry",
                            tint = CrimsonLight,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}
