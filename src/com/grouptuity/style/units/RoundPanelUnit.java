package com.grouptuity.style.units;

import com.grouptuity.Grouptuity;
import com.grouptuity.StyleTemplate;

public class RoundPanelUnit
{
	public static void addTo(StyleTemplate style)
	{
		int radius = Grouptuity.toActualPixels(20);
		style.topLeftRadius = radius;
		style.topRightRadius = radius;
		style.bottomLeftRadius = radius;
		style.bottomRightRadius = radius;
	}
}