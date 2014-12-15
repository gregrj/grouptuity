package com.grouptuity.view;

import com.grouptuity.Grouptuity;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.TextView;

public class AlertDialogList
{
	private AlertDialog dialog;
	final private AlertDialog.Builder builder;
	final private Controller controller;
	final private boolean hasIcons;

	public static interface Controller
	{
		public void optionSelected(int i);
	}

	public AlertDialogList(final Context context, String[] names, int[] icons, Controller c)
	{
		controller = c;

		hasIcons = (icons!=null);
		final Item[] items = new Item[names.length];
		for(int i=0; i<names.length; i++)
			items[i] = new Item(names[i],hasIcons ? icons[i] : 0);

		ListAdapter adapter = new ArrayAdapter<Item>(context,android.R.layout.select_dialog_item,android.R.id.text1,items)
		{
			public View getView(int position, View convertView, ViewGroup parent)
			{
				View v = super.getView(position, convertView, parent);
				TextView text = (TextView) v.findViewById(android.R.id.text1);
				if(hasIcons)
					text.setCompoundDrawablesWithIntrinsicBounds(items[position].icon, 0, 0, 0);
				text.setCompoundDrawablePadding(Grouptuity.toActualPixels(8));
				return v;
			}
		};

		builder = new AlertDialog.Builder(context);
		builder.setAdapter(adapter, new DialogInterface.OnClickListener()
		{
			public void onClick(DialogInterface dialog, int item){controller.optionSelected(item);}
		});
	}

	public void dismiss(){if(dialog!=null)dialog.dismiss();}
	public void show(){dialog = builder.show();}
	public void setTitle(String title){builder.setTitle(title);}

	private class Item
	{
		final private String text;
		final private int icon;
		private Item(String str, int i){text = str;icon = i;}
		@Override
		public String toString(){return text;}
	}
}