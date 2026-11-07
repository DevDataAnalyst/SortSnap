package com.sortsnap.ui.components

import android.app.Activity
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.sortsnap.R
import com.sortsnap.data.delete.DeleteStep
import com.sortsnap.data.delete.DeleteTarget
import com.sortsnap.data.delete.MediaDeleter
import com.sortsnap.domain.Formatting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * State machine for "move to trash". Every path ends in a confirmation the user must accept:
 * the system dialog (Android 10+) or our own dialog (Android 9 and below).
 */
class DeleteFlowState(private val deleter: MediaDeleter, private val scope: CoroutineScope) {
    var step: DeleteStep? by mutableStateOf(null)
        private set

    fun start(targets: List<DeleteTarget>) {
        scope.launch { step = deleter.start(targets) }
    }

    fun onSystemResult(approved: Boolean) {
        scope.launch { step = deleter.onSystemResult(approved) }
    }

    fun confirmInApp() {
        scope.launch { step = deleter.confirmInApp() }
    }

    fun cancelInApp() {
        step = DeleteStep.Cancelled
    }

    fun consumeResult() {
        step = null
    }
}

@Composable
fun rememberDeleteFlowState(deleter: MediaDeleter): DeleteFlowState {
    val scope = rememberCoroutineScope()
    return remember(deleter) { DeleteFlowState(deleter, scope) }
}

/** Hosts the system IntentSender launch and the in-app fallback dialog; reports the outcome once. */
@Composable
fun DeleteFlowHost(state: DeleteFlowState, onFinished: (DeleteStep) -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        state.onSystemResult(result.resultCode == Activity.RESULT_OK)
    }
    when (val step = state.step) {
        is DeleteStep.LaunchSystem -> LaunchedEffect(step) {
            launcher.launch(IntentSenderRequest.Builder(step.intentSender).build())
        }
        is DeleteStep.ConfirmInApp -> AlertDialog(
            modifier = androidx.compose.ui.Modifier.testTag("confirm_delete_dialog"),
            onDismissRequest = state::cancelInApp,
            title = { Text(stringResource(R.string.confirm_delete_title, step.count)) },
            text = { Text(stringResource(R.string.confirm_delete_body, Formatting.bytes(step.bytes))) },
            confirmButton = {
                TextButton(onClick = state::confirmInApp, modifier = androidx.compose.ui.Modifier.testTag("confirm_delete")) {
                    Text(stringResource(R.string.confirm_delete_action))
                }
            },
            dismissButton = { TextButton(onClick = state::cancelInApp) { Text(stringResource(R.string.cancel)) } },
        )
        is DeleteStep.Done, DeleteStep.Cancelled, is DeleteStep.Failed -> LaunchedEffect(step) {
            onFinished(step)
            state.consumeResult()
        }
        null -> Unit
    }
}

/** Snackbar text for a finished deletion. */
fun deleteResultMessage(context: Context, step: DeleteStep): String = when (step) {
    is DeleteStep.Done -> context.getString(R.string.freed_space, Formatting.bytes(step.freedBytes)) +
        if (step.trashed) " · " + context.getString(R.string.moved_to_trash_hint) else ""
    is DeleteStep.Failed -> context.getString(R.string.delete_failed, step.message)
    else -> context.getString(R.string.delete_cancelled)
}
