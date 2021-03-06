package com.grouptuity.view;

import android.content.Context;
import android.view.MotionEvent;
import android.widget.ScrollView;

public class LockableScrollView extends ScrollView
{
	public LockableScrollView(Context context){super(context);}

	private boolean locked;
	private int firstPointer;

	public void lock(){locked = true;}
	public void unlock(){locked = false;}

	public boolean onInterceptTouchEvent(MotionEvent e)
	{
		//Hack: ScrollView cannot handle MotionEvents from pointers other than the first pointer so ignore all MotionEvents generated by other pointers
		if(e.getAction()==MotionEvent.ACTION_DOWN)
			firstPointer = e.getPointerId(0);
		else if(e.getPointerId(0)!=firstPointer)
			return false;

		//Ignore the MotionEvent if this is locked
		if(locked)
			return false;
		else
			return super.onInterceptTouchEvent(e);
	}

	public boolean onTouchEvent(MotionEvent e)
	{
		switch(e.getAction())
		{
			case MotionEvent.ACTION_DOWN:
			case MotionEvent.ACTION_MOVE:	if(locked)
												return false;
											else
												return super.onTouchEvent(e);
		}
		return super.onTouchEvent(e);
	}
}