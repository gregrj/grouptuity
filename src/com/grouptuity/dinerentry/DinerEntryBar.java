package com.grouptuity.dinerentry;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import com.grouptuity.*;
import com.grouptuity.model.Diner;
import com.grouptuity.view.Panel;
import com.grouptuity.style.composites.*;

public class DinerEntryBar extends ViewTemplate<DinerEntryModel,DinerEntryBar.DinerEntryBarController>
{
	final protected Panel panel;
	final private LinearLayout focusGrabber; //the use of the focusGrabber here is to avoid a bug with the private TextView.HandleView during detach from window
	final private EditText searchEditText;
	final private ImageButton addButton;
	private boolean suppressRewind;
	private Bitmap add, addDisabeld;

	protected static interface DinerEntryBarController extends ControllerTemplate
	{
		void onTextContactEntry(String text);
		void onTextContactTyping(String text);
		void onAddButtonClick(String text);
	}

	public DinerEntryBar(final ActivityTemplate<DinerEntryModel> context, LinearLayout fg)
	{
		super(context);
		focusGrabber = fg;

		panel = new Panel(context,new MetalSquarePanelStyle());
		panel.setOrientation(LinearLayout.HORIZONTAL);
		panel.setGravity(Gravity.CENTER_VERTICAL);
		addView(panel,Grouptuity.fillFillLP());

		searchEditText = new EditText(context);
		if(model.dinerNameSearch!=null && !model.dinerNameSearch.trim().equals(""))
			searchEditText.setText(model.dinerNameSearch);
		searchEditText.setFreezesText(true);
		searchEditText.setHint("Search for Contacts");
		searchEditText.setHorizontallyScrolling(true);
		searchEditText.setInputType(InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
		searchEditText.setOnClickListener(new OnClickListener(){public void onClick(View v){if(model.getAddedContactBasedOnCurrentSearch())blank();}});
		searchEditText.setOnLongClickListener(new OnLongClickListener(){public boolean onLongClick(View v){if(activity.softKeyboardShowing)return true;else return false;}});
		searchEditText.setOnEditorActionListener(new OnEditorActionListener()
		{
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event)
			{
				//TODO may need to ensure it was an enter
				controller.onTextContactEntry(searchEditText.getText().toString());
				return false;
			}
		});
		searchEditText.addTextChangedListener(new TextWatcher()
		{
			public void onTextChanged(CharSequence s, int start, int before, int count){}
			public void beforeTextChanged(CharSequence s, int start, int count,	int after){}
			public void afterTextChanged(Editable s)
			{
				if(suppressRewind)
					suppressRewind = false;
				else
					controller.onTextContactTyping(s.toString());
			}
		});
		panel.addView(searchEditText,Grouptuity.wrapWrapLP(1.0f));

		add = BitmapFactory.decodeResource(context.getResources(),R.drawable.plus);
		addDisabeld = BitmapFactory.decodeResource(context.getResources(),R.drawable.plus_disabled);
		addButton = new ImageButton(context)
		{
			protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
			{
				setMeasuredDimension(searchEditText.getMeasuredHeight(),searchEditText.getMeasuredHeight());
			}
		};
		addButton.setImageResource(R.drawable.plus_disabled);
		addButton.setOnClickListener(new OnClickListener()
		{
			public void onClick(View v)
			{
				if(model.dinerNameSearch!=null && !model.dinerNameSearch.trim().equals(""))
					controller.onAddButtonClick(model.dinerNameSearch);
			}
		});
		panel.addView(addButton,Grouptuity.wrapWrapLP());
	}
	
	protected void saveState(Bundle bundle)
	{
		bundle.putInt("com.grouptuity."+getId()+"selectionStart",searchEditText.getSelectionStart());
		bundle.putInt("com.grouptuity."+getId()+"selectionEnd",searchEditText.getSelectionEnd());
	}
	protected void restoreState(Bundle bundle)
	{
		if(model.dinerNameSearch!=null && !model.dinerNameSearch.trim().equals(""))
			searchEditText.setSelection(bundle.getInt("com.grouptuity."+getId()+"selectionStart"),bundle.getInt("com.grouptuity."+getId()+"selectionEnd"));
	}
	protected void applyStyle(){}

	@Override
	protected void refresh()
	{
		if(model.dinerNameSearch.equals(""))
		{
			addButton.setImageBitmap(addDisabeld);
			addButton.setEnabled(false);
		}
		else
		{
			boolean enableAddButton = true;
			for(Diner d: Grouptuity.getBill().diners)if(model.dinerNameSearch.compareToIgnoreCase(d.name)==0){enableAddButton = false;break;}
			if(enableAddButton){addButton.setEnabled(true);addButton.setImageBitmap(add);}else{addButton.setEnabled(false);addButton.setImageBitmap(addDisabeld);}
		}
	}
	
	protected void blank()
	{
		suppressRewind = true;
		focusGrabber.requestFocus();
		searchEditText.setText("");
		searchEditText.requestFocus();
	}
	protected void blankWithRewind()
	{
		suppressRewind = false;
		focusGrabber.requestFocus();
		searchEditText.setText("");
		searchEditText.requestFocus();
	}
}