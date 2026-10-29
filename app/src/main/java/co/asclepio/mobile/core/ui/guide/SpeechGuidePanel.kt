package co.asclepio.mobile.core.ui.guide

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import co.asclepio.mobile.R
import co.asclepio.mobile.core.ui.*

/** Controls contain no screen data and never start narration merely by opening the dialog. */
@Composable
fun SpeechGuidePanel(controller: SpeechGuideController, state: SpeechGuideState, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(16.dp).widthIn(max = 600.dp).fillMaxWidth(), shape = MaterialTheme.shapes.large) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PageHeader(stringResource(R.string.guide_title))
                when (state) {
                    SpeechGuideState.Speaking -> UiNotice(stringResource(R.string.guide_speaking))
                    SpeechGuideState.Paused -> UiNotice(stringResource(R.string.guide_paused))
                    SpeechGuideState.Unavailable -> UiNotice(stringResource(R.string.guide_unavailable), NoticeTone.Warning)
                    SpeechGuideState.Idle -> Unit
                }
                when (state) {
                    SpeechGuideState.Speaking -> AsclepioButton(controller::pause, Modifier.fillMaxWidth().testTag("guide-pause")) { Text(stringResource(R.string.guide_pause)) }
                    SpeechGuideState.Paused -> AsclepioButton(controller::resume, Modifier.fillMaxWidth().testTag("guide-resume")) { Text(stringResource(R.string.guide_resume)) }
                    else -> AsclepioButton(controller::start, Modifier.fillMaxWidth().testTag("guide-listen")) { Text(stringResource(R.string.guide_listen)) }
                }
                OutlinedButton(controller::repeat, Modifier.fillMaxWidth().testTag("guide-repeat")) { Text(stringResource(R.string.guide_repeat)) }
                if (state != SpeechGuideState.Idle) OutlinedButton(controller::stop, Modifier.fillMaxWidth().testTag("guide-stop")) { Text(stringResource(R.string.guide_stop)) }
                TextButton(onClose, Modifier.testTag("guide-close")) { Text(stringResource(R.string.close)) }
            }
        }
    }
}
