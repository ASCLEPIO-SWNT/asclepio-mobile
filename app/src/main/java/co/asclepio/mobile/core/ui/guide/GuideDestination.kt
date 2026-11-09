package co.asclepio.mobile.core.ui.guide

import androidx.annotation.StringRes
import co.asclepio.mobile.R

/** Catálogo cerrado de instrucciones de navegación; nunca se construye a partir de datos privados. */
enum class GuideDestination(@param:StringRes private val first: Int, @param:StringRes private val second: Int) {
    Welcome(R.string.guide_welcome_1, R.string.guide_welcome_2),
    PatientLogin(R.string.guide_patient_login_1, R.string.guide_patient_login_2),
    StaffLogin(R.string.guide_staff_login_1, R.string.guide_staff_login_2),
    TenantPicker(R.string.guide_tenant_1, R.string.guide_tenant_2),
    SignUp(R.string.guide_signup_1, R.string.guide_signup_2),
    Home(R.string.guide_home_1, R.string.guide_home_2),
    Profile(R.string.guide_profile_1, R.string.guide_profile_2),
    PreRegistration(R.string.guide_preregistration_1, R.string.guide_preregistration_2),
    MyRequests(R.string.guide_requests_1, R.string.guide_requests_2),
    StaffPending(R.string.guide_staff_pending_1, R.string.guide_staff_pending_2),
    StaffReception(R.string.dash_guide_reception, R.string.dash_guide_refresh),
    StaffRegistration(R.string.guide_staff_registration_1, R.string.guide_staff_registration_2),
    StaffEncounters(R.string.guide_staff_encounters_1, R.string.guide_staff_encounters_2),
    StaffEncounterDetail(R.string.guide_staff_encounter_detail_1, R.string.guide_staff_encounter_detail_2),
    NurseTray(R.string.dash_guide_nurse, R.string.dash_guide_refresh),
    MedicineDashboard(R.string.dash_guide_medicine, R.string.dash_guide_refresh),
    AdministrationDashboard(R.string.dash_guide_admin, R.string.dash_guide_refresh),
    StaffQueue(R.string.dash_guide_medicine, R.string.dash_guide_refresh),
    StaffReports(R.string.dash_guide_admin, R.string.dash_guide_refresh),
    ClinicalCapture(R.string.clinical_guide_capture, R.string.clinical_guide_history),
    ClinicalHistory(R.string.clinical_guide_history, R.string.clinical_guide_history),
    Status(R.string.guide_status_1, R.string.guide_status_2),
    SessionVerification(R.string.guide_verification_1, R.string.guide_verification_2),
    Preferences(R.string.guide_preferences_1, R.string.guide_preferences_2);

    internal val resources: List<Int> get() = listOf(first, second).distinct()
}
