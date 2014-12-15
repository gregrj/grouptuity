package com.grouptuity.view;

import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import com.grouptuity.*;
import com.grouptuity.view.Panel;
import com.grouptuity.view.ScalableTextView2;
import com.grouptuity.style.composites.*;

public class InstructionsBar extends ViewTemplate<ModelTemplate,InstructionsBar.InstructionsBarController>
{
	public Panel panel;
	private ScalableTextView2 textView;
	private String instructionsText;
	private boolean venmoMode, venmoModeActive;

	public static interface InstructionsBarController extends ControllerTemplate{public void onClick();}

	@SuppressWarnings("unchecked")
	public InstructionsBar(final ActivityTemplate<? extends ModelTemplate> context)
	{
		super((ActivityTemplate<ModelTemplate>)context);
		context.assignID(this);

		panel = new Panel(context,new MetalSquarePanelStyle2());
		panel.setStyle(ViewState.AUXILIARY,new VenmoSquarePanelStyle2());
		panel.setStyle(ViewState.HIGHLIGHTED,new VenmoSquarePanelStyle2Pressed());
		panel.setOrientation(LinearLayout.HORIZONTAL);
		panel.setGravity(Gravity.CENTER);
		addView(panel,Grouptuity.fillFillLP());

		textView = new ScalableTextView2(context, new MetalSquarePanelStyle(), context.titleBar, ScalableTextView2.ScalingStyle.MATCH_HEIGHT);
		textView.setStyle(ViewState.AUXILIARY,new VenmoSquarePanelStyle());
		textView.setStyle(ViewState.HIGHLIGHTED,new VenmoSquarePanelStylePressed());
		textView.setText(" ");
		panel.addView(textView,Grouptuity.wrapFillLP(1.0f));

		setOnTouchListener(new InputListenerTemplate(false)
		{
			protected boolean handlePress()
			{
				if(venmoModeActive)
					panel.setViewState(ViewState.HIGHLIGHTED);
				return true;
			}
			protected boolean handleHold(long holdTime){return true;}
			protected boolean handleRelease()
			{
				if(venmoModeActive)
					panel.setViewState(ViewState.AUXILIARY);
				return true;
			}
			protected boolean handleClick()
			{
				if(venmoModeActive && controller!=null)
					controller.onClick();
				return true;
			}
			protected boolean handleLongClick(){return true;}
		});
	}

	protected void saveState(Bundle bundle){}
	protected void restoreState(Bundle bundle){}
	protected void applyStyle(){}
	protected void refresh()
	{
		if(venmoMode)
		{
			int numPendingVenmo = Grouptuity.getBill().getNumPendingVenmoPayments();
			if(numPendingVenmo>0)
			{
				panel.setViewState(ViewState.AUXILIARY);
				textView.setViewState(ViewState.AUXILIARY);
				textView.setText(getVenmoInstructions(numPendingVenmo));
				venmoModeActive = true;
			}
			else
			{
				panel.setViewState(ViewState.DEFAULT);
				textView.setViewState(ViewState.DEFAULT);
				textView.setText(instructionsText);
				venmoModeActive = false;
			}
		}
		else
			textView.setText(instructionsText);
	}

	public void setInstructionsText(String str){instructionsText = str;refresh();}
	public String getVenmoInstructions(int count){return "Process "+count+" Venmo Transaction"+((count==1)?" ":"s");}

	//too lazy to make a new class for this so just modify the existing bar
	public void activateVenmoMode(){venmoMode = true;}
}