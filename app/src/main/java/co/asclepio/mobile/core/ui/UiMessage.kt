package co.asclepio.mobile.core.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** Mensaje de la app: un id de strings.xml y sus argumentos de formato (R34). */
data class UiMessage(@param:StringRes val resId: Int, val args: List<Any> = emptyList())

@Composable
fun UiMessage.text(): String = stringResource(resId, *args.toTypedArray())
