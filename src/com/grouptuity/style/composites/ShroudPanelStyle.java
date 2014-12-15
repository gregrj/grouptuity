package com.grouptuity.style.composites;

import com.grouptuity.style.units.*;

public class ShroudPanelStyle extends com.grouptuity.StyleTemplate
{
	protected void defineStyle()
	{
		ColorUnitShroud.addTo(this);
		BorderUnit.addTo(this);
		RoundPanelUnit.addTo(this);
		ShadowUnitSide.addTo(this);
	}
}