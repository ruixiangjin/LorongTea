package com.lorongtea.mobile.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lorongtea.mobile.R
import com.lorongtea.mobile.data.ApiFailure
import com.lorongtea.mobile.data.SessionRepository
import com.lorongtea.mobile.ui.common.display
import com.lorongtea.mobile.ui.components.LoadNotice
import com.lorongtea.mobile.ui.components.Page
import com.lorongtea.mobile.ui.components.PrimaryAction
import com.lorongtea.mobile.ui.components.ScreenHeading
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginState(val submitting: Boolean = false, val error: String? = null)

class LoginViewModel(private val accounts: SessionRepository) : ViewModel() {
    private val mutable = MutableStateFlow(LoginState())
    val state = mutable.asStateFlow()

    fun signIn(username: String, password: String) {
        if (mutable.value.submitting) return
        mutable.value = LoginState(submitting = true)
        viewModelScope.launch {
            try {
                accounts.signIn(username.trim(), password)
                mutable.value = LoginState()
            } catch (error: ApiFailure) {
                mutable.value = LoginState(error = error.display())
            } catch (_: IOException) {
                mutable.value = LoginState(error = "Connection unavailable. Try signing in again.")
            }
        }
    }
}

@Composable
fun LoginScreen(state: LoginState, expired: Boolean, signIn: (String, String) -> Unit) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Page() {
        Column(
            modifier = Modifier.imePadding().verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.tea_brand),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().aspectRatio(1.85f).clip(MaterialTheme.shapes.extraLarge),
                contentScale = ContentScale.Crop,
            )
            ScreenHeading(stringResource(R.string.brand_name), stringResource(R.string.brand_tagline))
            LoadNotice(state.submitting, state.error ?: stringResource(R.string.session_expired).takeIf { expired })
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text(stringResource(R.string.username)) },
                singleLine = true,
                enabled = !state.submitting,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.password)) },
                singleLine = true,
                enabled = !state.submitting,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryAction(
                label = stringResource(if (state.submitting) R.string.signing_in else R.string.sign_in),
                onClick = { signIn(username, password) },
                enabled = username.isNotBlank() && password.isNotBlank() && !state.submitting,
            )
        }
    }
}
