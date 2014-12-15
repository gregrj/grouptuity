package com.grouptuity.itementry;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.grouptuity.*;
import com.grouptuity.style.composites.*;
import com.grouptuity.view.Panel;

public class ItemEntryCommandPanel extends ViewTemplate<ItemEntryModel,ItemEntryCommandPanel.ItemEntryCommandPanelController>
{
	final private Panel panel, addItem, addDebt, addDeal, browse;

	public static interface ItemEntryCommandPanelController extends ControllerTemplate
	{
		public void onAddItem();
		public void onAddDebt();
		public void onAddDeal();
		public void onBrowse();
	}

	public ItemEntryCommandPanel(final ActivityTemplate<ItemEntryModel> context)
	{
		super(context);

		panel = new Panel(context,new RoundForegroundPanelStyle());
		addView(panel,Grouptuity.fillFillLP());

		LinearLayout linearLayout = new LinearLayout(context);
		linearLayout.setOrientation(LinearLayout.VERTICAL);
		panel.addView(linearLayout,Grouptuity.fillFillLP());

		EmbeddedStyle embeddedStyle = new EmbeddedStyle(true,true,false,false);
		embeddedStyle.setGradientSizingView(linearLayout);
		addItem = new Panel(context,embeddedStyle)
		{
			protected void addContents()
			{
				TextView tv = new TextView(context);
				tv.setText("\nAdd Item\n");
				tv.setTextSize(30);
				tv.setTextColor(Color.WHITE);
				tv.setGravity(Gravity.CENTER);
				addView(tv);
			}
		};
		addItem.setStyle(ViewState.HIGHLIGHTED,new EmbeddedStylePressed(true,true,false,false));
		addItem.setOnTouchListener(new InputListenerTemplate(false)
		{
			protected boolean handleRelease(){addItem.setViewState(ViewState.DEFAULT);return true;}
			protected boolean handlePress(){addItem.setViewState(ViewState.HIGHLIGHTED);return true;}
			protected boolean handleLongClick(){return true;}
			protected boolean handleHold(long holdTime){return true;}
			protected boolean handleClick(){controller.onAddItem();return true;}
		});
		linearLayout.addView(addItem,Grouptuity.fillFillLP(1.0f));

		embeddedStyle = new EmbeddedStyle(false,false,false,false);
		embeddedStyle.setGradientSizingView(linearLayout);
		addDebt = new Panel(context,embeddedStyle)
		{
			TextView tv;

			protected void addContents()
			{
				tv = new TextView(context);
				tv.setText("Add\n   Debt   ");
				tv.setTextSize(22);
				tv.setTextColor(Color.WHITE);
				tv.setGravity(Gravity.CENTER);
				addView(tv);
			}
			protected void applyStyle(){tv.setTextColor(style.textColor);}
		};
		addDebt.setStyle(ViewState.HIGHLIGHTED,new EmbeddedStylePressed(false,false,false,false));
		addDebt.setOnTouchListener(new InputListenerTemplate(false)
		{
			protected boolean handleRelease(){addDebt.setViewState(ViewState.DEFAULT);return true;}
			protected boolean handlePress()
			{
				if(addDebt.viewState==ViewState.DISABLED)
					return false;
				addDebt.setViewState(ViewState.HIGHLIGHTED);
				return true;
			}
			protected boolean handleLongClick(){return true;}
			protected boolean handleHold(long holdTime){return true;}
			protected boolean handleClick(){controller.onAddDebt();return true;}
		});

		embeddedStyle = new EmbeddedStyle(false,false,false,false);
		embeddedStyle.setGradientSizingView(linearLayout);
		addDeal = new Panel(context,embeddedStyle)
		{
			TextView tv;

			protected void addContents()
			{
				tv = new TextView(context);
				tv.setText("Add\n   Deal   ");
				tv.setTextSize(22);
				tv.setTextColor(Color.WHITE);
				tv.setGravity(Gravity.CENTER);
				addView(tv);
			}
			protected void applyStyle(){tv.setTextColor(style.textColor);}
		};
		addDeal.setStyle(ViewState.HIGHLIGHTED,new EmbeddedStylePressed(false,false,false,false));
		addDeal.setOnTouchListener(new InputListenerTemplate(false)
		{
			protected boolean handleRelease(){addDeal.setViewState(ViewState.DEFAULT);return true;}
			protected boolean handlePress()
			{
				if(addDeal.viewState==ViewState.DISABLED)
					return false;
				addDeal.setViewState(ViewState.HIGHLIGHTED);
				return true;
			}
			protected boolean handleLongClick(){return true;}
			protected boolean handleHold(long holdTime){return true;}
			protected boolean handleClick(){controller.onAddDeal();return true;}
		});

		LinearLayout bottomRow = new LinearLayout(context);
		bottomRow.setOrientation(LinearLayout.HORIZONTAL);
		LinearLayout.LayoutParams l1 = Grouptuity.fillFillLP(1.0f);
		l1.setMargins(0,0,1,0);
		LinearLayout.LayoutParams l2 = Grouptuity.fillFillLP(1.0f);
		l2.setMargins(1,0,0,0);
		bottomRow.addView(addDebt,l1);
		bottomRow.addView(addDeal,l2);
		LinearLayout.LayoutParams l3 = Grouptuity.fillFillLP(1.0f);
		l3.setMargins(0,2,0,2);
		linearLayout.addView(bottomRow,l3);

		embeddedStyle = new EmbeddedStyle(false,false,true,true);
		embeddedStyle.setGradientSizingView(linearLayout);
		browse = new Panel(context,embeddedStyle)
		{
			protected void addContents()
			{
				TextView tv = new TextView(context);
				tv.setText("Browse Diner List");
				tv.setTextSize(22);
				tv.setTextColor(Color.WHITE);
				tv.setGravity(Gravity.CENTER);
				addView(tv);
			}
		};
		browse.setStyle(ViewState.HIGHLIGHTED,new EmbeddedStylePressed(false,false,true,true));
		browse.setOnTouchListener(new InputListenerTemplate(false)
		{
			protected boolean handleRelease(){browse.setViewState(ViewState.DEFAULT);return true;}
			protected boolean handlePress(){browse.setViewState(ViewState.HIGHLIGHTED);return true;}
			protected boolean handleLongClick(){return true;}
			protected boolean handleHold(long holdTime){return true;}
			protected boolean handleClick(){controller.onBrowse();return true;}
		});
		linearLayout.addView(browse,Grouptuity.fillFillLP(1.0f));
	}

	protected void saveState(Bundle bundle){}
	protected void restoreState(Bundle bundle){}
	protected void applyStyle(){}
	protected void refresh(){}
}