package com.ztmanager.ui.browser.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ztmanager.ui.theme.ZtTheme

data class MenuAction(
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

/** ZArchiver style list dialog: a header followed by a list of actions. */
@Composable
fun MenuDialog(
    title: String,
    subtitle: String?,
    actions: List<MenuAction>,
    onDismiss: () -> Unit,
    leading: (@Composable () -> Unit)? = null
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 12.dp)
                ) {
                    if (leading != null) {
                        leading()
                        Spacer(Modifier.width(14.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!subtitle.isNullOrEmpty()) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = ZtTheme.colors.secondaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                HorizontalDivider(color = ZtTheme.colors.divider)
                LazyColumn(Modifier.heightIn(max = 520.dp)) {
                    items(actions) { a ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = a.enabled) {
                                    onDismiss()
                                    a.onClick()
                                }
                                .padding(horizontal = 20.dp, vertical = 13.dp)
                        ) {
                            Icon(
                                a.icon, null,
                                tint = if (a.enabled) MaterialTheme.colorScheme.primary else ZtTheme.colors.secondaryText,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(18.dp))
                            Text(
                                a.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (a.enabled) MaterialTheme.colorScheme.onSurface else ZtTheme.colors.secondaryText
                            )
                        }
                    }
                }
                Spacer(Modifier.padding(4.dp))
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String, selectable: Boolean = true) {
    Column(Modifier.padding(vertical = 5.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = ZtTheme.colors.secondaryText)
        if (selectable) {
            SelectionContainer { Text(value, style = MaterialTheme.typography.bodyMedium) }
        } else {
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp)
    ) {
        androidx.compose.material3.RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 2.dp)
    ) {
        androidx.compose.material3.Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
