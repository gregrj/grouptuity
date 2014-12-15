package com.grouptuity.view;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import com.grouptuity.*;

//This class is the same as ScalableTextView, but with an alternative algorithm (i.e. no child TextView) which is more suitable for preventing text overflow
//It has not been tested for uses other than the InstructionsBar (e.g. SlidingBar buttons, TitleBar)

public class ScalableTextView2 extends ViewTemplate<ModelTemplate,ControllerTemplate>
{
	final private Paint textPaint;
	final private Rect measureRect;
	final private float tolerance = 0.2f;
	final private View matchView;
	final private ScalingStyle scalingStyle;
	private int matchViewWidth, matchViewHeight;
	private int paddingLeftOverride, paddingTopOverride, paddingRightOverride, paddingBottomOverride;
	private float scalingMultiplier, sizingUnit, textSize;
	private String text;

	public enum ScalingStyle
	{
		MATCH_WIDTH,	//TODO not tested, see onMeasure()
		MATCH_HEIGHT,
		MATCH_WIDTH_AND_HEIGHT,		//TODO not tested, see onMeasure()
		SQUARE_MATCH_WIDTH,		//TODO not tested, see onMeasure()
		SQUARE_MATCH_HEIGHT;		//TODO not tested, see onMeasure()
	}

	@SuppressWarnings("unchecked")
	public ScalableTextView2(ActivityTemplate<?> context, StyleTemplate defaultStyle, View mView, ScalingStyle s)
	{
		super((ActivityTemplate<ModelTemplate>) context,defaultStyle);
		matchView = mView;
		scalingStyle = s;
		scalingMultiplier = 1.0f;
		paddingLeftOverride = -1;
		paddingTopOverride = -1;
		paddingRightOverride = -1;
		paddingBottomOverride = -1;

		measureRect = new Rect();
		textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		textPaint.setAntiAlias(true);	//might not be necessary
		textPaint.setTextSize(30.0f);
	}

	public float getTextSize(){return textSize;}

	public void setText(String str){text = str;requestLayout();}
	public void setTypeface(Typeface tf){textPaint.setTypeface(tf);requestLayout();}
	public void setScalingMultiplier(float m){scalingMultiplier = m;}
	public void setPaddingOverride(int l, int t, int r, int b){paddingLeftOverride = l;paddingTopOverride = t;paddingRightOverride = r;paddingBottomOverride = b;}

	protected void saveState(Bundle bundle){}
	protected void restoreState(Bundle bundle){}
	protected void applyStyle()
	{
		textPaint.setColor(style.textColor);
		setPadding((paddingLeftOverride>=0)?paddingLeftOverride:getPaddingLeft(),(paddingTopOverride>=0)?paddingTopOverride:getPaddingTop(),(paddingRightOverride>=0)?paddingRightOverride:getPaddingRight(),(paddingBottomOverride>=0)?paddingBottomOverride:getPaddingBottom());
	}
	protected void refresh(){}

