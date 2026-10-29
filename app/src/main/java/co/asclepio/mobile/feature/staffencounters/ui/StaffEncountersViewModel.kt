package co.asclepio.mobile.feature.staffencounters.ui

import co.asclepio.mobile.core.encounters.*
import co.asclepio.mobile.core.encounters.ui.EncounterListViewModel

class StaffEncountersViewModel(repository: EncountersRepository, sitesRepository: StaffSitesRepository, changes: StaffDataChanges) :
    EncounterListViewModel(repository, sitesRepository, changes)
