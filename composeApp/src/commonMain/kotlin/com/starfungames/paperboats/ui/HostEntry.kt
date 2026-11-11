package com.starfungames.paperboats.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starfungames.paperboats.party.controllerSocketUrl

/**
 * For a controller the host didn't serve (the Android app): ask for the TV's address, then run [controller] on its
 * game socket. [controller] gets a way back here for when the address was wrong.
 */
@Composable
fun TypedHostController(
    savedAddress: String,
    saveAddress: (String) -> Unit,
    controller: @Composable (hostUrl: String, changeHost: () -> Unit) -> Unit,
) {
    var typed by remember { mutableStateOf(savedAddress) }
    var hostUrl by remember { mutableStateOf<String?>(null) }
    val url = hostUrl
    if (url != null) {
        controller(url) { hostUrl = null }
        return
    }
    HostEntry(typed, onAddress = { typed = it }, onConnect = {
        hostUrl = controllerSocketUrl(typed)?.also { saveAddress(typed.trim()) }
    })
}

@Composable
internal fun HostEntry(address: String, onAddress: (String) -> Unit, onConnect: () -> Unit) {
    var tried by remember { mutableStateOf(false) }
    val wrong = tried && controllerSocketUrl(address) == null
    val connect = {
        tried = true
        onConnect()
    }
    Column(
        Modifier.fillMaxSize().background(Palette.night).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        BasicText("Find the TV", style = Huge.copy(color = Palette.ink, fontSize = 64.sp, letterSpacing = (-1).sp))
        val hint = if (wrong) {
            "That isn't an address yet. It looks like 192.168.1.24:8642"
        } else {
            "Type the address under the QR code on the big screen"
        }
        BasicText(
            hint,
            style = Body.copy(color = if (wrong) Palette.light else Palette.inkSoft, fontWeight = FontWeight.Normal),
        )
        BasicTextField(
            address,
            {
                tried = false
                onAddress(it)
            },
            singleLine = true,
            textStyle = Body.copy(color = Palette.ink, fontSize = 28.sp),
            cursorBrush = SolidColor(Palette.light),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { connect() }),
            modifier = Modifier.fillMaxWidth().background(Palette.water, RoundedCornerShape(12.dp)).padding(18.dp),
        )
        BigButton("Connect", connect)
    }
}
