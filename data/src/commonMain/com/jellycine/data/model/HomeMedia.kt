package com.jellycine.data.model

enum class HomeMediaKind { FOLDER, PHOTO, VIDEO, UNSUPPORTED }

fun BaseItemDto.homeMediaKind(): HomeMediaKind = when {
    isFolder == true || type.equals("Folder", true) || type.equals("PhotoAlbum", true) -> HomeMediaKind.FOLDER
    type.equals("Photo", true) || mediaType.equals("Photo", true) -> HomeMediaKind.PHOTO
    type.equals("Video", true) || type.equals("Movie", true) || mediaType.equals("Video", true) -> HomeMediaKind.VIDEO
    else -> HomeMediaKind.UNSUPPORTED
}
