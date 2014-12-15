package com.grouptuity.view;

import android.graphics.BitmapFactory;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import com.grouptuity.*;
import com.grouptuity.style.composites.*;

public class TitleBar extends Panel
{
	final public static int LOGO_AND_MENU = 1;
	final public static int BACK_AND_MENU = 2;
	ScalableTextView3 textView;

	public TitleBar(final ActivityTemplate<?> context, String titleText, int titleBarStyle)
	{
		super(context,new TitleBarStyle());
		setOrientation(LinearLayout.HORIZONTAL);
		setGravity(Gravity.CENTER_VERTICAL);

		final Panel leftIcon =  generateLeftIcon(context,titleBarStyle==BACK_AND_MENU);
		leftIcon.setPadding(5,0,0,0);

		final Panel rightIcon = generateMenuIcon(context);

		textView = new ScalableTextView3(activity, new EmbeddedStyleText())
		{
			@Override
			protected void onMeasure(int w, int h)
			{
				determineViewSize(w, h);
				setMeasuredDimension(getMeasuredWidth(), leftIcon.getMeasuredHeight());
				determineTextSize();
			}
		};
		textView.setText(titleText);
		int p = Grouptuity.toActualPixels(10);
		textView.setPaddingOverride(p,p,p,p);

		addView(leftIcon,Grouptuity.wrapWrapLP());
		addView(textView,Grouptuity.wrapWrapLP(1.0f));
		addView(rightIcon,Grouptuity.wrapWrapLP());
	}
	
	private Panel generateLeftIcon(final ActivityTemplate<?> context, final boolean backButton)
	{
		final Panel icon = new Panel(context,new TitleBarStyle())
		{
			protected void addContents()
			{
				ImageView image = new ImageView(context);
				if(backButton)
					image.setBackgroundResource(R.drawable.backicon);
				else
					image.setBackgroundResource(R.drawable.logo_on_blank);
				addView(image,Grouptuity.wrapWrapLP());
			}
		};
		if(backButton)
		{
			icon.setStyle(ViewState.HIGHLIGHTED,new TitleBarStylePressed());
			icon.setOnTouchListener(new InputListenerTemplate(false)
			{
				protected boolean handlePress(){icon.setViewState(ViewState.HIGHLIGHTED);return true;}
				protected boolean handleHold(long holdTime){return true;}
				protected boolean handleRelease(){icon.setViewState(ViewState.DEFAULT);return true;}
				protected boolean handleClick(){context.terminateActivity();return true;}
				protected boolean handleLongClick(){return true;}
			});
		}
		return icon;
	}

	private Panel generateMenuIcon(final ActivityTemplate<?> context)
	{
		final Panel icon = new Panel(context,new TitleBarStyle())
		{
			protected void addContents()
			{
				ImageView image = new ImageView(context);
				image.setImageBitmap(BitmapFactory.decodeResource(context.getResources(),R.drawable.menuicon));
				addView(image,Grouptuity.wrapWrapLP());
			}
		};
		icon.setStyle(ViewState.HIGHLIGHTED,new TitleBarStylePressed());
		icon.setOnTouchListener(new InputListenerTemplate(false)
		{
			protected boolean handlePress(){icon.setViewState(ViewState.HIGHLIGHTED);return true;}
			protected boolean handleHold(long holdTime){return true;}
			protected boolean handleRelease(){icon.setViewState(ViewState.DEFAULT);return true;}
			protected boolean handleClick(){activity.openOptionsMenu();return true;}
			protected boolean handleLongClick(){return true;}
		});
		return icon;
	}

	public void setTitle(String title){textView.setText(title);}
	public float getTextSize(){return textView.getTextSize();}
}