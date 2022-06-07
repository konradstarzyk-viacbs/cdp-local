"Standard:Episode" {
    reportLabel("ContentType", "Episode")
    eachElement {
        groupStatus("Content Overall Status")
        field("?VideoPlaylists") {
            reportFieldName("VideoPlaylist")
            notEmpty()
        }
    }
}
"Standard:Movie" {
    reportLabel("ContentType", "Movie")
    eachElement {
        groupStatus("Content Overall Status")
        field("?VideoPlaylists") {
            reportFieldName("VideoPlaylist")
            notEmpty()
        }
    }
}
"Standard:ShowVideo" {
    reportLabel("ContentType", "ShowVideo")
    eachElement {
        groupStatus("Content Overall Status")
        field("VideoType.TypeName") {
            reportFieldName("Video Type Name")
            equalsTo("clip")
        }
        VideoAssetRefs {
            
            notEmpty()
        }
    }
}
"Standard:MusicVideo" {
    reportLabel("ContentType", "MusicVideo")
    eachElement {
        groupStatus("Content Overall Status")
        field("VideoType.TypeName") {
            reportFieldName("Video Type Name")
            equalsTo("clip")
        }
        VideoAssetRefs {
            
            notEmpty()
        }
    }
}
"Standard:VideoPlaylist" {
    reportLabel("ContentType", "VideoPlaylist")
    eachElement {
        groupStatus("Content Overall Status")
        VideoAssetRefs {
            notEmpty()
        }
    }
}
