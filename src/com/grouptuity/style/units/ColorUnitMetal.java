package com.grouptuity.style.units;

import android.graphics.Color;
import com.grouptuity.StyleTemplate;

public class ColorUnitMetal
{
	public static void addTo(StyleTemplate style)
	{
		style.primaryColor = Color.WHITE;
		style.secondaryColor = 0xFFC3C3C3;
		style.textColor = Color.DKGRAY;
		style.innerGradient = true;
	}
}