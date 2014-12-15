package com.grouptuity.style.composites;

import com.grouptuity.style.units.*;

public class ListingStyleHighlighted extends com.grouptuity.StyleTemplate
{
	protected void defineStyle()
	{
		ColorUnitListingHighlighted.addTo(this);
		PaddingUnitTight.addTo(this);
	}
}