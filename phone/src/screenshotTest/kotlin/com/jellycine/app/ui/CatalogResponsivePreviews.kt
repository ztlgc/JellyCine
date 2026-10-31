package com.jellycine.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.jellycine.app.ui.components.common.CompactCatalogHeader
import com.jellycine.app.ui.screens.dashboard.home.BrandHeader
import com.jellycine.app.ui.screens.dashboard.home.LibraryItemCaption
import com.jellycine.app.ui.screens.dashboard.media.LibraryBrowserToolbar

@PreviewTest
@Preview(name = "Catalog narrow", widthDp = 320, heightDp = 800, locale = "zh-rCN")
@Preview(name = "Catalog phone", widthDp = 412, heightDp = 892, locale = "zh-rCN")
@Preview(name = "Catalog large font", widthDp = 320, heightDp = 1100, locale = "zh-rCN", fontScale = 2f)
@Preview(name = "Catalog landscape", widthDp = 640, heightDp = 320, locale = "zh-rCN", fontScale = 1.5f)
@Composable
fun CatalogResponsivePreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Column(Modifier.fillMaxSize().background(Color.Black).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BrandHeader(serverName = "这是一个很长的家庭影音服务器名称", showUserIcon = true)
            LibraryBrowserToolbar(countText = "正在读取 12000 / 18000", sortLabel = "影评人评分",
                descending = true, randomSort = false, listView = false, hasFilters = true,
                canShuffle = true, onSort = {}, onDirection = {}, onView = {}, onFilter = {}, onShuffle = {})
            CompactCatalogHeader(title = "收藏的长名称电影和电视剧", countText = "12,000 / 18,000",
                imageUrl = null, progress = 1f, isTablet = false, onClick = {})
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(2) {
                    Column(Modifier.width(112.dp)) {
                        Box(Modifier.fillMaxWidth().height(166.dp).background(Color(0xFF22252A)))
                        LibraryItemCaption(title = "山野之间的第一次远行", metadata = "2026 · 电影",
                            landscapeLayout = false, titleMaxLines = 1)
                    }
                }
            }
            Text("后续栏目", color = Color.White, modifier = Modifier.padding(16.dp))
        }
    }
}
