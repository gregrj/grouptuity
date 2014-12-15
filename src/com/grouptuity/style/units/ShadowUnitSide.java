package com.grouptuity.style.units;

import com.grouptuity.Grouptuity;
import com.grouptuity.StyleTemplate;

public class ShadowUnitSide
{
	public static void addTo(StyleTemplate style)
	{
		style.shadowColor = 0x80000000;
		style.shadowRadius = Grouptuity.toActualPixels(3);
		int contraction = Grouptuity.toActualPixels(10);
		style.leftContraction = contraction;
		style.topContraction = contraction;
		style.rightContraction = contraction;
		style.bottomContraction = contraction;
		style.shadowDX = Grouptuity.toActualPixels(7);
		style.shadowDY = Grouptuity.toActualPixels(7);
	}
}