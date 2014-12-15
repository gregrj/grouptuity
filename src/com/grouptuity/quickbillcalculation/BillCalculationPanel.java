package com.grouptuity.quickbillcalculation;

import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;
import com.grouptuity.*;
import com.grouptuity.quickbillcalculation.BillCalculationModel;
import com.grouptuity.view.Panel;
import com.grouptuity.style.composites.*;

public class BillCalculationPanel extends ViewTemplate<BillCalculationModel,BillCalculationPanel.BillCalculationPanelController>
{
	final private Panel panel;
	final private RelativeLayout rawSubtotalRow, taxRow, afterTaxSubtotalRow, tipRow, totalRow;
	final private TextView rawSubtotalLabel, taxLabel, afterTaxSubtotalLabel, tipLabel, totalLabel;
	final private EditText rawSubtotalET, taxPercentET, taxValueET, afterTaxSubtotalET, tipPercentET, tipValueET, totalValueET;
	final public String	BLANK_DISPLAY_STRING = "  --  ";

	protected static interface BillCalculationPanelController extends ControllerTemplate
	{
		void onRawSubtotalClick();
		void onTaxPercentClick();
		void onAfterTaxSubtotalClick();
		void onTaxValueClick();
		void onTipPercentClick();
		void onTipValueClick();
		void onTotalClick();
	}

	private static enum BillCalculationFieldType
	{
		TAX_PERCENT(20.0f,false),
		TAX_VALUE(20.0f,false),
		TIP_PERCENT(20.0f,false),
		TIP_VALUE(20.0f,false),
		PRETAX_SUBTOTAL(22.0f,true),
		TOTAL(25.0f,true);

		final private float textSize;
		final private boolean boldFace;

		private BillCalculationFieldType(float tSize, boolean bold)
		{
			textSize = tSize;
			boldFace = bold;
		}
	}

	protected BillCalculationPanel(ActivityTemplate<BillCalculationModel> context)
	{
		super(context);
		panel = new Panel(context,new RoundForegroundPanelStyle());
		addView(panel,Grouptuity.fillFillLP());

		rawSubtotalRow = new RelativeLayout(context);
		taxRow = new RelativeLayout(context);
		afterTaxSubtotalRow = new RelativeLayout(context);
		tipRow = new RelativeLayout(context);
		totalRow = new RelativeLayout(context);
		panel.addView(rawSubtotalRow,Grouptuity.fillWrapLP(1.0f));
		panel.addView(taxRow,Grouptuity.fillWrapLP(1.0f));
		panel.addView(afterTaxSubtotalRow,Grouptuity.fillWrapLP(1.0f));
		panel.addView(tipRow,Grouptuity.fillWrapLP(1.0f));
		panel.addView(totalRow,Grouptuity.fillWrapLP(1.0f));

		rawSubtotalLabel = new TextView(context);
		rawSubtotalLabel.setText("Subtotal");
		rawSubtotalLabel.setTypeface(Typeface.DEFAULT_BOLD);
		rawSubtotalLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP,22.0f);
		rawSubtotalLabel.setGravity(Gravity.CENTER);
		RelativeLayout.LayoutParams rawSubtotalLabelLP = Grouptuity.relWrapWrapLP(RelativeLayout.ALIGN_PARENT_LEFT);
		rawSubtotalLabelLP.addRule(RelativeLayout.CENTER_VERTICAL);
		rawSubtotalRow.addView(rawSubtotalLabel,rawSubtotalLabelLP);

