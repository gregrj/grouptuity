package com.grouptuity.style.composites;

import com.grouptuity.style.units.*;

public class EmbeddedStyleText extends com.grouptuity.StyleTemplate
{
	protected void defineStyle()
	{
		ColorUnitEmbeddedText.addTo(this);
		PaddingUnitTight.addTo(this);
	}
}