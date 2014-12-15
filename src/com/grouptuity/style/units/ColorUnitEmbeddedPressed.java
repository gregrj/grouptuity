package com.grouptuity.style.units;

import android.graphics.Color;
import com.grouptuity.Grouptuity;
import com.grouptuity.StyleTemplate;

public class ColorUnitEmbeddedPressed
{
	public static void addTo(StyleTemplate style)
	{
		style.primaryColor = Grouptuity.mediumColor;
		style.secondaryColor = Grouptuity.darkColor;
		style.borderColor = Color.BLACK;
		style.textColor = Color.WHITE;
		style.innerGradient = true;
	}
}