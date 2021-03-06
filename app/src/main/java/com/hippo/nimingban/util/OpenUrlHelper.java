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

package com.hippo.nimingban.util;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.support.customtabs.CustomTabsIntent;
import android.text.TextUtils;

import com.hippo.nimingban.R;
import com.hippo.nimingban.client.ac.NMBUriParser;
import com.hippo.nimingban.ui.PostActivity;
import com.hippo.nimingban.ui.WebViewActivity;
import com.hippo.yorozuya.ResourcesUtils;

public final class OpenUrlHelper {

    private OpenUrlHelper() {
    }

    public static void openUrl(Activity activity, String url, boolean checkNMB) {
        if (TextUtils.isEmpty(url)) {
            return;
        }

        Intent intent;
        Uri uri = Uri.parse(url);

        // Is nmb url
        if (checkNMB) {
            NMBUriParser.PostResult result = NMBUriParser.parsePostUri(uri);
            if (result.site != null && result.id != null) {
                intent = new Intent(activity, PostActivity.class);
                intent.setAction(PostActivity.ACTION_SITE_ID);
                intent.putExtra(PostActivity.KEY_SITE, result.site.getId());
                intent.putExtra(PostActivity.KEY_ID, result.id);
                activity.startActivity(intent);
                return;
            }
        }

        // CustomTabs
        String packageName = CustomTabsHelper.getPackageNameToUse(activity);
        if (packageName != null) {
            new CustomTabsIntent.Builder()
                    .setToolbarColor(ResourcesUtils.getAttrColor(activity, R.attr.colorPrimary))
                    .setShowTitle(true)
                    .build()
                    .launchUrl(activity, uri);
            return;
        }

        // Intent.ACTION_VIEW
        intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));
        PackageManager pm = activity.getPackageManager();
        ResolveInfo ri = pm.resolveActivity(intent, 0);
        if (ri != null) {
            activity.startActivity(intent);
            return;
        }

        // Use WebViewActivity
        intent = new Intent(activity, WebViewActivity.class);
        intent.putExtra(WebViewActivity.KEY_URL, url);
        activity.startActivity(intent);
    }
}
