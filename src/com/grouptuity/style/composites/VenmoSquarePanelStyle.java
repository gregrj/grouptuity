package com.grouptuity.style.composites;

import com.grouptuity.style.units.*;

public class VenmoSquarePanelStyle extends com.grouptuity.StyleTemplate
{
	protected void defineStyle()
	{
		ColorUnitVenmo.addTo(this);
		ShadowUnitBottom.addTo(this);
		PaddingUnitTight.addTo(this);
		bottomPaddingCompensationPixels = 5;
	}
}