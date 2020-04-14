package com.mycelium.wapi.wallet.eth.coins

import com.mycelium.wapi.wallet.Address
import com.mycelium.wapi.wallet.coins.CryptoCurrency
import com.mycelium.wapi.wallet.coins.families.Families
import com.mycelium.wapi.wallet.eth.EthAddress
import org.web3j.abi.datatypes.Address as W3jAddress
import org.web3j.crypto.WalletUtils

abstract class EthCoin : CryptoCurrency() {
    init {
        family = Families.ETHEREUM
        unitExponent = 18
        addressPrefix = ""
    }

    override fun parseAddress(addressString: String): Address? {
        return if (WalletUtils.isValidAddress(addressString)) {
            // additional wrap of addressString into Address is called upon
            // to unify addresses with and without '0x' prefix
            EthAddress(this, W3jAddress(addressString).toString())
        } else {
            null
        }
    }

    companion object {
        @JvmStatic val BLOCK_TIME_IN_SECONDS = 15
    }
}