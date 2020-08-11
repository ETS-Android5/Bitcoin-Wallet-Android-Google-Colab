/*
 * Copyright 2013, 2014 Megion Research and Development GmbH
 *
 * Licensed under the Microsoft Reference Source License (MS-RSL)
 *
 * This license governs use of the accompanying software. If you use the software, you accept this license.
 * If you do not accept the license, do not use the software.
 *
 * 1. Definitions
 * The terms "reproduce," "reproduction," and "distribution" have the same meaning here as under U.S. copyright law.
 * "You" means the licensee of the software.
 * "Your company" means the company you worked for when you downloaded the software.
 * "Reference use" means use of the software within your company as a reference, in read only form, for the sole purposes
 * of debugging your products, maintaining your products, or enhancing the interoperability of your products with the
 * software, and specifically excludes the right to distribute the software outside of your company.
 * "Licensed patents" means any Licensor patent claims which read directly on the software as distributed by the Licensor
 * under this license.
 *
 * 2. Grant of Rights
 * (A) Copyright Grant- Subject to the terms of this license, the Licensor grants you a non-transferable, non-exclusive,
 * worldwide, royalty-free copyright license to reproduce the software for reference use.
 * (B) Patent Grant- Subject to the terms of this license, the Licensor grants you a non-transferable, non-exclusive,
 * worldwide, royalty-free patent license under licensed patents for reference use.
 *
 * 3. Limitations
 * (A) No Trademark License- This license does not grant you any rights to use the Licensor’s name, logo, or trademarks.
 * (B) If you begin patent litigation against the Licensor over patents that you think may apply to the software
 * (including a cross-claim or counterclaim in a lawsuit), your license to the software ends automatically.
 * (C) The software is licensed "as-is." You bear the risk of using it. The Licensor gives no express warranties,
 * guarantees or conditions. You may have additional consumer rights under your local laws which this license cannot
 * change. To the extent permitted under your local laws, the Licensor excludes the implied warranties of merchantability,
 * fitness for a particular purpose and non-infringement.
 */

package com.mycelium.wallet.activity.modern;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.mycelium.wallet.R;
import com.mycelium.wallet.Utils;

/**
 * Helper class that makes it easy to let a new toast replace another if they
 * come in rapid succession
 */
public class Toaster {
    private static Toast currentToast;
    private final Context context;
    private Activity activity;
    private Fragment fragment;

    public Toaster(Activity activity) {
        this.activity = activity;
        context = activity.getApplicationContext();
    }

    public Toaster(Fragment fragment) {
        this.fragment = fragment;
        context = fragment.getContext();
    }

    public Toaster(Context context) {
        this.context = context;
    }

    public void toast(int resourceId, boolean shortDuration) {
        // Resolve the message from the resource id
        String message;
        try {
            message = context.getResources().getString(resourceId);
        } catch (Resources.NotFoundException e) {
            return;
        }
        toast(message, shortDuration);
    }

    public void toast(String message, boolean shortDuration) {
        cancelCurrentToast();
        if (fragment != null && !fragment.isAdded()) {            
            return;            
        }
        Context currentContext = fragment != null ? fragment.getActivity() : activity;
        if (currentContext == null) {
            currentContext = context;
        }
        currentToast = Toast.makeText(currentContext, message, Toast.LENGTH_SHORT);
        currentToast.setDuration(shortDuration ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG);
        currentToast.show();
    }

    public void toastConnectionError() {
        toastConnectionError("");
    }

    public void toastConnectionError(String additionInfo) {
        if (Utils.isConnected(context)) {
            toast(context.getString(R.string.no_server_connection, additionInfo), false);
        } else {
            toast(R.string.no_network_connection, true);
        }
    }

    public static void onStop() {
        cancelCurrentToast();
    }

    private static void cancelCurrentToast() {
        if(currentToast != null) {
            currentToast.cancel();
            currentToast = null;
        }
    }
}
