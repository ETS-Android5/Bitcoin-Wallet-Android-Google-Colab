package com.mycelium.bequant.remote.model

import java.io.Serializable


class KYCRequest : Serializable {
    var address_1: String? = null
    var address_2: String? = null
    var birthday: String? = null
    var city: String? = null
    var country: String? = null
    var first_name: String? = null
    var last_name: String? = null
    var nationality: String? = null
    var zip: String? = null
}