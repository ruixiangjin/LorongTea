package com.lorongtea.mobile.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lorongtea.mobile.R
import com.lorongtea.mobile.data.CartLine
import com.lorongtea.mobile.data.CartPriceUpdate
import com.lorongtea.mobile.data.MenuItem
import com.lorongtea.mobile.data.Order
import com.lorongtea.mobile.data.OrderLine
import com.lorongtea.mobile.data.OrderStatus
import com.lorongtea.mobile.data.PaymentStatus
import com.lorongtea.mobile.data.PendingCheckout
import com.lorongtea.mobile.data.Store
import com.lorongtea.mobile.data.totalCents
import com.lorongtea.mobile.ui.catalog.HOUSE_DRINK
import com.lorongtea.mobile.ui.catalog.MenuScreen
import com.lorongtea.mobile.ui.catalog.MenuState
import com.lorongtea.mobile.ui.catalog.storeName
import com.lorongtea.mobile.ui.checkout.CartScreen
import com.lorongtea.mobile.ui.checkout.CartState
import com.lorongtea.mobile.ui.checkout.CheckoutProgress
import com.lorongtea.mobile.ui.common.RemoteState
import com.lorongtea.mobile.ui.components.Page
import com.lorongtea.mobile.ui.components.ScreenHeading
import com.lorongtea.mobile.ui.orders.OrderDetailScreen
import com.lorongtea.mobile.ui.orders.OrderDetailState
import com.lorongtea.mobile.ui.theme.LorongTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class UiScenariosActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LorongTheme { UiScenarios() } }
    }
}

private enum class Scenario(val label: Int) {
    STOREFRONT(R.string.lab_storefront),
    REPRICED(R.string.lab_repriced),
    PENDING(R.string.lab_pending),
    ORDER(R.string.lab_order),
    CAMPAIGN(R.string.lab_campaign),
}

internal val sampleStores = listOf(
    Store("1", "bukit-bintang", "武吉免登店"),
    Store("2", "petaling-jaya", "八打灵再也店"),
)

private fun sampleMenu(store: Store) = listOf(
    MenuItem("oolong", HOUSE_DRINK, if (store.id == "1") 1090 else 1150, true),
    MenuItem("boba", "黑糖珍珠奶茶", if (store.id == "1") 990 else 1050, store.id == "1"),
    MenuItem("pandan", "斑斓椰奶茶", if (store.id == "1") 1190 else 1090, true),
)

@Composable
private fun UiScenarios() {
    var scenario by rememberSaveable { mutableStateOf<Scenario?>(null) }
    val home = { scenario = null }
    BackHandler(enabled = scenario != null, onBack = home)
    when (scenario) {
        null -> Page {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 18.dp),
            ) {
                item { ScreenHeading(stringResource(R.string.lab_title), stringResource(R.string.lab_description)) }
                Scenario.entries.forEach { example ->
                    item(key = example.name) {
                        OutlinedButton(onClick = { scenario = example }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(example.label))
                        }
                    }
                }
            }
        }
        Scenario.STOREFRONT -> StorefrontScenario(home)
        Scenario.REPRICED -> CheckoutScenario(repriced = true, back = home)
        Scenario.PENDING -> CheckoutScenario(repriced = false, back = home)
        Scenario.ORDER -> OrderScenario(home)
        Scenario.CAMPAIGN -> CampaignScenario(home)
    }
}

@Composable
private fun StorefrontScenario(back: () -> Unit) {
    var storeIndex by rememberSaveable { mutableIntStateOf(0) }
    val store = sampleStores[storeIndex]
    val screens = rememberSaveableStateHolder()
    screens.SaveableStateProvider(store.id) {
        val items = remember(store) { sampleMenu(store) }
        val opening = remember { fridayOpening() }
        var quantities by rememberSaveable { mutableStateOf(IntArray(items.size)) }
        var cart by rememberSaveable { mutableStateOf(false) }
        var review by rememberSaveable { mutableStateOf(false) }
        var campaign by rememberSaveable { mutableStateOf(false) }
        var placed by rememberSaveable { mutableStateOf(false) }
        val lines = items.mapIndexedNotNull { index, item ->
            quantities[index].takeIf { it > 0 }?.let { CartLine("demo", store.id, item.productId, item.name, item.priceCents, it) }
        }
        val localBack: () -> Unit = {
            when {
                review -> review = false
                placed -> placed = false
                cart -> cart = false
                campaign -> campaign = false
                else -> back()
            }
        }
        BackHandler(onBack = localBack)
        when {
            campaign -> CampaignScenario(back = localBack, initialStore = storeIndex)
            placed -> OrderScenario(localBack, store)
            cart -> CartScreen(
                state = CartState(lines, checkout = CheckoutProgress(review = lines.takeIf { review })),
                store = storeName(store),
                change = { productId, delta ->
                    val index = items.indexOfFirst { it.productId == productId }
                    if (index >= 0) quantities = quantities.copyOf().apply { this[index] = (this[index] + delta).coerceAtLeast(0) }
                },
                review = { review = true },
                dismissReview = { review = false },
                confirm = {
                    review = false
                    cart = false
                    quantities = IntArray(items.size)
                    placed = true
                },
                recover = {},
                back = localBack,
            )
            else -> MenuScreen(
                state = MenuState(
                    RemoteState(data = items, loading = false),
                    lines.associate { it.productId to it.quantity },
                    cartTotal = lines.totalCents(),
                ),
                storeId = store.id,
                store = storeName(store),
                refresh = {},
                add = { item ->
                    val index = items.indexOf(item)
                    quantities = quantities.copyOf().apply { this[index]++ }
                },
                cart = { cart = true },
                changeStore = { storeIndex = 1 - storeIndex },
                campaigns = listOf(sampleOffer(store, opening, CampaignExample.STORE_STATUS)),
                openCampaign = { campaign = true },
            )
        }
    }
}

