package net.corda.insurance.exchange.request

import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party

data class InsuranceRequest(val value: Double,
                            val item: String,
                            val daysOfInsurance: Long,
                            val requestor: Party) : LinearState {
    override val linearId: UniqueIdentifier
        get() = UniqueIdentifier(item)
    override val participants: List<AbstractParty>
        get() = listOf(requestor)
}