package com.grouptuity.view;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.FontMetrics;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import com.grouptuity.*;

public class ScalableTextView3 extends Panel
{
	final private Paint textPaint;
	final private Rect measureRect;
	final private float tolerance = 0.2f;
	private int maxWidth, maxHeight;
	private float sizingUnit, textSize;
	private int paddingLeftOverride, paddingTopOverride, paddingRightOverride, paddingBottomOverride;
	private String text;

	@SuppressWarnings("unchecked")
	public ScalableTextView3(ActivityTemplate<?> context, StyleTemplate defaultStyle)
	{
		super((ActivityTemplate<ModelTemplate>) context,defaultStyle);
		textSize = 30.0f;
		text = "";
		paddingLeftOverride = -1;
		paddingTopOverride = -1;
		paddingRightOverride = -1;
		paddingBottomOverride = -1;

		measureRect = new Rect();
		textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		textPaint.setTextAlign(Paint.Align.CENTER);
		textPaint.setAntiAlias(true);	//might not be necessary
		textPaint.setTextSize(textSize);
	}

	public float getTextSize(){return textSize;}
	public void setText(String str){text = str;requestLayout();}
	public void setTypeface(Typeface tf){textPaint.setTypeface(tf);requestLayout();}
	public void setPaddingOverride(int l, int t, int r, int b){paddingLeftOverride = l;paddingTopOverride = t;paddingRightOverride = r;paddingBottomOverride = b;}

	protected void saveState(Bundle bundle){}
	protected void restoreState(Bundle bundle){}
	protected void applyStyle()
	{
		textPaint.setColor(style.textColor);
		setPadding((paddingLeftOverride>=0)?paddingLeftOverride:getPaddingLeft(),(paddingTopOverride>=0)?paddingTopOverride:getPaddingTop(),(paddingRightOverride>=0)?paddingRightOverride:getPaddingRight(),(paddingBottomOverride>=0)?paddingBottomOverride:getPaddingBottom());
	}
	protected void refresh(){}

	protected void onMeasure(int w, int h){determineViewSize(w, h);determineTextSize();}

	protected void determineViewSize(int w, int h){super.onMeasure(w, h);}
	protected void determineTextSize()
	{
		maxWidth = getMeasuredWidth();
		maxHeight = getMeasuredHeight();
		if(maxWidth==0 || maxHeight==0){return;}

		sizingUnit = 2.0f;
		boolean previousWasShrink = false;
		float newTextSize = 0.0f;
		while(sizingUnit>tolerance)
		{
			textPaint.getTextBounds(text, 0, text.length(), measureRect);
			FontMetrics fm = textPaint.getFontMetrics();
		//	Grouptuity.log("iterate: "+textSize+", "+fm.ascent+", "+fm.descent+", "+(fm.descent-fm.ascent+getPaddingTop()+getPaddingBottom())+", "+maxHeight);
			if((measureRect.width()+getPaddingLeft()+getPaddingRight())>maxWidth || (fm.descent-fm.ascent+getPaddingTop()+getPaddingBottom())>maxHeight)
			{
				if(previousWasShrink)
				{
					newTextSize = textSize-sizingUnit;
					if(newTextSize<=0.0f){sizingUnit /= 2;continue;}
				}
				else
				{
					sizingUnit /= 2;
					newTextSize = textSize-sizingUnit;
					previousWasShrink = true;
				}
			}
			else
			{
				if(previousWasShrink)
				{
					sizingUnit /= 2;
					newTextSize = textSize+sizingUnit;
					previousWasShrink = false;
				}
				else
					newTextSize = textSize+sizingUnit;
			}
			textSize = newTextSize;
			textPaint.setTextSize(textSize);	
		}
	}

	protected void dispatchDraw(Canvas canvas)
	{
		super.dispatchDraw(canvas);
	//	textPaint.getTextBounds(text,0,text.length(),measureRect);
	//	Grouptuity.log("text: "+getMeasuredHeight()+", "+measureRect.height()+", "+getPaddingTop()+", "+getPaddingBottom());
	//	FontMetrics fm = textPaint.getFontMetrics();
	//	Grouptuity.log("metricstext: "+fm.ascent+", "+fm.bottom+", "+fm.descent+", "+fm.leading+", "+fm.top);
		canvas.drawText(text,getMeasuredWidth()/2,getPaddingTop()-textPaint.ascent(),textPaint);
		//canvas.drawText(text,(getMeasuredWidth()-measureRect.width())/2,(getMeasuredHeight()+getPaddingTop()-textPaint.ascent()-getPaddingBottom())/2,textPaint);
	}
}