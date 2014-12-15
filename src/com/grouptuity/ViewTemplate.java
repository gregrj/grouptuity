package com.grouptuity;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.View;
import android.widget.LinearLayout;

public abstract class ViewTemplate<M extends ModelTemplate, C extends ControllerTemplate> extends LinearLayout
{
	final protected ActivityTemplate<M> activity;
	final protected M model;
	final protected ViewTemplate<M,C> view;
	final private StyleTemplate[] styles;
	public C controller;
	protected View placeholder;
	public StyleTemplate style;
	public ViewState viewState, previousViewState;

	public static enum ViewState
	{
		DEFAULT,
		HIGHLIGHTED,
		DISABLED,
		AUXILIARY;
	}

	public ViewTemplate(ActivityTemplate<M> context){this(context,new StyleTemplate());}
	public ViewTemplate(ActivityTemplate<M> context, StyleTemplate defaultStyle)
	{
		super(context);
		activity = context;
		model = context.model;
		view = this;
		styles = new StyleTemplate[ViewState.values().length];

		viewState = ViewState.DEFAULT;
		previousViewState = viewState;
		styles[viewState.ordinal()] = defaultStyle;
		style = defaultStyle;
		setOrientation(LinearLayout.VERTICAL);
	}

	public void setController(C c){controller = c;}
	final public void setStyle(ViewState vs, StyleTemplate styleTemplate)
	{
		styles[vs.ordinal()] = styleTemplate;
		if(vs==viewState)
		{
			style = styleTemplate;
			style.apply(this);
		}
	}
	final public void setViewState(ViewState vs)
	{
		if(vs!=viewState)
		{
			previousViewState = viewState;
			viewState = vs;
			style = styles[vs.ordinal()];
			style.apply(this);
		}
	}
	final public void revertViewState()
	{
		if(previousViewState!=viewState)
		{
			viewState = previousViewState;
			style = styles[viewState.ordinal()];
			style.apply(this);
		}
	}

	public void hide(){setVisibility(GONE);if(placeholder!=null)placeholder.setVisibility(GONE);}
	public void show(){setVisibility(VISIBLE);if(placeholder!=null)placeholder.setVisibility(VISIBLE);}

	protected abstract void saveState(Bundle bundle);
	protected abstract void restoreState(Bundle bundle);
	protected abstract void applyStyle();
	protected abstract void refresh();

	protected void onAttachedToWindow(){super.onAttachedToWindow();activity.addControllableView(this);style.apply(this);refresh();}
	protected void onDetachedFromWindow(){super.onDetachedFromWindow();activity.removeControllableView(this);}
	final protected void onSizeChanged(int w, int h, int oldW, int oldH){style.apply(this);}
	final public View getPlaceholder()
	{
		if(placeholder==null)
			placeholder = generatePlaceholder();
		return placeholder;
	}
	protected View generatePlaceholder(){return new View(activity){protected void onMeasure(int w, int h){setMeasuredDimension(view.getWidth(),view.getHeight());}};}
	final protected void forceStyleApply(){style.forceApply(this);}

	final protected Parcelable onSaveInstanceState()
	{
		Parcelable superState = super.onSaveInstanceState();
		Bundle customState = new Bundle();
		saveState(customState);
		SavedState savedState = new SavedState(superState,customState);
	    return savedState;
	}
	final protected void onRestoreInstanceState(Parcelable state)
	{
		if(!(state instanceof SavedState))
			super.onRestoreInstanceState(state);
		else
		{
			SavedState savedState = (SavedState) state;
			super.onRestoreInstanceState(savedState.getSuperState());
			restoreState(savedState.customState);
		}
	}
	final private static class SavedState extends BaseSavedState
	{
		Bundle customState;
		private SavedState(Parcelable superState, Bundle bundle){super(superState);customState = bundle;}
		private SavedState(Parcel parcel){super(parcel);customState = parcel.readBundle();}
		public void writeToParcel(Parcel parcel, int flags){super.writeToParcel(parcel,flags);parcel.writeBundle(customState);}
	}
}