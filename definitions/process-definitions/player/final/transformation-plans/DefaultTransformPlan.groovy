package tech.viacom.contentdeliveryplatform.processorchestrator.transformation

import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.DocumentContext
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.Option
import groovy.json.JsonOutput
import net.minidev.json.JSONArray
import org.apache.commons.lang3.StringUtils
import org.jetbrains.annotations.NotNull
import tech.viacom.contentdeliveryplatform.processorchestrator.common.ArcDateUtil

import java.text.DecimalFormat
import java.time.ZonedDateTime
import java.time.temporal.ChronoField

import static java.time.format.DateTimeFormatter.ofPattern
import static java.util.TimeZone.getTimeZone

@SuppressWarnings("unused")
class DefaultTransformPlan implements TransformationPlan {

    private static final JSON_PATH_CONFIGURATION = Configuration.builder().options(Option.SUPPRESS_EXCEPTIONS).build()

    private static final String THUMBNAIL_URL = "https://www.mtvnimages.com/uri/"
    private static final String TOPAZ_DAI_URL = "https://topaz.viacomcbs.digital/topaz/api/%s/dai"
    private static final String INGEST_DOMAIN = "topaz.viacomcbs.digital"
    private static final DURATION_FORMATTER = ofPattern("[[H:m:][m:]]s[[.SSS][.SS][.S]")
    private static final FEED_DATE_FORMATTER = ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz").withZone(getTimeZone("UTC").toZoneId())
    private static final DecimalFormat CUEPOINTS_FORMAT = new DecimalFormat("0.###")

    @Override
    Map<String, Object> transform(@NotNull Map<String, ?> contentItem, @NotNull String endpoint) {
        def contentItemJson = jsonPath(contentItem)
        def mgid = mgid(contentItemJson, endpoint)
        def latestModifiedDate = findLastModifiedDate(contentItem, contentItemJson)
        def pubDate = pubDate(contentItemJson, latestModifiedDate)
        return [
                "title"                : contentItemJson.read('$.Title'),
                "thumbnailUrl"         : thumbnailUrl(contentItemJson),
                "contentID"            : mgid,
                "ingestUrl"            : ingestUrl(mgid),
                "lastModifiedDate"     : latestModifiedDate?.format(FEED_DATE_FORMATTER),
                "lastMediaModifiedDate": latestModifiedDate?.format(FEED_DATE_FORMATTER),
                "pubDate"              : pubDate?.format(FEED_DATE_FORMATTER),
                "contentDuration"      : contentDuration(contentItemJson),
                "fwCaid"               : fwCaid(contentItemJson),
                "cuepoints"            : cuepoints(contentItemJson),
                "closedCaptionUrl"     : closedCaptionUrl(contentItemJson, mgid)
        ]
    }

    private static ZonedDateTime pubDate(contentItemJson, latestModifiedDate) {
        getDate(contentItemJson.read('$.mtvi:created')) ?: latestModifiedDate
    }

    private static def thumbnailUrl(DocumentContext contentItemJson) {
        def imagesWithCaptionsPath = contentItemJson.read('$.ImagesWithCaptions[*].Image.ImageAssetRefs[*].URI')[0]
        if (imagesWithCaptionsPath != null) {
            return THUMBNAIL_URL + imagesWithCaptionsPath
        }
        def imagesPath = contentItemJson.read('$.Images[*].ImageAssetRefs[*].URI')[0]
        if (imagesPath != null) {
            return THUMBNAIL_URL + imagesPath
        }
        return null
    }

    private static String mgid(DocumentContext contentItemJson, String endpoint) {
        def contentType = contentType(contentItemJson.read('$.mtvi:contentType'))
        def brand = brand(endpoint)
        def mtviId = mtviId(contentItemJson.read('$.mtvi:id'))

        if (contentType == null || brand == null || mtviId == null) {
            return null
        }
        return ["mgid", "arc", contentType, brand, mtviId].join(":")
    }

    private static String ingestUrl(String contentID) {
        if (contentID == null) {
            return null
        }
        return String.format(TOPAZ_DAI_URL, contentID)
    }

    private static String contentType(String mtviContentType) {
        switch (mtviContentType) {
            case "Standard:ShowVideo":
                return "showvideo"
            case "Standard:Episode":
                return "episode"
            case "Standard:Video":
                return "video"
            case "Standard:MusicVideo":
                return "musicvideo"
            case "Standard:Movie":
                return "movie"
            default:
                return null
        }
    }

