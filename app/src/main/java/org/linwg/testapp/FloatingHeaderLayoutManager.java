package org.linwg.testapp;

import android.graphics.Rect;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FloatingHeaderLayoutManager extends RecyclerView.LayoutManager {
    private static boolean DEBUG = true;
    private static final String TAG = "FLM";
    private int mTotalHeight = 0;
    private int mExtentHeight = 0;
    private int verticallyScrollOffset = 0;
    private SparseArray<ViewInfo> viewInfoArray = new SparseArray<>();
    private SparseArray<ViewInfo> floatingViewInfoArray = new SparseArray<>();
    private SparseIntArray itemHeightArray = new SparseIntArray();
    private int firstVisibleItemPosition = 0;
    private int lastVisibleItemPosition = -1;
    private List<Integer> floatingPositionList = new ArrayList<>();
    private List<View> floatingView = new ArrayList<>();
    private ArrayList<?> vhListInPoll = null;
    private ArrayList<RecyclerView.ViewHolder> mAttachedScrap = null;
    private ArrayList<RecyclerView.ViewHolder> mCachedViews = null;

    public FloatingHeaderLayoutManager(Integer... floatingPosition) {
        this.floatingPositionList.addAll(Arrays.asList(floatingPosition));
    }

    public int getLastVisibleItemPosition() {
        return lastVisibleItemPosition;
    }

    class ViewInfo {
        View view;
        int position;
        int measureTop;
        int measureBottom;
        Rect rect = new Rect();
    }

    @Override
    public boolean isAutoMeasureEnabled() {
        return true;
    }

    @Override
    public RecyclerView.LayoutParams generateDefaultLayoutParams() {
        return new RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        if (DEBUG) {
            Log.i(TAG, "onLayoutChildren  " + state.toString());
        }
        if (state.getItemCount() == 0) {
            removeAndRecycleAllViews(recycler);
            viewInfoArray.clear();
            itemHeightArray.clear();
            floatingViewInfoArray.clear();
            floatingView.clear();
            verticallyScrollOffset = 0;
            return;
        }
        detachAndScrapAttachedViews(recycler);
        floatingViewInfoArray.clear();
        floatingView.clear();
        if (DEBUG && vhListInPoll != null && mAttachedScrap != null && mCachedViews != null) {
            Log.d(TAG, "pool.size = " + vhListInPoll.size() + " scrap size =" + mAttachedScrap.size() + " cache size = " + mCachedViews.size());
        }
        relayout(recycler, state);
    }

    private void relayout(RecyclerView.Recycler recycler, RecyclerView.State state) {
        int itemFinalY = 0;
        int position = 0;
        //根据滚动距离计算第一个可见的Item
        while (itemHeightArray.size() != 0 && position < getItemCount() - 1) {
            int height = itemHeightArray.get(position);
            itemFinalY += height;
            position++;
            if (itemFinalY - verticallyScrollOffset >= 0) {
                itemFinalY -= height;
                position--;
                break;
            }
        }
        firstVisibleItemPosition = position;
        int visibleHeight = getHeight() - getPaddingTop() - getPaddingBottom();
        //在Relayout之前所有的View已经detach回收,这里只需要填充可见部分并获得最后一个可见Item
        while (true) {
            itemFinalY += fillFromTop(recycler, state, position);
            position++;
            if (itemFinalY > visibleHeight + verticallyScrollOffset || position == getItemCount()) {
                position--;
                break;
            }
        }
        lastVisibleItemPosition = position;
        //修正悬浮Item的层级，是其显示于最顶层
        fixFloatingOrder(recycler);
        mTotalHeight = viewInfoArray.size() >= getItemCount() ? getRealContentHeight() : computeVerticalScrollRange(state);
        mExtentHeight = computeVerticalScrollExtent(state);
    }

    private int layoutItem(int position, RecyclerView.Recycler recycler) {
        ViewInfo viewInfo = viewInfoArray.get(position);
        if (DEBUG) {
            Log.d(TAG, "layoutItem position = " + position + " rect = " + viewInfo.rect);
            if (vhListInPoll != null && mAttachedScrap != null && mCachedViews != null) {
                Log.d(TAG, "pool.size = " + vhListInPoll.size() + " scrap size =" + mAttachedScrap.size() + " cache size = " + mCachedViews.size());
            }
        }
        addView(viewInfo.view);
        measureChildWithMargins(viewInfo.view, 0, 0);
        layoutDecoratedWithMargins(viewInfo.view, viewInfo.rect.left, viewInfo.rect.top, viewInfo.rect.right, viewInfo.rect.bottom);
        //悬浮Item被添加后，此处缓存起来
        if (floatingPositionList.contains(position) && floatingViewInfoArray.get(position) == null) {
            floatingViewInfoArray.put(position, viewInfo);
            if (!floatingView.contains(viewInfo.view)) {
                floatingView.add(viewInfo.view);
            }
        }
        if ((viewInfo.rect.bottom < getPaddingTop() || viewInfo.rect.top > getHeight() - getPaddingBottom()) && !floatingPositionList.contains(position)) {
            //非悬浮Item，布局后如果此Item依然超出屏幕范围，则回收此Item
            removeAndRecycleView(viewInfo.view, recycler);
            if (DEBUG) {
                Log.d(TAG, "recycler after layout position = " + position);
                if (vhListInPoll != null && mAttachedScrap != null && mCachedViews != null) {
                    Log.d(TAG, "pool.size = " + vhListInPoll.size() + " scrap size =" + mAttachedScrap.size() + " cache size = " + mCachedViews.size());
                }
            }
        }
        return viewInfo.rect.height();
    }

    @Override
    public boolean canScrollVertically() {
        return true;
    }

    @Override
    public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
        int lastHeight = mTotalHeight - mExtentHeight;
        int consume = dy;
        //边界锁定
        if (verticallyScrollOffset + dy < 0) {
            consume = -verticallyScrollOffset;
        } else if (verticallyScrollOffset + dy > lastHeight && mTotalHeight >= mExtentHeight) {
            consume = lastHeight - verticallyScrollOffset;
        }
        if (consume == 0) {
            return 0;
        }
        verticallyScrollOffset += consume;
        //偏移我们自己缓存的View的边框属性
        offsetViewInfoArray(consume);
        //偏移已经添加在试图上的View
        offsetChildrenVertical(-consume);

        if (DEBUG && vhListInPoll == null) {
            reflectGetRecyclerPoolInstance(recycler);
        }
        if (DEBUG && vhListInPoll != null && mCachedViews != null && mAttachedScrap != null) {
            Log.d(TAG, "                              ");
            Log.d(TAG, "=======> Before recycle<======");
            Log.d(TAG, String.format("first = %s and last = %s", firstVisibleItemPosition, lastVisibleItemPosition));
            Log.d(TAG, "VH in pool : " + TextUtils.join(",", vhListInPoll));
        }
        if (dy > 0) {
            //向上滑动，遍历回收顶部不可见Item，添加底部空白部分，直到空白部分被填满
            recycleItemAndFillByScrollDown(recycler, state);
        } else {
            //向上滑动，遍历回收底部不可见Item，添加顶部空白部分，直到空白部分被填满
            recycleItemAndFillByScrollUp(recycler, state);
        }
        if (DEBUG && vhListInPoll != null) {
            Log.d(TAG, String.format("first = %s and last = %s", firstVisibleItemPosition, lastVisibleItemPosition));
            Log.d(TAG, "VH in pool : " + TextUtils.join(",", vhListInPoll));
            Log.d(TAG, "=======> After recycle<======");
            Log.d(TAG, "                              ");
        }
        //修正悬浮Item的层级
        fixFloatingOrder(recycler);
        return consume;
    }

    private void fixFloatingOrder(RecyclerView.Recycler recycler) {
        int top = 0;
        for (int i = 0; i < floatingPositionList.size(); i++) {
            Integer position = floatingPositionList.get(i);
            ViewInfo viewInfo = viewInfoArray.get(position);
            if (viewInfo != null && viewInfo.view != null) {
                int measureTop = viewInfo.measureTop;
                //该 rect 在offset以及fill时会偏移到当前显示区域，根据该区域的top决定该View的新的Top
                int curTop = viewInfo.rect.top;
                if (curTop < top) {
                    //当前顶部小于最小顶部
                    viewInfo.rect.offset(0, top - curTop);
                } else {
                    //当前顶部大于最小顶部
                    if (top >= measureTop - verticallyScrollOffset) {
                        //理论值还是比最小的小,还原滚动程度
                        viewInfo.rect.offset(0, top - curTop);
                    } else {
                        //理论是大于最小值了，必须偏移到理论值
                        viewInfo.rect.offset(0, measureTop - verticallyScrollOffset - curTop);
                    }
                }
                removeView(viewInfo.view);
                viewInfo.view = next(recycler, position);
                layoutItem(position, recycler);
                top += viewInfo.rect.height();
            }
        }
    }

    private View next(RecyclerView.Recycler recycler, int position) {
        if (floatingPositionList.contains(position)) {
            ViewInfo viewInfo = floatingViewInfoArray.get(position);
            if (viewInfo != null && viewInfo.view != null) {
                return viewInfo.view;
            }
        }
        return recycler.getViewForPosition(position);
    }

    private void reflectGetRecyclerPoolInstance(RecyclerView.Recycler recycler) {
        Class<? extends RecyclerView.Recycler> aClass = recycler.getClass();
        try {
            Field mRecyclerPool = aClass.getDeclaredField("mRecyclerPool");
            mRecyclerPool.setAccessible(true);
            RecyclerView.RecycledViewPool o = (RecyclerView.RecycledViewPool) mRecyclerPool.get(recycler);
            Class<? extends RecyclerView.RecycledViewPool> aClass1 = o.getClass();
            Field mScrap = aClass1.getDeclaredField("mScrap");
            mScrap.setAccessible(true);
            SparseArray<?> o1 = (SparseArray<?>) mScrap.get(o);
            Object o2 = o1.get(0);
            Class<?> aClass2 = o2.getClass();
            Field mScrapHeap = aClass2.getDeclaredField("mScrapHeap");
            mScrapHeap.setAccessible(true);
            vhListInPoll = (ArrayList<?>) mScrapHeap.get(o2);

            Field mAttachedScrap = aClass.getDeclaredField("mAttachedScrap");
            mAttachedScrap.setAccessible(true);
            this.mAttachedScrap = (ArrayList<RecyclerView.ViewHolder>) mAttachedScrap.get(recycler);

            Field mCachedViews = aClass.getDeclaredField("mCachedViews");
            mCachedViews.setAccessible(true);
            this.mCachedViews = (ArrayList<RecyclerView.ViewHolder>) mCachedViews.get(recycler);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    private void offsetViewInfoArray(int dy) {
        int size = viewInfoArray.size();
        for (int i = 0; i < size; i++) {
            int key = viewInfoArray.keyAt(i);
            ViewInfo viewInfo = viewInfoArray.get(key);
            if (viewInfo != null) {
                viewInfo.rect.offset(0, -dy);
            }
        }
    }

    private void recycleItemAndFillByScrollUp(RecyclerView.Recycler recycler, RecyclerView.State state) {
        while (true) {
            recycleBottomItem(recycler);
            if (fillTopItem(recycler, state) <= 0) {
                return;
            }
        }
    }

    private void recycleItemAndFillByScrollDown(RecyclerView.Recycler recycler, RecyclerView.State state) {
        while (true) {
            recycleTopItem(recycler);
            if (fillBottomItem(recycler, state) <= 0) {
                return;
            }
        }
    }

    private int fillTopItem(RecyclerView.Recycler recycler, RecyclerView.State state) {
        ViewInfo firstViewInfo = viewInfoArray.get(firstVisibleItemPosition);
        int position = firstVisibleItemPosition - 1;
        if (position == -1) {
            return 0;
        }
        int itemFinalY = firstViewInfo.measureTop - verticallyScrollOffset;
        if (itemFinalY <= getPaddingTop()) {
            return 0;
        }
        //底部的Item在回收，但是此处获取到的view可能是悬浮的View，因为悬浮的View此处不回收，所以没有使用到回收池的view holder，可能导致回收池溢出
        View view = next(recycler, position);
        measureChildWithMargins(view, 0, 0);
        int height = getDecoratedMeasuredHeight(view);
        int width = getDecoratedMeasuredWidth(view);
        RecyclerView.LayoutParams layoutParams = (RecyclerView.LayoutParams) view.getLayoutParams();
        int leftMargin = layoutParams.leftMargin;
        int topMargin = layoutParams.topMargin;
        int bottomMargin = layoutParams.bottomMargin;
        itemHeightArray.put(position, height + topMargin + bottomMargin);
        ViewInfo viewInfo = viewInfoArray.get(position);
        if (viewInfo == null) {
            viewInfo = new ViewInfo();
        }
        int left = getPaddingLeft() + leftMargin;
        int top = itemFinalY - bottomMargin - height;
        viewInfo.rect.set(left, top, left + width, top + height);
        viewInfo.position = position;
        viewInfo.view = view;
        viewInfo.measureTop = firstViewInfo.measureTop - bottomMargin - height - topMargin;
        viewInfo.measureBottom = firstViewInfo.measureTop;
        viewInfoArray.put(position, viewInfo);
        layoutItem(position, recycler);
        firstVisibleItemPosition = position;
        return viewInfo.rect.height();
    }

    private int recycleTopItem(RecyclerView.Recycler recycler) {
        if (lastVisibleItemPosition <= 0) {
            firstVisibleItemPosition = 0;
            return 0;
        }
        if (firstVisibleItemPosition > lastVisibleItemPosition) {
            firstVisibleItemPosition = lastVisibleItemPosition;
            return 0;
        }
        while (floatingPositionList.contains(firstVisibleItemPosition)) {
            firstVisibleItemPosition++;
        }
        ViewInfo viewInfo = viewInfoArray.get(firstVisibleItemPosition);
        if (viewInfo == null) {
            if (DEBUG) {
                Log.d(TAG, "recycle top item fail : viewInfo == null");
            }
            return 0;
        }
        if ((viewInfo.rect.bottom <= getPaddingTop())) {
            removeAndRecycleView(viewInfo.view, recycler);
            if (DEBUG) {
                Log.d(TAG, "recycle top item:" + firstVisibleItemPosition + "after recycler pool.size = " + vhListInPoll.size() + " scrap size =" + mAttachedScrap.size() + " cache size = " + mCachedViews.size());
                reflectGetViewHolderAndLog(viewInfo.view);
            }
            firstVisibleItemPosition++;
            return viewInfo.rect.height();
        }
        Log.d(TAG, "recycle top item fail : viewInfo.rect.bottom > getPaddingTop() :" + viewInfo.rect.bottom + "-" + getPaddingTop());
        return 0;
    }

    private int recycleBottomItem(RecyclerView.Recycler recycler) {
//        if (floatingPositionList.contains(lastVisibleItemPosition)) {
//            ViewInfo viewInfo = floatingViewInfoArray.get(lastVisibleItemPosition);
//            if (viewInfo.measureTop - verticallyScrollOffset > getHeight() - getPaddingBottom()) {
//                removeAndRecycleView(viewInfo.view, recycler);
//                Log.d(TAG, "recycle float item:" + lastVisibleItemPosition);
//                floatingViewInfoArray.remove(lastVisibleItemPosition);
//                floatingView.remove(viewInfo.view);
//                lastVisibleItemPosition--;
//                return viewInfo.rect.height();
//            } else {
//                return 0;
//            }
//        }
        if (lastVisibleItemPosition < firstVisibleItemPosition) {
            lastVisibleItemPosition = firstVisibleItemPosition;
            return 0;
        }
        while (floatingPositionList.contains(lastVisibleItemPosition)) {
            lastVisibleItemPosition--;
        }
        ViewInfo viewInfo = viewInfoArray.get(lastVisibleItemPosition);
        if (viewInfo.rect.top >= getHeight() - getPaddingBottom()) {
            removeAndRecycleView(viewInfo.view, recycler);
            reflectGetViewHolderAndLog(viewInfo.view);
            if (DEBUG) {
                Log.d(TAG, "recycle bottom item:" + lastVisibleItemPosition);
            }
            lastVisibleItemPosition--;
            return viewInfo.rect.height();
        }
        return 0;
    }

    private int fillBottomItem(RecyclerView.Recycler recycler, RecyclerView.State state) {
        ViewInfo lastViewInfo = viewInfoArray.get(lastVisibleItemPosition);
        final int position = lastVisibleItemPosition + 1;
        if (position == state.getItemCount()) {
            return 0;
        }
        int measureBottom = lastViewInfo == null ? getPaddingTop() : lastViewInfo.measureBottom;
        int itemFinalY = measureBottom - verticallyScrollOffset;
        int visibleHeight = getHeight() - getPaddingBottom() - getPaddingTop();
        if (itemFinalY >= visibleHeight) {
            return 0;
        }
        View view = next(recycler, position);
        measureChildWithMargins(view, 0, 0);
        int height = getDecoratedMeasuredHeight(view);
        int width = getDecoratedMeasuredWidth(view);
        RecyclerView.LayoutParams layoutParams = (RecyclerView.LayoutParams) view.getLayoutParams();
        int leftMargin = layoutParams.leftMargin;
        int topMargin = layoutParams.topMargin;
        int bottomMargin = layoutParams.bottomMargin;
        itemHeightArray.put(position, height + topMargin + bottomMargin);
        ViewInfo viewInfo = viewInfoArray.get(position);
        if (viewInfo == null) {
            viewInfo = new ViewInfo();
        }
        int left = getPaddingLeft() + leftMargin;
        int top = itemFinalY + getPaddingTop() + topMargin;
        viewInfo.rect.set(left, top, left + width, top + height);
        viewInfo.position = position;
        viewInfo.view = view;
        viewInfo.measureTop = measureBottom;
        viewInfo.measureBottom = measureBottom + height + topMargin + bottomMargin;
        viewInfoArray.put(position, viewInfo);
        layoutItem(position, recycler);
        lastVisibleItemPosition = position;
        return viewInfo.rect.height();
    }

    private int fillFromTop(RecyclerView.Recycler recycler, RecyclerView.State state, int position) {
        ViewInfo firstViewInfo = viewInfoArray.get(position - 1);
        int lastItemBottom = (firstViewInfo == null ? 0 : firstViewInfo.measureBottom);
        View view = next(recycler, position);
        measureChildWithMargins(view, 0, 0);
        int height = getDecoratedMeasuredHeight(view);
        int width = getDecoratedMeasuredWidth(view);
        RecyclerView.LayoutParams layoutParams = (RecyclerView.LayoutParams) view.getLayoutParams();
        int leftMargin = layoutParams.leftMargin;
        int topMargin = layoutParams.topMargin;
        int bottomMargin = layoutParams.bottomMargin;
        itemHeightArray.put(position, height + topMargin + bottomMargin);
        ViewInfo viewInfo = viewInfoArray.get(position);
        if (viewInfo == null) {
            viewInfo = new ViewInfo();
        }
        int left = getPaddingLeft() + leftMargin;
        int top = lastItemBottom - verticallyScrollOffset + topMargin;
        viewInfo.rect.set(left, top, left + width, top + height);
        viewInfo.position = position;
        viewInfo.view = view;
        viewInfo.measureTop = lastItemBottom;
        viewInfo.measureBottom = lastItemBottom + topMargin + bottomMargin + height;
        viewInfoArray.put(position, viewInfo);
        layoutItem(position, recycler);
        return viewInfo.rect.height();
    }

    private void reflectGetViewHolderAndLog(View view) {
        RecyclerView.LayoutParams layoutParams = (RecyclerView.LayoutParams) view.getLayoutParams();
        if (layoutParams != null) {
            Class<? extends RecyclerView.LayoutParams> aClass = layoutParams.getClass();
            try {
                Field mViewHolder = aClass.getDeclaredField("mViewHolder");
                mViewHolder.setAccessible(true);
                RecyclerView.ViewHolder viewHolder = (RecyclerView.ViewHolder) mViewHolder.get(layoutParams);
                Class<? extends RecyclerView.ViewHolder> holderClass = RecyclerView.ViewHolder.class;
                Field mFlags = holderClass.getDeclaredField("mFlags");
                mFlags.setAccessible(true);
                int flag = (int) mFlags.get(viewHolder);
                Method isScrap = holderClass.getDeclaredMethod("isScrap");
                isScrap.setAccessible(true);
                Boolean isScrapR = (Boolean) isScrap.invoke(viewHolder);
                Log.d(TAG, "recycle vh property: mFlags = " + flag + "  isScrap = " + isScrapR + " vh.itemView.parent = " + viewHolder.itemView.getParent());
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public int computeVerticalScrollOffset(@NonNull RecyclerView.State state) {
        if (getChildCount() == 0) {
            return 0;
        }
        if (floatingPositionList.contains(0) && firstVisibleItemPosition == 0) {
            return verticallyScrollOffset;
        }
        ViewInfo viewInfoStart = viewInfoArray.get(firstVisibleItemPosition);
        ViewInfo viewInfoEnd = viewInfoArray.get(lastVisibleItemPosition);
        final int laidOutArea = Math.abs(viewInfoEnd.measureBottom
                - viewInfoStart.measureTop);
        final int itemRange = lastVisibleItemPosition - firstVisibleItemPosition + 1;
        final float avgSizePerRow = (float) laidOutArea / itemRange;
        final RecyclerView.LayoutParams params = (RecyclerView.LayoutParams)
                viewInfoStart.view.getLayoutParams();
        int decoratedStart = getDecoratedTop(viewInfoStart.view) - params.topMargin;
        return Math.round(firstVisibleItemPosition * avgSizePerRow + (getPaddingTop() - decoratedStart));
    }

    private int getRealContentHeight() {
        int range = 0;
        for (int i = 0; i < getItemCount(); i++) {
            range += itemHeightArray.get(i);
        }
        return range;
    }

    @Override
    public int computeVerticalScrollRange(@NonNull RecyclerView.State state) {
        if (getChildCount() == 0) {
            return 0;
        }
        ViewInfo viewInfoStart = viewInfoArray.get(firstVisibleItemPosition);
        ViewInfo viewInfoEnd = viewInfoArray.get(lastVisibleItemPosition);
        int heightStart = viewInfoStart.measureTop;
        int heightEnd = viewInfoEnd.measureBottom;
        int perHeight = (heightEnd - heightStart) / (lastVisibleItemPosition - firstVisibleItemPosition + 1);
        return perHeight * getItemCount();
    }

    @Override
    public int computeVerticalScrollExtent(@NonNull RecyclerView.State state) {
        if (getChildCount() == 0) {
            return 0;
        }
        ViewInfo viewInfoStart = viewInfoArray.get(firstVisibleItemPosition);
        ViewInfo viewInfoEnd = viewInfoArray.get(lastVisibleItemPosition);
        return Math.min(viewInfoEnd.measureBottom - viewInfoStart.measureTop, getHeight() - getPaddingTop() - getPaddingBottom());
    }
}
