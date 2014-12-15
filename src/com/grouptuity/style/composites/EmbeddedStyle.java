package com.grouptuity.style.composites;

import com.grouptuity.style.units.*;

public class EmbeddedStyle extends com.grouptuity.StyleTemplate
{
	public EmbeddedStyle(){super();}

	public EmbeddedStyle(boolean roundTopLeft, boolean roundTopRight, boolean roundBottomLeft, boolean roundBottomRight)
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
		ColorUnitEmbedded.addTo(this);
		BorderUnit.addTo(this);
		PaddingUnitTight.addTo(this);
	}
}