@Composable
private fun CheckoutScenario(repriced: Boolean, back: () -> Unit) {
    var quantity by rememberSaveable { mutableIntStateOf(1) }
    var pending by rememberSaveable { mutableStateOf(!repriced) }
    var review by rememberSaveable { mutableStateOf(false) }
    var placed by rememberSaveable { mutableStateOf(false) }
    val store = sampleStores.first()
    if (placed) {
        BackHandler { placed = false }
        OrderScenario({ placed = false }, store)
        return
    }
    val lines = if (quantity > 0) listOf(
        CartLine("demo", store.id, "oolong", HOUSE_DRINK, if (repriced) 1190 else 1090, quantity),
    ) else emptyList()
    CartScreen(
        state = CartState(
            lines = lines,
            pending = if (pending) PendingCheckout("demo", store.id, "demo-checkout", "{}", 0L) else null,
            checkout = CheckoutProgress(
                review = lines.takeIf { review },
                priceUpdate = if (repriced) CartPriceUpdate(1090, 1190, emptySet()) else null,
            ),
        ),
        store = storeName(store),
        change = { _, delta -> quantity = (quantity + delta).coerceAtLeast(0) },
        review = { review = true },
        dismissReview = { review = false },
        confirm = {
            review = false
            placed = true
        },
        recover = {
            pending = false
            placed = true
        },
        back = back,
    )
}

private enum class PickupExample(val label: Int, val status: OrderStatus, val payment: PaymentStatus) {
    CREATED(R.string.order_created, OrderStatus.CREATED, PaymentStatus.UNPAID),
    PREPARING(R.string.order_preparing, OrderStatus.PREPARING, PaymentStatus.UNPAID),
    READY_UNPAID(R.string.lab_ready_unpaid, OrderStatus.READY, PaymentStatus.UNPAID),
    READY_PAID(R.string.lab_ready_paid, OrderStatus.READY, PaymentStatus.PAID),
    COLLECTED(R.string.order_collected, OrderStatus.COMPLETED, PaymentStatus.PAID),
}

@Composable
internal fun OrderScenario(
    back: () -> Unit,
    store: Store = sampleStores.first(),
    orderId: String = "DEMO-01",
    price: Long = 1090,
) {
    var example by rememberSaveable { mutableStateOf(PickupExample.PREPARING) }
    var cancelled by rememberSaveable { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    ScenarioFrame(
        controls = {
            ScenarioPicker(PickupExample.entries, example, { stringResource(it.label) }) {
                example = it
                cancelled = false
            }
        },
    ) {
        val order = Order(
            id = orderId,
            storeId = store.id,
            status = if (cancelled) OrderStatus.CANCELLED else example.status,
            paymentStatus = example.payment,
            totalCents = price,
            items = listOf(OrderLine("oolong", HOUSE_DRINK, price, 1)),
            pickupToken = if (example == PickupExample.READY_PAID) "DEMO-TEA" else null,
        )
        OrderDetailScreen(
            state = OrderDetailState(order, loading = refreshing),
            stores = sampleStores,
            refresh = {
                if (!refreshing) scope.launch {
                    refreshing = true
                    delay(700)
                    refreshing = false
                }
            },
            cancel = { cancelled = true },
            back = back,
        )
    }
}

@Composable
internal fun ScenarioFrame(controls: @Composable () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.statusBarsPadding()) {
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = { controls() },
        )
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
internal fun <T> ScenarioPicker(
    choices: List<T>,
    current: T,
    label: @Composable (T) -> String,
    select: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(label(current), style = MaterialTheme.typography.labelLarge)
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(label(choice)) },
                    onClick = {
                        expanded = false
                        select(choice)
                    },
                )
            }
        }
    }
}
