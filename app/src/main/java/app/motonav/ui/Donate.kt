package app.motonav.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.motonav.i18n.tr

/**
 * «Κέρασέ με έναν καφέ»: ανοίγει τη σελίδα δωρεάς (PayPal.me / Buy Me a Coffee / Revolut κ.λπ.).
 * Ο σύνδεσμος ορίζεται εδώ· αν είναι κενός, το κουμπί δεν φαίνεται.
 */
object Donate {
    const val URL = "https://paypal.me/STAVROSMALAGARDIS"
}

@Composable
fun DonateButton(modifier: Modifier = Modifier.fillMaxWidth()) {
    if (Donate.URL.isBlank()) return
    val context = LocalContext.current
    TextChoice(
        tr("☕  Κέρασέ με έναν καφέ"),
        selected = false,
        onClick = {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Donate.URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        },
        modifier = modifier,
    )
}
