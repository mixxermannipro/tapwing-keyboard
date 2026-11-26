// SPDX-License-Identifier: LicenseRef-Tapwing-Source-Available

package com.tapwing.keyboard.ui.keyboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapwing.keyboard.R
import com.tapwing.keyboard.ui.theme.LocalTapwingKeyboardColors

@Composable
fun NaturalVoiceOnboardingSheet(
    onGetStarted: () -> Unit,
    onDismiss: () -> Unit
) {
    val kbColors = LocalTapwingKeyboardColors.current
    Surface(
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        color = kbColors.cardSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Icon(
                    imageVector = Icons.Rounded.Mic,
                    contentDescription = null,
                    tint = kbColors.keyText,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(26.dp)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(32.dp)
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cd_close),
                        tint = kbColors.keyHintText
                    )
                }
            }

            Text(
                text = stringResource(R.string.voice_onboarding_title),
                color = kbColors.keyText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            OnboardingFeatureCard(
                icon = Icons.Rounded.AutoFixHigh,
                title = stringResource(R.string.voice_onboarding_f1_title),
                subtitle = stringResource(R.string.voice_onboarding_f1_desc)
            )
            OnboardingFeatureCard(
                icon = Icons.Rounded.Edit,
                title = stringResource(R.string.voice_onboarding_f2_title),
                subtitle = stringResource(R.string.voice_onboarding_f2_desc)
            )
            OnboardingFeatureCard(
                icon = Icons.Rounded.Language,
                title = stringResource(R.string.voice_onboarding_f3_title),
                subtitle = stringResource(R.string.voice_onboarding_f3_desc)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = kbColors.keyHintText, modifier = Modifier.size(14.dp))
                Text(
                    text = stringResource(R.string.voice_onboarding_privacy),
                    color = kbColors.keyHintText,
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }

            Button(
                onClick = onGetStarted,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.btn_get_started))
            }
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_no_thanks))
            }
        }
    }
}

@Composable
private fun OnboardingFeatureCard(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    val kbColors = LocalTapwingKeyboardColors.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = kbColors.cardAltSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = kbColors.keyText, modifier = Modifier.size(20.dp))
            Column {
                Text(title, color = kbColors.keyText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(subtitle, color = kbColors.keyHintText, fontSize = 11.sp)
            }
        }
    }
}
