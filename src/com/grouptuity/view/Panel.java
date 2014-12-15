package com.grouptuity.view;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.os.Bundle;
import com.grouptuity.*;

public class Panel extends ViewTemplate<ModelTemplate, ControllerTemplate>
{
	@SuppressWarnings("unchecked")
	public Panel(ActivityTemplate<?> context, StyleTemplate defaultStyle){super((ActivityTemplate<ModelTemplate>) context, defaultStyle);addContents();}

	protected void addContents(){}

	protected void saveState(Bundle bundle){}
	protected void restoreState(Bundle bundle){}
	protected void applyStyle(){}
	protected void refresh(){}

	protected void dispatchDraw(Canvas canvas)
	{
		if(style.shapeDrawable==null)
		{
			RectF drawRect = new RectF(style.leftContraction,style.topContraction,getMeasuredWidth()-style.rightContraction,getMeasuredHeight()-style.bottomContraction);
			canvas.drawRoundRect(drawRect,style.topLeftRadius,style.topLeftRadius,style.innerPaint);
			canvas.drawRoundRect(drawRect,style.topLeftRadius,style.topLeftRadius,style.borderPaint);
		}
		else
			style.shapeDrawable.draw(canvas);
		super.dispatchDraw(canvas);
	}
}