	protected void onMeasure(int w, int h)
	{
		super.onMeasure(w, h);
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
										setMeasuredDimension(matchViewWidth,getMeasuredHeight());	//TODO not tested since the changeHeight() algorithms were changed
										break;
			case MATCH_HEIGHT:			if(getMeasuredHeight()<matchViewHeight)
											changeHeight(false,matchViewHeight);
										else if(getMeasuredHeight()>matchViewHeight)
											changeHeight(true,matchViewHeight);
										setMeasuredDimension(activity.displayMetrics.widthPixels<getMeasuredWidth()?activity.displayMetrics.widthPixels:getMeasuredWidth(),matchViewHeight);	//TODO this isn't universal and only applies to views that occupy the entire line
										break;
			case MATCH_WIDTH_AND_HEIGHT:if(getMeasuredHeight()<matchViewHeight)
											changeHeight(false,matchViewHeight);
										else if(getMeasuredHeight()>matchViewHeight)
											changeHeight(true,matchViewHeight);
										if(getMeasuredWidth()>matchViewWidth)
											changeWidth(true,matchViewWidth);
										setMeasuredDimension(matchViewWidth,matchViewHeight);	//TODO not tested since the changeHeight() algorithms were changed 
										break;
			case SQUARE_MATCH_WIDTH:	if(getMeasuredWidth()<matchViewWidth)
											changeWidth(false,matchViewWidth);
										else if(getMeasuredWidth()>matchViewWidth)
											changeWidth(true,matchViewWidth);
										if(getMeasuredHeight()>matchViewWidth)
											changeHeight(true,matchViewWidth);
										setMeasuredDimension(matchViewWidth,matchViewHeight);	//TODO not tested since the changeHeight() algorithms were changed
										break;
			case SQUARE_MATCH_HEIGHT:	if(getMeasuredHeight()<matchViewHeight)
											changeHeight(false,matchViewHeight);
										else if(getMeasuredHeight()>matchViewHeight)
											changeHeight(true,matchViewHeight);
										if(getMeasuredWidth()>matchViewHeight)
											changeWidth(true,matchViewHeight);
										setMeasuredDimension(matchViewHeight,matchViewHeight);	//TODO not tested since the changeHeight() algorithms were changed
										break;
		}

		textPaint.getTextBounds(text,0,text.length(),measureRect);
		while((getPaddingLeft()+measureRect.width()+getPaddingRight())>getMeasuredWidth())
		{
			textSize -= 1f;
			textPaint.setTextSize(textSize);
			textPaint.getTextBounds(text,0,text.length(),measureRect);
			if(textSize<=0)
				return;
		}
	}

	protected void dispatchDraw(Canvas canvas)
	{
		super.dispatchDraw(canvas);
		textPaint.getTextBounds(text,0,text.length(),measureRect);
		canvas.drawText(text,(getMeasuredWidth()-measureRect.width())/2,(getMeasuredHeight()+getPaddingTop()-textPaint.ascent()-getPaddingBottom())/2,textPaint);
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
					newTextSize = textSize-sizingUnit;
					if(newTextSize<=0.0f){sizingUnit /= 2;continue;}
				}
				else
				{
					sizingUnit /= 2;
					newTextSize = textSize+sizingUnit;
					previousWasShrink = false;
				}
			}
			else
			{
				if(getMeasuredWidth()<matchMeasurement)
					newTextSize = textSize+sizingUnit;
				else
				{
					sizingUnit /= 2;
					newTextSize = textSize-sizingUnit;
					previousWasShrink = true;
				}
			}

			textSize = newTextSize;
			textPaint.setTextSize(textSize);
			textPaint.getTextBounds(text, 0, text.length(), measureRect);
			setMeasuredDimension(getPaddingLeft()+measureRect.width()+getPaddingRight(), (int)(getPaddingTop()+textPaint.descent()-textPaint.ascent()+getPaddingBottom()));
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
					newTextSize = textSize-sizingUnit;
					if(newTextSize<=0.0f){sizingUnit /= 2;continue;}
				}
				else
				{
					sizingUnit /= 2;
					newTextSize = textSize+sizingUnit;
					previousWasShrink = false;
				}
			}
			else
			{
				if(getMeasuredHeight()<matchMeasurement)
					newTextSize = textSize+sizingUnit;
				else
				{
					sizingUnit /= 2;
					newTextSize = textSize-sizingUnit;
					previousWasShrink = true;
				}
			}

			textSize = newTextSize;
			textPaint.setTextSize(textSize);
			textPaint.getTextBounds(text, 0, text.length(), measureRect);
			setMeasuredDimension(getPaddingLeft()+measureRect.width()+getPaddingRight(), (int)(getPaddingTop()+textPaint.descent()-textPaint.ascent()+getPaddingBottom()));
		}while(sizingUnit>tolerance && getMeasuredHeight()!=matchMeasurement);
	}
}