package app.jfago

import android.content.Context
import android.content.Intent

fun shareCsv(context: Context, filename: String, csv: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_SUBJECT, filename)
        putExtra(Intent.EXTRA_TEXT, csv)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(Intent.createChooser(send, "Export from Allerium").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
