package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Textsms
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AiProvider
import com.example.model.DetectionMethod
import com.example.model.DetectionResultType
import com.example.model.DetectedQuestion
import com.example.model.DiagnosticLogEntry
import com.example.model.ReplyItem
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TestCaseScenario(
    val title: String,
    val description: String,
    val sampleText: String,
    val expectedOutcome: String,
    val categoryIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val iconColor: Color,
    val isOcrScenario: Boolean = false
)

@Composable
fun SimulatorTab(
    currentQuestion: DetectedQuestion?,
    activeReplies: List<ReplyItem>,
    isGenerating: Boolean,
    errorMessage: String?,
    settings: ReplySettings,
    activeProvider: AiProvider?,
    diagnosticLogs: List<DiagnosticLogEntry>,
    onSimulateQuestion: (String, String) -> Unit,
    onSimulateOcrQuestion: (String, String) -> Unit = { _, _ -> },
    onCopyReply: (ReplyItem) -> Unit,
    onClearDiagnosticLogs: () -> Unit
) {
    var customInput by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    var isRunningBatchSuite by remember { mutableStateOf(false) }

    val testScenarios = listOf(
        TestCaseScenario(
            title = "1. Reflective & Personal Inquiry",
            description = "Self-reflection question: 'What is one thing you are secretly proud of yourself for?'",
            sampleText = "What is one thing you are secretly proud of yourself for?",
            expectedOutcome = "MATCHED: Interrogative Reflective Question Pattern",
            categoryIcon = Icons.Default.AutoAwesome,
            iconColor = AccentYellow
        ),
        TestCaseScenario(
            title = "2. Real World Conceptual Question",
            description = "Complex explanation prompt: 'If you have to explain to a 5-year-old what is integration in ONE sentence, what would you say?'",
            sampleText = "If you have to explain to a 5-year-old what is integration in ONE sentence, what would you say?",
            expectedOutcome = "MATCHED: Interrogative Question Pattern",
            categoryIcon = Icons.Default.QuestionAnswer,
            iconColor = CrimsonLight
        ),
        TestCaseScenario(
            title = "3. Latest Visible Question in Long Chat",
            description = "Actual latest question at the bottom of the chat stream",
            sampleText = "If you could have dinner with any historical figure, who would it be and why?",
            expectedOutcome = "MATCHED: Contains '?' combined with question word 'who'/'why'",
            categoryIcon = Icons.Default.QuestionAnswer,
            iconColor = TechGreen
        ),
        TestCaseScenario(
            title = "4. Statement Filter Without Question Mark",
            description = "Header/statement text without '?' (must NEVER be detected as question)",
            sampleText = "Okay, final boss question:",
            expectedOutcome = "REJECTED: Strict requirement: Missing question mark '?'",
            categoryIcon = Icons.Default.Clear,
            iconColor = TextMuted
        ),
        TestCaseScenario(
            title = "5. Short Simple Question",
            description = "Standard single-sentence question with interrogative punctuation",
            sampleText = "Are you free for lunch tomorrow?",
            expectedOutcome = "MATCHED: Question Punctuation / Interrogative Clause",
            categoryIcon = Icons.Default.QuestionAnswer,
            iconColor = TechBlue
        ),
        TestCaseScenario(
            title = "6. Long Multi-line Question",
            description = "Message spanning multiple paragraphs with embedded inquiry",
            sampleText = "Hi Alex,\nCould we reschedule our meeting to tomorrow afternoon at 3 PM?\nThanks!",
            expectedOutcome = "MATCHED: Multi-line Question Structure",
            categoryIcon = Icons.Default.Notes,
            iconColor = AccentPurple
        ),
        TestCaseScenario(
            title = "7. Math Notation Question",
            description = "Arithmetic calculation & algebraic equation detection",
            sampleText = "Can you calculate 15 * 8 + 32?",
            expectedOutcome = "MATCHED: Math Prompt / Expression Formula",
            categoryIcon = Icons.Default.Functions,
            iconColor = AccentYellow
        ),
        TestCaseScenario(
            title = "8. Normal Messaging App Text",
            description = "Regular non-question statement text in chat stream",
            sampleText = "I'm heading out now, see you soon.",
            expectedOutcome = "REJECTED: Filtered as non-question statement",
            categoryIcon = Icons.Default.Textsms,
            iconColor = TextMuted
        ),
        TestCaseScenario(
            title = "9. Unreadable Canvas / WebView (OCR Fallback)",
            description = "Custom graphic canvas / game UI with 0 text nodes — triggers background On-Device ML Kit OCR fallback",
            sampleText = "What is the capital of Australia?",
            expectedOutcome = "MATCHED via On-Device ML Kit OCR Fallback (Non-blocking background inference)",
            categoryIcon = Icons.Default.CameraAlt,
            iconColor = AccentGreen,
            isOcrScenario = true
        )
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Section Header & Batch Runner
        item {
            ControlPanelCard(
                modifier = Modifier.fillMaxWidth(),
                shapeRadius = 14.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Speed, contentDescription = null, tint = CrimsonPrimary, modifier = Modifier.size(16.dp))
                            Text(
                                text = "OVERLAY PIPELINE SIMULATOR",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextWhite
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = TechBlue.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, TechBlue.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "VIRTUAL VM",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = TechBlue,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (!isRunningBatchSuite) {
                                isRunningBatchSuite = true
                                coroutineScope.launch {
                                    for (scenario in testScenarios) {
                                        customInput = scenario.sampleText
                                        if (scenario.isOcrScenario) {
                                            onSimulateOcrQuestion(scenario.sampleText, "Canvas App (OCR Fallback)")
                                        } else {
                                            onSimulateQuestion(scenario.sampleText, "Accessibility Test Runner")
                                        }
                                        delay(900)
                                    }
                                    isRunningBatchSuite = false
                                }
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                    ) {
                        if (isRunningBatchSuite) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = TextWhite)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Running All 9 Test Scenarios...", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Run All 9 Verification Scenarios",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite,
                                softWrap = false,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // Dedicated Test Case Scenarios (As requested)
        item {
            ControlPanelCard(
                modifier = Modifier.fillMaxWidth(),
                shapeRadius = 14.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "SELECT INDIVIDUAL SCENARIOS TO TEST",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextSecondary
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        testScenarios.forEach { scenario ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        customInput = scenario.sampleText
                                        if (scenario.isOcrScenario) {
                                            onSimulateOcrQuestion(scenario.sampleText, "Canvas App (OCR Fallback)")
                                        } else {
                                            onSimulateQuestion(scenario.sampleText, "Accessibility Test Scenario")
                                        }
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = DarkSurfaceVariant,
                                border = BorderStroke(1.dp, DarkCardBorder)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(scenario.iconColor.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = scenario.categoryIcon,
                                                contentDescription = null,
                                                tint = scenario.iconColor,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = scenario.title,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.5.sp,
                                                    color = TextWhite
                                                )
                                                if (scenario.isOcrScenario) {
                                                    StatusBadge(text = "OCR", style = StatusBadgeStyle.GREEN_LIVE)
                                                }
                                            }
                                            Text(
                                                text = "\"${scenario.sampleText.replace("\n", " ↵ ")}\"",
                                                fontSize = 11.sp,
                                                color = TextSecondary,
                                                maxLines = 1
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            customInput = scenario.sampleText
                                            if (scenario.isOcrScenario) {
                                                onSimulateOcrQuestion(scenario.sampleText, "Canvas App (OCR Fallback)")
                                            } else {
                                                onSimulateQuestion(scenario.sampleText, "Accessibility Test Scenario")
                                            }
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = DarkCardElevated, contentColor = TechBlue),
                                        border = BorderStroke(1.dp, TechBlue.copy(alpha = 0.4f))
                                    ) {
                                        Text(if (scenario.isOcrScenario) "OCR Test" else "Test", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Interactive Long Chat Thread Viewport Simulator
        item {
            ControlPanelCard(
                modifier = Modifier.fillMaxWidth(),
                shapeRadius = 14.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.QuestionAnswer, contentDescription = null, tint = TechBlue, modifier = Modifier.size(16.dp))
                            Text(
                                text = "LONG CHAT SCROLL STREAM (VIEWPORT TEST)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TechBlue
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = TechBlue.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, TechBlue.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "BOTTOM-FIRST SCAN",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = TechBlue,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = "Simulate scrolling through a long chat with older statements/headers (e.g., \"Okay, final boss question:\") and the latest question at the bottom. Tap 'Scan Viewport' to verify the lowest valid question is prioritized.",
                        fontSize = 11.5.sp,
                        color = TextSecondary
                    )

                    // Simulated Chat Box with multi-message timeline
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkSurfaceVariant)
                            .border(1.dp, DarkCardBorder, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val mockChatMessages = listOf(
                            Triple("Alex", "Welcome to the group chat everyone!", false),
                            Triple("Alex", "Did you check the previous notes?", true),
                            Triple("Host", "Okay, final boss question:", false),
                            Triple("Host", "Get ready everyone...", false),
                            Triple("Host", "If you could have dinner with any historical figure, who would it be and why?", true)
                        )

                        mockChatMessages.forEachIndexed { idx, (sender, msgText, hasQ) ->
                            val isLatest = idx == mockChatMessages.lastIndex
                            val isFinalBossHeader = msgText.contains("final boss")
                            
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        customInput = msgText
                                        onSimulateQuestion(msgText, "Chat Stream ($sender)")
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isLatest) TechGreen.copy(alpha = 0.15f) 
                                        else if (isFinalBossHeader) AccentYellow.copy(alpha = 0.1f) 
                                        else DarkCardElevated,
                                border = BorderStroke(
                                    1.dp,
                                    if (isLatest) TechGreen.copy(alpha = 0.5f) 
                                    else if (isFinalBossHeader) AccentYellow.copy(alpha = 0.3f) 
                                    else DarkCardBorder
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(sender, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = if (isLatest) TechGreen else TextMuted)
                                            if (isLatest) {
                                                Text("• [LATEST AT BOTTOM]", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = TechGreen)
                                            } else if (isFinalBossHeader) {
                                                Text("• [STATEMENT - NO '?']", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = AccentYellow)
                                            } else {
                                                Text("• [OLD / HIGHER UP]", fontSize = 9.5.sp, color = TextMuted)
                                            }
                                        }
                                        Text(
                                            text = "\"$msgText\"",
                                            fontSize = 12.sp,
                                            fontWeight = if (isLatest) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (isLatest) TextWhite else TextSecondary
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            customInput = msgText
                                            onSimulateQuestion(msgText, "Chat Stream ($sender)")
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isLatest) TechGreen.copy(alpha = 0.25f) else DarkCardElevated,
                                            contentColor = if (isLatest) TechGreen else TextMuted
                                        ),
                                        border = BorderStroke(1.dp, if (isLatest) TechGreen.copy(alpha = 0.5f) else DarkCardBorder),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text(if (hasQ) "Test" else "Test Reject", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // Viewport scan trigger button
                    Button(
                        onClick = {
                            val latestQuestion = "If you could have dinner with any historical figure, who would it be and why?"
                            customInput = latestQuestion
                            onSimulateQuestion(latestQuestion, "Viewport Scanner (Lowest On Screen)")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TechGreen.copy(alpha = 0.2f),
                            contentColor = TechGreen
                        ),
                        border = BorderStroke(1.dp, TechGreen.copy(alpha = 0.6f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = TechGreen, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Scan Chat Viewport (Prioritizes Lowest Question with '?')",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = TechGreen,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Live Question Input
        item {
            ControlPanelCard(
                modifier = Modifier.fillMaxWidth(),
                shapeRadius = 14.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Simulate Custom Inbound Message Bubble:",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = TextWhite
                    )

                    OutlinedTextField(
                        value = customInput,
                        onValueChange = { customInput = it },
                        placeholder = { Text("Type any question or math equation (e.g., Solve 2x + 6 = 18?)...", color = TextMuted, fontSize = 12.5.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("simulator_text_input"),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CrimsonPrimary,
                            unfocusedBorderColor = DarkCardBorder,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedContainerColor = DarkSurfaceVariant,
                            unfocusedContainerColor = DarkSurfaceVariant
                        ),
                        singleLine = false,
                        maxLines = 4
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                if (customInput.isNotBlank()) {
                                    onSimulateQuestion(customInput, "Accessibility Node")
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .testTag("simulator_submit_button"),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CrimsonPrimary,
                                contentColor = Color.White
                            )
                        ) {
                            if (isGenerating) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Synthesizing...", fontSize = 12.sp)
                            } else {
                                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Accessibility Scan", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Button(
                            onClick = {
                                if (customInput.isNotBlank()) {
                                    onSimulateOcrQuestion(customInput, "Canvas App (OCR Fallback)")
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .testTag("simulator_ocr_button"),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DarkCardElevated,
                                contentColor = TechGreen
                            ),
                            border = BorderStroke(1.dp, TechGreen.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(15.dp), tint = TechGreen)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ML Kit OCR Scan", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TechGreen)
                        }
                    }
                }
            }
        }

        // Live Simulated Overlay Card Result
        item {
            ControlPanelCard(
                modifier = Modifier.fillMaxWidth(),
                shapeRadius = 14.dp,
                isSelected = currentQuestion != null,
                activeColor = if (currentQuestion != null) TechBlue else DarkCardBorder
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LIVE OVERLAY PREVIEW OUTPUT",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = TechBlue
                        )

                        if (currentQuestion != null) {
                            StatusBadge(text = "QUESTION ACTIVE", style = StatusBadgeStyle.GREEN_LIVE)
                        } else {
                            StatusBadge(text = "AWAITING TRIGGER", style = StatusBadgeStyle.MUTED_OFF)
                        }
                    }

                    if (currentQuestion != null) {
                        // Detected Prompt Display
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkSurfaceVariant)
                                .border(1.dp, DarkCardBorder, RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Captured Text (${currentQuestion.sourceApp ?: "System"}):", fontSize = 10.5.sp, color = TextMuted)

                                    // Detection Method Badge
                                    if (currentQuestion.detectionMethod == DetectionMethod.MLKIT_OCR) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = AccentPurple.copy(alpha = 0.2f),
                                            border = BorderStroke(1.dp, AccentPurple.copy(alpha = 0.5f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(Icons.Default.CameraAlt, contentDescription = null, tint = AccentPurple, modifier = Modifier.size(11.dp))
                                                Text(
                                                    text = "ON-DEVICE ML KIT OCR (${currentQuestion.ocrLatencyMs ?: 0}ms)",
                                                    fontSize = 9.5.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold,
                                                    color = AccentPurple
                                                )
                                            }
                                        }
                                    } else {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = TechBlue.copy(alpha = 0.15f),
                                            border = BorderStroke(1.dp, TechBlue.copy(alpha = 0.4f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(Icons.Default.Speed, contentDescription = null, tint = TechBlue, modifier = Modifier.size(11.dp))
                                                Text(
                                                    text = "ACCESSIBILITY SCAN (FAST)",
                                                    fontSize = 9.5.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TechBlue
                                                )
                                            }
                                        }
                                    }
                                }

                                Text(
                                    text = "\"${currentQuestion.text}\"",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextWhite
                                )

                                if (currentQuestion.englishMeaning != null && settings.understandingMode) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = CrimsonLight,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = "Intent: ${currentQuestion.englishMeaning}",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = CrimsonLight
                                        )
                                    }
                                }
                            }
                        }

                        // Generated Candidate Replies
                        if (isGenerating) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = CrimsonPrimary)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Generating quick replies via ${activeProvider?.displayName}...", fontSize = 12.sp, color = TextSecondary)
                            }
                        } else if (activeReplies.isNotEmpty()) {
                            Text("Click any suggestion to copy to clipboard:", fontSize = 11.5.sp, color = TextSecondary)

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                activeReplies.forEachIndexed { index, reply ->
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { onCopyReply(reply) }
                                            .testTag("simulated_reply_$index"),
                                        shape = RoundedCornerShape(8.dp),
                                        color = DarkCardElevated,
                                        border = BorderStroke(1.dp, DarkCardBorder)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(20.dp)
                                                        .clip(CircleShape)
                                                        .background(CrimsonPrimary.copy(alpha = 0.15f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "${index + 1}",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = CrimsonLight
                                                    )
                                                }

                                                Text(
                                                    text = reply.text,
                                                    fontSize = 13.sp,
                                                    color = TextWhite
                                                )
                                            }

                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Copy",
                                                tint = TechBlue,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "No active prompt. Type a question above or tap one of the verification test scenarios.",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }

                    if (errorMessage != null) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = CrimsonDark.copy(alpha = 0.3f),
                            border = BorderStroke(1.dp, CrimsonPrimary)
                        ) {
                            Text(
                                text = "Engine Error: $errorMessage",
                                fontSize = 11.5.sp,
                                color = CrimsonLight,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }

        // Real-Time Diagnostic Scanner Feed (MANDATORY REQUIREMENT with OCR vs Accessibility source differentiation)
        item {
            ControlPanelCard(
                modifier = Modifier.fillMaxWidth(),
                shapeRadius = 14.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
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
                                imageVector = Icons.Default.FormatListBulleted,
                                contentDescription = null,
                                tint = TechGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "REAL-TIME DIAGNOSTIC SCANNER LOG",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = TechGreen
                            )
                        }

                        if (diagnosticLogs.isNotEmpty()) {
                            OutlinedButton(
                                onClick = onClearDiagnosticLogs,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, DarkCardBorder),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted)
                            ) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Clear", fontSize = 10.5.sp)
                            }
                        }
                    }

                    // Explanatory callout for OCR vs Accessibility
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = DarkCardElevated,
                        border = BorderStroke(1.dp, DarkCardBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = TechBlue, modifier = Modifier.size(16.dp))
                            Text(
                                text = "Accessibility scan is the fast primary scanner (~5ms). When an app has 0 accessibility nodes (e.g. Flutter custom canvas or WebView), On-Device ML Kit OCR activates as a background fallback (~30-80ms). Detections from OCR are identified with latency below and are expected for canvas apps, not a bug.",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                lineHeight = 15.sp
                            )
                        }
                    }

                    if (diagnosticLogs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkSurfaceVariant)
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No diagnostic events yet. Trigger a test scenario or open a whitelisted chat app.",
                                fontSize = 11.5.sp,
                                color = TextMuted
                            )
                        }
                    } else {
                        val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            diagnosticLogs.take(20).forEach { log ->
                                val isMatched = log.result == DetectionResultType.MATCHED
                                val isOcr = log.detectionMethod == DetectionMethod.MLKIT_OCR
                                val isError = log.category in listOf("AUTH_FAILURE", "QUOTA_EXCEEDED", "NO_API_KEY", "PROVIDER_ERROR", "BAD_REQUEST", "MODEL_NOT_FOUND", "EMPTY_RESPONSE")

                                val borderColor = when {
                                    isError -> CrimsonPrimary.copy(alpha = 0.6f)
                                    isMatched && isOcr -> AccentPurple.copy(alpha = 0.6f)
                                    isMatched -> TechGreen.copy(alpha = 0.5f)
                                    else -> DarkCardBorder
                                }

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp)),
                                    shape = RoundedCornerShape(8.dp),
                                    color = DarkSurfaceVariant,
                                    border = BorderStroke(1.dp, borderColor)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            when {
                                                                isError -> CrimsonPrimary
                                                                isMatched -> TechGreen
                                                                else -> AccentYellow
                                                            }
                                                        )
                                                )
                                                Text(
                                                    text = if (isError) log.category else log.result.label,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = when {
                                                        isError -> CrimsonLight
                                                        isMatched -> TechGreen
                                                        else -> AccentYellow
                                                    }
                                                )

                                                // Detection Method Pill Badge
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = if (isOcr) AccentPurple.copy(alpha = 0.2f) else TechBlue.copy(alpha = 0.15f),
                                                    border = BorderStroke(1.dp, if (isOcr) AccentPurple.copy(alpha = 0.4f) else TechBlue.copy(alpha = 0.3f))
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isOcr) Icons.Default.CameraAlt else Icons.Default.Speed,
                                                            contentDescription = null,
                                                            tint = if (isOcr) AccentPurple else TechBlue,
                                                            modifier = Modifier.size(10.dp)
                                                        )
                                                        Text(
                                                            text = if (isOcr) {
                                                                if (log.latencyMs != null) "OCR (${log.latencyMs}ms)" else "ML Kit OCR"
                                                            } else "Accessibility",
                                                            fontSize = 9.5.sp,
                                                            fontFamily = FontFamily.Monospace,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isOcr) AccentPurple else TechBlue
                                                        )
                                                    }
                                                }

                                                if (!isError) {
                                                    Text(
                                                        text = "• ${log.category}",
                                                        fontSize = 10.5.sp,
                                                        color = TextSecondary
                                                    )
                                                }
                                            }

                                            Text(
                                                text = "${log.source} @ ${timeFormat.format(Date(log.timestamp))}",
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = TextMuted
                                            )
                                        }

                                        Text(
                                            text = "\"${log.rawText.replace("\n", " ↵ ")}\"",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = TextWhite
                                        )

                                        Text(
                                            text = if (isError) "Diagnostic: ${log.reason}" else "Reason: ${log.reason}",
                                            fontSize = 11.sp,
                                            fontWeight = if (isError) FontWeight.SemiBold else FontWeight.Normal,
                                            color = when {
                                                isError -> CrimsonLight
                                                isMatched && isOcr -> AccentPurple.copy(alpha = 0.95f)
                                                isMatched -> TechGreen.copy(alpha = 0.9f)
                                                else -> TextSecondary
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
    }
}

