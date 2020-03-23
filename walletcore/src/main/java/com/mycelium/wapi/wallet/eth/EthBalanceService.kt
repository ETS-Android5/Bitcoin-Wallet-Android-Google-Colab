package com.mycelium.wapi.wallet.eth

import com.mycelium.wapi.wallet.coins.Balance
import com.mycelium.wapi.wallet.coins.CryptoCurrency
import com.mycelium.wapi.wallet.coins.Value
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName

class EthBalanceService(private val address: String,
                        private val coinType: CryptoCurrency,
                        var client: Web3j) {
    var balance: Balance = Balance.getZeroBalance(coinType)
        private set

    fun updateBalanceCache() {
        return try {
            val balanceRequest = client.ethGetBalance(address, DefaultBlockParameterName.LATEST)
            val balanceResult = balanceRequest.send()

            balance = Balance(Value.valueOf(coinType, balanceResult.balance),
                    Value.zeroValue(coinType), Value.zeroValue(coinType), Value.zeroValue(coinType))
        } catch (e: Exception) {
        }
    }
}