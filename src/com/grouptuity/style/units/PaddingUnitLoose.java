package com.grouptuity.style.units;

import com.grouptuity.Grouptuity;
import com.grouptuity.StyleTemplate;

public class PaddingUnitLoose
{
	public static void addTo(StyleTemplate style)
	{
		int padding = Grouptuity.toActualPixels(20);
		style.leftPadding = padding;
		style.topPadding = padding;
		style.rightPadding = padding;
		style.bottomPadding = padding;
	}
}