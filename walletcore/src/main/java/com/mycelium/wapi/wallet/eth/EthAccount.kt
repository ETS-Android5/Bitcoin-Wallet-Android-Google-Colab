package com.mycelium.wapi.wallet.eth

import com.mrd.bitlib.crypto.InMemoryPrivateKey
import com.mrd.bitlib.util.BitUtils
import com.mrd.bitlib.util.HexUtils
import com.mycelium.net.HttpsEndpoint
import com.mycelium.wapi.wallet.*
import com.mycelium.wapi.wallet.btc.FeePerKbFee
import com.mycelium.wapi.wallet.coins.Balance
import com.mycelium.wapi.wallet.coins.Value
import com.mycelium.wapi.wallet.coins.Value.Companion.max
import com.mycelium.wapi.wallet.coins.Value.Companion.valueOf
import com.mycelium.wapi.wallet.exceptions.BuildTransactionException
import com.mycelium.wapi.wallet.exceptions.InsufficientFundsException
import com.mycelium.wapi.wallet.exceptions.TransactionBroadcastException
import com.mycelium.wapi.wallet.genericdb.EthAccountBacking
import org.web3j.crypto.*

import org.web3j.tx.Transfer
import org.web3j.utils.Convert
import org.web3j.utils.Numeric
import java.io.IOException
import java.math.BigInteger
import java.util.*

