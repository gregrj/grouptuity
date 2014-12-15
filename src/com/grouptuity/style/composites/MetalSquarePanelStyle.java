package com.grouptuity.style.composites;

import com.grouptuity.style.units.*;

public class MetalSquarePanelStyle extends com.grouptuity.StyleTemplate
{
	protected void defineStyle()
	{
		ColorUnitMetal.addTo(this);
		ShadowUnitBottom.addTo(this);
		PaddingUnitTight.addTo(this);
		bottomPaddingCompensationPixels = 5;
	}
}