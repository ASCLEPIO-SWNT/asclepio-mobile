package co.asclepio.mobile.feature.triage.ui

import co.asclepio.mobile.core.encounters.*
import co.asclepio.mobile.core.encounters.ui.EncounterListViewModel

class TriageTrayViewModel(repository: EncountersRepository, sitesRepository: StaffSitesRepository, changes: StaffDataChanges) :
    EncounterListViewModel(repository, sitesRepository, changes, EncounterStatus.WAITING_TRIAGE)
