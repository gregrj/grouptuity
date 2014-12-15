package com.grouptuity.style.units;

import android.graphics.Color;
import com.grouptuity.Grouptuity;
import com.grouptuity.StyleTemplate;

public class ColorUnitEmbedded
{
	public static void addTo(StyleTemplate style)
	{
		style.primaryColor = Grouptuity.lightColor;
		style.secondaryColor = Grouptuity.mediumColor;
		style.borderColor = Color.BLACK;
		style.textColor = Color.WHITE;
		style.innerGradient = true;
	}
}