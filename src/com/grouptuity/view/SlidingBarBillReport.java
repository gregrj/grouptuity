package com.grouptuity.view;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import com.grouptuity.*;
import com.grouptuity.model.*;
import com.grouptuity.style.composites.*;
import com.grouptuity.view.BillableListingAdapter.BillableListingAdapterController;

public class SlidingBarBillReport extends ViewTemplate<BillModelTemplate,SlidingBarBillReport.SlidingBarBillReportController>
{
	final private BillableListingAdapter billableListingAdapter;
	final private ListView listView;
	final private Panel subtotalPanel;
	final private RelativeLayout subtotalRelativeLayout;
	final private TextView subtotalValue, subtotalText;
	final private Paint linePaint;

	public static interface SlidingBarBillReportController extends ControllerTemplate
	{
		void onItemValueEditClick(Item itemToEdit);
		void onItemDeleteClick(Item itemToDelete);
		void onItemPayerListClick(Item itemToEdit);
		void onDebtValueEditClick(Debt debtToEdit);
		void onDebtDeleteClick(Debt debtToDelete);
		void onDebtPayerListClick(Debt debtToEdit);
		void onDebtPayeeListClick(Debt debtToEdit);
		void onDiscountValueEditClick(Discount discountToEdit);
		void onDiscountCostEditClick(Discount discountToEdit);
		void onDiscountDeleteClick(Discount discountToDelete);
		void onDiscountPayerListClick(Discount discountToEdit);
		void onDiscountPayeeListClick(Discount discountToEdit);
	}

	@SuppressWarnings("unchecked")
	public SlidingBarBillReport(ActivityTemplate<?> context, final SlidingBarBillReportController controllerRef)
	{
		super((ActivityTemplate<BillModelTemplate>)context);
		setBackgroundColor(Grouptuity.foregroundColor);

		linePaint = new Paint();
		linePaint.setColor(Color.LTGRAY);

		subtotalPanel = new Panel(context, new ListingStyle());
		subtotalPanel.setOrientation(LinearLayout.HORIZONTAL);

		subtotalRelativeLayout = new RelativeLayout(context);
		subtotalRelativeLayout.setPadding(10, 10, 10, 10);	

		subtotalValue = new TextView(context);
		subtotalValue.setText(Grouptuity.formatNumber(Grouptuity.NumberFormat.CURRENCY,Grouptuity.getBill().getRawSubtotal()));
		subtotalValue.setTypeface(Typeface.DEFAULT_BOLD);
		subtotalValue.setTextColor(Grouptuity.softTextColor);
		subtotalValue.setTextSize(TypedValue.COMPLEX_UNIT_SP,20.0f);

		subtotalText = new TextView(context);
		subtotalText.setText("Subtotal:");
		subtotalText.setTypeface(Typeface.DEFAULT_BOLD);
		subtotalText.setTextColor(Grouptuity.softTextColor);
		subtotalText.setTextSize(TypedValue.COMPLEX_UNIT_SP,20.0f);

		subtotalRelativeLayout.addView(subtotalText,relWrapWrapLP(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.CENTER_VERTICAL));
		subtotalRelativeLayout.addView(subtotalValue,relWrapWrapLP(RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.CENTER_VERTICAL));
		subtotalPanel.addView(subtotalRelativeLayout,Grouptuity.fillWrapLP());
		addView(subtotalPanel);

		billableListingAdapter = new BillableListingAdapter(context, new BillableListingAdapterController()
		{
			public void onEditValueClick(Billable billable)
			{
				if(billable.billableType.isItem)
					controllerRef.onItemValueEditClick((Item)billable);
				else if(billable.billableType.isDebt)
					controllerRef.onDebtValueEditClick((Debt)billable);
				else
					controllerRef.onDiscountValueEditClick((Discount)billable);
			}
			public void onEditCostClick(Billable billable){controllerRef.onDiscountCostEditClick((Discount)billable);}
			public void onDeleteItemClick(Billable billable)
			{
				if(billable.billableType.isItem)
					controllerRef.onItemDeleteClick((Item)billable);
				else if(billable.billableType.isDebt)
					controllerRef.onDebtDeleteClick((Debt)billable);
				else
					controllerRef.onDiscountDeleteClick((Discount)billable);
			}
			public void onPayerListClick(Billable billable)
			{
				if(billable.billableType.isItem)
					controllerRef.onItemPayerListClick((Item)billable);
				else if(billable.billableType.isDebt)
					controllerRef.onDebtPayerListClick((Debt)billable);
				else
					controllerRef.onDiscountPayerListClick((Discount)billable);
			}
			public void onPayeeListClick(Billable billable)
			{
				if(billable.billableType.isDebt)
					controllerRef.onDebtPayeeListClick((Debt)billable);
				else
					controllerRef.onDiscountPayeeListClick((Discount)billable);
			}
		});
		listView = new ListView(activity);
		listView.setAdapter(billableListingAdapter);
		listView.setFastScrollEnabled(true);
		listView.setCacheColorHint(Grouptuity.foregroundColor);
		addView(listView,Grouptuity.fillWrapLP(1.0f));
		refresh();
	}
	protected void dispatchDraw(Canvas canvas)
	{
		super.dispatchDraw(canvas);
		canvas.drawLine(subtotalPanel.getLeft(), subtotalPanel.getBottom()-1, subtotalPanel.getRight(), subtotalPanel.getBottom()-1, linePaint);
	}

	protected void saveState(Bundle bundle){}
	protected void restoreState(Bundle bundle){}
	protected void applyStyle(){}
	protected void refresh(){subtotalValue.setText(Grouptuity.formatNumber(Grouptuity.NumberFormat.CURRENCY,Grouptuity.getBill().getRawSubtotal())); billableListingAdapter.refresh();}

	private RelativeLayout.LayoutParams relWrapWrapLP(int... rules)
	{
		RelativeLayout.LayoutParams LP = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT);
		for(int r : rules ) {LP.addRule(r);}
		return LP;
	}
}