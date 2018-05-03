package net.corda.insurance.exchange.binding

import net.corda.core.contracts.Contract
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction
import net.corda.insurance.exchange.offer.InsuranceOffer
import net.corda.insurance.exchange.request.InsuranceRequest

class InsuranceContract : Contract {
    override fun verify(tx: LedgerTransaction) {

        //there should only be one command on this tx.
        val bindCommand = tx.commands.requireSingleCommand<BIND>()


        //only one offer allowed
        val offer = tx.inRefsOfType<InsuranceOffer>().single();

        //only one request allowed
        val request = tx.inRefsOfType<InsuranceRequest>().single()


        //only one insurance contract allowed
        val insuredState = tx.outputsOfType<InsuredState>().single()

        requireThat {
            "The input ${InsuranceRequest::class.java.simpleName} must be the one referenced on the insurance contract" using (insuredState.requestRef == request.ref)
            "The input ${InsuranceOffer::class.java.simpleName} must be the one referenced on the insurance contract" using (insuredState.offerRef == offer.ref)

            "The insurer must be the one who offered" using (insuredState.insurer == offer.state.data.insurer)
            "The requestor must be the one who requested" using (insuredState.requestor == request.state.data.requestor)
        }


    }

    class BIND : TypeOnlyCommandData()
}

