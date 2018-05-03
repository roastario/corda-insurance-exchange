package net.corda.insurance.exchange.request

import co.paralleluniverse.fibers.Suspendable
import net.corda.insurance.exchange.offer.InsuranceOffer
import net.corda.insurance.exchange.offer.OfferContract
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import java.util.concurrent.ThreadLocalRandom


class RequestFlow {
    @InitiatingFlow
    @StartableByRPC
    class Request(val value: Double, val item: String, val days: Long) : FlowLogic<Pair<StateAndRef<InsuranceOffer>, StateAndRef<InsuranceRequest>>>() {

        override val progressTracker: ProgressTracker = tracker()

        companion object {
            object CREATING : ProgressTracker.Step("Creating a new request!")
            object SIGNING : ProgressTracker.Step("Signing the request!")
            object VERIFYING : ProgressTracker.Step("Verifying the request!")
            object FINALISING : ProgressTracker.Step("committing the request!") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            object REQUESTING : ProgressTracker.Step("sending request to insurers")

            fun tracker() = ProgressTracker(CREATING, SIGNING, VERIFYING, FINALISING, REQUESTING)
        }

        @Suspendable
        override fun call(): Pair<StateAndRef<InsuranceOffer>, StateAndRef<InsuranceRequest>> {
            //setup the initial local transaction for committing our request to the ledger
            progressTracker.currentStep = CREATING
            val me = serviceHub.myInfo.legalIdentities.first()
            val notary = serviceHub.networkMapCache.notaryIdentities.single()
            val command = Command(RequestContract.CREATE(), me.owningKey)
            val state = InsuranceRequest(value, item, days, me)
            val utx = TransactionBuilder(notary = notary)
            utx.addOutputState(state, RequestContract::class.java.canonicalName)
            utx.addCommand(command)
            progressTracker.currentStep = SIGNING
            val stx = serviceHub.signInitialTransaction(utx)
            progressTracker.currentStep = VERIFYING
            stx.verify(serviceHub)
            progressTracker.currentStep = FINALISING
            val localIssuance = subFlow(FinalityFlow(stx, FINALISING.childProgressTracker()))
            //the request has been committed to the ledger

            //now we contact all insurers on the network, and send them the request - they will then return an offer
            val committedRequest = localIssuance.coreTransaction.outRefsOfType<InsuranceRequest>().single()
            val lowestOffer = serviceHub.networkMapCache.allNodes.map { it.legalIdentities.first() }
                    .filter { it.name.toString().toLowerCase().contains("insurer") }
                    .map {
                        val session = initiateFlow(it)
                        val outRef = committedRequest
                        session.send(outRef)
                        val offerTx = session.receive<SignedTransaction>().unwrap { it }
                        serviceHub.recordTransactions(StatesToRecord.ALL_VISIBLE, listOf(offerTx))
                        return@map offerTx.coreTransaction.outRefsOfType<InsuranceOffer>().single()
                    }.sortedBy { it.state.data.premium }
                    .first()

            return ( lowestOffer to committedRequest)
        }
    }

    @InitiatedBy(Request::class)
    class Respond(private val otherSide: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val requestedInsuranceStateAndRef = otherSide.receive<StateAndRef<InsuranceRequest>>().unwrap { it }
            val data = requestedInsuranceStateAndRef.state.data
            val me = serviceHub.myInfo.legalIdentities.first()
            val command = Command(OfferContract.CREATE(), me.owningKey)
            val premium = ThreadLocalRandom.current().nextDouble(1.0).times(data.value).times(data.daysOfInsurance)
            val state = InsuranceOffer(premium, me, requestedInsuranceStateAndRef.ref)
            val utx = TransactionBuilder(notary = requestedInsuranceStateAndRef.state.notary)
            utx.addCommand(command)
            utx.addOutputState(state, "net.corda.insurance.exchange.offer.OfferContract")
            val stx = serviceHub.signInitialTransaction(utx)
            val localIssuance = subFlow(FinalityFlow(stx, setOf(requestedInsuranceStateAndRef.state.data.requestor)))
            otherSide.send(localIssuance)
        }
    }
}

