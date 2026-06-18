package com.financeos.hub.features.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.financeos.hub.features.analytics.tabs.CategoriesTab
import com.financeos.hub.features.analytics.tabs.InsightsTab
import com.financeos.hub.features.analytics.tabs.OverviewTab
import com.financeos.hub.features.analytics.tabs.TrendsTab
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosType
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import kotlinx.coroutines.launch

private val TABS = listOf("Обзор", "Категории", "Тренды", "Инсайты")

@Composable
fun AnalyticsScreen(vm: AnalyticsViewModel = hiltViewModel()) {
    val state       = vm.state.collectAsState().value
    val pagerState  = rememberPagerState { TABS.size }
    val scope       = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FosColors.Background),
    ) {
        Text(
            text     = "Аналитика",
            style    = FosType.ScreenTitle,
            color    = FosColors.TextPrimary,
            modifier = Modifier.padding(
                start  = FosDimens.ScreenPadding,
                end    = FosDimens.ScreenPadding,
                top    = 16.dp,
                bottom = 4.dp,
            ),
        )

        ScrollableTabRow(
            selectedTabIndex  = pagerState.currentPage,
            containerColor    = FosColors.Background,
            contentColor      = FosColors.TextPrimary,
            edgePadding       = FosDimens.ScreenPadding,
            indicator         = { tabPositions ->
                TabRowDefaults.Indicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                    color    = FosColors.Positive,
                )
            },
            divider = {},
        ) {
            TABS.forEachIndexed { i, title ->
                Tab(
                    selected    = pagerState.currentPage == i,
                    onClick     = { scope.launch { pagerState.animateScrollToPage(i) } },
                    text        = {
                        Text(
                            text  = title,
                            style = FosType.Label,
                            color = if (pagerState.currentPage == i) FosColors.TextPrimary
                                    else FosColors.TextMuted,
                        )
                    },
                )
            }
        }

        HorizontalPager(
            state    = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (page) {
                0 -> OverviewTab(state)
                1 -> CategoriesTab(state)
                2 -> TrendsTab(state)
                3 -> InsightsTab(state)
            }
        }
    }
}
