package com.grouptuity.mainmenu;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.grouptuity.ActivityTemplate;
import com.grouptuity.ControllerTemplate;
import com.grouptuity.Grouptuity;
import com.grouptuity.InputListenerTemplate;
import com.grouptuity.mainmenu.MainMenuActivity.LaunchActivity;

public class LaunchButton extends LinearLayout
{
	LaunchButtonController controller;
	final private int padding = 10;
	final private int borderRadius = 10;
	final private int measureFactor = 2*(padding+borderRadius);
	final private Paint borderPaint, innerPaint, glowPaint;
	private boolean pressed;

	protected static interface LaunchButtonController extends ControllerTemplate{void onClick();}

	public LaunchButton(ActivityTemplate<MainMenuModel> context, LaunchActivity launch)
	{
		this(context, launch, new String[0], new Parcelable[0]);
	}
	public LaunchButton(ActivityTemplate<MainMenuModel> context, LaunchActivity launch, final String extraName, final Parcelable extra)
	{
		this(context, launch, new String[]{extraName}, new Parcelable[]{extra});
	}
	public LaunchButton(final ActivityTemplate<MainMenuModel> context, final LaunchActivity launch, final String[] extrasNames, final Parcelable[] extras)
	{
		super(context);
		setOrientation(LinearLayout.VERTICAL);
		setGravity(Gravity.CENTER);

		borderPaint = new Paint();
		borderPaint.setAntiAlias(true);
		borderPaint.setColor(0x77C8C8C8);

		innerPaint = new Paint();
		innerPaint.setAntiAlias(true);
		innerPaint.setColor(0xFFFFFFFF);

		glowPaint = new Paint();
		glowPaint.setAntiAlias(true);

		ImageView buttonImage = new ImageView(context);
		buttonImage.setBackgroundResource(launch.imageInt);
		addView(buttonImage,Grouptuity.wrapWrapLP());

		TextView buttonTitle = new TextView(context);
		buttonTitle.setText(launch.activityTitle);
		buttonTitle.setTypeface(null,Typeface.BOLD);
		buttonTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP,16.0f);
		buttonTitle.setGravity(Gravity.CENTER_HORIZONTAL);
		buttonTitle.setTextColor(Grouptuity.softTextColor);
		buttonTitle.setHorizontallyScrolling(true);
		addView(buttonTitle,Grouptuity.wrapWrapLP());

		final View v = this;
		setOnTouchListener(new InputListenerTemplate(false)
		{
			protected boolean handlePress()
			{
				glowPaint.setShader(new LinearGradient(0,0,0,v.getHeight(),0xFFFFC600,0xFFFFAA00,Shader.TileMode.CLAMP));
				pressed = true;
				invalidate();
				return true;
			}
			protected boolean handleHold(long holdTime){return true;}
			protected boolean handleRelease()
			{
				pressed = false;
				invalidate();
				return true;
			}
			protected boolean handleClick(){controller.onClick();return true;}
			protected boolean handleLongClick(){return true;}
		});
	}

	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
	{
		super.onMeasure(widthMeasureSpec,heightMeasureSpec);
		setMeasuredDimension(getMeasuredWidth()+measureFactor,getMeasuredHeight()+measureFactor);
	}

	protected void dispatchDraw(Canvas canvas)
	{
		RectF drawRect = new RectF(0,0,getWidth(),getHeight());
		canvas.drawRoundRect(drawRect,borderRadius,borderRadius,borderPaint);
		drawRect = new RectF(borderRadius,borderRadius,getWidth()-borderRadius,getHeight()-borderRadius);
		canvas.drawRoundRect(drawRect,borderRadius,borderRadius,(pressed)?glowPaint:innerPaint);

		super.dispatchDraw(canvas);
	}

	protected void saveState(Bundle bundle){}
	protected void restoreState(Bundle bundle){}
	protected void applyStyle(){}
	protected void refresh(){}
}