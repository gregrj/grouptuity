package com.grouptuity.billreview;

import android.os.Bundle;
import com.grouptuity.*;
import com.grouptuity.model.BillModelTemplate;

public class BillReviewModel extends BillModelTemplate
{
	//ACTIVITY STATES
	final static int DEFAULT = 0;
	final static int SET_EMAILS = 1;

	public BillReviewModel(ActivityTemplate<?> activity, Bundle bundle){super(activity,bundle);}

	protected void saveState(Bundle bundle){}
	protected void restoreState(Bundle bundle){}
}