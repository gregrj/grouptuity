package com.grouptuity.style.composites;

import com.grouptuity.style.units.*;

public class RoundForegroundPanelStyle extends com.grouptuity.StyleTemplate
{
	protected void defineStyle()
	{
		ColorUnitForeground.addTo(this);
		BorderUnit.addTo(this);
		RoundPanelUnit.addTo(this);
		ShadowUnitSide.addTo(this);
	}
}