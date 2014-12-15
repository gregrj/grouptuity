package com.grouptuity.style.composites;

import com.grouptuity.style.units.*;

public class BackgroundStyle extends com.grouptuity.StyleTemplate
{
	protected void defineStyle()
	{
		ColorUnitBackground.addTo(this);
		PaddingUnitTight.addTo(this);
	}
}