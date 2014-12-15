package com.grouptuity.style.composites;

import com.grouptuity.style.units.*;

public class ListingStyle extends com.grouptuity.StyleTemplate
{
	protected void defineStyle()
	{
		ColorUnitListing.addTo(this);
		PaddingUnitTight.addTo(this);
	}
}