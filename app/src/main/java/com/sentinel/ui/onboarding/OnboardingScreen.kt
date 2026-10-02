package com.sentinel.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sentinel.ui.components.GradientButton
import com.sentinel.ui.components.SecureBackground
import com.sentinel.ui.components.SecurePalette
import com.sentinel.ui.components.TacunsLogoMark
import com.sentinel.ui.components.TacunsWordmark
import com.tacu.nsfwzerotrust.R

/**
 * First-launch intro. Three short benefits, then the VPN disclosure, then an explicit
 * accept button — Google Play requires the disclosure in-app before the VPN permission
 * request, with an affirmative tap to consent, so that card and button must stay.
 */
@Composable
fun OnboardingScreen(onGetStarted: () -> Unit) {
    SecureBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = 460.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(36.dp))
                TacunsLogoMark(size = 92.dp)
                Spacer(Modifier.height(14.dp))
                TacunsWordmark(fontSize = 24.sp)
                Text(
                    "FIREWALL",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SecurePalette.Orange,
                    letterSpacing = 4.sp
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.intro_tagline),
                    fontSize = 13.5.sp,
                    color = SecurePalette.TextSoft,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp
                )

                Spacer(Modifier.height(26.dp))
                GlassGroup {
                    BenefitRow(Icons.Default.Shield,
                        stringResource(R.string.intro_benefit_block_title),
                        stringResource(R.string.intro_benefit_block_desc))
                    GroupDivider()
                    BenefitRow(Icons.Default.Apps,
                        stringResource(R.string.intro_benefit_apps_title),
                        stringResource(R.string.intro_benefit_apps_desc))
                    GroupDivider()
                    BenefitRow(Icons.Default.Lock,
                        stringResource(R.string.intro_benefit_private_title),
                        stringResource(R.string.intro_benefit_private_desc))
                }

                Spacer(Modifier.height(12.dp))
                GlassGroup {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.VerifiedUser, null, tint = SecurePalette.Orange,
                            modifier = Modifier.size(16.dp).padding(top = 1.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.vpn_disclosure_title), fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold, color = SecurePalette.TextMain)
                            Text(stringResource(R.string.vpn_disclosure_body), fontSize = 11.5.sp,
                                color = SecurePalette.TextSoft, lineHeight = 16.sp)
                            Text(stringResource(R.string.intro_vpn_note), fontSize = 11.5.sp,
                                color = SecurePalette.TextFaint, lineHeight = 16.sp)
                        }
                    }
                }

                Spacer(Modifier.height(22.dp))
                GradientButton(
                    label   = stringResource(R.string.intro_accept),
                    onClick = onGetStarted
                )
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun GlassGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(SecurePalette.GlassBrush)
            .border(1.dp, SecurePalette.Edge, RoundedCornerShape(22.dp)),
        content = content
    )
}

@Composable
private fun GroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 66.dp, end = 14.dp),
        color = SecurePalette.Edge,
        thickness = 1.dp
    )
}

@Composable
private fun BenefitRow(icon: ImageVector, title: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(SecurePalette.Orange.copy(alpha = 0.14f))
                .border(1.dp, SecurePalette.Orange.copy(alpha = 0.28f), RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = SecurePalette.Orange, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = SecurePalette.TextMain)
            Text(desc, fontSize = 12.sp, color = SecurePalette.TextSoft, lineHeight = 16.sp)
        }
    }
}
