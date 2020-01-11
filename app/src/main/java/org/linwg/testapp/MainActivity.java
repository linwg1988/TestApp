package org.linwg.testapp;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import www.lssc.com.common.adapter.BaseRecyclerAdapter;
import www.lssc.com.common.app.AbstractBaseActivity;
import www.lssc.com.common.utils.ToastUtil;
import www.lssc.com.common.view.recyclerview.SmartRecyclerView;

public class MainActivity extends AbstractBaseActivity {

    List<String> dataList = new ArrayList<>();
    TextView tvSmart;
    TextView tvLinear;

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_main;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        for (int i = 0; i < 100; i++) {
            dataList.add(String.valueOf(i));
        }
        tvSmart = findViewById(R.id.tvSmart);
        tvLinear = findViewById(R.id.tvLinear);
        SmartRecyclerView.registerErrorView(SmartRecyclerView.ERR_TYPE_EMPTY, R.layout.layout_empty);
        SmartRecyclerView smartRecyclerView = findViewById(R.id.swipe_target);
        SmartRecyclerView recyclerView = findViewById(R.id.recyclerView);
        smartRecyclerView.setLayoutManager(new FloatingHeaderLayoutManager(1, 3));
        recyclerView.setLayoutManager(new LinearLayoutManager(mContext));
        TestAdapter baseRecyclerAdapter = new TestAdapter(this, dataList);
        TestAdapter2 testAdapter2 = new TestAdapter2(this, dataList);
        smartRecyclerView.setAdapter(baseRecyclerAdapter);
        recyclerView.setAdapter(testAdapter2);
        tvSmart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                baseRecyclerAdapter.addData(String.valueOf(baseRecyclerAdapter.getItemCount()));
            }
        });
        tvLinear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                testAdapter2.addData(String.valueOf(testAdapter2.getItemCount()));
            }
        });
        smartRecyclerView.setOnPreloadListener(new SmartRecyclerView.OnPreloadListener() {
            @Override
            public void onLastPositionVisible() {
                Log.e("SmartRecyclerView", "onLastPositionVisible");
            }

            @Override
            public void onDistanceConformRemind() {
                Log.e("SmartRecyclerView", "onDistanceConformRemind");
                baseRecyclerAdapter.addData(String.valueOf(baseRecyclerAdapter.getItemCount()));
            }
        });
        findViewById(R.id.tvAdd).setOnClickListener(v ->
                smartRecyclerView.showEmptyView()
        );
        smartRecyclerView.setOnItemClickListener((viewHolder, i) -> ToastUtil.show(MainActivity.this, "Click Position:" + viewHolder.getLayoutPosition()));
        smartRecyclerView.setOnItemLongClickListener((viewHolder, i) -> ToastUtil.show(MainActivity.this, "Long Click Position:" + viewHolder.getLayoutPosition()));
    }

    class TestAdapter extends BaseRecyclerAdapter<String, RecyclerView.ViewHolder> {
        int i = 0;
        int[] colors = {Color.RED, Color.GREEN, Color.BLUE, Color.CYAN, Color.GRAY, Color.DKGRAY, Color.YELLOW, Color.MAGENTA, Color.LTGRAY};

        public TestAdapter(@NonNull Context context, @Nullable List<String> list) {
            super(context, list);
        }

        @Override
        public int getItemViewType(int position) {
            if (position == 1 || position == 3) {
                return 1;
            }
            return super.getItemViewType(position);
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Log.e("FLM", "onCreateViewHolder");
            i++;
            tvSmart.setText("FLM当前创建VH个数 ： " + i);
            View view = LayoutInflater.from(mContext).inflate(R.layout.test_item, parent, false);
            return new RecyclerView.ViewHolder(view) {
            };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            int layoutPosition = holder.getLayoutPosition();
            Log.e("FLM", "onBindViewHolder" + layoutPosition);
            TextView tvTest = holder.itemView.findViewById(R.id.tvTest);
            if (getItemViewType(layoutPosition) == 1) {
                tvTest.setBackgroundColor(colors[0]);
            } else {
                tvTest.setBackgroundColor(colors[1]);
            }
            tvTest.setText(String.valueOf(layoutPosition));
        }
    }

    class TestAdapter2 extends BaseRecyclerAdapter<String, RecyclerView.ViewHolder> {
        int i;
        int[] colors = {Color.RED, Color.GREEN, Color.BLUE, Color.CYAN, Color.GRAY, Color.DKGRAY, Color.YELLOW, Color.MAGENTA, Color.LTGRAY};

        public TestAdapter2(@NonNull Context context, @Nullable List<String> list) {
            super(context, list);
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Log.e("LLM", "onCreateViewHolder");
            i++;
            tvLinear.setText("LLM当前创建VH个数 ： " + i);
            View view = LayoutInflater.from(mContext).inflate(R.layout.test_item, parent, false);
            return new RecyclerView.ViewHolder(view) {
            };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            TextView tvTest = holder.itemView.findViewById(R.id.tvTest);
            tvTest.setBackgroundColor(colors[holder.getLayoutPosition() % colors.length]);
            tvTest.setText(String.valueOf(holder.getLayoutPosition()));
        }
    }
}
