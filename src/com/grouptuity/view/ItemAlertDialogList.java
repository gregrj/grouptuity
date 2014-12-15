package com.grouptuity.view;

import com.grouptuity.*;
import com.grouptuity.Grouptuity.NumberFormat;
import com.grouptuity.model.Item;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.TextView;

public class ItemAlertDialogList
{
	final private AlertDialog.Builder builder;
	final private Controller controller;
	final private Context context;

	public static interface Controller{public void optionSelected(int i);}

	public ItemAlertDialogList(final Context context1, Controller c)
	{
		context = context1;
		controller = c;
		builder = new AlertDialog.Builder(context);
		builder.setTitle("Choose Item to Apply Discount");
	}

	public void show()
	{
		Item[] items = new Item[Grouptuity.getBill().items.size()];
		Grouptuity.getBill().items.toArray(items);
		final DialogItem[] dialogItems = new DialogItem[items.length];
		for(int i=0; i<dialogItems.length; i++)
			dialogItems[i] = new DialogItem(items[i]);
		
		ListAdapter adapter = new ArrayAdapter<DialogItem>(context,android.R.layout.select_dialog_item,android.R.id.text1,dialogItems)
		{
			public View getView(int position, View convertView, ViewGroup parent)
			{
				View v = super.getView(position, convertView, parent);
				TextView tv = (TextView) v.findViewById(android.R.id.text1);
				tv.setSingleLine();
				tv.setText(dialogItems[position].toString());

				return v;
			}
		};
		builder.setAdapter(adapter, new DialogInterface.OnClickListener()
		{
			public void onClick(DialogInterface dialog, int item){controller.optionSelected(item);}
		});
		builder.show();
	}

	private class DialogItem
	{
		final private String returnString;
		private DialogItem(Item item)
		{
			int numPayers = item.payers.size();
			String name;
			if(Grouptuity.ENABLE_ADVANCED_ITEMS.getValue())
			{
				if(numPayers==1)
					name = item.billableType.name+" ("+item.payers.get(0).name+")";
				else
					name = "Shared "+item.billableType.name+" ("+numPayers+" diners)";
			}
			else
			{
				if(numPayers==1)
					name = "("+item.payers.get(0).name+")";
				else
					name = "Shared ("+numPayers+" diners)";

			}
			returnString = Grouptuity.formatNumber(NumberFormat.CURRENCY, item.value) + " " + name;
		}
		@Override
		public String toString(){return returnString;}
	}
}