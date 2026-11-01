package com.jahid.together.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jahid.together.data.model.HomeLocation
import com.jahid.together.data.model.LiveLocation
import com.jahid.together.data.model.UserProfile
import com.jahid.together.ui.components.BengaliFormatter
import com.jahid.together.ui.components.TogetherButton
import com.jahid.together.ui.components.TogetherCard
import com.jahid.together.ui.theme.*
import com.jahid.together.ui.viewmodel.LocationViewModel
import com.jahid.together.ui.viewmodel.PairUiStatus
import com.jahid.together.ui.viewmodel.PairViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(
    currentUser: UserProfile,
    pairViewModel: PairViewModel,
    onPairSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pairStatus by pairViewModel.pairStatus.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Create Code, 1: Join Code
    var inputCode by remember { mutableStateOf("") }

    LaunchedEffect(pairStatus) {
        if (pairStatus is PairUiStatus.PairedSuccess) {
            Toast.makeText(context, "অভিনন্দন! যুগল সফলভাবে যুক্ত হয়েছে ❤️", Toast.LENGTH_LONG).show()
            pairViewModel.resetStatus()
            onPairSuccess()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("দুজনে যুক্ত হোন ❤️", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(RosePrimaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text("💑", fontSize = 42.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "দুজনের নিরাপদ সংযোগ",
                style = MaterialTheme.typography.titleLarge,
                color = RosePrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "অ্যাপটিতে সর্বোচ্চ দুইজন (স্বামী ও স্ত্রী) সুরক্ষিতভাবে যুক্ত থাকতে পারবেন",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.clip(RoundedCornerShape(14.dp))
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("আমন্ত্রণ কোড তৈরি", fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("কোড দিয়ে যুক্ত হোন", fontWeight = FontWeight.SemiBold) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (pairStatus is PairUiStatus.Error) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Text(
                        text = (pairStatus as PairUiStatus.Error).message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(14.dp),
                        fontSize = 13.sp
                    )
                }
            }

            if (selectedTab == 0) {
                // Generate Code Tab
                TogetherCard {
                    Text(
                        text = "আপনার সঙ্গীকে আমন্ত্রণ পাঠান",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = RosePrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "একটি ৬ অক্ষরের সুরক্ষিত আমন্ত্রণ কোড তৈরি করে আপনার সঙ্গীর সাথে শেয়ার করুন। কোডটি ২৪ ঘণ্টা কার্যকর থাকবে।",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    if (pairStatus is PairUiStatus.CodeGenerated) {
                        val invite = (pairStatus as PairUiStatus.CodeGenerated).invite
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(RosePrimaryContainer)
                                .border(2.dp, RosePrimary, RoundedCornerShape(16.dp))
                                .padding(vertical = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = invite.code,
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 6.sp,
                                    color = RosePrimary,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "মেয়াদ: ২৪ ঘণ্টার জন্য সক্রিয়",
                                    color = CoralOnSecondaryContainer,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Together Code", invite.code))
                                    Toast.makeText(context, "কোড কপি করা হয়েছে!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f).height(50.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("কপি করুন")
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Button(
                                onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "আমার প্রিয়, Together ❤️ অ্যাপে যুক্ত হতে এই আমন্ত্রণ কোডটি ব্যবহার করুন: ${invite.code}"
                                        )
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "কোড শেয়ার করুন"))
                                },
                                modifier = Modifier.weight(1f).height(50.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = RosePrimary)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("শেয়ার করুন")
                            }
                        }
                    } else {
                        TogetherButton(
                            text = "নতুন আমন্ত্রণ কোড তৈরি করুন",
                            onClick = { pairViewModel.createInvite(currentUser) },
                            isLoading = pairStatus is PairUiStatus.Loading,
                            testTag = "generate_code_btn"
                        )
                    }
                }
            } else {
                // Enter Partner's Code Tab
                TogetherCard {
                    Text(
                        text = "সঙ্গীর আমন্ত্রণ কোড লিখুন",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = RosePrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "আপনার সঙ্গী যে ৬ অক্ষরের কোড পাঠিয়েছেন তা নিচে প্রবেশ করিয়ে যুক্ত হোন।",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    OutlinedTextField(
                        value = inputCode,
                        onValueChange = { if (it.length <= 6) inputCode = it.uppercase() },
                        label = { Text("৬ অক্ষরের কোড") },
                        placeholder = { Text("যেমন: AB3XYZ") },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 4.sp,
                            textAlign = TextAlign.Center,
                            fontFamily = FontFamily.Monospace
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("pair_code_input"),
                        shape = RoundedCornerShape(14.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    TogetherButton(
                        text = "সঙ্গীর সাথে যুক্ত হোন ❤️",
                        onClick = { pairViewModel.joinInvite(currentUser, inputCode) },
                        isLoading = pairStatus is PairUiStatus.Loading,
                        enabled = inputCode.length == 6,
                        testTag = "join_pair_btn"
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeLocationSetupScreen(
    pairId: String,
    currentLocation: LiveLocation?,
    existingHome: HomeLocation?,
    locationViewModel: LocationViewModel,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var latText by remember { mutableStateOf(existingHome?.latitude?.toString() ?: (currentLocation?.latitude?.toString() ?: "23.8103")) }
    var lonText by remember { mutableStateOf(existingHome?.longitude?.toString() ?: (currentLocation?.longitude?.toString() ?: "90.4125")) }
    var addressText by remember { mutableStateOf(existingHome?.address ?: "আমাদের প্রিয় বাসস্থান") }
    var isSaving by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("বাড়ির অবস্থান নির্ধারণ 🏡", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFEF3C7)),
                contentAlignment = Alignment.Center
            ) {
                Text("🏡", fontSize = 38.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "যৌথ গৃহ অবস্থান ও ৫০০ মিটার জিওফেন্স",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = RosePrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "যেকোনো একজন সঙ্গী বাড়ির অবস্থান একবার সেট করলে তা উভয়ের জন্য সংরক্ষিত হবে। বাড়ির ৫০০ মিটার ও ১০০ মিটার ব্যাসার্ধে স্বয়ংক্ৰিয় নোটিফিকেশন আসবে।",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            TogetherCard {
                if (currentLocation != null && currentLocation.isValid()) {
                    OutlinedButton(
                        onClick = {
                            latText = currentLocation.latitude.toString()
                            lonText = currentLocation.longitude.toString()
                            Toast.makeText(context, "বর্তমান জিপিএস অবস্থান নেওয়া হয়েছে", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = null, tint = RosePrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("বর্তমান অবস্থানকে বাড়ি বানান", color = RosePrimary, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                OutlinedTextField(
                    value = addressText,
                    onValueChange = { addressText = it },
                    label = { Text("বাড়ির নাম বা ঠিকানা") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = latText,
                    onValueChange = { latText = it },
                    label = { Text("অক্ষাংশ (Latitude)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = lonText,
                    onValueChange = { lonText = it },
                    label = { Text("দ্রাঘিমাংশ (Longitude)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Surface(
                    color = RosePrimaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "🔔 সক্রিয় নোটিফিকেশন নিয়মাবলী:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = RosePrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "• ৫০০ মিটারে প্রবেশ: \"আপনার কলিজা আসতেছে ❤️ গেট খুলে রাখো।\"", fontSize = 12.sp)
                        Text(text = "• ১০০ মিটারে সান্নিধ্য: \"গেট খুলে রাখো, আর মাত্র ১০০ মিটার দূরে!\"", fontSize = 12.sp)
                        Text(text = "• ৫০০ মিটার ত্যাগ: \"আপনার কলিজা চলে গেছে ❤️ গেট লাগিয়ে রাখো।\"", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                TogetherButton(
                    text = "বাড়ির অবস্থান নিশ্চিত করুন",
                    onClick = {
                        val lat = latText.toDoubleOrNull()
                        val lon = lonText.toDoubleOrNull()
                        if (lat != null && lon != null) {
                            isSaving = true
                            val home = HomeLocation(
                                latitude = lat,
                                longitude = lon,
                                address = addressText.ifBlank { "আমাদের প্রিয় বাসস্থান" },
                                radiusMeters = 500.0,
                                setAt = System.currentTimeMillis()
                            )
                            locationViewModel.saveHomeLocation(pairId, home) { res ->
                                isSaving = false
                                if (res.isSuccess) {
                                    Toast.makeText(context, "বাড়ি সংরক্ষিত হয়েছে 🏡", Toast.LENGTH_SHORT).show()
                                    onSaved()
                                } else {
                                    Toast.makeText(context, res.exceptionOrNull()?.message ?: "ত্রুটি হয়েছে", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            Toast.makeText(context, "সঠিক স্থানাঙ্ক দিন", Toast.LENGTH_SHORT).show()
                        }
                    },
                    isLoading = isSaving,
                    testTag = "save_home_btn"
                )
            }
        }
    }
}
