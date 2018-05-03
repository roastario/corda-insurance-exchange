package net.corda.insurance.exchange.offer

import net.corda.core.contracts.Contract
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.transactions.LedgerTransaction

class OfferContract : Contract {
    override fun verify(tx: LedgerTransaction) {
    }

    class CREATE : TypeOnlyCommandData()
    class CANCEL : TypeOnlyCommandData()
}

