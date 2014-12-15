package com.grouptuity.style.units;

import android.graphics.Color;
import com.grouptuity.Grouptuity;
import com.grouptuity.StyleTemplate;

public class ColorUnitEmbeddedDisabled
{
	public static void addTo(StyleTemplate style)
	{
		style.primaryColor = Grouptuity.darkColor;
		style.secondaryColor = Grouptuity.darkColor;
		style.borderColor = Color.BLACK;
		style.textColor = Color.GRAY;
	}
}