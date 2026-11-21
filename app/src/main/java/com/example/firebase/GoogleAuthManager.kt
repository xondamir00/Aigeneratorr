package com.example.firebase

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object GoogleAuthManager {
    private const val TAG = "Auth"

    fun attemptAutoSignIn(
        context: Context,
        credentialManager: CredentialManager,
        onAuthSuccess: () -> Unit,
        onUnauthenticated: () -> Unit,
        scope: CoroutineScope
    ) {
        if (Firebase.auth.currentUser != null) {
            onAuthSuccess()
            return
        }
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            onUnauthenticated()
            return
        }
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        scope.launch {
            try {
                val credential = credentialManager.getCredential(context, request).credential
                if (credential is CustomCredential &&
                    credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    Firebase.auth.signInWithCredential(
                        GoogleAuthProvider.getCredential(token, null)
                    ).await()
                    onAuthSuccess()
                } else {
                    onUnauthenticated()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                onUnauthenticated()
            }
        }
    }

    fun onGoogleSignInClicked(
        context: Context,
        credentialManager: CredentialManager,
        onAuthSuccess: () -> Unit,
        onAuthError: (String) -> Unit,
        scope: CoroutineScope,
        onAuthCancelled: () -> Unit = {}
    ) {
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            onAuthError("Google Sign-In konfiguratsiyasi topilmadi (default_web_client_id).")
            return
        }
        val activity = context as? Activity
        if (activity == null) {
            onAuthError("Google kirish oynasini ochib bo'lmadi.")
            return
        }

        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        scope.launch {
            try {
                val credential = credentialManager.getCredential(activity, request).credential
                if (credential is CustomCredential &&
                    credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    Firebase.auth.signInWithCredential(
                        GoogleAuthProvider.getCredential(token, null)
                    ).await()
                    onAuthSuccess()
                } else {
                    onAuthError("Kutilmagan Google hisob ma'lumoti olindi.")
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w(TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
                onAuthCancelled()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Google Sign-In failed", e)
                onAuthError(e.localizedMessage ?: "Google orqali kirishda xatolik yuz berdi.")
            }
        }
    }

    fun signOut(
        credentialManager: CredentialManager,
        scope: CoroutineScope,
        onSignOutComplete: () -> Unit = {}
    ) {
        scope.launch {
            withContext(NonCancellable) {
                try {
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to clear credential state: ${e.message}")
                } finally {
                    Firebase.auth.signOut()
                    onSignOutComplete()
                }
            }
        }
    }
}
