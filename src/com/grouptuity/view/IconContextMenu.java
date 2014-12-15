
package com.grouptuity.view;

import java.util.ArrayList;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.TextView;
import com.grouptuity.*;

public class IconContextMenu implements DialogInterface.OnCancelListener, DialogInterface.OnDismissListener
{
	private static final int LIST_PREFERED_HEIGHT = 100;

	final private Activity activity;
	final private int dialogId;
	final private IconMenuAdapter menuAdapter;
	private IconContextMenuOnClickListener clickHandler;

	public interface IconContextMenuOnClickListener{public abstract void onClick(int menuId);}
	
	public IconContextMenu(ActivityTemplate<?> context, int id)
	{
		activity = context;
		dialogId = id;
		menuAdapter = new IconMenuAdapter(context);
	}

	public void addItem(CharSequence title, int imageResourceId, int actionTag){menuAdapter.addItem(new IconContextMenuItem(activity.getResources(), title, imageResourceId, actionTag));}

	public void setOnClickListener(IconContextMenuOnClickListener listener){clickHandler = listener;}

	public Dialog createMenu(String menuItitle)
	{
		final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		builder.setTitle(menuItitle);
		builder.setAdapter(menuAdapter, new DialogInterface.OnClickListener()
		{
			public void onClick(DialogInterface dialoginterface, int i)
			{
				IconContextMenuItem item = (IconContextMenuItem) menuAdapter.getItem(i);
				if(clickHandler != null)
					clickHandler.onClick(item.actionTag);
			}
		});
		builder.setInverseBackgroundForced(true);

		AlertDialog dialog = builder.create();
		dialog.setOnCancelListener(this);
		dialog.setOnDismissListener(this);
		return dialog;
	}

	public void onCancel(DialogInterface dialog){cleanup();}

	public void onDismiss(DialogInterface dialog){}

	private void cleanup(){activity.dismissDialog(dialogId);}

	private class IconMenuAdapter extends BaseAdapter
	{
		private Context context = null;
		private ArrayList<IconContextMenuItem> mItems = new ArrayList<IconContextMenuItem>();

		public IconMenuAdapter(Context context){this.context = context;}

		public void addItem(IconContextMenuItem menuItem){mItems.add(menuItem);}

		public int getCount(){return mItems.size();}

		public Object getItem(int position){return mItems.get(position);}

		public long getItemId(int position)
		{
			IconContextMenuItem item = (IconContextMenuItem) getItem(position);
			return item.actionTag;
		}

		public View getView(int position, View convertView, ViewGroup parent)
		{
			IconContextMenuItem item = (IconContextMenuItem) getItem(position);
			Resources res = activity.getResources();

			if(convertView == null)
			{
				TextView temp = new TextView(context);
				AbsListView.LayoutParams param = new AbsListView.LayoutParams(AbsListView.LayoutParams.FILL_PARENT, AbsListView.LayoutParams.WRAP_CONTENT);
				temp.setLayoutParams(param);
				temp.setPadding((int)toPixel(res, 15), 0, (int)toPixel(res, 15), 0);
				temp.setGravity(android.view.Gravity.CENTER_VERTICAL);

				Theme th = context.getTheme();
				TypedValue tv = new TypedValue();

				if(th.resolveAttribute(android.R.attr.textAppearanceLargeInverse, tv, true))
					temp.setTextAppearance(context, tv.resourceId);

				temp.setMinHeight(LIST_PREFERED_HEIGHT);
				temp.setCompoundDrawablePadding((int)toPixel(res, 14));
				convertView = temp;
			}

			TextView textView = (TextView) convertView;
			textView.setTag(item);
			textView.setText(item.text);
			textView.setCompoundDrawablesWithIntrinsicBounds(item.image, null, null, null);

			return textView;
		}

		private float toPixel(Resources res, int dip){return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dip, res.getDisplayMetrics());}
	}

	private class IconContextMenuItem
	{
		final public CharSequence text;
		final public Drawable image;
		final public int actionTag;

		public IconContextMenuItem(Resources res, CharSequence title, int imageResourceId, int tag)
		{
			text = title;
			image = res.getDrawable(imageResourceId);
			actionTag = tag;
		}
	}
}