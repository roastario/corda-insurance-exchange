package net.corda.insurance.exchange.offer

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateRef
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party

data class InsuranceOffer(val premium: Double,
                          val insurer: Party,
                          val requestState: StateRef) : ContractState {
    override val participants: List<AbstractParty>
        get() = listOf(insurer)
}