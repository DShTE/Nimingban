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

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Dialog;
import android.app.Fragment;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.SystemClock;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.alibaba.fastjson.JSON;
import com.hippo.nimingban.Constants;
import com.hippo.nimingban.NMBAppConfig;
import com.hippo.nimingban.NMBApplication;
import com.hippo.nimingban.R;
import com.hippo.nimingban.client.data.ACSite;
import com.hippo.nimingban.network.SimpleCookieStore;
import com.hippo.nimingban.network.TransportableHttpCookie;
import com.hippo.nimingban.service.DaDiaoService;
import com.hippo.nimingban.util.LinkMovementMethod2;
import com.hippo.nimingban.util.OpenUrlHelper;
import com.hippo.nimingban.util.ReadableTime;
import com.hippo.nimingban.util.Settings;
import com.hippo.preference.FixedSwitchPreference;
import com.hippo.text.Html;
import com.hippo.unifile.UniFile;
import com.hippo.util.ActivityHelper;
import com.hippo.util.LogCat;
import com.hippo.widget.Slider;
import com.hippo.yorozuya.IOUtils;
import com.hippo.yorozuya.LayoutUtils;
import com.hippo.yorozuya.Messenger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.HttpCookie;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class SettingsActivity extends AbsPreferenceActivity {

    private static final int REQUEST_CODE_FRAGMENT = 0;

    private static final String[] ENTRY_FRAGMENTS = {
            DisplayFragment.class.getName(),
            ConfigFragment.class.getName(),
            InfoFragment.class.getName()
    };

    @Override
    protected int getLightThemeResId() {
        return R.style.AppTheme;
    }

    @Override
    protected int getDarkThemeResId() {
        return R.style.AppTheme_Dark;
    }

    private class FakeLayoutInflater extends LayoutInflater {

        private LayoutInflater mInflater;

        protected FakeLayoutInflater(LayoutInflater inflater) {
            super(null);
            mInflater = inflater;
        }

        @Override
        public LayoutInflater cloneInContext(Context newContext) {
            return null;
        }

        @Override
        public View inflate(int resource, ViewGroup root, boolean attachToRoot) {
            return mInflater.inflate(R.layout.item_preference_header, root, attachToRoot);
        }
    }

    private void replaceHeaderLayoutResId() {
        try {
            ListAdapter adapter = getListAdapter();
            Class headerAdapterClazz = Class.forName("android.preference.PreferenceActivity$HeaderAdapter");
            if (!headerAdapterClazz.isInstance(adapter)) {
                return;
            }

            boolean ok = false;

            try {
                Field field = headerAdapterClazz.getDeclaredField("mLayoutResId");
                field.setAccessible(true);
                field.setInt(adapter, R.layout.item_preference_header);

                ok = true;
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            }

            if (!ok) {
                try {
                    Field field = headerAdapterClazz.getDeclaredField("mInflater");
                    field.setAccessible(true);
                    field.set(adapter, new FakeLayoutInflater((LayoutInflater) field.get(adapter)));

                    ok = true;
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (NoSuchFieldException e) {
                    e.printStackTrace();
                } catch (ClassCastException e) {
                    e.printStackTrace();
                }
            }

            if (ok) {
                getListView().setDivider(getResources().getDrawable(R.drawable.transparent));
                getListView().setDividerHeight(0);
            }

        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setActionBarUpIndicator(getResources().getDrawable(R.drawable.ic_arrow_left_dark_x24));

        replaceHeaderLayoutResId();
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
    public void onBuildHeaders(List<Header> target) {
        loadHeadersFromResource(R.xml.settings_headers, target);
    }

    @Override
    public void startWithFragment(String fragmentName, Bundle args,
            Fragment resultTo, int resultRequestCode, @StringRes int titleRes,
            @StringRes int shortTitleRes) {
        Intent intent = onBuildStartFragmentIntent(fragmentName, args, titleRes, shortTitleRes);
        if (resultTo == null) {
            startActivityForResult(intent, REQUEST_CODE_FRAGMENT);
        } else {
            resultTo.startActivityForResult(intent, resultRequestCode);
        }
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        for (String fragment : ENTRY_FRAGMENTS) {
            if (fragment.equals(fragmentName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_FRAGMENT) {
            if (resultCode == RESULT_OK) {
                setResult(RESULT_OK);
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    // A bug for SwitchPreference in pre-L
    // http://stackoverflow.com/questions/15632215/preference-items-being-automatically-re-set
    public static class DisplayFragment extends PreferenceFragment
            implements Preference.OnPreferenceClickListener, Preference.OnPreferenceChangeListener {

        private static final String KEY_TEXT_FORMAT = "text_format";

        private FixedSwitchPreference mPrettyTime;
        private Preference mTextFormat;
        private Preference mDynamicComments;
        private ListPreference mImageLoadingStrategy;
        private FixedSwitchPreference mImageLoadingStrategy2;
        private FixedSwitchPreference mFastScroller;
        private FixedSwitchPreference mColorStatusBar;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.display_settings);

            Resources resources = getResources();

            mPrettyTime = (FixedSwitchPreference) findPreference(Settings.KEY_PRETTY_TIME);
            mTextFormat = findPreference(KEY_TEXT_FORMAT);
            mDynamicComments = findPreference(Settings.KEY_DYNAMIC_COMMENTS);
            mImageLoadingStrategy = (ListPreference) findPreference(Settings.KEY_IMAGE_LOADING_STRATEGY);
            mImageLoadingStrategy2 = (FixedSwitchPreference) findPreference(Settings.KEY_IMAGE_LOADING_STRATEGY_2);
            mFastScroller = (FixedSwitchPreference) findPreference(Settings.KEY_FAST_SCROLLER);
            mColorStatusBar = (FixedSwitchPreference) findPreference(Settings.KEY_COLOR_STATUS_BAR);

            mPrettyTime.setOnPreferenceChangeListener(this);
            mDynamicComments.setOnPreferenceChangeListener(this);
            mImageLoadingStrategy.setOnPreferenceChangeListener(this);
            mImageLoadingStrategy2.setOnPreferenceChangeListener(this);
            mFastScroller.setOnPreferenceChangeListener(this);
            mColorStatusBar.setOnPreferenceChangeListener(this);

            mTextFormat.setOnPreferenceClickListener(this);

            long time = System.currentTimeMillis() - 3 * ReadableTime.HOUR_MILLIS;
            String plain = ReadableTime.getPlainTime(time);
            String timeAgo = ReadableTime.getTimeAgo(time);
            mPrettyTime.setSummaryOn(resources.getString(R.string.main_pretty_time_summary, timeAgo, plain));
            mPrettyTime.setSummaryOff(resources.getString(R.string.main_pretty_time_summary, plain, timeAgo));

            updateTextFormatSummary();

            if (Build.VERSION.SDK_INT != Build.VERSION_CODES.KITKAT) {
                mColorStatusBar.setEnabled(false);
                mColorStatusBar.setSummaryOn(R.string.main_color_status_bar_summary_disable);
                mColorStatusBar.setSummaryOff(R.string.main_color_status_bar_summary_disable);
            }
        }

        private void updateTextFormatSummary() {
            mTextFormat.setSummary(getResources().getString(
                    R.string.main_text_format_summary, Settings.getFontSize(),
                    Settings.getLineSpacing()));
        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            String key = preference.getKey();
            if (Settings.KEY_PRETTY_TIME.equals(key)) {
                getActivity().setResult(RESULT_OK);
                return true;
            } else if (Settings.KEY_DYNAMIC_COMMENTS.equals(key)) {
                getActivity().setResult(RESULT_OK);
                return true;
            } else if (Settings.KEY_IMAGE_LOADING_STRATEGY.equals(key)) {
                getActivity().setResult(RESULT_OK);
                return true;
            } else if (Settings.KEY_IMAGE_LOADING_STRATEGY_2.equals(key)) {
                getActivity().setResult(RESULT_OK);
                return true;
            } else if (Settings.KEY_FAST_SCROLLER.equals(key)) {
                Messenger.getInstance().notify(Constants.MESSENGER_ID_FAST_SCROLLER, newValue);
                return true;
            } else if (Settings.KEY_COLOR_STATUS_BAR.equals(key)) {
                Messenger.getInstance().notify(Constants.MESSENGER_ID_CHANGE_THEME, Settings.getDarkTheme());
            }
            return true;
        }

        private class TextFormatDialogHelper implements Slider.OnSetProgressListener,
                DialogInterface.OnClickListener {

            public View mView;
            public TextView mPreview;
            public Slider mFontSize;
            public Slider mLineSpacing;

            @SuppressLint("InflateParams")
            public TextFormatDialogHelper() {
                mView = getActivity().getLayoutInflater().inflate(R.layout.dialog_text_format, null);
                mPreview = (TextView) mView.findViewById(R.id.preview);
                mFontSize = (Slider) mView.findViewById(R.id.font_size);
                mLineSpacing = (Slider) mView.findViewById(R.id.line_spacing);

                int fontSize = Settings.getFontSize();
                int lineSpacing = Settings.getLineSpacing();

                mPreview.setTextSize(fontSize);
                mPreview.setLineSpacing(LayoutUtils.dp2pix(getActivity(), lineSpacing), 1.0f);

                mFontSize.setProgress(fontSize);
                mLineSpacing.setProgress(lineSpacing);
                mFontSize.setOnSetProgressListener(this);
                mLineSpacing.setOnSetProgressListener(this);
            }

            public View getView() {
                return mView;
            }

            @Override
            public void onSetProgress(Slider slider, int newProgress, int oldProgress, boolean byUser, boolean confirm) {
                if (slider == mFontSize && byUser) {
                    mPreview.setTextSize(newProgress);
                } else if (slider == mLineSpacing && byUser) {
                    mPreview.setLineSpacing(LayoutUtils.dp2pix(getActivity(), newProgress), 1.0f);
                }
            }

            @Override
            public void onClick(@NonNull DialogInterface dialog, int which) {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    Settings.putFontSize(mFontSize.getProgress());
                    Settings.putLineSpacing(mLineSpacing.getProgress());

                    getActivity().setResult(RESULT_OK);

                    updateTextFormatSummary();
                }
            }
        }

        @Override
        public boolean onPreferenceClick(Preference preference) {
            String key = preference.getKey();
            if (KEY_TEXT_FORMAT.equals(key)) {
                TextFormatDialogHelper helper = new TextFormatDialogHelper();
                new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.text_format)
                        .setView(helper.getView())
                        .setPositiveButton(android.R.string.ok, helper)
                        .show();
            }

            return true;
        }
    }

    public static class ConfigFragment extends PreferenceFragment
            implements Preference.OnPreferenceClickListener {

        public static final int REQUEST_CODE_PICK_IMAGE_DIR = 0;
        public static final int REQUEST_CODE_PICK_IMAGE_DIR_L = 1;

        private static final String KEY_AC_COOKIES = "ac_cookies";
        private static final String KEY_SAVE_COOKIES = "save_cookies";
        private static final String KEY_RESTORE_COOKIES = "restore_cookies";
        private static final String KEY_ABOUT_ANALYSIS = "about_analysis";

        private Preference mACCookies;
        private Preference mSaveCookies;
        private Preference mRestoreCookies;
        private Preference mFeedId;
        private Preference mImageSaveLocation;
        private Preference mAboutAnalysis;

        private TimingLife mTimingLife;

        private final long[] mHits = new long[8];

        @Override
        public void onDestroy() {
            super.onDestroy();
            stopTimingLife();
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.config_settings);

            mACCookies = findPreference(KEY_AC_COOKIES);
            mSaveCookies = findPreference(KEY_SAVE_COOKIES);
            mRestoreCookies = findPreference(KEY_RESTORE_COOKIES);
            mFeedId = findPreference(Settings.KEY_FEED_ID);
            mImageSaveLocation = findPreference(Settings.KEY_IMAGE_SAVE_LOACTION);
            mAboutAnalysis = findPreference(KEY_ABOUT_ANALYSIS);

            mACCookies.setOnPreferenceClickListener(this);
            mSaveCookies.setOnPreferenceClickListener(this);
            mRestoreCookies.setOnPreferenceClickListener(this);
            mFeedId.setOnPreferenceClickListener(this);
            mImageSaveLocation.setOnPreferenceClickListener(this);
            mAboutAnalysis.setOnPreferenceClickListener(this);

            long maxAge = ACSite.getInstance().getCookieMaxAge(getActivity());
            setACCookiesSummary(maxAge);
            updateFeedIdSummary();
            updateImageSaveLocation();
        }

        private void startTimingLife(long millisInFuture) {
            if (mTimingLife == null) {
                mTimingLife = new TimingLife(millisInFuture, 1000);
                mTimingLife.start();
            }
        }

        private void stopTimingLife() {
            if (mTimingLife != null) {
                mTimingLife.cancel();
                mTimingLife = null;
            }
        }

        private void setACCookiesSummary(long maxAge) {
            Resources resources = getActivity().getResources();
            if (maxAge == -1) {
                mACCookies.setSummary(resources.getString(R.string.main_ac_cookies_summary_forever));
                stopTimingLife();
            } else if (maxAge == -2) {
                mACCookies.setSummary(resources.getString(R.string.main_ac_cookies_summary_no));
                stopTimingLife();
            } else {
                long time = maxAge * 1000;
                mACCookies.setSummary(resources.getString(R.string.main_ac_cookies_summary_valid,
                        ReadableTime.getTimeInterval(time)));
                startTimingLife(time);
            }
        }

        private void openDirPicker() {
            UniFile uniFile = Settings.getImageSaveLocation();
            Intent intent = new Intent(getActivity(), DirPickerActivity.class);
            if (uniFile != null) {
                intent.putExtra(DirPickerActivity.KEY_FILE_URI, uniFile.getUri());
            }
            startActivityForResult(intent, REQUEST_CODE_PICK_IMAGE_DIR);
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        private void openDirPickerL() {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            try {
                startActivityForResult(intent, REQUEST_CODE_PICK_IMAGE_DIR_L);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(getActivity(), R.string.em_cant_find_activity, Toast.LENGTH_SHORT).show();
            }
        }

        private void showDirPickerDialogKK() {
            new AlertDialog.Builder(getActivity()).setMessage(R.string.pick_dir_kk)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            openDirPicker();
                        }
                    }).show();
        }

        private void showDirPickerDialogL() {
            DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    switch (which) {
                        case DialogInterface.BUTTON_POSITIVE:
                            openDirPicker();
                            break;
                        case DialogInterface.BUTTON_NEUTRAL:
                            openDirPickerL();
                            break;
                    }
                }
            };

            new AlertDialog.Builder(getActivity()).setMessage(R.string.pick_dir_l)
                    .setPositiveButton(android.R.string.ok, listener)
                    .setNeutralButton(R.string.document, listener)
                    .show();
        }

        private class SaveCookieTask extends AsyncTask<Void, Void, String> {

            @Override
            protected String doInBackground(Void... params) {
                File dir = NMBAppConfig.getCookiesDir();
                if (dir == null) {
                    return null;
                }

                SimpleCookieStore cookieStore = NMBApplication.getSimpleCookieStore(getActivity());
                List<TransportableHttpCookie> list = cookieStore.getTransportableCookies();

                boolean ok;
                File file = new File(dir, ReadableTime.getFilenamableTime(System.currentTimeMillis()));
                FileWriter fileWriter = null;
                try {
                    boolean first = true;
                    fileWriter = new FileWriter(file);
                    fileWriter.append('[');
                    for (TransportableHttpCookie thc : list) {
                        if (first) {
                            first = false;
                        } else {
                            fileWriter.append(',');
                        }
                        fileWriter.append(JSON.toJSONString(thc));
                    }
                    fileWriter.append(']');
                    fileWriter.flush();
                    ok = true;
                } catch (Exception e) {
                    ok = false;
                } finally {
                    IOUtils.closeQuietly(fileWriter);
                }

                if (!ok) {
                    file.delete();
                    return null;
                } else {
                    return file.getPath();
                }
            }

            @Override
            protected void onPostExecute(String path) {
                mSaveCookies.setEnabled(true);
                Context context = getActivity();
                Toast.makeText(getActivity(), path == null ? context.getString(R.string.save_cookies_failed) :
                        context.getString(R.string.save_cookies_to, path), Toast.LENGTH_SHORT).show();
            }
        }

        private class RestoreCookieDialogHelper implements DialogInterface.OnClickListener {

            private File[] mFiles;

            public RestoreCookieDialogHelper() {
                File dir = NMBAppConfig.getCookiesDir();
                if (dir == null) {
                    mFiles = new File[0];
                } else {
                    mFiles = dir.listFiles();
                    Arrays.sort(mFiles);
                }
            }

            private String[] getList() {
                int n = mFiles.length;
                String[] strings = new String[n];
                for (int i = 0; i < n; i++) {
                    strings[i] = mFiles[i].getName();
                }
                return strings;
            }

            @Override
            public void onClick(@NonNull DialogInterface dialog, int which) {
                File file = mFiles[which];
                InputStream is = null;
                try {
                    is = new FileInputStream(file);
                    String str = IOUtils.readString(is, "UTF-8");
                    List<TransportableHttpCookie> list = JSON.parseArray(str, TransportableHttpCookie.class);
                    SimpleCookieStore cookieStore = NMBApplication.getSimpleCookieStore(getActivity());
                    cookieStore.removeAll();
                    for (TransportableHttpCookie thc : list) {
                        URL url;
                        try {
                            url = new URL(thc.url);
                        } catch (MalformedURLException e) {
                            continue;
                        }
                        HttpCookie cookie = thc.to();
                        if (cookie == null) {
                            continue;
                        }
                        cookieStore.add(url, cookie);
                    }
                    NMBApplication.updateCookies(getActivity());
                    Toast.makeText(getActivity(), R.string.restore_cookies_successfully, Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(getActivity(), R.string.not_valid_cookie_file, Toast.LENGTH_SHORT).show();
                } finally {
                    IOUtils.closeQuietly(is);
                    // Restore timing
                    setACCookiesSummary(-2);
                    setACCookiesSummary(ACSite.getInstance().getCookieMaxAge(getActivity()));
                }
            }
        }

        private void updateFeedIdSummary() {
            mFeedId.setSummary(getActivity().getResources().getString(
                    R.string.main_feed_id_summary, Settings.getFeedId()));
        }

        private class FeedIdDialogHelper implements View.OnClickListener {

            public View mView;
            public EditText mEditText;

            public View mPositive;
            public View mNegative;
            public View mNeutral;

            public Dialog mDialog;

            @SuppressLint("InflateParams")
            public FeedIdDialogHelper() {
                mView = getActivity().getLayoutInflater().inflate(R.layout.dialog_feed_id, null);
                mEditText = (EditText) mView.findViewById(R.id.edit_text);
                mEditText.setText(Settings.getFeedId());
            }

            public View getView() {
                return mView;
            }

            public void setDialog(AlertDialog dialog) {
                mDialog = dialog;
                mPositive = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                mNegative = dialog.getButton(DialogInterface.BUTTON_NEGATIVE);
                mNeutral = dialog.getButton(DialogInterface.BUTTON_NEUTRAL);
                mPositive.setOnClickListener(this);
                mNegative.setOnClickListener(this);
                mNeutral.setOnClickListener(this);
            }

            @Override
            public void onClick(@NonNull View v) {
                if (mPositive == v) {
                    String feedId = mEditText.getText().toString();
                    if (!TextUtils.isEmpty(feedId)) {
                        Settings.putFeedId(feedId);
                        mDialog.dismiss();
                        updateFeedIdSummary();
                    } else {
                        Toast.makeText(getActivity(), R.string.invalid_feed_id, Toast.LENGTH_SHORT).show();
                    }
                } else if (mNegative == v) {
                    mEditText.setText(Settings.getRandomFeedId());
                } else if (mNeutral == v) { // mac
                    mEditText.setText(Settings.getMacFeedId());
                }
            }
        }

        public void updateImageSaveLocation() {
            UniFile uniFile = Settings.getImageSaveLocation();
            if (uniFile == null) {
                mImageSaveLocation.setSummary(R.string.main_image_save_locatio_summary_invalid);
            } else {
                mImageSaveLocation.setSummary(uniFile.getUri().toString());
            }
        }

        @Override
        public boolean onPreferenceClick(Preference preference) {
            String key = preference.getKey();
            if (KEY_AC_COOKIES.equals(key)) {
                System.arraycopy(mHits, 1, mHits, 0, mHits.length - 1);
                mHits[mHits.length - 1] = SystemClock.uptimeMillis();
                if (mHits[0] >= (SystemClock.uptimeMillis() - 3000)) {
                    Arrays.fill(mHits, 0);
                    // TODO
                }
                return true;
            } else if (KEY_SAVE_COOKIES.equals(key)) {
                mSaveCookies.setEnabled(false);
                new SaveCookieTask().execute();
                return true;
            } else if (KEY_RESTORE_COOKIES.equals(key)) {
                RestoreCookieDialogHelper helper = new RestoreCookieDialogHelper();
                String[] list = helper.getList();
                if (list.length == 0) {
                    Toast.makeText(getActivity(), R.string.cant_find_cookie_file, Toast.LENGTH_SHORT).show();
                } else {
                    new AlertDialog.Builder(getActivity())
                            .setTitle(R.string.select_cookie_file)
                            .setItems(list, helper)
                            .show();
                }
            } else if (Settings.KEY_FEED_ID.equals(key)) {
                FeedIdDialogHelper helper = new FeedIdDialogHelper();
                AlertDialog dialog = new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.main_feed_id)
                        .setView(helper.getView())
                        .setPositiveButton(android.R.string.ok, null)
                        .setNegativeButton(R.string.random, null)
                        .setNeutralButton(R.string.mac_addr, null)
                        .show();
                helper.setDialog(dialog);
            } else if (Settings.KEY_IMAGE_SAVE_LOACTION.equals(key)) {
                int sdk = Build.VERSION.SDK_INT;
                if (sdk < Build.VERSION_CODES.KITKAT) {
                    openDirPicker();
                } else if (sdk < Build.VERSION_CODES.LOLLIPOP) {
                    showDirPickerDialogKK();
                } else {
                    showDirPickerDialogL();
                }
            } else if (KEY_ABOUT_ANALYSIS.equals(key)) {
                try {
                    CharSequence message = Html.fromHtml(IOUtils.readString(
                            getResources().openRawResource(R.raw.about_analysis), "UTF-8"));
                    Dialog dialog = new AlertDialog.Builder(getActivity())
                            .setTitle(R.string.data_analysis)
                            .setMessage(message)
                            .show();
                    TextView messageView = (TextView) dialog.findViewById(android.R.id.message);
                    if (messageView != null) {
                        messageView.setMovementMethod(new LinkMovementMethod2(getActivity()));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            return true;
        }

        @Override
        @TargetApi(Build.VERSION_CODES.KITKAT)
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            switch (requestCode) {
                case REQUEST_CODE_PICK_IMAGE_DIR:
                    if (resultCode == RESULT_OK) {
                        UniFile uniFile = UniFile.fromUri(getActivity(), data.getData());
                        if (uniFile != null) {
                            Settings.putImageSaveLocation(uniFile);
                            updateImageSaveLocation();
                        } else {
                            Toast.makeText(getActivity(), R.string.cant_get_image_save_location, Toast.LENGTH_SHORT).show();
                        }
                    }
                    break;
                case REQUEST_CODE_PICK_IMAGE_DIR_L:
                    if (resultCode == RESULT_OK) {
                        Uri treeUri = data.getData();
                        getActivity().getContentResolver().takePersistableUriPermission(
                                treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                        UniFile uniFile = UniFile.fromTreeUri(getActivity(), treeUri);
                        if (uniFile != null) {
                            Settings.putImageSaveLocation(uniFile);
                            updateImageSaveLocation();
                        } else {
                            Toast.makeText(getActivity(), R.string.cant_get_image_save_location, Toast.LENGTH_SHORT).show();
                        }
                    }
                    break;
                default:
                    super.onActivityResult(requestCode, resultCode, data);
            }
        }

        private class TimingLife extends CountDownTimer {

            public TimingLife(long millisInFuture, long countDownInterval) {
                super(millisInFuture, countDownInterval);
            }

            @Override
            public void onTick(long millisUntilFinished) {
                setACCookiesSummary(millisUntilFinished / 1000);
            }

            @Override
            public void onFinish() {
            }
        }
    }

    public static class InfoFragment extends PreferenceFragment
            implements Preference.OnPreferenceClickListener {

        private static final String KEY_AUTHOR = "author";
        private static final String KEY_SOURCE = "source";
        private static final String KEY_NOTICE = "notice";
        private static final String KEY_VERSION = "version";
        private static final String KEY_DUMP_LOGCAT = "dump_logcat";
        private static final String KEY_HELP_NIMINGBAN = "help_nimingban";
        private static final String KEY_HELP = "help";

        private Preference mAuthor;
        private Preference mSource;
        private Preference mNotice;
        private Preference mVersion;
        private Preference mDumpLogcat;
        private Preference mHelpNimingban;
        private Preference mHelp;

        private boolean mShowTip = true;
        private final long[] mHits = new long[8];

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.info_settings);

            mAuthor = findPreference(KEY_AUTHOR);
            mSource = findPreference(KEY_SOURCE);
            mNotice = findPreference(KEY_NOTICE);
            mVersion = findPreference(KEY_VERSION);
            mDumpLogcat = findPreference(KEY_DUMP_LOGCAT);
            mHelpNimingban = findPreference(KEY_HELP_NIMINGBAN);
            mHelp = findPreference(KEY_HELP);

            mAuthor.setOnPreferenceClickListener(this);
            mSource.setOnPreferenceClickListener(this);
            mNotice.setOnPreferenceClickListener(this);
            mVersion.setOnPreferenceClickListener(this);
            mDumpLogcat.setOnPreferenceClickListener(this);
            mHelpNimingban.setOnPreferenceClickListener(this);
            mHelp.setOnPreferenceClickListener(this);

            mAuthor.setSummary("Hippo <hipposeven332$gmail.com>".replaceAll("\\$", "@"));

            try {
                PackageInfo packageInfo = getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0);
                mVersion.setSummary(packageInfo.versionName);
            } catch (PackageManager.NameNotFoundException e) {
                // Ignore
            }
        }

        @Override
        public boolean onPreferenceClick(Preference preference) {
            String key = preference.getKey();
            if (KEY_AUTHOR.equals(key)) {
                ActivityHelper.sendEmail(getActivity(),
                        "hipposeven332$gmail.com".replaceAll("\\$", "@"),
                        "About Nimingban",
                        null);
            } else if (KEY_SOURCE.equals(key)) {
                OpenUrlHelper.openUrl(getActivity(), "https://github.com/seven332/Nimingban", false);
            } else if (KEY_NOTICE.equals(key)) {
                try {
                    String str = IOUtils.readString(getActivity().getResources().getAssets().open("NOTICE"), "UTF-8");
                    View view = getActivity().getLayoutInflater().inflate(R.layout.dialog_notice, null);
                    TextView tv = (TextView) view.findViewById(R.id.text);
                    tv.setText(str);
                    new AlertDialog.Builder(getActivity()).setView(view).show();

                    if (!Settings.getConvert()) {
                        String country = Locale.getDefault().getCountry();
                        if ("CN".equals(country) || "TW".equals(country)) {
                            Settings.putConvert(true);
                            Toast.makeText(getActivity(), R.string.new_feature_available, Toast.LENGTH_SHORT).show();
                        }
                    }
                } catch (IOException e) {
                    Toast.makeText(getActivity(), R.string.cant_open_notice, Toast.LENGTH_SHORT).show();
                }
            } else if (KEY_VERSION.equals(key)) {
                System.arraycopy(mHits, 1, mHits, 0, mHits.length - 1);
                mHits[mHits.length - 1] = SystemClock.uptimeMillis();
                if (mHits[0] >= (SystemClock.uptimeMillis() - 3000)) {
                    Arrays.fill(mHits, 0);

                    if (mShowTip) {
                        mShowTip = false;
                        Toast.makeText(getActivity(), R.string.da_diao_tip, Toast.LENGTH_SHORT).show();
                    } else {
                        Intent intent = new Intent(getActivity(), DaDiaoService.class);
                        intent.setAction(DaDiaoService.ACTION_DA_DIAO);
                        getActivity().startService(intent);
                    }
                }
            } else if (KEY_DUMP_LOGCAT.equals(key)) {
                boolean ok;
                File file = null;
                File dir = NMBAppConfig.getLogcatDir();
                if (dir != null) {
                    file = new File(dir, "logcat-" + ReadableTime.getFilenamableTime(System.currentTimeMillis()) + ".txt");
                    ok = LogCat.save(file);
                } else {
                    ok = false;
                }
                Resources resources = getResources();
                Toast.makeText(getActivity(),
                        ok ? resources.getString(R.string.dump_logcat_to, file.getPath()) :
                                resources.getString(R.string.dump_logcat_failed), Toast.LENGTH_SHORT).show();
            } else if (KEY_HELP_NIMINGBAN.equals(key)) {
                new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.help_nimingban_title)
                        .setMessage(R.string.help_nimingban_message)
                        .show();
            } else if (KEY_HELP.equals(key)) {
                OpenUrlHelper.openUrl(getActivity(), "http://nimingban.herokuapp.com/help.html", false);
            }
            return true;
        }
    }
}
