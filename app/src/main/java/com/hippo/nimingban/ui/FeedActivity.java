/*
 * Copyright 2015 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.nimingban.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.h6ah4i.android.widget.advrecyclerview.animator.GeneralItemAnimator;
import com.h6ah4i.android.widget.advrecyclerview.animator.SwipeDismissItemAnimator;
import com.h6ah4i.android.widget.advrecyclerview.swipeable.RecyclerViewSwipeManager;
import com.h6ah4i.android.widget.advrecyclerview.swipeable.SwipeableItemAdapter;
import com.h6ah4i.android.widget.advrecyclerview.touchguard.RecyclerViewTouchActionGuardManager;
import com.h6ah4i.android.widget.advrecyclerview.utils.AbstractSwipeableItemViewHolder;
import com.h6ah4i.android.widget.advrecyclerview.utils.WrapperAdapterUtils;
import com.hippo.nimingban.Constants;
import com.hippo.nimingban.NMBApplication;
import com.hippo.nimingban.R;
import com.hippo.nimingban.client.NMBClient;
import com.hippo.nimingban.client.NMBRequest;
import com.hippo.nimingban.client.data.ACSite;
import com.hippo.nimingban.client.data.Post;
import com.hippo.nimingban.client.data.Site;
import com.hippo.nimingban.itemanimator.FloatItemAnimator;
import com.hippo.nimingban.util.ReadableTime;
import com.hippo.nimingban.util.Settings;
import com.hippo.nimingban.widget.ContentLayout;
import com.hippo.nimingban.widget.LoadImageView;
import com.hippo.rippleold.RippleSalon;
import com.hippo.widget.Snackbar;
import com.hippo.widget.recyclerview.EasyRecyclerView;
import com.hippo.widget.recyclerview.MarginItemDecoration;
import com.hippo.yorozuya.LayoutUtils;
import com.hippo.yorozuya.Messenger;
import com.hippo.yorozuya.NumberUtils;
import com.hippo.yorozuya.ObjectUtils;
import com.hippo.yorozuya.ResourcesUtils;

import java.util.List;

public final class FeedActivity extends TranslucentActivity implements EasyRecyclerView.OnItemClickListener {

    private static final String TAG = FeedActivity.class.getSimpleName();

    private NMBClient mNMBClient;

    private FeedHelper mFeedHelper;

    private ContentLayout mContentLayout;
    private EasyRecyclerView mRecyclerView;
    private RecyclerView.Adapter mAdapter;
    private RecyclerView.Adapter mWrappedAdapter;
    private RecyclerViewSwipeManager mRecyclerViewSwipeManager;
    private RecyclerViewTouchActionGuardManager mRecyclerViewTouchActionGuardManager;

    private NMBRequest mNMBRequest;

    @Override
    protected int getLightThemeResId() {
        return Settings.getColorStatusBar() ? R.style.NormalActivity : R.style.NormalActivity_NoStatus;
    }

    @Override
    protected int getDarkThemeResId() {
        return Settings.getColorStatusBar() ? R.style.NormalActivity_Dark : R.style.NormalActivity_Dark_NoStatus;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mNMBClient = NMBApplication.getNMBClient(this);

        setStatusBarColor(ResourcesUtils.getAttrColor(this, R.attr.colorPrimaryDark));
        ToolbarActivityHelper.setContentView(this, R.layout.activity_feed);
        setActionBarUpIndicator(getResources().getDrawable(R.drawable.ic_arrow_left_dark_x24));

        mContentLayout = (ContentLayout) findViewById(R.id.content_layout);
        mRecyclerView = mContentLayout.getRecyclerView();

        mFeedHelper = new FeedHelper();
        mFeedHelper.setEmptyString(getString(R.string.no_feed));
        mContentLayout.setHelper(mFeedHelper);
        if (Settings.getFastScroller()) {
            mContentLayout.showFastScroll();
        } else {
            mContentLayout.hideFastScroll();
        }

        // Layout Manager
        int halfInterval = getResources().getDimensionPixelOffset(R.dimen.card_interval) / 2;
        if (getResources().getBoolean(R.bool.two_way)) {
            mRecyclerView.setLayoutManager(new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL));
            mRecyclerView.addItemDecoration(new MarginItemDecoration(halfInterval));
            mRecyclerView.setPadding(halfInterval, halfInterval, halfInterval, halfInterval);
            mRecyclerView.setItemAnimator(new FloatItemAnimator(mRecyclerView));
        } else {
            mRecyclerView.addItemDecoration(new MarginItemDecoration(0, halfInterval, 0, halfInterval));
            mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
            mRecyclerView.setPadding(0, halfInterval, 0, halfInterval);
        }

        // touch guard manager  (this class is required to suppress scrolling while swipe-dismiss animation is running)
        mRecyclerViewTouchActionGuardManager = new RecyclerViewTouchActionGuardManager();
        mRecyclerViewTouchActionGuardManager.setInterceptVerticalScrollingWhileAnimationRunning(true);
        mRecyclerViewTouchActionGuardManager.setEnabled(true);

        // swipe manager
        mRecyclerViewSwipeManager = new RecyclerViewSwipeManager();

        mAdapter = new FeedAdapter();
        mAdapter.setHasStableIds(true);
        mWrappedAdapter = mRecyclerViewSwipeManager.createWrappedAdapter(mAdapter);      // wrap for swiping

        final GeneralItemAnimator animator = new SwipeDismissItemAnimator();

        // Change animations are enabled by default since support-v7-recyclerview v22.
        // Disable the change animation in order to make turning back animation of swiped item works properly.
        animator.setSupportsChangeAnimations(false);

        mRecyclerView.hasFixedSize();
        mRecyclerView.setAdapter(mWrappedAdapter);  // requires *wrapped* adapter
        mRecyclerView.setItemAnimator(animator);
        mRecyclerView.setOnItemClickListener(this);
        mRecyclerView.setSelector(RippleSalon.generateRippleDrawable(ResourcesUtils.getAttrBoolean(this, R.attr.dark)));
        mRecyclerView.setDrawSelectorOnTop(true);
        mRecyclerView.setClipToPadding(false);
        mRecyclerView.setClipChildren(false);

        // NOTE:
        // The initialization order is very important! This order determines the priority of touch event handling.
        //
        // priority: TouchActionGuard > Swipe > DragAndDrop
        mRecyclerViewTouchActionGuardManager.attachRecyclerView(mRecyclerView);
        mRecyclerViewSwipeManager.attachRecyclerView(mRecyclerView);

        mFeedHelper.firstRefresh();

        Messenger.getInstance().register(Constants.MESSENGER_ID_FAST_SCROLLER, this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        Messenger.getInstance().unregister(Constants.MESSENGER_ID_FAST_SCROLLER, this);

        if (mRecyclerViewSwipeManager != null) {
            mRecyclerViewSwipeManager.release();
            mRecyclerViewSwipeManager = null;
        }

        if (mRecyclerViewTouchActionGuardManager != null) {
            mRecyclerViewTouchActionGuardManager.release();
            mRecyclerViewTouchActionGuardManager = null;
        }

        if (mRecyclerView != null) {
            mRecyclerView.setItemAnimator(null);
            mRecyclerView.setAdapter(null);
            mRecyclerView = null;
        }

        if (mWrappedAdapter != null) {
            WrapperAdapterUtils.releaseAll(mWrappedAdapter);
            mWrappedAdapter = null;
        }
        mAdapter = null;

        if (mNMBRequest != null) {
            mNMBRequest.cancel();
            mNMBRequest = null;
        }
    }

    @Override
    public void onReceive(int id, Object obj) {
        if (Constants.MESSENGER_ID_FAST_SCROLLER == id) {
            if (obj instanceof Boolean) {
                if ((Boolean) obj) {
                    mContentLayout.showFastScroll();
                } else {
                    mContentLayout.hideFastScroll();
                }
            }
        } else {
            super.onReceive(id, obj);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onItemClick(EasyRecyclerView parent, View view, int position, long id) {
        Intent intent = new Intent(this, PostActivity.class);
        intent.setAction(PostActivity.ACTION_POST);
        intent.putExtra(PostActivity.KEY_POST, mFeedHelper.getDataAt(position));
        startActivity(intent);
        return true;
    }

    private class FeedHolder extends AbstractSwipeableItemViewHolder implements View.OnClickListener {

        public View swipable;
        public TextView leftText;
        public TextView centerText;
        public TextView rightText;
        private TextView content;
        private LoadImageView thumb;

        public FeedHolder(View itemView) {
            super(itemView);

            swipable = itemView.findViewById(R.id.swipable);
            leftText = (TextView) itemView.findViewById(R.id.left_text);
            centerText = (TextView) itemView.findViewById(R.id.center_text);
            rightText = (TextView) itemView.findViewById(R.id.right_text);
            content = (TextView) itemView.findViewById(R.id.content);
            thumb = (LoadImageView) itemView.findViewById(R.id.thumb);

            thumb.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            int position = getAdapterPosition();
            if (position >= 0 && position < mFeedHelper.size()) {
                Post post = mFeedHelper.getDataAt(position);
                String image = post.getNMBImageUrl();
                if (!TextUtils.isEmpty(image)) {
                    Intent intent = new Intent(FeedActivity.this, GalleryActivity2.class);
                    intent.setAction(GalleryActivity2.ACTION_SINGLE_IMAGE);
                    intent.putExtra(GalleryActivity2.KEY_SITE, post.getNMBSite().getId());
                    intent.putExtra(GalleryActivity2.KEY_ID, post.getNMBId());
                    intent.putExtra(GalleryActivity2.KEY_IMAGE, image);
                    FeedActivity.this.startActivity(intent);
                }
            }
        }

        @Override
        public View getSwipeableContainerView() {
            return swipable;
        }
    }

    private class FeedAdapter extends RecyclerView.Adapter<FeedHolder> implements SwipeableItemAdapter<FeedHolder> {

        @Override
        public FeedHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
            return new FeedHolder(getLayoutInflater().inflate(R.layout.item_feed, viewGroup, false));
        }

        @Override
        public void onBindViewHolder(FeedHolder holder, int i) {
            Post post = mFeedHelper.getDataAt(i);
            holder.leftText.setText(post.getNMBDisplayUsername());
            holder.centerText.setText("No." + post.getNMBId());
            holder.rightText.setText(ReadableTime.getDisplayTime(post.getNMBTime()));
            holder.content.setText(post.getNMBDisplayContent());

            String thumbUrl = post.getNMBThumbUrl();

            boolean showImage;
            boolean loadFromNetwork;
            int ils = Settings.getImageLoadingStrategy();
            if (ils == Settings.IMAGE_LOADING_STRATEGY_ALL ||
                    (ils == Settings.IMAGE_LOADING_STRATEGY_WIFI && NMBApplication.isConnectedWifi(FeedActivity.this))) {
                showImage = true;
                loadFromNetwork = true;
            } else {
                showImage = Settings.getImageLoadingStrategy2();
                loadFromNetwork = false;
            }

            if (!TextUtils.isEmpty(thumbUrl) && showImage) {
                holder.thumb.setVisibility(View.VISIBLE);
                holder.thumb.unload();
                holder.thumb.load(thumbUrl, thumbUrl, loadFromNetwork);
            } else {
                holder.thumb.setVisibility(View.GONE);
                holder.thumb.unload();
            }

            holder.content.setTextSize(Settings.getFontSize());
            holder.content.setLineSpacing(LayoutUtils.dp2pix(FeedActivity.this, Settings.getLineSpacing()), 1.0f);
        }

        @Override
        public long getItemId(int position) {
            Post post = mFeedHelper.getDataAt(position);
            return NumberUtils.parseLongSafely(post.getNMBId(), 0l);
        }

        @Override
        public int getItemCount() {
            return mFeedHelper.size();
        }

        @Override
        public int onGetSwipeReactionType(FeedHolder holder, int position, int x, int y) {
            return RecyclerViewSwipeManager.REACTION_CAN_SWIPE_BOTH;
        }

        @Override
        public void onSetSwipeBackground(FeedHolder draftHolder, int i, int i1) {
            // Empty
        }

        @Override
        public void onSwipeItemStarted(FeedHolder holder, int position) {
            mFeedHelper.setEnable(false);
        }

        @Override
        public int onSwipeItem(FeedHolder holder, int position, int result) {
            switch (result) {
                // remove
                case RecyclerViewSwipeManager.RESULT_SWIPED_RIGHT:
                case RecyclerViewSwipeManager.RESULT_SWIPED_LEFT:
                    return RecyclerViewSwipeManager.AFTER_SWIPE_REACTION_REMOVE_ITEM;
                // other --- do nothing
                case RecyclerViewSwipeManager.RESULT_CANCELED:
                default:
                    return RecyclerViewSwipeManager.AFTER_SWIPE_REACTION_DEFAULT;
            }
        }

        @Override
        public void onPerformAfterSwipeReaction(FeedHolder holder, final int position, int result, int reaction) {
            mFeedHelper.setEnable(true);

            if (reaction == RecyclerViewSwipeManager.AFTER_SWIPE_REACTION_REMOVE_ITEM) {
                String previousId;
                String nextId;
                if (position <= 0) {
                    previousId = null;
                } else {
                    previousId = mFeedHelper.getDataAt(position - 1).getNMBId();
                }
                if (position >= mFeedHelper.size() - 1) {
                    nextId = null;
                } else {
                    nextId = mFeedHelper.getDataAt(position + 1).getNMBId();
                }
                final Post post = mFeedHelper.getDataAt(position);
                final String id = post.getNMBId();
                final String oldPreviousId = previousId;
                final String oldNextId = nextId;

                mFeedHelper.removeAt(position);
                if (mFeedHelper.size() == 0) {
                    mFeedHelper.showEmptyString();
                }

                Snackbar snackbar = Snackbar.make(mRecyclerView, R.string.feed_deleted, Snackbar.LENGTH_LONG);
                snackbar.setAction(R.string.undo, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String previousId;
                        String nextId;
                        if (position <= 0) {
                            previousId = null;
                        } else {
                            previousId = mFeedHelper.getDataAt(position - 1).getNMBId();
                        }
                        if (position >= mFeedHelper.size()) {
                            nextId = null;
                        } else {
                            nextId = mFeedHelper.getDataAt(position).getNMBId();
                        }

                        if (ObjectUtils.equal(previousId, oldPreviousId) && ObjectUtils.equal(nextId, oldNextId)) {
                            mFeedHelper.addAt(position, post);
                            if (mFeedHelper.size() != 0) {
                                mFeedHelper.showContent();
                            }
                        } else {
                            mFeedHelper.refresh();
                        }
                    }
                });
                snackbar.setCallback(new Snackbar.Callback() {
                    @Override
                    public void onDismissed(Snackbar snackbar, int event) {
                        if (event != DISMISS_EVENT_ACTION) {
                            NMBRequest request = new NMBRequest();
                            request.setSite(ACSite.getInstance());
                            request.setMethod(NMBClient.METHOD_DEL_FEED);
                            request.setArgs(ACSite.getInstance().getUserId(FeedActivity.this), id);
                            request.setCallback(new DelFeedListener());
                            mNMBClient.execute(request);
                        }
                    }
                });
                snackbar.show();
            }
        }
    }

    private class FeedHelper extends ContentLayout.ContentHelper<Post> {

        @Override
        protected void getPageData(int taskId, int type, int page) {
            Site site = ACSite.getInstance(); // TODO only AC ?
            NMBRequest request = new NMBRequest();
            mNMBRequest = request;
            request.setSite(site);
            request.setMethod(NMBClient.METHOD_GET_FEED);
            request.setArgs(site.getUserId(FeedActivity.this), page);
            request.setCallback(new FeedListener(taskId, page, request));
            mNMBClient.execute(request);
        }

        @Override
        protected Context getContext() {
            return FeedActivity.this;
        }

        @Override
        protected void notifyDataSetChanged() {
            mAdapter.notifyDataSetChanged();
        }

        @Override
        protected void notifyItemRangeRemoved(int positionStart, int itemCount) {
            mAdapter.notifyItemRangeRemoved(positionStart, itemCount);
        }

        @Override
        protected void notifyItemRangeInserted(int positionStart, int itemCount) {
            mAdapter.notifyItemRangeInserted(positionStart, itemCount);
        }
    }

    private class FeedListener implements NMBClient.Callback<List<Post>> {

        private int mTaskId;
        private int mTaskPage;
        private NMBRequest mRequest;

        public FeedListener(int taskId, int taskPage, NMBRequest request) {
            mTaskId = taskId;
            mTaskPage = taskPage;
            mRequest = request;
        }

        @Override
        public void onSuccess(List<Post> result) {
            if (mNMBRequest == mRequest) {
                // It is current request

                // Clear
                mNMBRequest = null;

                if (result.isEmpty()) {
                    mFeedHelper.setPages(mTaskPage);
                    mFeedHelper.onGetEmptyData(mTaskId);
                } else {
                    mFeedHelper.setPages(Integer.MAX_VALUE);
                    mFeedHelper.onGetPageData(mTaskId, result);
                }
            }
            // Clear
            mRequest = null;
        }

        @Override
        public void onFailure(Exception e) {
            if (mNMBRequest == mRequest) {
                // It is current request

                // Clear
                mNMBRequest = null;

                mFeedHelper.onGetExpection(mTaskId, e);
            }
            // Clear
            mRequest = null;
        }

        @Override
        public void onCancel() {
            if (mNMBRequest == mRequest) {
                // It is current request

                // Clear
                mNMBRequest = null;
            }
            // Clear
            mRequest = null;
        }
    }

    private static class DelFeedListener implements NMBClient.Callback<Void> {

        @Override
        public void onSuccess(Void result) {
            Log.d(TAG, "del feed onSuccess");
        }

        @Override
        public void onFailure(Exception e) {
            Log.d(TAG, "del feed onFailure");
        }

        @Override
        public void onCancel() {
            Log.d(TAG, "del feed onCancel");
        }
    }
}
