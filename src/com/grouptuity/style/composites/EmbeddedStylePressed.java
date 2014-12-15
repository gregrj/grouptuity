package com.grouptuity.style.composites;

import com.grouptuity.style.units.*;

public class EmbeddedStylePressed extends com.grouptuity.StyleTemplate
{
	public EmbeddedStylePressed(){super();}

	public EmbeddedStylePressed(boolean roundTopLeft, boolean roundTopRight, boolean roundBottomLeft, boolean roundBottomRight)
	{
		if(roundTopLeft)
			topLeftRadius = 20;
		if(roundTopRight)
			topRightRadius = 20;
		if(roundBottomLeft)
			bottomLeftRadius = 20;
		if(roundBottomRight)
			bottomRightRadius = 20;
		defineStyle();
		preprocess();
	}

	protected void defineStyle()
	{
		ColorUnitEmbeddedPressed.addTo(this);
		BorderUnit.addTo(this);
		PaddingUnitTight.addTo(this);
	}
}