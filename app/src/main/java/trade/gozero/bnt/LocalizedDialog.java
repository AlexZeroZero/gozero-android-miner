package trade.gozero.bnt;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;

/** Translate the display copy, leaving click listeners and payloads intact. */
final class LocalizedDialog extends AlertDialog.Builder {
    LocalizedDialog(Context context){super(context);}
    @Override public AlertDialog.Builder setTitle(CharSequence value){return super.setTitle(L10n.text(value.toString()));}
    @Override public AlertDialog.Builder setMessage(CharSequence value){return super.setMessage(L10n.text(value.toString()));}
    @Override public AlertDialog.Builder setPositiveButton(CharSequence value,DialogInterface.OnClickListener click){return super.setPositiveButton(L10n.text(value.toString()),click);}
    @Override public AlertDialog.Builder setNegativeButton(CharSequence value,DialogInterface.OnClickListener click){return super.setNegativeButton(L10n.text(value.toString()),click);}
}