class EthAccount(private val accountContext: EthAccountContext,
                 credentials: Credentials? = null,
                 backing: EthAccountBacking,
                 private val accountListener: AccountListener?,
                 web3jWrapper: Web3jWrapper,
                 private val transactionServiceEndpoints: List<HttpsEndpoint>,
                 address: EthAddress? = null) : AbstractEthERC20Account(accountContext.currency, credentials,
        backing, EthAccount::class.simpleName, web3jWrapper, address) {
    private var removed = false
    var enabledTokens: MutableList<String> = accountContext.enabledTokens?.toMutableList()
            ?: mutableListOf()

    val accountIndex: Int
        get() = accountContext.accountIndex

    fun removeEnabledToken(tokenName: String) {
        enabledTokens.remove(tokenName)
        accountContext.enabledTokens = enabledTokens
    }

    fun addEnabledToken(tokenName: String) {
        enabledTokens.add(tokenName)
        accountContext.enabledTokens = enabledTokens
    }

    fun isEnabledToken(tokenName: String) = enabledTokens.contains(tokenName)

    fun hasHadActivity(): Boolean =
            accountBalance.spendable.isPositive() || accountContext.nonce > BigInteger.ZERO

    @Throws(InsufficientFundsException::class, BuildTransactionException::class)
    override fun createTx(toAddress: Address, value: Value, gasPrice: Fee, data: TransactionData?): Transaction {
        val gasPriceValue = (gasPrice as FeePerKbFee).feePerKb
        val ethTxData = (data as? EthTransactionData)
        val nonce = ethTxData?.nonce ?: getNewNonce(receivingAddress)
        val gasLimit = ethTxData?.gasLimit ?: BigInteger.valueOf(typicalEstimatedTransactionSize.toLong())
        val inputData = ethTxData?.inputData ?: ""
        val fee = if (ethTxData?.suggestedGasPrice != null) valueOf(coinType, ethTxData.suggestedGasPrice!!) else gasPrice.feePerKb

        if (gasPriceValue.value <= BigInteger.ZERO) {
            throw BuildTransactionException(Throwable("Gas price should be positive and non-zero"))
        }
        if (value.value < BigInteger.ZERO) {
            throw BuildTransactionException(Throwable("Value should be positive"))
        }
        if (gasLimit < typicalEstimatedTransactionSize.toBigInteger()) {
            throw BuildTransactionException(Throwable("Gas limit must be at least 21000"))
        }
        if (value > calculateMaxSpendableAmount(gasPriceValue, null)) {
            throw InsufficientFundsException(Throwable("Insufficient funds to send " + Convert.fromWei(value.value.toBigDecimal(), Convert.Unit.ETHER) +
                    " ether with gas price " + Convert.fromWei(gasPriceValue.valueAsBigDecimal, Convert.Unit.GWEI) + " gwei"))
        }

        try {
            val rawTransaction = RawTransaction.createTransaction(nonce, fee.value, gasLimit, toAddress.toString(), value.value, inputData)
            return EthTransaction(coinType, toAddress, value, FeePerKbFee(fee), rawTransaction)
        } catch (e: Exception) {
            throw BuildTransactionException(Throwable(e.localizedMessage))
        }
    }

    override fun signTx(request: Transaction?, keyCipher: KeyCipher?) {
        val rawTransaction = (request as EthTransaction).rawTransaction
        val signedMessage = TransactionEncoder.signMessage(rawTransaction, credentials)
        val hexValue = Numeric.toHexString(signedMessage)
        request.signedHex = hexValue
        request.txHash = TransactionUtils.generateTransactionHash(rawTransaction, credentials)
    }

    override fun broadcastTx(tx: Transaction): BroadcastResult {
        try {
            val ethSendTransaction = web3jWrapper.ethSendTransaction((tx as EthTransaction).rawTransaction, credentials!!)
            if (ethSendTransaction.hasError()) {
                return BroadcastResult(ethSendTransaction.error.message, BroadcastResultType.REJECTED)
            }
            backing.putTransaction(-1, System.currentTimeMillis() / 1000, "0x" + HexUtils.toHex(tx.txHash),
                    tx.signedHex!!, receivingAddress.addressString, tx.toAddress.toString(), tx.value,
                    (tx.gasPrice as FeePerKbFee).feePerKb * tx.rawTransaction.gasLimit, 0, tx.rawTransaction.nonce)
        } catch (e: IOException) {
            throw TransactionBroadcastException(e.localizedMessage)
        }
        return BroadcastResult(BroadcastResultType.SUCCESS)
    }

    override fun getCoinType() = accountContext.currency

    override fun getBasedOnCoinType() = coinType

    private val ethBalanceService = EthBalanceService(receivingAddress.toString(), coinType, web3jWrapper)

    override fun getAccountBalance() = accountContext.balance

    override fun setLabel(label: String?) {
        accountContext.accountName = label!!
    }

    override fun getNonce() = accountContext.nonce

    override fun setNonce(nonce: BigInteger) {
        accountContext.nonce = nonce
    }

    @Synchronized
    override fun doSynchronization(mode: SyncMode?): Boolean {
        if (removed || isArchived) {
            return false
        }
        syncTransactions()
        return updateBalanceCache()
    }

    override fun updateBalanceCache(): Boolean {
        ethBalanceService.updateBalanceCache()
        var newBalance = ethBalanceService.balance

        val pendingReceiving = getPendingReceiving()
        val pendingSending = getPendingSending()
        newBalance = Balance(valueOf(coinType, newBalance.confirmed.value - pendingSending),
                valueOf(coinType, pendingReceiving), valueOf(coinType, pendingSending), Value.zeroValue(coinType))
        if (newBalance != accountContext.balance) {
            accountContext.balance = newBalance
            accountListener?.balanceUpdated(this)
            return true
        }
        return false
    }

    private fun getPendingReceiving(): BigInteger {
        return backing.getUnconfirmedTransactions(receivingAddress.addressString).filter {
            !it.sender.addressString.equals(receiveAddress.addressString, true)
                    && it.receiver.addressString.equals(receiveAddress.addressString, true)
        }
                .map { it.value.value }
                .fold(BigInteger.ZERO, BigInteger::add)
    }

    private fun getPendingSending(): BigInteger {
        return backing.getUnconfirmedTransactions(receivingAddress.addressString).filter {
            it.sender.addressString.equals(receiveAddress.addressString, true)
                    && !it.receiver.addressString.equals(receiveAddress.addressString, true)
        }
                .map { tx -> tx.value.value + tx.fee!!.value }
                .fold(BigInteger.ZERO, BigInteger::add) +

                backing.getUnconfirmedTransactions(receivingAddress.addressString).filter {
                    it.sender.addressString.equals(receiveAddress.addressString, true)
                            && it.receiver.addressString.equals(receiveAddress.addressString, true)
                }
                        .map { tx -> tx.fee!!.value }
                        .fold(BigInteger.ZERO, BigInteger::add)
    }

    private fun syncTransactions() {
        val remoteTransactions = EthTransactionService(receiveAddress.addressString, transactionServiceEndpoints).getTransactions()
        remoteTransactions.forEach { tx ->
            backing.putTransaction(tx.blockHeight.toInt(), tx.blockTime, tx.txid, "", tx.from, tx.to,
                    valueOf(coinType, tx.value), valueOf(coinType, tx.gasPrice * (tx.gasUsed ?: typicalEstimatedTransactionSize.toBigInteger())),
                    tx.confirmations.toInt(), tx.nonce, tx.gasLimit, tx.gasUsed)
        }
    }

    override fun archiveAccount() {
        accountContext.archived = true
        dropCachedData()
    }

    override fun activateAccount() {
        accountContext.archived = false
        dropCachedData()
    }

    override fun dropCachedData() {
        clearBacking()
        accountContext.balance = Balance.getZeroBalance(coinType)
    }

    override fun isVisible() = true

    override fun isDerivedFromInternalMasterseed() = true

    override fun getId(): UUID = credentials?.ecKeyPair?.toUUID()
            ?: UUID.nameUUIDFromBytes(receivingAddress.getBytes())

    override fun broadcastOutgoingTransactions() = true

    override fun calculateMaxSpendableAmount(gasPrice: Value, ign: EthAddress?): Value {
        val spendable = accountBalance.spendable - gasPrice * typicalEstimatedTransactionSize.toLong()
        return max(spendable, Value.zeroValue(coinType))
    }

    override fun getLabel() = accountContext.accountName

    override fun getBlockChainHeight() = accountContext.blockHeight

    override fun setBlockChainHeight(height: Int) {
        accountContext.blockHeight = height
    }

    override fun isArchived() = accountContext.archived

    override fun getSyncTotalRetrievedTransactions() = 0 // TODO implement after full transaction history implementation

    override fun getTypicalEstimatedTransactionSize() = Transfer.GAS_LIMIT.toInt()

    override fun getPrivateKey(cipher: KeyCipher?): InMemoryPrivateKey {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    fun fetchTxNonce(txid: String): BigInteger? {
        return try {
            val tx = web3jWrapper.ethGetTransactionByHash(txid).send()
            if (tx.result == null) {
                null
            } else {
                val nonce = tx.result.nonce
                backing.updateNonce(txid, nonce)
                nonce
            }
        } catch (e: Exception) {
            null
        }
    }
}


fun ECKeyPair.toUUID(): UUID = UUID(
        BitUtils.uint64ToLong(publicKey.toByteArray(), 8),
        BitUtils.uint64ToLong(publicKey.toByteArray(), 16))