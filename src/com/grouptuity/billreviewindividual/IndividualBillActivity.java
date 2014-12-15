package com.grouptuity.billreviewindividual;

import android.os.Bundle;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;

import com.grouptuity.*;
import com.grouptuity.view.InstructionsBar;
import com.grouptuity.view.TitleBar;

public class IndividualBillActivity extends ActivityTemplate<IndividualBillModel>
{
	private ViewPager billViewPager;
	private InstructionsBar instructions;

	protected void createActivity(Bundle bundle)
	{
		model = new IndividualBillModel(this,bundle);
		menuInt = R.menu.individual_bill_review_menu;
		rootLayout.setBackgroundColor(Grouptuity.backgroundColor);

		titleBar = new TitleBar(this,"Personalized Bills",TitleBar.BACK_AND_MENU);
		mainLinearLayout.addView(titleBar,Grouptuity.fillWrapLP());

		instructions = new InstructionsBar(this);
		instructions.setInstructionsText("Swipe to view bills for other diners");

		billViewPager = new ViewPager(this);
		billViewPager.setOffscreenPageLimit(Grouptuity.getBill().diners.size());
		billViewPager.setAdapter(new BillPagerAdapter());
		billViewPager.setCurrentItem(Grouptuity.getBill().diners.indexOf(model.currentDiner));
	}

	protected void createHorizontalLayout(){}
	protected void createVerticalLayout()
	{
		LinearLayout.LayoutParams ibLP = new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT);
		ibLP.bottomMargin = -instructions.panel.style.bottomContraction;
		mainLinearLayout.addView(instructions,ibLP);
		mainLinearLayout.addView(billViewPager,Grouptuity.fillWrapLP());
	}
	protected String createHelpString()
	{
		return	"View personalized bills with individual items and payment instructions. Swipe your "+
				"finger left and right to see bills for different diners. Hit the back button to "+
				"return to the overall bill review.";
	}

	@Override
	protected GrouptuityPreference<Boolean> getTutorialPopupFlag(){return Grouptuity.SHOW_TUTORIAL_INDIVBILL;}


	protected void processStateChange(int state){}

	private class BillPagerAdapter extends PagerAdapter
	{
		public Object instantiateItem(View collection, int position)
		{
			IndividualBillPanel billPanel = new IndividualBillPanel(activity, Grouptuity.getBill().diners.get(position));
			((ViewPager) collection).addView(billPanel,Grouptuity.fillWrapLP());
			return billPanel;
		}
		public void destroyItem(ViewGroup collection, int position, Object view){collection.removeView((View)view);}
		public int getCount(){return Grouptuity.getBill().diners.size();}
		public boolean isViewFromObject(View view, Object object){return view==((IndividualBillPanel)object);}
	}
}