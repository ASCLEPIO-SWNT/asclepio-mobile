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
    Status(R.string.guide_status_1, R.string.guide_status_2),
    SessionVerification(R.string.guide_verification_1, R.string.guide_verification_2),
    Preferences(R.string.guide_preferences_1, R.string.guide_preferences_2);

    internal val resources: List<Int> get() = listOf(first, second).distinct()
}
