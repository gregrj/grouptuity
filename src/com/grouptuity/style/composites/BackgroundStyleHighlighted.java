package com.grouptuity.style.composites;

import com.grouptuity.style.units.*;

public class BackgroundStyleHighlighted extends com.grouptuity.StyleTemplate
{
	protected void defineStyle()
	{
		ColorUnitListingHighlighted.addTo(this);
		PaddingUnitTight.addTo(this);
	}
}