package co.asclepio.mobile.feature.staffencounters.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import co.asclepio.mobile.core.encounters.EncounterStatus
import co.asclepio.mobile.core.encounters.ui.*

@Composable
fun StaffEncountersScreen(
    state: EncounterListUiState, onRefresh: () -> Unit, onRetrySites: () -> Unit,
    onSiteChange: (String?) -> Unit, onStatusChange: (EncounterStatus?) -> Unit,
    onPrevious: () -> Unit, onNext: () -> Unit, onOpen: (String) -> Unit, onScroll: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) = EncounterListScreen(state, onRefresh, onRetrySites, onSiteChange, onStatusChange, onPrevious, onNext, onOpen, onScroll, modifier)