    private static String brand(String endpoint) {
        if (endpoint != null && !endpoint.isBlank()) {
            return endpoint
        } else {
            return null
        }
    }

    private static def mtviId(String mtviId) {
        if (mtviId != null && !mtviId.isBlank()) {
            return mtviId
        } else {
            return null
        }
    }

    private static ZonedDateTime findLastModifiedDate(Map<String, ?> contentItem, DocumentContext contentItemJson) {
        switch (contentItem['mtvi:contentType']) {
            case "Standard:ShowVideo":
                return latestDateOfAllItemsAndAssets(contentItemJson)
            case "Standard:MusicVideo":
                return latestDateOfAllItemsAndAssets(contentItemJson)
            case "Standard:Episode":
                return latestDateOfAllItemsAndAssetsAndPlaylistsWithAssets(contentItemJson)
            case "Standard:Movie":
                return latestDateOfAllItemsAndAssetsAndPlaylistsWithAssets(contentItemJson)
            default:
                return null
        }
    }

    private static ZonedDateTime latestDateOfAllItemsAndAssets(DocumentContext contentItemJson) {
        def dateFieldReferences = [
                '$.mtvi:lastModified',
                '$.mtvi:publishedTo',
                '$.VideoAssetRefs[*].mtvi:lastModified',
                '$.VideoAssetRefs[*].mtvi:publishedTo',
                '$.TranscriptAssetRefs[*].mtvi:lastModified',
                '$.TranscriptAssetRefs[*].mtvi:publishedTo',
                '$.AudioAssetRefs[*].mtvi:lastModified',
                '$.AudioAssetRefs[*].mtvi:publishedTo'
        ]

        return findLatestModifiedDate(dateFieldReferences, contentItemJson)
    }

    private static ZonedDateTime latestDateOfAllItemsAndAssetsAndPlaylistsWithAssets(DocumentContext contentItemJson) {
        def dateFieldReferences = [
                '$.mtvi:lastModified',
                '$.mtvi:publishedTo',
                '$.VideoAssetRefs[*].mtvi:lastModified',
                '$.VideoAssetRefs[*].mtvi:publishedTo',
                '$.TranscriptAssetRefs[*].mtvi:lastModified',
                '$.TranscriptAssetRefs[*].mtvi:publishedTo',
                '$.AudioAssetRefs[*].mtvi:lastModified',
                '$.AudioAssetRefs[*].mtvi:publishedTo',
                '$.?VideoPlaylists[*].mtvi:lastModified',
                '$.?VideoPlaylists[*].mtvi:publishedTo',
                '$.?VideoPlaylists[*].Videos[*].VideoAssetRefs[*].mtvi:lastModified',
                '$.?VideoPlaylists[*].Videos[*].VideoAssetRefs[*].mtvi:publishedTo',
                '$.?VideoPlaylists[*].Videos[*].TranscriptAssetRefs[*].mtvi:lastModified',
                '$.?VideoPlaylists[*].Videos[*].TranscriptAssetRefs[*].mtvi:publishedTo',
                '$.?VideoPlaylists[*].Videos[*].AudioAssetRefs[*].mtvi:lastModified',
                '$.?VideoPlaylists[*].Videos[*].AudioAssetRefs[*].mtvi:publishedTo'
        ]

        return findLatestModifiedDate(dateFieldReferences, contentItemJson)
    }

    private static ZonedDateTime findLatestModifiedDate(dateFieldReferences, contentItemJson) {
        def allDates = dateFieldReferences.collect { fieldReference -> contentItemJson.read(fieldReference) }.flatten()
        def maxDate = null
        for (def stringDate : allDates) {
            def date = getDate(stringDate as Map<String, ?>)
            if (date != null) {
                if (maxDate == null || date.isAfter(maxDate)) {
                    maxDate = date
                }

            }
        }
        return maxDate
    }

    private static ZonedDateTime getDate(Map<String, ?> dateField) {
        if (dateField == null) {
            return null
        }
        val:
            String date = dateField["\$date"]
            return ArcDateUtil.parseArcDate(date)
    }

