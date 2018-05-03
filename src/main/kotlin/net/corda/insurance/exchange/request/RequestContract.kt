package net.corda.insurance.exchange.request

import net.corda.core.contracts.Contract
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.transactions.LedgerTransaction

class RequestContract : Contract {
    override fun verify(tx: LedgerTransaction) {
    }

    class CREATE : TypeOnlyCommandData()
    class CANCEL : TypeOnlyCommandData()
}

