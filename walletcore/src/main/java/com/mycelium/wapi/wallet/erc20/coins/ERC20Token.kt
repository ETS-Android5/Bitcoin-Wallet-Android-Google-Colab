package com.mycelium.wapi.wallet.erc20.coins

import com.mycelium.wapi.wallet.GenericAddress
import com.mycelium.wapi.wallet.coins.CryptoCurrency
import com.mycelium.wapi.wallet.eth.EthAddress
import org.web3j.abi.datatypes.Address
import org.web3j.crypto.WalletUtils

class ERC20Token(name: String = "", symbol: String = "", unitExponent: Int = 18, val contractAddress: String) : CryptoCurrency(name, name, symbol, unitExponent, 2, false) {

    override fun parseAddress(addressString: String): GenericAddress? =
            if (WalletUtils.isValidAddress(addressString)) {
                // additional wrap of addressString into Address is called upon
                // to unify addresses with and without '0x' prefix
                EthAddress(this, Address(addressString).toString())
            } else {
                null
            }
}