    private static def contentDuration(DocumentContext contentItemJson) {
        def videoDuration = contentItemJson.read('$.VideoDuration')
        if (videoDuration != null) {
            return parseDurationIntoSeconds(videoDuration)
        }
        def playlist = selectContentDurationPlaylist(contentItemJson)
        if (playlist != null) {
            def playlistJson = jsonPath(playlist)
            return contentDurationFromPlaylist(playlistJson)
        }
        return null
    }

    private static def selectContentDurationPlaylist(DocumentContext contentItemJson) {
        def playlistWithTypeNameFullEpisodeSegmented = contentItemJson.read('$.?VideoPlaylists[?(@.VideoPlaylistType.TypeName == \'Full Episode (segmented)\')]')[0]
        if (playlistWithTypeNameFullEpisodeSegmented != null) {
            return playlistWithTypeNameFullEpisodeSegmented
        }
        def playlistWithTypeNameFullEpisodeUnsegmented = contentItemJson.read('$.?VideoPlaylists[?(@.VideoPlaylistType.TypeName == \'Full Episode (unsegmented)\')]')[0]
        if (playlistWithTypeNameFullEpisodeUnsegmented != null) {
            return playlistWithTypeNameFullEpisodeUnsegmented
        }
        return contentItemJson.read('$.?VideoPlaylists[0]')
    }

    private static def contentDurationFromPlaylist(DocumentContext playlistJson) {
        def duration = playlistJson.read('$.Duration')
        if (duration != null) {
            return parseDurationIntoSeconds(duration)
        }
        def videoDurations = playlistJson.read('$.Videos[*].VideoDuration')
        if (videoDurations != null) {
            def totalInMilliseconds = 0
            for (def videoDuration : videoDurations) {
                totalInMilliseconds += parseDurationIntoMilliseconds(videoDuration)
            }
            return (totalInMilliseconds / 1000).longValue()
        }
        return null
    }

    private static def parseDurationIntoSeconds(String duration) {
        def parsedDuration = DURATION_FORMATTER.parse(duration)
        long total = 0
        if (parsedDuration.isSupported(ChronoField.HOUR_OF_DAY)) {
            total += parsedDuration.getLong(ChronoField.HOUR_OF_DAY) * 3600
        }
        if (parsedDuration.isSupported(ChronoField.MINUTE_OF_HOUR)) {
            total += parsedDuration.getLong(ChronoField.MINUTE_OF_HOUR) * 60
        }
        if (parsedDuration.isSupported(ChronoField.SECOND_OF_MINUTE)) {
            total += parsedDuration.getLong(ChronoField.SECOND_OF_MINUTE)
        }
        return total
    }

    private static def parseDurationIntoMilliseconds(String duration) {
        def parsedDuration = DURATION_FORMATTER.parse(duration)
        long total = 0
        if (parsedDuration.isSupported(ChronoField.HOUR_OF_DAY)) {
            total += parsedDuration.getLong(ChronoField.HOUR_OF_DAY) * 3600 * 1000
        }
        if (parsedDuration.isSupported(ChronoField.MINUTE_OF_HOUR)) {
            total += parsedDuration.getLong(ChronoField.MINUTE_OF_HOUR) * 60 * 1000
        }
        if (parsedDuration.isSupported(ChronoField.SECOND_OF_MINUTE)) {
            total += parsedDuration.getLong(ChronoField.SECOND_OF_MINUTE) * 1000
        }
        if (parsedDuration.isSupported(ChronoField.MILLI_OF_SECOND)) {
            total += parsedDuration.getLong(ChronoField.MILLI_OF_SECOND)
        }
        return total
    }

    private static def fwCaid(DocumentContext contentItemJson) {
        return contentItemJson.read('$.mtvi:id')
    }

    private String cuepoints(DocumentContext contentItemJson) {
        def contentType = contentItemJson.read('$.mtvi:contentType')
        if (contentType == "Standard:ShowVideo" || contentType == "Standard:MusicVideo") {
            return cuepointsShowAndMusicVideo(contentItemJson)
        } else if (contentType == "Standard:Episode" || contentType == "Standard:Movie") {
            return cuepointsEpisodeAndMovie(contentItemJson)
        }
        return "0"
    }

