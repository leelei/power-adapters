package com.nextfaze.poweradapters.recyclerview;

import android.support.v4.util.ArrayMap;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;
import com.nextfaze.poweradapters.DataObserver;
import com.nextfaze.poweradapters.PowerAdapter;
import lombok.NonNull;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class RecyclerConverterAdapter extends RecyclerView.Adapter<RecyclerConverterAdapter.Holder> {

    @NonNull
    private final Set<RecyclerView.AdapterDataObserver> mAdapterDataObservers = new HashSet<>();

    @NonNull
    private final PowerAdapter mPowerAdapter;

    @NonNull
    private final DataObserver mDataSetObserver = new DataObserver() {
        @Override
        public void onChanged() {
            mShadowItemCount = mPowerAdapter.getItemCount();
            notifyDataSetChanged();
        }

        @Override
        public void onItemRangeChanged(int positionStart, int itemCount) {
            validateItemCount();
            notifyItemRangeChanged(positionStart, itemCount);
        }

        @Override
        public void onItemRangeInserted(int positionStart, int itemCount) {
            mShadowItemCount += itemCount;
            validateItemCount();
            notifyItemRangeInserted(positionStart, itemCount);
        }

        @Override
        public void onItemRangeRemoved(int positionStart, int itemCount) {
            mShadowItemCount -= itemCount;
            validateItemCount();
            notifyItemRangeRemoved(positionStart, itemCount);
        }

        @Override
        public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
            validateItemCount();
            if (itemCount == 1) {
                notifyItemMoved(fromPosition, toPosition);
            } else {
                // TODO: There's likely a more specific (series of?) calls we can make here instead of generic "changed" fallback.
                notifyDataSetChanged();
            }
        }
    };

    @NonNull
    private final Map<Object, Integer> mViewTypeObjectToInt = new ArrayMap<>();

    @NonNull
    private final Map<Integer, Object> mViewTypeIntToObject = new ArrayMap<>();

    private int mNextViewTypeInt;

    /** Used to track the expected number of items, based on incoming notifications. */
    private int mShadowItemCount;

    RecyclerConverterAdapter(@NonNull PowerAdapter powerAdapter) {
        mPowerAdapter = powerAdapter;
        setHasStableIds(mPowerAdapter.hasStableIds());
    }

    @Override
    public int getItemCount() {
        return mPowerAdapter.getItemCount();
    }

    @Override
    public long getItemId(int position) {
        return mPowerAdapter.getItemId(position);
    }

    @Override
    public int getItemViewType(int position) {
        Object viewType = mPowerAdapter.getItemViewType(position);
        Integer viewTypeInt = mViewTypeObjectToInt.get(viewType);
        if (viewTypeInt == null) {
            viewTypeInt = mNextViewTypeInt++;
            mViewTypeObjectToInt.put(viewType, viewTypeInt);
            mViewTypeIntToObject.put(viewTypeInt, viewType);
        }
        return viewTypeInt;
    }

    @Override
    public Holder onCreateViewHolder(ViewGroup parent, int itemViewType) {
        return new Holder(mPowerAdapter.newView(parent, mViewTypeIntToObject.get(itemViewType)));
    }

    @Override
    public void onBindViewHolder(Holder holder, int position) {
        mPowerAdapter.bindView(holder.itemView, holder.holder);
    }

    @Override
    public void registerAdapterDataObserver(RecyclerView.AdapterDataObserver observer) {
        super.registerAdapterDataObserver(observer);
        if (mAdapterDataObservers.add(observer) && mAdapterDataObservers.size() == 1) {
            mShadowItemCount = mPowerAdapter.getItemCount();
            mPowerAdapter.registerDataObserver(mDataSetObserver);
        }
    }

    @Override
    public void unregisterAdapterDataObserver(RecyclerView.AdapterDataObserver observer) {
        super.unregisterAdapterDataObserver(observer);
        if (mAdapterDataObservers.remove(observer) && mAdapterDataObservers.size() == 0) {
            mPowerAdapter.unregisterDataObserver(mDataSetObserver);
            mShadowItemCount = 0;
        }
    }

    int getObserverCount() {
        return mAdapterDataObservers.size();
    }

    /**
     * Check the item count by comparing with our shadow count. If they don't match, there's a good chance {@link
     * RecyclerView} will crash later on. By doing it aggressively ourselves, we can catch a poorly-behaved {@link
     * PowerAdapter} early.
     */
    private void validateItemCount() {
        int itemCount = mPowerAdapter.getItemCount();
        if (mShadowItemCount != itemCount) {
            throw new IllegalStateException("Inconsistency detected: expected item count " +
                    mShadowItemCount + " but it is " + itemCount);
        }
    }

    static final class Holder extends RecyclerView.ViewHolder {

        @NonNull
        private final com.nextfaze.poweradapters.Holder holder = new com.nextfaze.poweradapters.Holder() {
            @Override
            public int getPosition() {
                return getLayoutPosition();
            }
        };

        Holder(View itemView) {
            super(itemView);
        }
    }
}
