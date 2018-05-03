package net.corda.insurance.exchange.binding

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.insurance.exchange.offer.InsuranceOffer
import net.corda.insurance.exchange.request.InsuranceRequest


class InsureFlow {
    @InitiatingFlow
    @StartableByRPC
    class BindFlow(val offerStateAndRef: StateAndRef<InsuranceOffer>, val requestStateAndRef: StateAndRef<InsuranceRequest>) : FlowLogic<SignedTransaction>() {


        @Suspendable
        override fun call(): SignedTransaction {
            //setup the initial local transaction for committing our request to the ledger
            val me = serviceHub.myInfo.legalIdentities.first()
            val notary = serviceHub.networkMapCache.notaryIdentities.single()
            val insurer = offerStateAndRef.state.data.insurer


            val state = InsuredState(offerStateAndRef.ref, requestStateAndRef.ref,
                    me, insurer)


            val utx = TransactionBuilder(notary = notary)

            utx.addInputState(offerStateAndRef)
            utx.addInputState(requestStateAndRef)

            utx.addOutputState(state, InsuranceContract::class.java.canonicalName)

            val requiredSigners = state.participants
                    .map { it.owningKey }
                    .toList()

            utx.addCommand(InsuranceContract.BIND(), requiredSigners)
            utx.verify(serviceHub)
            val stx = serviceHub.signInitialTransaction(utx)
            val signedTx = subFlow(CollectSignaturesFlow(stx, setOf(initiateFlow(insurer))))
            return subFlow(FinalityFlow(signedTx))
        }
    }

    @InitiatedBy(BindFlow::class)
    class Signer(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val flow = object : SignTransactionFlow(counterpartySession) {
                @Suspendable
                override fun checkTransaction(stx: SignedTransaction) {
                    //all checks delegated to contract
                }
            }
            val stx = subFlow(flow)
            return waitForLedgerCommit(stx.id)
        }
    }
}

