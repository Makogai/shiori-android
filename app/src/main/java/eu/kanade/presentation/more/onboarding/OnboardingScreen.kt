package eu.kanade.presentation.more.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.StringResource
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.automirroredrounded.ArrowBackIosNew
import mihon.icons.materialsymbols.rounded.Folder
import mihon.icons.materialsymbols.rounded.Palette
import mihon.icons.materialsymbols.rounded.RocketLaunch
import mihon.icons.materialsymbols.rounded.VerifiedUser
import soup.compose.material.motion.animation.materialSharedAxisX
import soup.compose.material.motion.animation.rememberSlideDistance
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.theme.IosDefaults
import tachiyomi.presentation.core.theme.card

private class StepHeader(val icon: ImageVector, val title: StringResource, val subtitle: StringResource)

/** The first-run flow in the iOS look: back and progress dots on top, a card per step, the main button below. */
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    onRestoreBackup: () -> Unit,
) {
    val slideDistance = rememberSlideDistance()

    var currentStep by rememberSaveable { mutableIntStateOf(0) }
    val steps = remember {
        listOf(
            ThemeStep() to StepHeader(
                MaterialSymbols.Rounded.Palette,
                MR.strings.onboarding_heading,
                MR.strings.onboarding_description,
            ),
            StorageStep() to StepHeader(
                MaterialSymbols.Rounded.Folder,
                MR.strings.onboarding_storage_title,
                MR.strings.onboarding_storage_subtitle,
            ),
            PermissionStep() to StepHeader(
                MaterialSymbols.Rounded.VerifiedUser,
                MR.strings.onboarding_permissions_title,
                MR.strings.onboarding_permissions_subtitle,
            ),
            GuidesStep(onRestoreBackup = onRestoreBackup) to StepHeader(
                MaterialSymbols.Rounded.RocketLaunch,
                MR.strings.onboarding_done_title,
                MR.strings.onboarding_done_subtitle,
            ),
        )
    }
    val isLastStep = currentStep == steps.lastIndex
    val primary = MaterialTheme.colorScheme.primary

    BackHandler(enabled = currentStep != 0) {
        currentStep--
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .background(Brush.verticalGradient(0f to primary.copy(alpha = 0.18f), 0.45f to Color.Transparent)),
    ) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            Box(modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp)) {
                IconButton(
                    onClick = { currentStep-- },
                    enabled = currentStep != 0,
                    modifier = Modifier.align(Alignment.CenterStart).alpha(if (currentStep != 0) 1f else 0f),
                ) {
                    Icon(
                        imageVector = MaterialSymbols.AutoMirroredRounded.ArrowBackIosNew,
                        contentDescription = stringResource(MR.strings.action_bar_up_description),
                        tint = primary,
                    )
                }
                ProgressDots(current = currentStep, count = steps.size, modifier = Modifier.align(Alignment.Center))
            }
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    materialSharedAxisX(
                        forward = targetState > initialState,
                        slideDistance = slideDistance,
                    )
                },
                modifier = Modifier.weight(1f),
                label = "stepContent",
            ) { index ->
                val (step, header) = steps[index]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = IosDefaults.SectionInset, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = header.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                    Text(
                        text = stringResource(header.title),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 20.dp),
                    )
                    Text(
                        text = stringResource(header.subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp, bottom = 24.dp, start = 8.dp, end = 8.dp),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(IosDefaults.SectionRadius))
                            .background(MaterialTheme.colorScheme.card),
                    ) {
                        step.Content()
                    }
                }
            }
            Button(
                onClick = { if (isLastStep) onComplete() else currentStep++ },
                enabled = steps[currentStep].first.isComplete,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .height(52.dp),
            ) {
                Text(
                    text = stringResource(
                        if (isLastStep) MR.strings.onboarding_action_finish else MR.strings.onboarding_action_next,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** A dot per step, the current one stretched into a capsule in the accent colour. */
@Composable
private fun ProgressDots(current: Int, count: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { index ->
            val width by animateDpAsState(if (index == current) 22.dp else 7.dp, label = "dotWidth")
            Box(
                modifier = Modifier
                    .width(width)
                    .height(7.dp)
                    .clip(CircleShape)
                    .background(
                        if (index == current) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = if (index < current) 0.4f else 0.18f)
                        },
                    ),
            )
        }
    }
}
