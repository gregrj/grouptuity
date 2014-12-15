package com.grouptuity.billcalculation;

import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;
import com.grouptuity.*;
import com.grouptuity.billcalculation.BillCalculationModel;
import com.grouptuity.view.Panel;
import com.grouptuity.style.composites.*;

public class BillCalculationPanel extends ViewTemplate<BillCalculationModel,BillCalculationPanel.BillCalculationPanelController>
{
	final private Panel panel;
	final private RelativeLayout discountedSubtotalRow, taxableSubtotalRow, taxRow, afterTaxSubtotalRow, tipRow, tipableSubtotalRow, totalRow;
	final private TextView discountedSubtotalLabel, taxableSubtotalLabel, tipableSubtotalLabel, taxLabel, afterTaxSubtotalLabel, tipLabel, totalLabel, discountedSubtotalValue, taxableSubtotalValue, tipableSubtotalValue, afterTaxSubtotalValue, totalValue;
	final private EditText discountedSubtotalET, taxPercentET, taxValueET, afterTaxSubtotalET, tipPercentET, tipValueET;
	final private float ableSubtotalTextSize = 18.0f;

	protected static interface BillCalculationPanelController extends ControllerTemplate
	{
		void onRawSubtotalClick();
		void onTaxPercentClick();
		void onAfterTaxSubtotalClick();
		void onTaxValueClick();
		void onTipPercentClick();
		void onTipValueClick();
	}

	private static enum BillCalculationFieldType
	{
		TAX_PERCENT(20.0f,false),
		TAX_VALUE(20.0f,false),
		TIP_PERCENT(20.0f,false),
		TIP_VALUE(20.0f,false),
		PRETAX_SUBTOTAL(22.0f,true);

		final private float textSize;
		final private boolean boldFace;

		private BillCalculationFieldType(float tSize, boolean bold){textSize = tSize;boldFace = bold;}
	}

	protected BillCalculationPanel(ActivityTemplate<BillCalculationModel> context)
	{
		super(context);
		panel = new Panel(context,new RoundForegroundPanelStyle());
		addView(panel,Grouptuity.fillFillLP());

		discountedSubtotalRow = new RelativeLayout(context);
		taxableSubtotalRow = new RelativeLayout(context);
		tipableSubtotalRow = new RelativeLayout(context);
		taxRow = new RelativeLayout(context);
		afterTaxSubtotalRow = new RelativeLayout(context);
		tipRow = new RelativeLayout(context);
		totalRow = new RelativeLayout(context);
		panel.addView(discountedSubtotalRow,Grouptuity.fillWrapLP(1.0f));
		panel.addView(taxableSubtotalRow,Grouptuity.fillWrapLP(1.0f));
		panel.addView(taxRow,Grouptuity.fillWrapLP(1.0f));
		panel.addView(afterTaxSubtotalRow,Grouptuity.fillWrapLP(1.0f));
		panel.addView(tipableSubtotalRow,Grouptuity.fillWrapLP(1.0f));
		panel.addView(tipRow,Grouptuity.fillWrapLP(1.0f));
		panel.addView(totalRow,Grouptuity.fillWrapLP(1.0f));

		discountedSubtotalLabel = new TextView(context);
		discountedSubtotalLabel.setText("Subtotal");
		discountedSubtotalLabel.setTypeface(Typeface.DEFAULT_BOLD);
		discountedSubtotalLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP,22.0f);
		discountedSubtotalLabel.setGravity(Gravity.CENTER);
		RelativeLayout.LayoutParams discountedSubtotalLabelLP = Grouptuity.relWrapWrapLP(RelativeLayout.ALIGN_PARENT_LEFT);
		discountedSubtotalLabelLP.addRule(RelativeLayout.CENTER_VERTICAL);
		discountedSubtotalRow.addView(discountedSubtotalLabel,discountedSubtotalLabelLP);

		discountedSubtotalValue = new TextView(context);
		discountedSubtotalValue.setTypeface(Typeface.DEFAULT_BOLD);
		discountedSubtotalValue.setTextSize(TypedValue.COMPLEX_UNIT_SP,22.0f);
		discountedSubtotalValue.setGravity(Gravity.CENTER_VERTICAL);
		RelativeLayout.LayoutParams discountedSubtotalValueLP = Grouptuity.relWrapWrapLP(RelativeLayout.ALIGN_PARENT_RIGHT);
		discountedSubtotalValueLP.addRule(RelativeLayout.CENTER_VERTICAL);

