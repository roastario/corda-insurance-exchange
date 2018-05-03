package net.corda.insurance.exchange.binding

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateRef
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party

data class InsuredState(val offerRef: StateRef,
                        val requestRef: StateRef,
                        val requestor: Party,
                        val insurer: Party) : ContractState {
    override val participants: List<AbstractParty>
        get() = listOf(insurer, requestor)
}