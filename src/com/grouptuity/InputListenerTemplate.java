package com.grouptuity;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

public abstract class InputListenerTemplate implements View.OnTouchListener
{
	final private boolean longClickEnabled, defaultReturnBoolean;
	protected MotionEvent event;
	private boolean recordingInput;

	protected InputListenerTemplate(boolean longClick){this(longClick, false);}
	protected InputListenerTemplate(boolean longClick, boolean defaultReturn)
	{
		longClickEnabled = longClick;
		defaultReturnBoolean = defaultReturn;
		recordingInput = false;
	}

	public boolean onTouch(View v, MotionEvent e)
	{
		event = e;

		switch(e.getAction())
		{
			case MotionEvent.ACTION_DOWN:	recordingInput = true;
											return handlePress();
			case MotionEvent.ACTION_MOVE:	if(recordingInput)
												if(!testBounds(v,e))
												{
													recordingInput = false;
													return handleRelease();
												}
												else if(longClickEnabled && e.getEventTime()-e.getDownTime() > ViewConfiguration.getLongPressTimeout())
												{
													recordingInput = false;
													handleRelease();
													return handleLongClick();
												}
												else
													return handleHold(e.getEventTime()-e.getDownTime());
											break;
			case MotionEvent.ACTION_UP:		if(recordingInput)
											{
												recordingInput = false;
												handleRelease();
												return handleClick();
											}break;
			case MotionEvent.ACTION_CANCEL: if(recordingInput)
											{
												recordingInput = false;
												return handleRelease();
											}
											break;
			default:	Grouptuity.log("Unknown InputListenterTemplate Event: "+e.getAction());break;
			
		}
		return defaultReturnBoolean;
	}

	protected boolean testBounds(View v, MotionEvent e)
	{
		if(e.getX()>=0 && e.getX()<v.getWidth() && e.getY()>=0 && e.getY()<v.getHeight())
			return true;
		else
			return false;
	}

	protected abstract boolean handlePress();
	protected abstract boolean handleHold(long holdTime);
	protected abstract boolean handleRelease();
	protected abstract boolean handleClick();
	protected abstract boolean handleLongClick();
}