    private String cuepointsShowAndMusicVideo(DocumentContext contentItemJson) {
        def adCues = contentItemJson.read('$.AdCues')
        if (adCues == null) {
            return "0"
        }
        def inpoints = contentItemJson.read('$.AdCues[*].Inpoint')
        if (inpoints instanceof JSONArray) {
            return inpoints.collect { parseDurationIntoMilliseconds(it) }
                    .collect { toCuepointFormat(it) }
                    .join(',')
        }
        return toCuepointFormat(parseDurationIntoMilliseconds(inpoints))
    }

    private String cuepointsEpisodeAndMovie(DocumentContext contentItemJson) {
        def videoPlaylists = selectContentDurationPlaylist(contentItemJson)
        if (videoPlaylists == null) {
            return "0"
        }
        def durationList = jsonPath(videoPlaylists).read('$.Videos[*].VideoDuration')
                .collect { parseDurationIntoMilliseconds(it) }
        return calculateCuepoints(durationList)
                .collect { toCuepointFormat(it) }
                .join(',')
    }

    private ArrayList<Long> calculateCuepoints(List<Long> durationList) {
        if (durationList == null || durationList.isEmpty() || durationList.size() == 1) {
            return [0]
        }
        def result = [durationList[0]]
        for (def index = 1; index < durationList.size() - 1; index++) {
            result[index] = result[index - 1] + durationList[index]
        }
        result
    }

    private String toCuepointFormat(Long cuepoint) {
        return CUEPOINTS_FORMAT.format(cuepoint / 1000.0)
    }

    private static def jsonPath(Object object) {
        return JsonPath.parse(JsonOutput.toJson(object), JSON_PATH_CONFIGURATION)
    }

    private static String closedCaptionUrl(DocumentContext documentContext, String mgid) {
        def language = language(documentContext)
        if (language?.trim()) {
            return "https://${INGEST_DOMAIN}/topaz/api/${mgid}/vtt?language=${language}"
        }
    }

    private static String language(DocumentContext contentItemJson) {
        switch (contentItemJson.read('$.mtvi:contentType')) {
            case "Standard:ShowVideo":
                return languageFromTranscriptAssetRefs(contentItemJson)
            case "Standard:MusicVideo":
                return languageFromTranscriptAssetRefs(contentItemJson)
            case "Standard:Episode":
                return languageFromVideoTranscriptAssetRefs(contentItemJson)
            case "Standard:Movie":
                return languageFromFirstPlaylistVideoTranscriptAssetRefs(contentItemJson)
            default:
                return null
        }
    }

    private static String languageFromTranscriptAssetRefs(DocumentContext documentContext) {
        def languageTypeNameField = '$.TranscriptAssetRefs[*].LanguageTag.TypeName'
        def languageFieldName = '$.TranscriptAssetRefs[*].Language'
        List<String> typeName = documentContext.read(languageTypeNameField)
        if (!typeName?.isEmpty()) {
            return typeName.first()
        }
        List<String> language = documentContext.read(languageFieldName)
        return language?.isEmpty() ? null : language.first()
    }

    private static String languageFromVideoTranscriptAssetRefs(DocumentContext documentContext) {
        def playlists = documentContext.read('$.?VideoPlaylists')
        for (def playlist : playlists) {
            def videos = playlist.get("Videos")
            def lang = languageFromFirstVideosWithAvailableLang(videos)
            if (StringUtils.isNotEmpty(lang)) {
                return lang
            }

        }
        return null
    }

    private static String languageFromFirstPlaylistVideoTranscriptAssetRefs(DocumentContext documentContext) {
        def playlist = documentContext.read('$.?VideoPlaylists[0]')
        def videos = playlist?.get("Videos")
        def lang = languageFromFirstVideosWithAvailableLang(videos)
        if (StringUtils.isNotEmpty(lang)) {
            return lang
        }
        return null
    }

    private static String languageFromFirstVideosWithAvailableLang(videos) {
        for (def video : videos) {
            def transcriptRefs = video.get("TranscriptAssetRefs")
            for (def transcriptRef : transcriptRefs) {
                String languageTypeName = transcriptRef?.get("LanguageTag")?.get("TypeName")
                String language = transcriptRef?.get("Language")
                if (StringUtils.isNotEmpty(languageTypeName)) {
                    return languageTypeName
                } else if (StringUtils.isNotEmpty(language)) {
                    return language
                }
            }
        }
        return null
    }
}
