package com.agustin.bebederoiot

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var scrollRoot: View
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        // Si ya hay una sesión iniciada y no pasaron 7 días sin usar la app,
        // se salta el login y se abre la lista directamente.
        if (auth.currentUser != null) {
            if (SesionManager.sesionVigente(this)) {
                irALista()
                return
            } else {
                SesionManager.cerrarSesion(this)   // sesión vencida: se pide iniciar de nuevo
            }
        }

        setContentView(R.layout.activity_main)

        scrollRoot = findViewById(R.id.scrollRoot)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)

        // Reserva espacio para las barras del sistema y el teclado
        ViewCompat.setOnApplyWindowInsetsListener(scrollRoot) { view, insets ->
            val space = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            view.setPadding(space.left, space.top, space.right, space.bottom)
            insets
        }

        btnLogin.setOnClickListener { attemptLogin() }

        // Botón "Listo" del teclado también inicia sesión
        etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin()
                true
            } else {
                false
            }
        }
    }

    private fun irALista() {
        SesionManager.registrarActividad(this)   // reinicia el contador de inactividad
        startActivity(Intent(this, ListaBebederosActivity::class.java))
        finish()
    }

    private fun attemptLogin() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString() // sin trim: los espacios pueden ser parte de la clave

        when {
            email.isEmpty() -> return showMessage("Ingresa tu correo electrónico")
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() ->
                return showMessage("El formato del correo no es válido")
            password.isEmpty() -> return showMessage("Ingresa tu contraseña")
        }

        hideKeyboard()
        setLoading(true)

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                setLoading(false)
                if (task.isSuccessful) {
                    irALista()
                } else {
                    showMessage(errorMessage(task.exception))
                }
            }
    }

    private fun errorMessage(e: Exception?): String = when (e) {
        is FirebaseAuthInvalidUserException -> "No existe una cuenta con ese correo"
        is FirebaseAuthInvalidCredentialsException -> "Correo o contraseña incorrectos"
        is FirebaseNetworkException -> "Sin conexión a internet. Revisa tu red"
        is FirebaseTooManyRequestsException -> "Demasiados intentos. Intenta más tarde"
        else -> "No se pudo iniciar sesión. Intenta nuevamente"
    }

    private fun setLoading(loading: Boolean) {
        btnLogin.isEnabled = !loading
        btnLogin.text = if (loading) "Ingresando..." else "Iniciar Sesión"
    }

    private fun showMessage(message: String) {
        Snackbar.make(scrollRoot, message, Snackbar.LENGTH_LONG).show()
    }

    private fun hideKeyboard() {
        WindowCompat.getInsetsController(window, window.decorView)
            .hide(WindowInsetsCompat.Type.ime())
    }
}