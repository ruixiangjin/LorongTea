package com.lorongtea.mobile

import com.lorongtea.mobile.data.CartLine
import com.lorongtea.mobile.data.MenuItem
import com.lorongtea.mobile.data.planCartReprice
import com.lorongtea.mobile.ui.checkout.CartState
import com.lorongtea.mobile.ui.checkout.CheckoutProgress
import com.lorongtea.mobile.ui.checkout.hasUnavailableDrinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CartPriceUpdateTest {
    @Test
    fun priceComparisonUsesTheSameCartQuantitiesAndWritesOnlyChangedLines() {
        val original = listOf(line("oolong", 1090, 2), line("boba", 990, 1))
        val menu = listOf(item("oolong", 1190), item("boba", 990))
        val plan = planCartReprice(original, menu)

        assertEquals(3170L, plan.comparison.previousTotal)
        assertEquals(3370L, plan.comparison.updatedTotal)
        assertEquals(listOf(original.first().copy(priceCents = 1190)), plan.changedLines)
        assertTrue(plan.comparison.unavailableProducts.isEmpty())
    }

    @Test
    fun unlistedAndMissingDrinksStayRemovableAndBlockReview() {
        val original = listOf(line("oolong", 1090, 1), line("boba", 990, 1), line("pandan", 1190, 1))
        val plan = planCartReprice(original, listOf(item("oolong", 1190), item("boba", 990, listed = false)))
        val state = CartState(original, checkout = CheckoutProgress(priceUpdate = plan.comparison))

        assertEquals(setOf("boba", "pandan"), plan.comparison.unavailableProducts)
        assertNull(plan.comparison.updatedTotal)
        assertTrue(state.hasUnavailableDrinks())
        assertFalse(state.copy(lines = plan.changedLines).hasUnavailableDrinks())
    }

    private fun line(id: String, price: Long, quantity: Int) = CartLine("customer", "store", id, id, price, quantity)
    private fun item(id: String, price: Long, listed: Boolean = true) = MenuItem(id, id, price, listed)
}
