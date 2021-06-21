package com.mycelium.giftbox.cards.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import com.mycelium.bequant.kyc.inputPhone.coutrySelector.CountryModel
import com.mycelium.wallet.R


class GiftBoxViewModel(application: Application) : AndroidViewModel(application) {
    val selectedCountries = MutableLiveData<List<CountryModel>>(emptyList())
    val countries = MutableLiveData<List<CountryModel>>(emptyList())
    val categories = MutableLiveData<List<String>>(emptyList())

    fun currentCountries(): LiveData<String> =
            Transformations.switchMap(selectedCountries) {
                return@switchMap MutableLiveData<String>(when (it.size) {
                    0 -> getApplication<Application>().getString(R.string.all_countries)
                    1 -> it[0].name
                    else -> getApplication<Application>().resources.getQuantityString(R.plurals.d_countries, it.size, it.size)
                })
            }
}