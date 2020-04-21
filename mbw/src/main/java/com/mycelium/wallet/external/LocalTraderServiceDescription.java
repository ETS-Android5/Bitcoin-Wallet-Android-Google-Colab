package com.mycelium.wallet.external;

import android.app.Activity;
import android.widget.Toast;

import com.mycelium.wallet.MbwManager;
import com.mycelium.wallet.R;
import com.mycelium.wallet.Utils;
import com.mycelium.wallet.lt.LocalTraderManager;
import com.mycelium.wallet.lt.activity.LtMainActivity;
import com.mycelium.wapi.wallet.Address;
import com.mycelium.wapi.wallet.eth.coins.EthMain;
import com.mycelium.wapi.wallet.eth.coins.EthTest;

public class LocalTraderServiceDescription extends BuySellServiceDescriptor {

   public LocalTraderServiceDescription() {
      super(R.string.lt_mycelium_marketplace, R.string.lt_mycelium_marketplace_description, 0, R.drawable.mycelium_logo_transp);
   }

   @Override
   public void launchService(Activity activity, MbwManager mbwManager, Address activeReceivingAddress) {
      if (!mbwManager.getSelectedAccount().canSpend()) {
         Toast.makeText(activity, R.string.lt_warning_watch_only_account, Toast.LENGTH_LONG).show();
         return;
      }
      if (!Utils.isAllowedForLocalTrader(mbwManager.getSelectedAccount())) {
         Toast.makeText(activity, R.string.lt_warning_wrong_account_type, Toast.LENGTH_LONG).show();
         return;
      }

      LocalTraderManager ltManager = mbwManager.getLocalTraderManager();
      boolean newActivity = ltManager.hasLocalTraderAccount() && ltManager.needsTraderSynchronization();
      LtMainActivity.callMe(activity, newActivity ? LtMainActivity.TAB_TYPE.ACTIVE_TRADES
              : LtMainActivity.TAB_TYPE.DEFAULT);
   }

   @Override
   public boolean showEnableInSettings() {
      // The LT settings are handled in their own category
      return false;
   }

   @Override
   public boolean isEnabled(MbwManager mbwManager) {
      return mbwManager.getLocalTraderManager().isLocalTraderEnabled()
              && mbwManager.getSelectedAccount().getCoinType() != EthMain.INSTANCE
              && mbwManager.getSelectedAccount().getCoinType() != EthTest.INSTANCE;
   }

   @Override
   public void setEnabled(MbwManager mbwManager, boolean enabledState) {
      mbwManager.getLocalTraderManager().setLocalTraderEnabled(enabledState);
   }
}
