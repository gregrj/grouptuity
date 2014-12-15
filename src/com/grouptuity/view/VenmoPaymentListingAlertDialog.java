package com.grouptuity.view;

import com.grouptuity.*;
import com.grouptuity.Grouptuity.NumberFormat;
import com.grouptuity.model.Payment;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.TextView;

public class VenmoPaymentListingAlertDialog
{
	final private AlertDialog.Builder builder;
	private AlertDialog dialog;

	public static interface Controller{public void paymentSelected(Payment p);}

	public VenmoPaymentListingAlertDialog(final ActivityTemplate<?> activity, final Payment[] payments, Controller c)
	{
		final Controller parentController = c;

		ListAdapter adapter = new ArrayAdapter<Payment>((Context)activity,R.layout.venmo_payment_listing_alert_dialog_item,R.id.vpladi_name,payments)
				{
					public View getView(int position, View convertView, ViewGroup parent)
					{
						View v = super.getView(position, convertView, parent);
						Payment p = payments[position];
						int icon;

						TextView amountTV = ((TextView)v.findViewById(R.id.vpladi_amount));

						if(p.payee==Grouptuity.getSelf())
						{
							((TextView)v.findViewById(R.id.vpladi_name)).setText(p.payer.name);
							icon = R.drawable.venmocharge;
							amountTV.setText("+"+(Grouptuity.formatNumber(NumberFormat.CURRENCY,p.amount)));
							amountTV.setTextColor(android.os.Build.VERSION.SDK_INT>11 ? 0xFFEEEEEE : 0xFF000000);
							((TextView)v.findViewById(R.id.vpladi_action)).setText("Charge");
						}
						else
						{
							((TextView)v.findViewById(R.id.vpladi_name)).setText(p.payee.name);
							icon = R.drawable.venmopay;
							amountTV.setText("-"+(Grouptuity.formatNumber(NumberFormat.CURRENCY,p.amount)));
							amountTV.setTextColor(0xFFB30000);
							((TextView)v.findViewById(R.id.vpladi_action)).setText("Pay");
						}

						
						((ImageView)v.findViewById(R.id.vpladi_icon)).setImageResource(icon);
						return v;
					}
				};

		builder = new AlertDialog.Builder(activity);
		builder.setIcon(R.drawable.payment_venmo);
		builder.setTitle("Choose Payment");
		builder.setAdapter(adapter, new AlertDialog.OnClickListener()
		{
			@Override
			public void onClick(DialogInterface dialog, int which)
			{
				parentController.paymentSelected(payments[which]);
			}
		});
	}

	public void dismiss(){if(dialog!=null)dialog.dismiss();}
	public void show(){dialog = builder.show();}
	public void setTitle(String title){builder.setTitle(title);}
}