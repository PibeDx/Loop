package com.etiennelawlor.loop.ui;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.TypedArray;
import android.speech.RecognizerIntent;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.etiennelawlor.loop.R;
import com.etiennelawlor.loop.adapters.SuggestionsAdapter;
import com.etiennelawlor.loop.otto.BusProvider;
import com.etiennelawlor.loop.otto.events.BackPressedEvent;
import com.etiennelawlor.loop.otto.events.FilterClickedEvent;
import com.etiennelawlor.loop.otto.events.SearchPerformedEvent;
import com.etiennelawlor.loop.otto.events.ShowSearchSuggestionsEvent;
import com.etiennelawlor.loop.otto.events.UpNavigationClickedEvent;
import com.etiennelawlor.loop.realm.RealmUtility;
import com.etiennelawlor.loop.utilities.LoopUtility;
import com.squareup.otto.Subscribe;

import java.util.ArrayList;
import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnFocusChange;
import butterknife.OnTextChanged;
import retrofit.Call;
import timber.log.Timber;

/**
 * Created by etiennelawlor on 10/6/15.
 */
public class MaterialSearchView extends FrameLayout implements
        SuggestionsAdapter.OnItemClickListener,
        SuggestionsAdapter.OnItemLongClickListener,
        SuggestionsAdapter.OnSearchSuggestionCompleteClickListener {

    // region Constants
    public static final int REQUEST_VOICE = 9999;
    // endregion

    // region Member Variables
    private boolean mAreSearchSuggestionsVisible;
    private DividerItemDecoration mDividerItemDecoration;
    private Integer mDefaultUpNavIcon;
    private SuggestionsAdapter mSuggestionsAdapter = new SuggestionsAdapter();
    private boolean mIsSearchEditTextFocused = false;

    @Bind(R.id.search_et)
    EditText mSearchEditText;
    @Bind(R.id.microphone_iv)
    ImageView mMicrophoneImageView;
    @Bind(R.id.clear_iv)
    ImageView mClearImageView;
    @Bind(R.id.filter_iv)
    ImageView mFilterImageView;
    @Bind(R.id.cv)
    CardView mCardView;
//    @Bind(R.id.user_avatar_riv)
//    CircleImageView mUserAvatarCircleImageView;
//    @Bind(R.id.back_iv)
//    ImageView mBackImageView;
    @Bind(R.id.up_navigation_iv)
    ImageView mUpNavigationImageView;
    @Bind(R.id.custom_search_view_ll)
    LinearLayout mCustomSearchViewLinearLayout;
    @Bind(R.id.divider_v)
    View mDividerView;
    @Bind(R.id.bg_cover_fl)
    FrameLayout mBackgroundCoverFrameLayout;
    @Bind(R.id.rv)
    RecyclerView mRecyclerView;
    // endregion

    // region Listeners
//    @OnClick(R.id.custom_search_view_ll)
//    public void searchViewLinearLayoutClicked(){
////        if(!mAreSearchSuggestionsVisible){
////            showSearchSuggestions();
////        }
//        mSearchEditText.requestFocus();
//    }

    @OnClick(R.id.bg_cover_fl)
    public void backgroundCoverFrameLayoutClicked(){
        if(mAreSearchSuggestionsVisible){
            hideSearchSuggestions();
        }
    }

    @OnClick(R.id.microphone_iv)
    public void microphoneImageViewClicked(){
        if(isVoiceAvailable()){
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now");
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);

            ((Activity)((ContextWrapper)mMicrophoneImageView.getContext()).getBaseContext()).startActivityForResult(intent, REQUEST_VOICE);
        }
    }

    @OnClick(R.id.filter_iv)
    public void filterImageViewClicked(){
        BusProvider.get().post(new FilterClickedEvent());
    }

    @OnClick(R.id.up_navigation_iv)
    public void upNavigationImageViewClicked(){
        if(mAreSearchSuggestionsVisible){
            hideSearchSuggestions();
        } else {
            Timber.d("Do something else");

            UpNavigationClickedEvent.Type type = null;
            switch (mDefaultUpNavIcon){
                case 0:
                    type = UpNavigationClickedEvent.Type.MENU;
                    break;
                case 1:
                    type = UpNavigationClickedEvent.Type.BACK;
                    break;
                default:
                    break;
            }

            BusProvider.get().post(new UpNavigationClickedEvent(type));
        }
    }

    @OnClick(R.id.clear_iv)
    public void clearImageViewClicked(){
        mSearchEditText.setText("");
    }

    @OnTextChanged(R.id.search_et)
    public void onSearchEditTextTextChanged(CharSequence text){
        if (text.length() > 0) {
            mMicrophoneImageView.setVisibility(View.GONE);
            mClearImageView.setVisibility(View.VISIBLE);
        } else {
            mClearImageView.setVisibility(View.GONE);
            mMicrophoneImageView.setVisibility(View.VISIBLE);
        }

        if(mIsSearchEditTextFocused) {
            mSuggestionsAdapter.setCurrentQuery(text.toString());
            BusProvider.get().post(new ShowSearchSuggestionsEvent(text.toString()));
        }

        mFilterImageView.setVisibility(View.GONE);
    }

    @OnClick(R.id.search_et)
    public void searchEditTextClicked(){
        mSearchEditText.requestFocus();
    }

    @OnFocusChange(R.id.search_et)
    public void onSearchEditTextFocusChanged(boolean focused) {
        Timber.d("onSearchEditTextFocusChanged() : focused - " + focused);
        mIsSearchEditTextFocused = focused;

        if(mIsSearchEditTextFocused){
            if(!mAreSearchSuggestionsVisible){
                showSearchSuggestions();
            }
            LoopUtility.showKeyboard(getContext(), mSearchEditText);
        } else {
            LoopUtility.hideKeyboard(getContext(), mSearchEditText);
        }
    }
    // endregion

    // region Constructors
    public MaterialSearchView(Context context) {
        super(context);
        init(null);
    }

    public MaterialSearchView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public MaterialSearchView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }
    // endregion

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        BusProvider.get().register(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        BusProvider.get().unregister(this);
    }

    // region SuggestionsAdapter.OnItemClickListener Methods
    @Override
    public void onItemClick(int position, View view) {
//        Timber.d("");
        Timber.d("SuggestionsAdapter : onItemClick()");

        TextView suggestionTextView = (TextView) view.findViewById(R.id.suggestion_tv);
        String suggestion = suggestionTextView.getText().toString();
//        Timber.d("SuggestionsAdapter : onItemClick() : suggestionTextView.getText() - "+suggestionTextView.getText());

        hideSearchSuggestions();

        BusProvider.get().post(new SearchPerformedEvent(suggestion));

//        setQuery("");
    }
    // endregion

    // region SuggestionsAdapter.OnItemLongClickListener Methods

    @Override
    public void onItemLongClick(int position, View view) {
        TextView suggestionTextView = (TextView) view.findViewById(R.id.suggestion_tv);
        final String suggestion = suggestionTextView.getText().toString();
        Timber.d("SuggestionsAdapter : onItemLongClick() : suggestion -  "+suggestion);

        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getContext(), R.style.DialogTheme);
        alertDialogBuilder.setMessage("Remove from search history?");
        alertDialogBuilder.setPositiveButton(getContext().getString(android.R.string.ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                RealmUtility.deleteQuery(suggestion);
                BusProvider.get().post(new ShowSearchSuggestionsEvent(getQuery()));
                dialog.dismiss();
            }
        });
        alertDialogBuilder.setNegativeButton(getContext().getString(android.R.string.cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        alertDialogBuilder.show();
    }
    // endregion

    // region SuggestionsAdapter.OnSearchSuggestionCompleteClickListener Methods
    @Override
    public void onSearchSuggestionCompleteClickListener(int position, TextView textView) {
        mSearchEditText.setText(textView.getText());
        int textLength = mSearchEditText.getText().length();
        mSearchEditText.setSelection(textLength, textLength);
    }
    // endregion

    // region Otto Methods
    @Subscribe
    public void onBackPressedEvent(BackPressedEvent event) {
//        String query = event.getQuery();
//        if (!TextUtils.isEmpty(query)) {
//            launchSearchActivity(query);
//        }
        hideSearchSuggestions();
    }
    // endregion

    // region Helper Methods
    private void init(AttributeSet attrs){
//        if (isInEditMode()) {
//            return;
//        }

        LayoutInflater.from(getContext()).inflate((R.layout.material_search_view), this, true);
        ButterKnife.bind(this);

        if (attrs != null) {
            TypedArray a = getContext().getTheme().obtainStyledAttributes(attrs, R.styleable.MaterialSearchView, 0, 0);
            try {
                mDefaultUpNavIcon = a.getInteger(R.styleable.MaterialSearchView_default_up_nav_icon, 1);
            } finally {
                a.recycle();
            }
        }

        setUpDefaultUpNavIcon();

        setUpListeners();
    }

    private void setUpListeners(){
        mSearchEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {

                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    hideSearchSuggestions();

                    BusProvider.get().post(new SearchPerformedEvent(getQuery()));

//                    setQuery("");
                    return true;
                } else {
                    return false;
                }
            }
        });
    }

    private void setUpDefaultUpNavIcon(){
        switch (mDefaultUpNavIcon){
            case 0:
                mUpNavigationImageView.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.ic_menu_black_24dp));
                break;
            case 1:
                mUpNavigationImageView.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.ic_arrow_back_black_24dp));
                break;
            default:
                break;
        }
    }

    private void showSearchSuggestions(){
        Timber.d("showSearchSuggestions()");

        BusProvider.get().post(new ShowSearchSuggestionsEvent(getQuery()));


//        List<String> suggestions = new ArrayList<String>();
//        suggestions.add("Bodyboarding");
//        suggestions.add("Surfing");
//        suggestions.add("Wind");
//        suggestions.add("Snowboarding");
//        suggestions.add("Skiing");
//        suggestions.add("Skateboarding");
//
////                suggestions.add("BMX");
////                suggestions.add("Motocross");
//
//        mSuggestionsAdapter.addAll(suggestions);

        mSuggestionsAdapter.setOnItemClickListener(this);
        mSuggestionsAdapter.setOnItemLongClickListener(this);
        mSuggestionsAdapter.setOnSearchSuggestionCompleteClickListener(this);

        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        mRecyclerView.setLayoutManager(layoutManager);

        mDividerItemDecoration = new DividerItemDecoration(getResources().getDrawable(R.drawable.divider));
        mRecyclerView.addItemDecoration(mDividerItemDecoration);
        mRecyclerView.setAdapter(mSuggestionsAdapter);

        if(mSuggestionsAdapter.getItemCount() > 0){
            mRecyclerView.setVisibility(View.VISIBLE);
            mDividerView.setVisibility(View.VISIBLE);
        } else {
            mRecyclerView.setVisibility(View.GONE);
            mDividerView.setVisibility(View.GONE);
        }

        mBackgroundCoverFrameLayout.setVisibility(View.VISIBLE);


//        mUserAvatarCircleImageView.setVisibility(View.GONE);
//        mBackImageView.setVisibility(View.VISIBLE);

        mUpNavigationImageView.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.ic_arrow_back_black_24dp));

        mAreSearchSuggestionsVisible = true;
    }

    private void hideSearchSuggestions(){
        mDividerView.setVisibility(View.GONE);
        mBackgroundCoverFrameLayout.setVisibility(View.GONE);

//        mBackImageView.setVisibility(View.GONE);
//        mUserAvatarCircleImageView.setVisibility(View.VISIBLE);

//        mUpNavigationImageView.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.ic_menu_black_24dp));

        setUpDefaultUpNavIcon();

        mRecyclerView.setVisibility(View.GONE);
        mRecyclerView.removeItemDecoration(mDividerItemDecoration);

        mAreSearchSuggestionsVisible = false;
        mSearchEditText.clearFocus();
    }

    private boolean isVoiceAvailable() {
        PackageManager pm = getContext().getPackageManager();
        List<ResolveInfo> activities = pm.queryIntentActivities(
                new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0);
        return (activities.size() != 0);
    }

    public void setQuery(String query){
        mSearchEditText.setText(query);
        mFilterImageView.setVisibility(View.VISIBLE);
    }

    public String getQuery(){
        return mSearchEditText.getText().toString();
    }

    public void addSuggestions(List<String> suggestions){
        mSuggestionsAdapter.clear();
        mSuggestionsAdapter.addAll(suggestions);

        if(mSuggestionsAdapter.getItemCount() > 0){
            mRecyclerView.setVisibility(View.VISIBLE);
            mDividerView.setVisibility(View.VISIBLE);
        } else {
            mRecyclerView.setVisibility(View.GONE);
            mDividerView.setVisibility(View.GONE);
        }

        mBackgroundCoverFrameLayout.setVisibility(View.VISIBLE);
    }
    // endregion
}
