package info.mayuragarkar.calculator.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import info.mayuragarkar.calculator.CalcViewModel
import info.mayuragarkar.calculator.R
import info.mayuragarkar.calculator.ResultState
import info.mayuragarkar.calculator.engine.Group
import info.mayuragarkar.calculator.engine.Op
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PREVIEW_CHARS = 4_000

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalcScreen(vm: CalcViewModel = viewModel()) {
    var showAbout by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val copy: (String) -> Unit = { text ->
        val ok = runCatching {
            context.getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("Result", text))
        }.isSuccess
        if (!ok || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            scope.launch { snackbar.showSnackbar(if (ok) "Copied" else "Too large to copy. Use Save instead.") }
        }
    }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val text = (vm.result as? ResultState.Done)?.text
        if (uri != null && text != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)!!.use { it.write(text.toByteArray()) }
                    }.isSuccess
                }
                snackbar.showSnackbar(if (ok) "Saved" else "Couldn't save the file")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { showAbout = true }) {
                        Icon(Icons.Outlined.Info, contentDescription = "About")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        BoxWithConstraints(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),
        ) {
            val inputs = @Composable {
                NumberField(vm.x, { vm.x = it }, "First number (x)")
                NumberField(vm.y, { vm.y = it }, "Second number (y)")
                NumberField(vm.z, { vm.z = it }, "Third number (z), optional")
            }
            val result = @Composable {
                ResultCard(
                    state = vm.result,
                    onCancel = vm::cancel,
                    onCopy = copy,
                    onSave = { op -> save.launch("${op.name.lowercase()}-result.txt") },
                    onUseAsX = vm::useAsX,
                    onClear = vm::clear,
                )
            }
            val operations = @Composable { Operations(vm::run) }

            if (maxWidth >= 840.dp) {
                Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        inputs()
                        operations()
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { result() }
                }
            } else {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    inputs()
                    result()
                    operations()
                }
            }
        }
    }

    if (showAbout) AboutDialog { showAbout = false }
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        label = { Text(label) },
        maxLines = 4,
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        trailingIcon = {
            IconButton(
                onClick = { onChange(if (value.startsWith("-")) value.drop(1) else "-" + value.removePrefix("+")) },
                modifier = Modifier.semantics { contentDescription = "Change sign of $label" },
            ) {
                Text("±", style = MaterialTheme.typography.titleLarge)
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Operations(onRun: (Op) -> Unit) {
    Group.entries.forEach { group ->
        Text(
            group.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Op.entries.filter { it.group == group }.forEach { op ->
                val label = @Composable {
                    Text(op.label, Modifier.semantics { contentDescription = op.title })
                }
                if (group == Group.BASIC) Button(onClick = { onRun(op) }) { label() }
                else FilledTonalButton(onClick = { onRun(op) }) { label() }
            }
        }
    }
    Text(
        "Operations without y use only x. z is the modulus for xʸ mod z. gcd and lcm use every number in " +
            "x, y and z, and each field can hold a list like 12, 18, 30. For an IBAN, paste it into x. " +
            "Whole-number operations don't accept decimals.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 16.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultCard(
    state: ResultState,
    onCancel: () -> Unit,
    onCopy: (String) -> Unit,
    onSave: (Op) -> Unit,
    onUseAsX: (String) -> Unit,
    onClear: () -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        Column(
            Modifier.padding(16.dp).heightIn(min = 96.dp).semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (state) {
                ResultState.Empty -> Text(
                    "Enter numbers, then pick an operation.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                is ResultState.Running -> {
                    Text(state.op.title, style = MaterialTheme.typography.labelLarge)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
                is ResultState.Failed -> {
                    Text(state.op.title, style = MaterialTheme.typography.labelLarge)
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                }
                is ResultState.Done -> {
                    Row {
                        Text(state.op.title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        state.digits?.let {
                            Text(
                                "%,d digit%s".format(it, if (it == 1) "" else "s"),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    val preview = remember(state.text) {
                        val t = state.text
                        if (t.length <= PREVIEW_CHARS) t
                        else t.take(PREVIEW_CHARS / 2) + "\n…\n" + t.takeLast(PREVIEW_CHARS / 2)
                    }
                    SelectionContainer(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                        Text(preview, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace))
                    }
                }
            }
            if (state !is ResultState.Running) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (state is ResultState.Done) {
                        TextButton(onClick = { onCopy(state.text) }) { Text("Copy") }
                        TextButton(onClick = { onSave(state.op) }) { Text("Save") }
                        state.value?.let { v -> TextButton(onClick = { onUseAsX(v) }) { Text("Use as x") } }
                    }
                    TextButton(onClick = onClear) { Text("Clear") }
                }
            }
        }
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        title = { Text(stringResource(R.string.app_name)) },
        text = {
            Text(
                "Version $version\n\n" +
                    "Designed & developed by Mayur Agarkar (Z-Day Apps).\n\n" +
                    "Licensed under GPL-3.0. Math by the malachite library (LGPL-3.0). " +
                    "Built with AndroidX and Jetpack Compose (Apache 2.0).",
            )
        },
    )
}
