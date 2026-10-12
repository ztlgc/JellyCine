package com.jellycine.data.model

import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.HomeMediaKind
import com.jellycine.data.model.homeMediaKind
import kotlin.test.assertEquals
import kotlin.test.Test

class HomeMediaKindTest {
    @Test fun folderTakesPriorityOverPhotoMetadata() {
        assertEquals(HomeMediaKind.FOLDER, BaseItemDto(isFolder = true, mediaType = "Photo").homeMediaKind())
        assertEquals(HomeMediaKind.FOLDER, BaseItemDto(type = "photoalbum", mediaType = "Photo").homeMediaKind())
    }

    @Test fun missingFolderFlagStillAllowsKnownFolders() {
        assertEquals(HomeMediaKind.FOLDER, BaseItemDto(type = "Folder").homeMediaKind())
    }

    @Test fun mediaTypeAllowsVideoSubtypes() {
        assertEquals(HomeMediaKind.VIDEO, BaseItemDto(type = "Trailer", mediaType = "video").homeMediaKind())
        assertEquals(HomeMediaKind.VIDEO, BaseItemDto(type = "Video").homeMediaKind())
    }

    @Test fun photosDoNotEnterVideoPlayback() {
        assertEquals(HomeMediaKind.PHOTO, BaseItemDto(type = "PHOTO").homeMediaKind())
        assertEquals(HomeMediaKind.PHOTO, BaseItemDto(mediaType = "Photo").homeMediaKind())
    }

    @Test fun emptyAndUnknownItemsAreNotPlayable() {
        assertEquals(HomeMediaKind.UNSUPPORTED, BaseItemDto().homeMediaKind())
        assertEquals(HomeMediaKind.UNSUPPORTED, BaseItemDto(type = "Audio", mediaType = "Audio").homeMediaKind())
    }
}
