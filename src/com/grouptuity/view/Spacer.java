package com.grouptuity.view;

import android.content.Context;
import android.widget.LinearLayout;

import com.grouptuity.Grouptuity;

public class Spacer extends LinearLayout
{
	private int minSpace = Grouptuity.toActualPixels(10);

	public Spacer(Context context){super(context);}
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
	{
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		setMeasuredDimension((getMeasuredWidth()<minSpace)?minSpace:getMeasuredWidth(),(getMeasuredHeight()<minSpace)?minSpace:getMeasuredHeight());
	}
}