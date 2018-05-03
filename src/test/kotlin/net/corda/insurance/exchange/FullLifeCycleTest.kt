package net.corda.insurance.exchange

import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.insurance.exchange.binding.InsureFlow
import net.corda.insurance.exchange.binding.InsuredState
import net.corda.insurance.exchange.offer.InsuranceOffer
import net.corda.insurance.exchange.request.InsuranceRequest
import net.corda.insurance.exchange.request.RequestFlow
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.User
import org.hamcrest.CoreMatchers.`is`
import org.junit.Assert
import org.junit.Test

class FullLifeCycleTest {

    @Test
    fun letsGo() {

        val user = User("user1", "test", permissions = setOf("ALL"))
        driver(DriverParameters(isDebug = true, startNodesInProcess = true,
                waitForAllNodesToFinish = false,
                extraCordappPackagesToScan = listOf("com.stefano"))) {
            val (nodeA, nodeB) = listOf(
                    startNode(providedName = CordaX500Name("customer1", "London", "GB"), rpcUsers = listOf(user)),
                    startNode(providedName = CordaX500Name("insurer1", "London", "GB"), rpcUsers = listOf(user))).map { it.getOrThrow() }

            val (lowestOffer, request) = nodeA.rpc.startFlow(RequestFlow::Request, 123.45, "item", 10).returnValue.getOrThrow()
            val insuranceStateQuery = nodeA.rpc.vaultQuery(InsuranceRequest::class.java)
            val requestStateRef = insuranceStateQuery.states.single()
            val requestState = requestStateRef.state.data

            Assert.assertThat(requestState.item, `is`("item"))
            Assert.assertThat(requestState.value, `is`(123.45))
            Assert.assertThat(requestState.daysOfInsurance, `is`(10L))

            val offerStateQuery = nodeB.rpc.vaultQuery(InsuranceOffer::class.java)
            val offerStateRef = offerStateQuery.states.single()
            val offerState = offerStateRef.state.data

            Assert.assertThat(offerState.requestState, `is`(requestStateRef.ref))

            val insuredItemStateRef = nodeA.rpc.startFlow(InsureFlow::BindFlow, lowestOffer, request).returnValue
                    .getOrThrow().tx.outRefsOfType<InsuredState>().single()
            val insuredItemState = insuredItemStateRef.state

            Assert.assertThat(insuredItemState.data.insurer, `is`(nodeB.nodeInfo.legalIdentities.single()))
            Assert.assertThat(insuredItemState.data.requestor, `is`(nodeA.nodeInfo.legalIdentities.single()))
            Assert.assertThat(insuredItemState.data.offerRef, `is`(offerStateRef.ref))
            Assert.assertThat(insuredItemState.data.requestRef, `is`(requestStateRef.ref))

        }


    }


}