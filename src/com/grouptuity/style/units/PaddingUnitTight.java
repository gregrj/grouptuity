package com.grouptuity.style.units;

import com.grouptuity.Grouptuity;
import com.grouptuity.StyleTemplate;

public class PaddingUnitTight
{
	public static void addTo(StyleTemplate style)
	{
		int padding = Grouptuity.toActualPixels(7);
		style.leftPadding = padding;
		style.topPadding = padding;
		style.rightPadding = padding;
		style.bottomPadding = padding;
	}
}