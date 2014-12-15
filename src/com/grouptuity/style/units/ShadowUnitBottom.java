package com.grouptuity.style.units;

import com.grouptuity.Grouptuity;
import com.grouptuity.StyleTemplate;

public class ShadowUnitBottom
{
	public static void addTo(StyleTemplate style)
	{
		style.shadowColor = 0x80000000;
		style.shadowRadius = Grouptuity.toActualPixels(3);
		style.bottomContraction = Grouptuity.toActualPixels(10);
		style.shadowDY = Grouptuity.toActualPixels(7);
	}
}