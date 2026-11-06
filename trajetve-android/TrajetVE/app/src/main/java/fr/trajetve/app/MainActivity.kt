package fr.trajetve.app

import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Écran du téléphone / de la tablette : connexion Google (nécessaire pour l'écran Android Auto),
 * ouverture du site, aperçu du trajet en cours tel que la voiture le verra.
 */
class MainActivity : ComponentActivity() {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private lateinit var status: TextView
    private lateinit var signBtn: Button
    private lateinit var tripTxt: TextView
    private var reg: ListenerRegistration? = null
    private val authListener = FirebaseAuth.AuthStateListener { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        fun text(size: Float = 15f, bold: Boolean = false) = TextView(this).apply {
            textSize = size
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, pad / 2, 0, pad / 2)
        }.also { root.addView(it) }
        fun button(label: String, onClick: () -> Unit) = Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { onClick() }
        }.also { root.addView(it) }

        text(26f, true).text = "Trajet VE"
        status = text()
        signBtn = button("Se connecter avec Google") { if (auth.currentUser == null) signIn() else auth.signOut() }
        button("Ouvrir Trajet VE") { open(SITE_URL) }
        button("Mes recharges") { open(RECHARGES_URL) }
        text(18f, true).text = "Trajet affiché dans la voiture"
        tripTxt = text()
        text(13f).apply {
            alpha = 0.7f
            text = "Le trajet vient du dernier calcul fait sur le site (ou de la feuille de route ouverte), " +
                "avec le même compte Google. Dans la voiture, ouvre Trajet VE depuis le menu des applis Android Auto."
        }
        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onStart() {
        super.onStart()
        auth.addAuthStateListener(authListener)
    }

    override fun onStop() {
        super.onStop()
        auth.removeAuthStateListener(authListener)
        reg?.remove(); reg = null
    }

    private fun open(url: String) {
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(this, Uri.parse(url))
    }

    private fun refresh() {
        val u = auth.currentUser
        reg?.remove(); reg = null
        if (u == null) {
            status.text = "Non connecté. La connexion est nécessaire pour l'écran Android Auto."
            signBtn.text = "Se connecter avec Google"
            tripTxt.text = "—"
            return
        }
        status.text = "Connecté : ${u.email ?: "compte Google"}"
        signBtn.text = "Déconnexion"
        reg = FirebaseFirestore.getInstance().collection("trajetve_car").document(u.uid)
            .addSnapshotListener { s, e ->
                if (e != null) { tripTxt.text = "Lecture impossible : ${e.message}"; return@addSnapshotListener }
                val car = CarState.fromRes(s?.get("res"))
                val t = Trip.parse(s?.getString("actif"))
                val sb = StringBuilder()
                if (car != null) sb.append("Batterie ${car.soc.toInt()} %${car.range?.let { " · ${it.toInt()} km" } ?: ""} (lecture ${ago(car.at)})\n\n")
                if (t == null) sb.append("Aucun trajet : calcule-le sur le site.")
                else {
                    sb.append("${t.from} → ${t.to}\nDépart ${hm(t.dep)}\n")
                    t.stops.forEachIndexed { i, st ->
                        sb.append("\n${i + 1}. ${st.name}\n   ${hm(st.eta)} · ${st.socIn ?: "?"} % → ${st.socOut ?: "?"} % · ${st.dur ?: 0} min ${kwTxt(st.kw)}")
                    }
                    t.end?.let { sb.append("\n\nArrivée ${hm(it.eta)} avec ${it.soc ?: "?"} %") }
                }
                tripTxt.text = sb.toString()
            }
    }

    private fun signIn() {
        lifecycleScope.launch {
            try {
                val option = GetSignInWithGoogleOption.Builder(getString(R.string.default_web_client_id)).build()
                val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
                val result = CredentialManager.create(this@MainActivity).getCredential(this@MainActivity, request)
                val c = result.credential
                if (c is CustomCredential && c.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val token = GoogleIdTokenCredential.createFrom(c.data).idToken
                    auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                } else {
                    status.text = "Connexion impossible : identifiant inattendu."
                }
            } catch (e: GetCredentialCancellationException) {
                // fenêtre fermée par l'utilisateur
            } catch (e: Exception) {
                status.text = "Connexion impossible : ${e.message}"
            }
        }
    }
}
