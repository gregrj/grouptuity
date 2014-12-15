package com.grouptuity.style.composites;

import com.grouptuity.style.units.*;

public class VenmoSquarePanelStylePressed extends com.grouptuity.StyleTemplate
{
	protected void defineStyle()
	{
		ColorUnitVenmoPressed.addTo(this);
		ShadowUnitBottom.addTo(this);
		PaddingUnitTight.addTo(this);
		bottomPaddingCompensationPixels = 5;
	}
}