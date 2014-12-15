package com.grouptuity.style.composites;

import com.grouptuity.style.units.*;

public class ShroudLabelStyle extends com.grouptuity.StyleTemplate
{
	protected void defineStyle()
	{
		ColorUnitShroud.addTo(this);
		PaddingUnitTight.addTo(this);
		this.circularSides = true;
	}
}