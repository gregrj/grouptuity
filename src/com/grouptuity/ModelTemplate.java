package com.grouptuity;

import java.util.ArrayList;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

public abstract class ModelTemplate
{
	final protected ActivityTemplate<?> activity;
	private ArrayList<Integer> stateStack;
	protected int previousState;
	protected boolean stateChanged;
	final public static int START = -2, HELP = -1;

	//note that all extending classes must have a public constructor

	protected ModelTemplate(ActivityTemplate<?> a, Bundle bundle)
	{
		activity = a;
		stateChanged = true;
		if(bundle != null)
		{
			stateStack = bundle.getIntegerArrayList("com.grouptuity.stateStack");
			previousState = bundle.getInt("com.grouptuity.previousState");
		}
		if(stateStack==null)
		{
			stateStack = new ArrayList<Integer>();
			stateStack.add(START);
			previousState=START;
		}
	}

	protected void superSaveState(Bundle bundle){bundle.putIntegerArrayList("com.grouptuity.stateStack", stateStack);bundle.putInt("com.grouptuity.previousState",previousState);saveState(bundle);}
	protected abstract void saveState(Bundle bundle);
	protected abstract void saveToDatabase();
	protected void superRestoreState(Bundle savedInstanceState){}

	public int getState(){return stateStack.get(stateStack.size()-1);}
	public int getState(int i){return stateStack.get(stateStack.size()-1-i);}
	public int gestPreviousState(){return previousState;}
	public void setNewState(int i){if(i!=getState()){previousState=getState();stateStack.add(i);stateChanged=true;}activity.refreshActivity();}
	public void revertState()
	{
		if(stateStack.size()>1)
		{
			previousState = getState();
			stateStack.remove(stateStack.size()-1);
			stateChanged=true;
		}
		activity.refreshActivity();
	}
	public void revertToState(int oldState)
	{
		if(stateStack.contains(oldState))
		{
			if(stateStack.get(stateStack.size()-1)!=oldState)
			{
				while(stateStack.get(stateStack.size()-1)!=oldState)
					previousState = stateStack.remove(stateStack.size()-1);
				stateChanged=true;
			}
			activity.refreshActivity();
		}
		else
			setNewState(oldState);
	}
	public void revertPastFirst(int oldState)
	{
		int index = stateStack.indexOf(oldState);
		if(index>0)
		{
			stateChanged=true;
			previousState = oldState;
			for(int i=stateStack.size()-1; i>index-1; i--)
				stateStack.remove(i);
		}
		activity.refreshActivity();
	}

	public static class Capsule<T> implements Parcelable
	{
		public T value;
		public Capsule(T t){value = t;}
		public Capsule(){value = null;}
		public int describeContents(){return 0;}
		public void writeToParcel(Parcel out, int flags){out.writeValue(value);}
		final public static Parcelable.Creator<Capsule<?>> CREATOR = new Parcelable.Creator<Capsule<?>>()
		{
			@SuppressWarnings({"rawtypes","unchecked"})
			public Capsule<?> createFromParcel(Parcel in){return new Capsule(in);}
			public Capsule<?>[] newArray(int size){return new Capsule<?>[size];}
		};
	}
}