package com.grouptuity.style.units;

import android.graphics.Color;
import com.grouptuity.Grouptuity;
import com.grouptuity.StyleTemplate;

public class ColorUnitListing
{
	public static void addTo(StyleTemplate style)
	{
		style.primaryColor = Color.WHITE;
		//style.secondaryColor = 0xFFF4F4F4;
		style.textColor = Grouptuity.softTextColor;
		//style.innerGradient = true;
	}
}