		taxLabel = new TextView(context);
		taxLabel.setText("Tax");
		taxLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP,22.0f);
		taxLabel.setGravity(Gravity.CENTER);
		RelativeLayout.LayoutParams taxLabelLP = Grouptuity.relWrapWrapLP(RelativeLayout.ALIGN_PARENT_LEFT);
		taxLabelLP.addRule(RelativeLayout.CENTER_VERTICAL);
		taxRow.addView(taxLabel,taxLabelLP);

		afterTaxSubtotalLabel = new TextView(context);
		afterTaxSubtotalLabel.setText("After Tax");
		afterTaxSubtotalLabel.setTypeface(Typeface.DEFAULT_BOLD);
		afterTaxSubtotalLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP,22.0f);
		afterTaxSubtotalLabel.setGravity(Gravity.CENTER);
		RelativeLayout.LayoutParams afterTaxSubtotalLabelLP = Grouptuity.relWrapWrapLP(RelativeLayout.ALIGN_PARENT_LEFT);
		afterTaxSubtotalLabelLP.addRule(RelativeLayout.CENTER_VERTICAL);
		afterTaxSubtotalRow.addView(afterTaxSubtotalLabel,afterTaxSubtotalLabelLP);

		tipLabel = new TextView(context);
		tipLabel.setText("Tip");
		tipLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP,22.0f);
		tipLabel.setGravity(Gravity.CENTER);
		RelativeLayout.LayoutParams tipLabelLP = Grouptuity.relWrapWrapLP(RelativeLayout.ALIGN_PARENT_LEFT);
		tipLabelLP.addRule(RelativeLayout.CENTER_VERTICAL);
		tipRow.addView(tipLabel,tipLabelLP);

		totalLabel = new TextView(context);
		totalLabel.setText("Total");
		totalLabel.setTypeface(Typeface.DEFAULT_BOLD);
		totalLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP,25.0f);
		totalLabel.setGravity(Gravity.CENTER_VERTICAL);
		RelativeLayout.LayoutParams totalLabelLP = Grouptuity.relWrapWrapLP(RelativeLayout.ALIGN_PARENT_LEFT);
		totalLabelLP.addRule(RelativeLayout.CENTER_VERTICAL);
		totalRow.addView(totalLabel,totalLabelLP);

		rawSubtotalET = new BillCalculationField(BillCalculationFieldType.PRETAX_SUBTOTAL){void controllerCallback(){controller.onRawSubtotalClick();}};
		RelativeLayout.LayoutParams rawSubtotalETLP = Grouptuity.relWrapWrapLP(RelativeLayout.ALIGN_PARENT_RIGHT);
		rawSubtotalETLP.addRule(RelativeLayout.CENTER_VERTICAL);
		rawSubtotalRow.addView(rawSubtotalET,rawSubtotalETLP);

		afterTaxSubtotalET = new BillCalculationField(BillCalculationFieldType.PRETAX_SUBTOTAL){void controllerCallback(){controller.onAfterTaxSubtotalClick();}};
		RelativeLayout.LayoutParams afterTaxSubtotalETLP = Grouptuity.relWrapWrapLP(RelativeLayout.ALIGN_PARENT_RIGHT);
		afterTaxSubtotalETLP.addRule(RelativeLayout.CENTER_VERTICAL);
		afterTaxSubtotalRow.addView(afterTaxSubtotalET,afterTaxSubtotalETLP);

		taxValueET = new BillCalculationField(BillCalculationFieldType.TAX_VALUE){void controllerCallback(){controller.onTaxValueClick();}};
		RelativeLayout.LayoutParams taxValueLP = Grouptuity.relWrapWrapLP(RelativeLayout.ALIGN_PARENT_RIGHT);
		taxValueLP.addRule(RelativeLayout.CENTER_VERTICAL);
		taxRow.addView(taxValueET,taxValueLP);

		taxPercentET = new BillCalculationField(BillCalculationFieldType.TAX_PERCENT){void controllerCallback(){controller.onTaxPercentClick();}};
		RelativeLayout.LayoutParams taxPercentLP = Grouptuity.relWrapWrapLP(RelativeLayout.LEFT_OF,taxValueET.getId());
		taxPercentLP.addRule(RelativeLayout.CENTER_VERTICAL);
		taxRow.addView(taxPercentET,taxPercentLP);

		tipValueET = new BillCalculationField(BillCalculationFieldType.TIP_VALUE){void controllerCallback(){controller.onTipValueClick();}};
		RelativeLayout.LayoutParams tipValueLP = Grouptuity.relWrapWrapLP(RelativeLayout.ALIGN_PARENT_RIGHT);
		tipValueLP.addRule(RelativeLayout.CENTER_VERTICAL);
		tipRow.addView(tipValueET,tipValueLP);

		tipPercentET = new BillCalculationField(BillCalculationFieldType.TIP_PERCENT){void controllerCallback(){controller.onTipPercentClick();}};
		RelativeLayout.LayoutParams tipPercentLP = Grouptuity.relWrapWrapLP(RelativeLayout.LEFT_OF,tipValueET.getId());
		tipPercentLP.addRule(RelativeLayout.CENTER_VERTICAL);
		tipRow.addView(tipPercentET,tipPercentLP);

		totalValueET = new BillCalculationField(BillCalculationFieldType.TOTAL){void controllerCallback(){controller.onTotalClick();}};
		RelativeLayout.LayoutParams totalValueETLP = Grouptuity.relWrapWrapLP(RelativeLayout.ALIGN_PARENT_RIGHT);
		totalValueETLP.addRule(RelativeLayout.CENTER_VERTICAL);
		totalRow.addView(totalValueET,totalValueETLP);
	}

	protected void saveState(Bundle bundle){}
	protected void restoreState(Bundle bundle){}
	protected void applyStyle()
	{
		rawSubtotalLabel.setTextColor(panel.style.textColor);
		taxLabel.setTextColor(panel.style.textColor);
		afterTaxSubtotalLabel.setTextColor(panel.style.textColor);
		tipLabel.setTextColor(panel.style.textColor);
		totalLabel.setTextColor(panel.style.textColor);
		rawSubtotalET.setTextColor(panel.style.textColor);
		taxPercentET.setTextColor(panel.style.textColor);
		afterTaxSubtotalET.setTextColor(panel.style.textColor);
		taxValueET.setTextColor(panel.style.textColor);
		tipPercentET.setTextColor(panel.style.textColor);
		tipValueET.setTextColor(panel.style.textColor);
		totalValueET.setTextColor(panel.style.textColor);
	}
	protected void refresh()
	{
		rawSubtotalET.setText(model.getRawSubtotal());
		afterTaxSubtotalET.setText(model.getAfterTaxSubtotal());
		totalValueET.setText(model.getTotal());

		if(Grouptuity.getQuickCalcBill().getRawSubtotal() <Grouptuity.PRECISION_ERROR)
		{
			taxPercentET.setText(BLANK_DISPLAY_STRING);
			taxValueET.setText(BLANK_DISPLAY_STRING);
			taxPercentET.setEnabled(false);
			taxValueET.setEnabled(false);
		}
		else
		{
			taxPercentET.setText(model.getTaxPercent());
			taxValueET.setText(model.getTaxValue());
			taxPercentET.setEnabled(true);
			taxValueET.setEnabled(true);
		}

		if(Grouptuity.getQuickCalcBill().getAfterTaxSubtotal()<Grouptuity.PRECISION_ERROR)
		{
			tipPercentET.setText(BLANK_DISPLAY_STRING);
			tipValueET.setText(BLANK_DISPLAY_STRING);
			tipPercentET.setEnabled(false);
			tipValueET.setEnabled(false);
		}
		else
		{
			tipValueET.setText(model.getTipValue());
			tipPercentET.setText(model.getTipPercent());
			tipPercentET.setEnabled(true);
			tipValueET.setEnabled(true);
		}
	}

	private abstract class BillCalculationField extends EditText
	{
		private BillCalculationField(BillCalculationFieldType type)
		{
			super(activity);
			activity.assignID(this);
			setTextSize(TypedValue.COMPLEX_UNIT_DIP,type.textSize);
			if(type.boldFace)
				setTypeface(Typeface.DEFAULT_BOLD);
			setGravity(Gravity.CENTER);
			setFocusable(false);
			setCursorVisible(false);
			setOnLongClickListener(new OnLongClickListener(){public boolean onLongClick(View v){return true;}});
			setOnTouchListener(new InputListenerTemplate(false)
			{
				protected boolean handlePress(){return false;}
				protected boolean handleHold(long holdTime){return false;}
				protected boolean handleRelease(){return false;}
				protected boolean handleClick(){controllerCallback();return false;}
				protected boolean handleLongClick(){return false;}
			});
		}

		abstract void controllerCallback();
	}
}