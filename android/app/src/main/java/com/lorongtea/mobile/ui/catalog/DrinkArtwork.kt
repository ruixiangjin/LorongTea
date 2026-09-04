package com.lorongtea.mobile.ui.catalog

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lorongtea.mobile.R

private enum class DemoDrink(val catalogName: String, val nameRes: Int, val imageRes: Int) {
    OOLONG("炭焙乌龙鲜奶", R.string.drink_oolong, R.drawable.tea_oolong),
    BOBA("黑糖珍珠奶茶", R.string.drink_boba, R.drawable.tea_boba),
    PANDAN("斑斓椰奶茶", R.string.drink_pandan, R.drawable.tea_pandan),
}

@Composable
internal fun drinkName(catalogName: String): String =
    DemoDrink.entries.firstOrNull { it.catalogName == catalogName }?.let { stringResource(it.nameRes) }
        ?: catalogName

@Composable
internal fun DrinkArtwork(catalogName: String, modifier: Modifier = Modifier) {
    val demo = DemoDrink.entries.firstOrNull { it.catalogName == catalogName }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        if (demo != null) {
            Image(
                painter = painterResource(demo.imageRes),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_cup), contentDescription = null, Modifier.size(30.dp))
            }
        }
    }
}
