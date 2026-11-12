package pe.pagobox.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import pe.pagobox.app.AppContainer

@Composable
fun BusinessSettingsScreen(container: AppContainer, onSaved: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        val s = container.settings.settings.first()
        name = s.businessName
        address = s.businessAddress
        phone = s.businessPhone
    }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Datos del establecimiento. Se muestran en la app; no se envían a ningún servidor.")
        OutlinedTextField(name, { name = it }, label = { Text("Nombre del negocio") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(address, { address = it }, label = { Text("Dirección") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            phone, { phone = it.filter { c -> c.isDigit() || c == '+' || c == ' ' } },
            label = { Text("Teléfono") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        )
        Button(enabled = name.isNotBlank(), onClick = {
            scope.launch {
                container.settings.setBusiness(name, address, phone)
                onSaved()
            }
        }) { Text("Guardar") }
    }
}
