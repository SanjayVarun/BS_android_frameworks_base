/*
 * Copyright (C) 2015 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.qs.tiles;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.android.systemui.R;
import com.android.systemui.qs.QSDetailItemsList;
import com.android.systemui.qs.QSTile;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.MetricsProto.MetricsEvent;
import org.cyanogenmod.internal.util.QSUtils;

import cyanogenmod.power.PerformanceManager;
import cyanogenmod.providers.CMSettings;

public class PerfProfileTile extends QSTile<PerfProfileTile.ProfileState> {

    private static final Intent BATTERY_SETTINGS = new Intent(Intent.ACTION_POWER_USAGE_SUMMARY);

    private String[] mEntries;
    private String[] mDescriptionEntries;
    private String[] mAnnouncementEntries;
    private int[] mPerfProfileValues;
    private int mNumPerfProfiles;
    private Icon mIcon;

    private PowerManager mPm;
    private PerformanceManager mPerformanceManager;
    private boolean mListening;

    private PerformanceProfileObserver mObserver;

    public PerfProfileTile(Host host) {
        super(host);
        populateList();
    }

    private void populateList() {
        mObserver = new PerformanceProfileObserver(mHandler);
        mPm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        mPerformanceManager = PerformanceManager.getInstance(mContext);
        mNumPerfProfiles = mPerformanceManager.getNumberOfProfiles();
        if (!(mNumPerfProfiles>0)) {
            return;
        }
        mPerfProfileValues = new int[mNumPerfProfiles];
        mEntries = new String[mNumPerfProfiles];
        mDescriptionEntries = new String[mNumPerfProfiles];
        mAnnouncementEntries = new String[mNumPerfProfiles];

        mIcon = ResourceIcon.get(R.drawable.ic_qs_perf_profile);

        // Filter out unsupported profiles
        Resources res = mContext.getResources();
        final int[] perfProfileValues = res.getIntArray(
                org.cyanogenmod.platform.internal.R.array.perf_profile_values);
        final String[] entries = res.getStringArray(
                org.cyanogenmod.platform.internal.R.array.perf_profile_entries);
        final String[] descriptionEntries = res.getStringArray(
                R.array.perf_profile_description);
        final String[] announcementEntries = res.getStringArray(
                R.array.perf_profile_announcement);
        int i = 0;

        for (int j = 0; j < perfProfileValues.length; j++) {
            if (perfProfileValues[j] < mNumPerfProfiles) {
                mPerfProfileValues[i] = perfProfileValues[j];
                mEntries[i] = entries[j];
                mDescriptionEntries[i] = descriptionEntries[j];
                mAnnouncementEntries[i] = announcementEntries[j];
                i++;
            }
        }
    }

    @Override
    public ProfileState newTileState() {
        return new ProfileState();
    }

    @Override
    protected void handleClick() {
        showDetail(true);
    }

    @Override
    public DetailAdapter getDetailAdapter() {
        return new PerfProfileDetailAdapter();
    }

    @Override
    protected void handleLongClick() {
        mHost.startActivityDismissingKeyguard(BATTERY_SETTINGS);
    }

    @Override
    public Intent getLongClickIntent() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.qs_tile_performance);
    }

    @Override
    protected void handleUpdateState(ProfileState state, Object arg) {
        state.profile = arg == null ? getCurrentProfileIndex() : (Integer) arg;
        state.label = mEntries[state.profile];
        state.icon = mIcon;
        state.contentDescription = mDescriptionEntries[state.profile];
    }

    private boolean isPerfProfilesSupported(){
        boolean isSupported = false;
        isSupported = (mPerformanceManager.getNumberOfProfiles() > 0);
        return isSupported;
    }

    @Override
    public boolean isAvailable(){
        return isPerfProfilesSupported();
    }

   @Override
    public int getMetricsCategory() {
        return MetricsEvent.QUICK_SETTINGS;
    }

    @Override
    protected String composeChangeAnnouncement() {
        return mAnnouncementEntries[getCurrentProfileIndex()];
    }

    @Override
    public void setListening(boolean listening) {
        if (mListening == listening) return;
        mListening = listening;
        if (listening) {
            mObserver.startObserving();
        } else {
            mObserver.endObserving();
        }
    }

    private class PerformanceProfileObserver extends ContentObserver {
        public PerformanceProfileObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            refreshState(getCurrentProfileIndex());
        }

        public void startObserving() {
            mContext.getContentResolver().registerContentObserver(
                    CMSettings.Secure.getUriFor(CMSettings.Secure.PERFORMANCE_PROFILE),
                    false, this);
        }

        public void endObserving() {
            mContext.getContentResolver().unregisterContentObserver(this);
        }
    }

    private int getCurrentProfileIndex() {
        int index = 0;
        int perfProfile = mPerformanceManager.getPowerProfile();

        int count = mPerfProfileValues.length;
        for (int i = 0; i < count; i++) {
            if (mPerfProfileValues[i] == perfProfile) {
                index = i;
                break;
            }
        }
        return index;
    }

    private void changeToProfile(int profileIndex) {
        mPerformanceManager.setPowerProfile(mPerfProfileValues[profileIndex]); // content observer will notify
    }

    public static class ProfileState extends QSTile.State {
        public int profile;

        @Override
        public boolean copyTo(State other) {
            final ProfileState o = (ProfileState) other;
            final boolean changed = profile != o.profile;
            return super.copyTo(other) || changed;
        }

        @Override
        protected StringBuilder toStringBuilder() {
            final StringBuilder rt = super.toStringBuilder();
            rt.insert(rt.length() - 1, ",profile=" + profile);
            return rt;
        }
    }

    private class PerfProfileDetailAdapter implements DetailAdapter,
            AdapterView.OnItemClickListener {
        private QSDetailItemsList mItems;

        @Override
        public CharSequence getTitle() {
            return mContext.getString(R.string.quick_settings_performance_profile_detail_title);
        }

        @Override
        public Boolean getToggleState() {
            return null;
        }

        @Override
        public Intent getSettingsIntent() {
            return BATTERY_SETTINGS;
        }

        @Override
        public void setToggleState(boolean state) {
            // noop
        }

        @Override
        public int getMetricsCategory() {
            return MetricsEvent.QUICK_SETTINGS;
        }

        @Override
        public View createDetailView(Context context, View convertView, ViewGroup parent) {
            mItems = QSDetailItemsList.convertOrInflate(context, convertView, parent);
            ArrayAdapter adapter = new ArrayAdapter<String>(mContext,
                    android.R.layout.simple_list_item_single_choice, mEntries);
            ListView listView = mItems.getListView();
            listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
            listView.setAdapter(adapter);
            listView.setOnItemClickListener(this);
            listView.setDivider(null);
            listView.setItemChecked(getCurrentProfileIndex(), true);
            return mItems;
        }

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            changeToProfile(position);
        }
    }
}
