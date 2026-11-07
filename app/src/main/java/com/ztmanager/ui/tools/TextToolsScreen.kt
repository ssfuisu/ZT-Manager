package com.ztmanager.ui.tools

import android.util.Base64
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.ztmanager.R
import com.ztmanager.fs.Format
import com.ztmanager.ui.AppState
import com.ztmanager.ui.common.Intents
import com.ztmanager.ui.theme.ZtTheme
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.CRC32

private data class TextOp(val label: String, val run: (String) -> String)

private fun hexDecode(s: String): String {
    val clean = s.filter { !it.isWhitespace() }.removePrefix("0x")
    require(clean.length % 2 == 0) { "Odd number of hex digits" }
    return String(ByteArray(clean.length / 2) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() }, Charsets.UTF_8)
}

private fun digest(algo: String, s: String) = Format.hex(MessageDigest.getInstance(algo).digest(s.toByteArray(Charsets.UTF_8)))

private fun json(s: String, indent: Int): String {
    val v = JSONTokener(s.trim()).nextValue()
    return when (v) {
        is JSONObject -> if (indent > 0) v.toString(indent) else v.toString()
        is JSONArray -> if (indent > 0) v.toString(indent) else v.toString()
        else -> throw IllegalArgumentException("Not a JSON object or array")
    }.replace("\\/", "/")
}

/** Text conversions: encodings, hashes, JSON, case and line tools. */
@Composable
fun TextToolsScreen() {
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val sDecode = stringResource(R.string.tt_decode)
    val countFmt = stringResource(R.string.tt_count_result)
    val groups = listOf(
        stringResource(R.string.tt_encode) to listOf(
            TextOp("Base64") { Base64.encodeToString(it.toByteArray(Charsets.UTF_8), Base64.NO_WRAP) },
            TextOp("Base64 $sDecode") { String(Base64.decode(it.trim(), Base64.DEFAULT), Charsets.UTF_8) },
            TextOp("URL") { URLEncoder.encode(it, "UTF-8") },
            TextOp("URL $sDecode") { URLDecoder.decode(it, "UTF-8") },
            TextOp("Hex") { Format.hex(it.toByteArray(Charsets.UTF_8)) },
            TextOp("Hex $sDecode") { hexDecode(it) },
            TextOp("\\uXXXX") { s -> s.map { c -> if (c.code < 128) c.toString() else String.format("\\u%04x", c.code) }.joinToString("") }
        ),
        stringResource(R.string.tt_hash) to listOf(
            TextOp("MD5") { digest("MD5", it) },
            TextOp("SHA-1") { digest("SHA-1", it) },
            TextOp("SHA-256") { digest("SHA-256", it) },
            TextOp("SHA-512") { digest("SHA-512", it) },
            TextOp("CRC32") { s -> CRC32().apply { update(s.toByteArray(Charsets.UTF_8)) }.value.let { String.format("%08x", it) } }
        ),
        "JSON" to listOf(
            TextOp(stringResource(R.string.tt_format)) { json(it, 2) },
            TextOp(stringResource(R.string.tt_minify)) { json(it, 0) }
        ),
        stringResource(R.string.tt_text) to listOf(
            TextOp("UPPER") { it.uppercase(Locale.getDefault()) },
            TextOp("lower") { it.lowercase(Locale.getDefault()) },
            TextOp("Title Case") { s -> s.split(" ").joinToString(" ") { w -> w.replaceFirstChar { c -> c.titlecase(Locale.getDefault()) } } },
            TextOp(stringResource(R.string.tt_reverse)) { it.reversed() },
            TextOp(stringResource(R.string.tt_trim)) { s -> s.lines().joinToString("\n") { it.trim() } },
            TextOp(stringResource(R.string.tt_sort)) { s -> s.lines().sortedWith(String.CASE_INSENSITIVE_ORDER).joinToString("\n") },
            TextOp(stringResource(R.string.tt_unique)) { s -> s.lines().distinct().joinToString("\n") },
            TextOp(stringResource(R.string.tt_remove_empty)) { s -> s.lines().filter { it.isNotBlank() }.joinToString("\n") },
            TextOp(stringResource(R.string.tt_count)) { s ->
                val words = s.split(Regex("\\s+")).count { it.isNotEmpty() }
                String.format(countFmt, s.length, words, if (s.isEmpty()) 0 else s.lines().size, s.toByteArray().size)
            }
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ZtTheme.colors.toolbar),
                navigationIcon = { IconButton(onClick = { AppState.pop() }) { Icon(Icons.Filled.ArrowBack, null) } },
                title = { Text(stringResource(R.string.tool_text_tools)) }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text(stringResource(R.string.tt_input)) },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp)
            )
            groups.forEach { (title, ops) ->
                Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    ops.forEach { op ->
                        AssistChip(
                            onClick = {
                                error = null
                                try {
                                    output = op.run(input)
                                } catch (e: Exception) {
                                    error = e.message ?: e.javaClass.simpleName
                                }
                            },
                            label = { Text(op.label) },
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.tt_output), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { input = output }) { Icon(Icons.Filled.ArrowUpward, stringResource(R.string.tt_use_as_input)) }
                IconButton(onClick = { if (output.isNotEmpty()) Intents.copyText(context, output) }) { Icon(Icons.Filled.ContentCopy, null) }
                IconButton(onClick = { input = ""; output = "" }) { Icon(Icons.Filled.Delete, null) }
            }
            OutlinedTextField(
                value = output,
                onValueChange = { output = it },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp)
            )
        }
    }
}
