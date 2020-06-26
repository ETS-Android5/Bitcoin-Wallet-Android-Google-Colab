package com.mycelium.wallet.activity.send.model

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.text.Html
import android.widget.Toast.LENGTH_LONG
import android.widget.Toast.makeText
import androidx.lifecycle.MutableLiveData
import com.mrd.bitlib.FeeEstimatorBuilder
import com.mrd.bitlib.model.Address
import com.mycelium.wallet.Constants.BTC_BLOCK_TIME_IN_SECONDS
import com.mycelium.wallet.MinerFee
import com.mycelium.wallet.R
import com.mycelium.wallet.Utils
import com.mycelium.wallet.activity.send.view.SelectableRecyclerView
import com.mycelium.wallet.activity.util.toStringWithUnit
import com.mycelium.wapi.wallet.BitcoinBasedGenericTransaction
import com.mycelium.wapi.wallet.GenericAddress
import com.mycelium.wapi.wallet.WalletAccount
import com.mycelium.wapi.wallet.btc.AbstractBtcAccount
import com.mycelium.wapi.wallet.btc.BtcTransaction
import com.mycelium.wapi.wallet.btc.FeePerKbFee
import com.mycelium.wapi.wallet.coins.Value
import com.mycelium.wapi.wallet.colu.coins.ColuMain
import com.mycelium.wapi.wallet.exceptions.GenericBuildTransactionException
import com.mycelium.wapi.wallet.exceptions.GenericInsufficientFundsException
import com.mycelium.wapi.wallet.exceptions.GenericOutputTooSmallException
import io.reactivex.BackpressureStrategy
import io.reactivex.Completable
import io.reactivex.schedulers.Schedulers

class SendBtcModel(context: Application,
                   account: WalletAccount<*>,
                   intent: Intent)
    : SendCoinsModel(context, account, intent) {
    val receivingAddressesList: MutableLiveData<List<GenericAddress>> = MutableLiveData()
    val feeDescription: MutableLiveData<String> = MutableLiveData()
    val isFeeExtended: MutableLiveData<Boolean> = MutableLiveData()

    init {
        receivingAddressesList.value = emptyList()
        feeDescription.value = ""
        isFeeExtended.value = false

        listToDispose.add(feeUpdatePublisher.toFlowable(BackpressureStrategy.LATEST)
                .observeOn(Schedulers.computation())
                .switchMapCompletable {
                    if (transaction?.type !is ColuMain) {
                        (transaction as? BitcoinBasedGenericTransaction)?.unsignedTx?.apply {
                            val inCount = fundingOutputs.size
                            val outCount = outputs.size

                            val feeEstimator = FeeEstimatorBuilder().setArrayOfInputs(fundingOutputs)
                                    .setArrayOfOutputs(outputs)
                                    .createFeeEstimator()
                            val size = feeEstimator.estimateTransactionSize()

                            feeDescription.postValue("$inCount In- / $outCount Outputs, ~$size bytes")

                            val fee = calculateFee()
                            if (fee != size * selectedFee.value!!.valueAsLong / 1000) {
                                val value = Value.valueOf(account.coinType, fee)
                                val fiatValue = mbwManager.exchangeRateManager.get(value, mbwManager.getFiatCurrency(account.coinType))
                                val fiat = if (fiatValue != null) {
                                    " (${fiatValue.toStringWithUnit(mbwManager.getDenomination(account.coinType))})"
                                } else {
                                    ""
                                }
                                feeWarning.postValue(Html.fromHtml(context.getString(R.string.fee_change_warning,
                                        value.toStringWithUnit(mbwManager.getDenomination(account.coinType)), fiat)))
                                isFeeExtended.postValue(true)
                            } else {
                                feeWarning.postValue("")
                                isFeeExtended.postValue(false)
                            }
                        }
                    }
                    Completable.complete()
                }
                .subscribe())
    }

    fun hasPaymentRequestHandler() = paymentRequestHandler.value != null

    fun hasPaymentCallbackUrl() = paymentRequestHandler.value?.paymentRequestInformation?.hasPaymentCallbackUrl()
            ?: false

    fun paymentRequestExpired() = paymentRequestHandler.value?.paymentRequestInformation?.isExpired
            ?: true

    fun sendResponseToPR(): Boolean {
        val address = Address(mbwManager.selectedAccount.receiveAddress.getBytes())
        val transaction = (signedTransaction as BtcTransaction).tx
        return paymentRequestHandler.value!!.sendResponse(transaction, address)
    }

    // Handles BTC payment request
    @Throws(GenericBuildTransactionException::class, GenericInsufficientFundsException::class, GenericOutputTooSmallException::class)
    override fun handlePaymentRequest(toSend: Value): TransactionStatus {
        val paymentRequestInformation = paymentRequestHandler.value!!.paymentRequestInformation
        var outputs = paymentRequestInformation.outputs

        // has the payment request an amount set?
        if (paymentRequestInformation.hasAmount()) {
            amount.postValue(Value.valueOf(Utils.getBtcCoinType(), outputs.totalAmount))
        } else {
            if (amount.value!!.isZero()) {
                return TransactionStatus.MISSING_ARGUMENTS
            }
            // build new output list with user specified amount
            outputs = outputs.newOutputsWithTotalAmount(toSend.valueAsLong)
        }

        val btcAccount = account as AbstractBtcAccount
        transaction = btcAccount.createTxFromOutputList(outputs, FeePerKbFee(selectedFee.value!!).feePerKb.valueAsLong)
        spendingUnconfirmed.postValue(account.isSpendingUnconfirmed(transaction))
        receivingAddress.postValue(null)
        transactionLabel.postValue(paymentRequestInformation.paymentDetails.memo)

        return TransactionStatus.OK
    }

    override fun getFeeLvlItems(): List<FeeLvlItem> {
        return MinerFee.values()
                .map { fee ->
                    val blocks = when (fee) {
                        MinerFee.LOWPRIO -> 20
                        MinerFee.ECONOMIC -> 10
                        MinerFee.NORMAL -> 3
                        MinerFee.PRIORITY -> 1
                    }
                    val duration = Utils.formatBlockcountAsApproxDuration(mbwManager, blocks, BTC_BLOCK_TIME_IN_SECONDS)
                    FeeLvlItem(fee, "~$duration", SelectableRecyclerView.SRVAdapter.VIEW_TYPE_ITEM)
                }
    }

    override fun signTransaction(activity: Activity) {
        // if we have a payment request, check if it is expired
        if (hasPaymentRequestHandler() && paymentRequestExpired()) {
            makeText(activity, activity.getString(R.string.payment_request_not_sent_expired), LENGTH_LONG).show()
            return
        }

        super.signTransaction(activity)
    }
}