		discountedSubtotalET = new BillCalculationField(BillCalculationFieldType.PRETAX_SUBTOTAL){void controllerCallback(){controller.onRawSubtotalClick();}};
		RelativeLayout.LayoutParams discountedSubtotalETLP = Grouptuity.relWrapWrapLP(RelativeLayout.ALIGN_PARENT_RIGHT);
		discountedSubtotalETLP.addRule(RelativeLayout.CENTER_VERTICAL);

		discountedSubtotalRow.addView(discountedSubtotalValue,discountedSubtotalValueLP);

		taxableSubtotalLabel= new TextView(context);
		taxableSubtotalLabel.setText("Taxed Subtotal");
		taxableSubtotalLabel.setTypeface(Typeface.DEFAULT);
		taxableSubtotalLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP,ableSubtotalTextSize);
		taxableSubtotalLabel.setGravity(Gravity.CENTER_VERTICAL);
		RelativeLayout.LayoutParams taxableSubtotalLabelLP = Grouptuity.relWrapWrapLP(RelativeLayout.ALIGN_PARENT_LEFT);
		taxableSubtotalLabelLP.addRule(RelativeLayout.CENTER_VERTICAL);
		taxableSubtotalRow.addView(taxableSubtotalLabel,taxableSubtotalLabelLP);

		taxableSubtotalValue = new TextView(context);
		taxableSubtotalValue.setTypeface(Typeface.DEFAULT);
		taxableSubtotalValue.setTextSize(TypedValue.COMPLEX_UNIT_SP,ableSubtotalTextSize);
		taxableSubtotalValue.setGravity(Gravity.CENTER_VERTICAL);
		RelativeLayout.LayoutParams taxableSubtotalValueLP = Grouptuity.relWrapWrapLP(RelativeLayout.ALIGN_PARENT_RIGHT);
		taxableSubtotalValueLP.addRule(RelativeLayout.CENTER_VERTICAL);
		taxableSubtotalRow.addView(taxableSubtotalValue,taxableSubtotalValueLP);

		tipableSubtotalLabel= new TextView(context);
		tipableSubtotalLabel.setText("Tipped Subtotal");
		tipableSubtotalLabel.setTypeface(Typeface.DEFAULT);
		tipableSubtotalLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP,ableSubtotalTextSize);
		tipableSubtotalLabel.setGravity(Gravity.CENTER_VERTICAL);
		RelativeLayout.LayoutParams tipableSubtotalLabelLP = Grouptuity.relWrapWrapLP(RelativeLayout.ALIGN_PARENT_LEFT);
		tipableSubtotalLabelLP.addRule(RelativeLayout.CENTER_VERTICAL);
		tipableSubtotalRow.addView(tipableSubtotalLabel,tipableSubtotalLabelLP);

		tipableSubtotalValue = new TextView(context);
		tipableSubtotalValue.setTypeface(Typeface.DEFAULT);
		tipableSubtotalValue.setTextSize(TypedValue.COMPLEX_UNIT_SP,ableSubtotalTextSize);
		tipableSubtotalValue.setGravity(Gravity.CENTER_VERTICAL);
		RelativeLayout.LayoutParams tipableSubtotalValueLP = Grouptuity.relWrapWrapLP(RelativeLayout.ALIGN_PARENT_RIGHT);
		tipableSubtotalValueLP.addRule(RelativeLayout.CENTER_VERTICAL);
		tipableSubtotalRow.addView(tipableSubtotalValue,tipableSubtotalValueLP);

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

		afterTaxSubtotalValue = new TextView(context);
		afterTaxSubtotalValue.setTypeface(Typeface.DEFAULT_BOLD);
		afterTaxSubtotalValue.setTextSize(TypedValue.COMPLEX_UNIT_SP,22.0f);
		afterTaxSubtotalValue.setGravity(Gravity.CENTER_VERTICAL);
		RelativeLayout.LayoutParams afterTaxSubtotalValueLP = Grouptuity.relWrapWrapLP(RelativeLayout.ALIGN_PARENT_RIGHT);
		afterTaxSubtotalValueLP.addRule(RelativeLayout.CENTER_VERTICAL);

		afterTaxSubtotalET = new BillCalculationField(BillCalculationFieldType.PRETAX_SUBTOTAL){void controllerCallback(){controller.onAfterTaxSubtotalClick();}};
		RelativeLayout.LayoutParams afterTaxSubtotalETLP = Grouptuity.relWrapWrapLP(RelativeLayout.ALIGN_PARENT_RIGHT);
		afterTaxSubtotalETLP.addRule(RelativeLayout.CENTER_VERTICAL);

		afterTaxSubtotalRow.addView(afterTaxSubtotalValue,afterTaxSubtotalValueLP);

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

		totalValue = new TextView(context);
		totalValue.setTypeface(Typeface.DEFAULT_BOLD);
		totalValue.setTextSize(TypedValue.COMPLEX_UNIT_SP,25.0f);
		totalValue.setGravity(Gravity.CENTER_VERTICAL);
		RelativeLayout.LayoutParams totalValueLP = Grouptuity.relWrapWrapLP(RelativeLayout.ALIGN_PARENT_RIGHT);
		totalValueLP.addRule(RelativeLayout.CENTER_VERTICAL);
		totalRow.addView(totalValue,totalValueLP);
	}

	protected void saveState(Bundle bundle){}
	protected void restoreState(Bundle bundle){}
	protected void applyStyle()
	{
		discountedSubtotalLabel.setTextColor(panel.style.textColor);
		taxableSubtotalLabel.setTextColor(panel.style.textColor);
		taxableSubtotalValue.setTextColor(panel.style.textColor);
		tipableSubtotalLabel.setTextColor(panel.style.textColor);
		tipableSubtotalValue.setTextColor(panel.style.textColor);
		taxLabel.setTextColor(panel.style.textColor);
		afterTaxSubtotalLabel.setTextColor(panel.style.textColor);
		tipLabel.setTextColor(panel.style.textColor);
		totalLabel.setTextColor(panel.style.textColor);
		discountedSubtotalValue.setTextColor(panel.style.textColor);
		discountedSubtotalET.setTextColor(panel.style.textColor);
		taxPercentET.setTextColor(panel.style.textColor);
		afterTaxSubtotalValue.setTextColor(panel.style.textColor);
		afterTaxSubtotalET.setTextColor(panel.style.textColor);
		taxValueET.setTextColor(panel.style.textColor);
		tipPercentET.setTextColor(panel.style.textColor);
		tipValueET.setTextColor(panel.style.textColor);
		totalValue.setTextColor(panel.style.textColor);
	}
	protected void refresh()
	{
		if(Math.abs(Grouptuity.getBill().getTaxableSubtotal()-Grouptuity.getBill().getDiscountedSubtotal()) > Grouptuity.PRECISION_ERROR)
		{
			taxableSubtotalRow.setVisibility(View.VISIBLE);
			taxableSubtotalValue.setText(model.getTaxableSubtotal());
		}
		else
			taxableSubtotalRow.setVisibility(View.GONE);

		if(Math.abs(Grouptuity.getBill().getTipableSubtotal()-Grouptuity.getBill().getDiscountedSubtotal()) > Grouptuity.PRECISION_ERROR)
		{
			tipableSubtotalRow.setVisibility(View.VISIBLE);
			tipableSubtotalValue.setText(model.getTipableSubtotal());
		}
		else
			tipableSubtotalRow.setVisibility(View.GONE);

		discountedSubtotalValue.setText(model.getBoundedDiscountedSubtotal());
		discountedSubtotalET.setText(model.getBoundedDiscountedSubtotal());
		taxValueET.setText(model.getTaxValue());
		afterTaxSubtotalValue.setText(model.getAfterTaxSubtotal());
		afterTaxSubtotalET.setText(model.getAfterTaxSubtotal());
		taxPercentET.setText(model.getTaxPercent());
		tipValueET.setText(model.getTipValue());
		tipPercentET.setText(model.getTipPercent());
		totalValue.setText(model.getTotal());
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