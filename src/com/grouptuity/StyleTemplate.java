package com.grouptuity;

import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.ShapeDrawable.ShaderFactory;
import android.graphics.drawable.shapes.RoundRectShape;
import android.graphics.Shader;
import android.view.View;
import android.widget.LinearLayout;

public class StyleTemplate
{
	public ShapeDrawable shapeDrawable;
	public Paint borderPaint, innerPaint;
	private LinearLayout gradientSizingLL;
	private int oldWidth, oldHeight;

	public int primaryColor;
	public int secondaryColor;
	public int borderColor;
	public int textColor;
	public int shadowColor;

	public int bottomPaddingCompensationPixels;
	public boolean circularSides;
	public int borderThickness, shadowRadius, shadowDX, shadowDY;
	public int topLeftRadius, topRightRadius, bottomLeftRadius, bottomRightRadius;
	public int leftContraction, topContraction, rightContraction, bottomContraction;
	public int leftPadding, topPadding, rightPadding, bottomPadding;
	public boolean innerGradient;

	public StyleTemplate(){defineStyle();preprocess();}

	protected void preprocess()
	{
		if(!(topLeftRadius==topRightRadius && bottomLeftRadius==bottomRightRadius && topLeftRadius==bottomLeftRadius))
		{
			shapeDrawable = new ShapeDrawable(new RoundRectShape(new float[]{topLeftRadius,topLeftRadius,topRightRadius,topRightRadius,bottomLeftRadius,bottomLeftRadius,bottomRightRadius,bottomRightRadius},null,null));
			shapeDrawable.setShaderFactory(new ShaderFactory(){public Shader resize(int width, int height){return new LinearGradient(0,0,0,height,primaryColor,secondaryColor,Shader.TileMode.CLAMP);}});
			innerPaint = null;
			borderPaint = null;
		}
		else
		{
			shapeDrawable = null;

			borderPaint = new Paint();
			borderPaint.setAntiAlias(true);
			borderPaint.setStyle(Style.STROKE);
			borderPaint.setStrokeWidth(borderThickness);
			borderPaint.setColor((borderThickness==0)?Color.TRANSPARENT:borderColor);

			innerPaint = new Paint();
			innerPaint.setAntiAlias(true);
			if(!innerGradient)
				innerPaint.setColor(primaryColor);

			if(shadowColor!=0)
			{
				innerPaint.setShadowLayer(shadowRadius,shadowDX,shadowDY,shadowColor);
			}

			if(!circularSides && topLeftRadius > 0)
			{
				leftPadding = (int)(topLeftRadius*0.75);
				topPadding = (int)(topLeftRadius*0.75);
				rightPadding = (int)(topLeftRadius*0.75);
				bottomPadding = (int)(topLeftRadius*0.75);
			}
		}

		leftPadding += leftContraction;
		topPadding += topContraction;
		rightPadding += rightContraction;
		bottomPadding += bottomContraction;
	}

	protected void forceApply(final ViewTemplate<?,?> vt){apply(vt, true);}
	protected void apply(final ViewTemplate<?,?> vt){apply(vt, false);}
	private void apply(final ViewTemplate<?,?> vt, boolean forced)
	{
		if(oldWidth!=vt.getWidth() || oldHeight!=vt.getHeight() || forced)
		{
			oldWidth = vt.getMeasuredWidth();
			oldHeight = vt.getMeasuredHeight();

			if(circularSides)
			{
				int rectangleRadius = vt.getHeight()/2;
				topLeftRadius = rectangleRadius;
				topRightRadius = rectangleRadius;
				bottomLeftRadius = rectangleRadius;
				bottomRightRadius = rectangleRadius;
				leftPadding = rectangleRadius+leftContraction;
				rightPadding = rectangleRadius+rightContraction;
			}

			if(shapeDrawable!=null)
				shapeDrawable.setBounds(leftContraction, topContraction, vt.getMeasuredWidth()-rightContraction, vt.getMeasuredHeight()-bottomContraction);
		}

		vt.setPadding(leftPadding,topPadding,rightPadding,bottomPadding-bottomPaddingCompensationPixels);

		if(innerGradient)
		{
			if(shapeDrawable==null)
				innerPaint.setShader(new LinearGradient(0,0,0,vt.getMeasuredHeight(),primaryColor,secondaryColor,Shader.TileMode.CLAMP));
			else if(gradientSizingLL!=null)
			{
				shapeDrawable.setShaderFactory(new ShaderFactory()
				{
					public Shader resize(int width, int height)
					{
						int startPos = 0;
						for(int i=0; i<gradientSizingLL.getChildCount(); i++)
						{
							View v = gradientSizingLL.getChildAt(i);
							if(v.equals(vt) || v.equals(vt.getParent()))
								break;
							else
								startPos += v.getMeasuredHeight();
						}

						final double fullSpan = gradientSizingLL.getHeight();
						final int startA = (primaryColor >> 24) & 0xFF;
						final int endA = (secondaryColor >> 24) & 0xFF;
						final int startR = (primaryColor >> 16) & 0xFF;
						final int endR = (secondaryColor >> 16) & 0xFF;
						final int startG = (primaryColor >> 8) & 0xFF;
						final int endG = (secondaryColor >> 8) & 0xFF;
						final int startB = (primaryColor >> 0) & 0xFF;
						final int endB = (secondaryColor >> 0) & 0xFF;
						final int actualA1 = (int)(startA + (endA-startA)*startPos/fullSpan);
						final int actualA2 = (int)(startA + (endA-startA)*(startPos+vt.getHeight())/fullSpan);
						final int actualR1 = (int)(startR + (endR-startR)*startPos/fullSpan);
						final int actualR2 = (int)(startR + (endR-startR)*(startPos+vt.getHeight())/fullSpan);
						final int actualG1 = (int)(startG + (endG-startG)*startPos/fullSpan);
						final int actualG2 = (int)(startG + (endG-startG)*(startPos+vt.getHeight())/fullSpan);
						final int actualB1 = (int)(startB + (endB-startB)*startPos/fullSpan);
						final int actualB2 = (int)(startB + (endB-startB)*(startPos+vt.getHeight())/fullSpan);

						return new LinearGradient(0,0,0,vt.getHeight(),Color.argb(actualA1,actualR1,actualG1,actualB1),Color.argb(actualA2,actualR2,actualG2,actualB2),Shader.TileMode.CLAMP);
					}
				});
			}
		}

		vt.applyStyle();
		vt.requestLayout();
		vt.invalidate();
	}

	protected void defineStyle(){}

	public void setGradientSizingView(LinearLayout gsll){gradientSizingLL = gsll;}
}