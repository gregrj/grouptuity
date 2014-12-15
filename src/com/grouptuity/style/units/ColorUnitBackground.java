package com.grouptuity.style.units;

import android.graphics.Color;
import com.grouptuity.Grouptuity;
import com.grouptuity.StyleTemplate;

public class ColorUnitBackground
{
	public static void addTo(StyleTemplate style)
	{
		style.primaryColor = Grouptuity.backgroundColor;
		style.textColor = Color.BLACK;
	}
}