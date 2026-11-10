# DT-01-mobile — Retirar del APK las funciones de personal

2026-11-09. Decisión del humano: la app móvil es solo del paciente (ADR 0008 de la API, sección "Ratificación"; roadmap "Alcance del móvil"). El ADR local 0002 queda sustituido.

## Objetivo
Retirar del APK todo lo de F-01-staff-mobile, F-02-staff-mobile, F-03-mobile y UI-02-mobile, sin tocar lo del paciente (F-01-mobile, F-02-mobile, UI-01-mobile).

## Inventario inicial (leader)
- **Se va (personal):** `feature/staffaccount`, `feature/staffencounters`, `feature/reception`, `feature/triage`, `feature/dashboards`, todo `core/encounters/` (+`ui/`), `core/auth/StaffDestinations.kt`, `core/ui/Dashboard{Components,DateTime,Read}.kt`; variante `Staff` de `Principal`/`StaffRole` y ramas staff de `SessionManager`, `AppContainer`, `AppNavigation`; entradas `Staff*` de `GuideDestination`; recursos `clinical_capture.xml`, `clinical_guides.xml`, `dashboards.xml`, `staff_*.xml` (+ `values-{de,en,fr,pt}`) y claves `staff_*`/`access_*` de personal en `strings.xml`/`voice_guide.xml`; pruebas `feature/{staffaccount,staffencounters,reception,triage,dashboards}`, `core/encounters`, `StaffSessionLifecycleTest`, `StaffTransitionRegressionTest`, `DashboardRetentionViewModelTest`, `Staff*TestEnv`, `StaffNavigationTest`, `StaffReceptionNavigationTest`; `androidTest/DashboardScreensInstrumentedTest`.
- **Se queda (paciente):** `core/auth` (sesión, Keystore, `/me`, `PatientCall`), `core/network`, `core/model`, `core/tenants`, `core/ui` (tema, preferencias, cinco idiomas, guía TTS), `feature/{account,patients,encounters,status,voice}`; pruebas del paciente, `ApiScopeTest`, `PrivacyTest`, `LocalizationCatalogTest`.
- **Pruebas colgadas conocidas:** `DashboardOverviewViewModelTest` R15 y `TriageTrayViewModelTest` R2 (sondeo staff); se eliminan con el slice.

## Plan
1. spec_author → specs/DT-01-mobile/ → spec_ready → ⏸ aprobación humana.
2. Estado de F-01-staff-mobile, F-02-staff-mobile, F-03-mobile y UI-02-mobile: pendiente de decisión del humano (el harness no tiene estado "retirado").
3. implementer → reviewer → init.sh completo en verde → done.

## Estado
spec_ready (2026-11-09). Esperando: PA-1, PA-2, la decisión sobre el estado "retirado" y la aprobación del humano.
