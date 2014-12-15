package com.grouptuity.view;

import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;
import com.grouptuity.*;

public class ScalableTextView extends ViewTemplate<ModelTemplate,ControllerTemplate>
{
	final private float tolerance = 0.1f;
	final private View matchView;
	final private TextView textView;
	final private ScalingStyle scalingStyle;
	private int widthMeasureSpec, heightMeasureSpec, matchViewWidth, matchViewHeight;
	private int paddingLeftOverride, paddingTopOverride, paddingRightOverride, paddingBottomOverride;
	private float scalingMultiplier, sizingUnit;

	public enum ScalingStyle
	{
		MATCH_WIDTH,
		MATCH_HEIGHT,
		MATCH_WIDTH_AND_HEIGHT,
		SQUARE_MATCH_WIDTH,
		SQUARE_MATCH_HEIGHT;
	}

	@SuppressWarnings("unchecked")
	public ScalableTextView(ActivityTemplate<?> context, StyleTemplate defaultStyle, View mView, ScalingStyle s)
	{
		super((ActivityTemplate<ModelTemplate>) context,defaultStyle);
		matchView = mView;
		scalingStyle = s;
		scalingMultiplier = 1.0f;
		paddingLeftOverride = -1;
		paddingTopOverride = -1;
		paddingRightOverride = -1;
		paddingBottomOverride = -1;
		setGravity(Gravity.CENTER);

		textView = new TextView(context);
		textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP,0.0f);
		textView.setGravity(Gravity.CENTER);
		textView.append("\u3000");	//Hack for Android 4.0 to trigger remeasuring of textView when changing text size
		addView(textView);
	}

	public String getText(){return (String) textView.getText();}
	public float getTextSize(){return textView.getTextSize();}

	public void setText(String str){textView.setText(str);}
	public void setTypeface(Typeface tf, int s){textView.setTypeface(tf,s);} //TODO move into applyStyle
	public void setScalingMultiplier(float m){scalingMultiplier = m;}
	public void setPaddingOverride(int l, int t, int r, int b)
	{
		paddingLeftOverride = l;
		paddingTopOverride = t;
		paddingRightOverride = r;
		paddingBottomOverride = b;
	}
	public void setSingleLine(boolean b){textView.setSingleLine(b);}
	public void gravity(int gravity){setGravity(gravity);textView.setGravity(gravity);}
	public void setHorizontallyScrolling(boolean b){textView.setHorizontallyScrolling(b);}

	protected void saveState(Bundle bundle){}
	protected void restoreState(Bundle bundle){}
	protected void applyStyle()
	{
		textView.setTextColor(style.textColor);
		setPadding((paddingLeftOverride>=0)?paddingLeftOverride:getPaddingLeft(),(paddingTopOverride>=0)?paddingTopOverride:getPaddingTop(),(paddingRightOverride>=0)?paddingRightOverride:getPaddingRight(),(paddingBottomOverride>=0)?paddingBottomOverride:getPaddingBottom());
	}
	protected void refresh(){}

	protected void onMeasure(int w, int h)
	{
		super.onMeasure(w, h);
		widthMeasureSpec = w;
		heightMeasureSpec = h;
		matchViewWidth = (int)(scalingMultiplier*matchView.getMeasuredWidth());
		matchViewHeight = (int)(scalingMultiplier*matchView.getMeasuredHeight());
		if(matchViewWidth==0 || matchViewHeight==0){setMeasuredDimension(0,0);return;}

		sizingUnit = 2.0f;
		switch(scalingStyle)
		{
			case MATCH_WIDTH:			if(getMeasuredWidth()<matchViewWidth)
											changeWidth(false,matchViewWidth);
										else if(getMeasuredWidth()>matchViewWidth)
											changeWidth(true,matchViewWidth);
										setMeasuredDimension(matchViewWidth,getMeasuredHeight());
										break;
			case MATCH_HEIGHT:			if(getMeasuredHeight()<matchViewHeight)
											changeHeight(false,matchViewHeight);
										else if(getMeasuredHeight()>matchViewHeight)
											changeHeight(true,matchViewHeight);
										setMeasuredDimension(getMeasuredWidth(),matchViewHeight);
										break;
			case MATCH_WIDTH_AND_HEIGHT:if(getMeasuredHeight()<matchViewHeight)
											changeHeight(false,matchViewHeight);
										else if(getMeasuredHeight()>matchViewHeight)
											changeHeight(true,matchViewHeight);
										if(getMeasuredWidth()>matchViewWidth)
											changeWidth(true,matchViewWidth);
										setMeasuredDimension(matchViewWidth,matchViewHeight);
										break;
			case SQUARE_MATCH_WIDTH:	if(getMeasuredWidth()<matchViewWidth)
											changeWidth(false,matchViewWidth);
										else if(getMeasuredWidth()>matchViewWidth)
											changeWidth(true,matchViewWidth);
										if(getMeasuredHeight()>matchViewWidth)
											changeHeight(true,matchViewWidth);
										setMeasuredDimension(matchViewWidth,matchViewHeight);
										break;
			case SQUARE_MATCH_HEIGHT:	if(getMeasuredHeight()<matchViewHeight)
											changeHeight(false,matchViewHeight);
										else if(getMeasuredHeight()>matchViewHeight)
											changeHeight(true,matchViewHeight);
										if(getMeasuredWidth()>matchViewHeight)
											changeWidth(true,matchViewHeight);
										setMeasuredDimension(matchViewHeight,matchViewHeight);
										break;
		}
	}

	private void changeWidth(boolean previousWasShrink, float matchMeasurement)
	{
		do
		{
			float newTextSize;
			if(previousWasShrink)
			{
				if(getMeasuredWidth()>matchMeasurement)
				{
					newTextSize = textView.getTextSize()-sizingUnit;
					if(newTextSize<=0.0f){sizingUnit /= 2;continue;}
				}
				else
				{
					sizingUnit /= 2;
					newTextSize = textView.getTextSize()+sizingUnit;
					previousWasShrink = false;
				}
			}
			else
			{
				if(getMeasuredWidth()<matchMeasurement)
					newTextSize = textView.getTextSize()+sizingUnit;
				else
				{
					sizingUnit /= 2;
					newTextSize = textView.getTextSize()-sizingUnit;
					previousWasShrink = true;
				}
			}

			textView.setTextSize(TypedValue.COMPLEX_UNIT_PX,newTextSize);
			super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		}while(sizingUnit>tolerance && getMeasuredWidth()!=matchMeasurement);
	}
	private void changeHeight(boolean previousWasShrink, float matchMeasurement)
	{
		do
		{
			float newTextSize;
			if(previousWasShrink)
			{
				if(getMeasuredHeight()>matchMeasurement)
				{
					newTextSize = textView.getTextSize()-sizingUnit;
					if(newTextSize<=0.0f){sizingUnit /= 2;continue;}
				}
				else
				{
					sizingUnit /= 2;
					newTextSize = textView.getTextSize()+sizingUnit;
					previousWasShrink = false;
				}
			}
			else
			{
				if(getMeasuredHeight()<matchMeasurement)
					newTextSize = textView.getTextSize()+sizingUnit;
				else
				{
					sizingUnit /= 2;
					newTextSize = textView.getTextSize()-sizingUnit;
					previousWasShrink = true;
				}
			}

			textView.setTextSize(TypedValue.COMPLEX_UNIT_PX,newTextSize);
			super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		}while(sizingUnit>tolerance && getMeasuredHeight()!=matchMeasurement);
	}
}