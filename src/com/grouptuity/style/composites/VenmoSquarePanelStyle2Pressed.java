package com.grouptuity.style.composites;

import com.grouptuity.Grouptuity;
import com.grouptuity.style.units.*;

public class VenmoSquarePanelStyle2Pressed extends com.grouptuity.StyleTemplate
{
	protected void defineStyle()
	{
		ColorUnitVenmoPressed.addTo(this);
		bottomContraction = Grouptuity.toActualPixels(10);
		bottomPaddingCompensationPixels = 5;
	}
}