package com.grouptuity.style.units;

import android.graphics.Color;
import com.grouptuity.*;

public class ColorUnitShroud
{
	public static void addTo(StyleTemplate style)
	{
		style.primaryColor = Grouptuity.shroudColor;
		style.borderColor = Grouptuity.shroudColor;
		style.textColor = Color.WHITE;
	}
}