package com.oplus.media;

import android.content.Context;
import android.media.DecoderCapabilities;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import libcore.content.type.MimeMap;

/** Compatibility implementation of the stock {@code com.oplus.media.MediaFile} API.
 * Extension/type tables and predicates replicate the stock behavior so that
 * clients (e.g. gallery file-type tracking and media-type checks) keep working
 * on builds without the stock oplus-framework. */
public class MediaFile {
    public static final int FILE_TYPE_3GPP = 0x17;
    public static final int FILE_TYPE_3GPP2 = 0x18;
    public static final int FILE_TYPE_AAC = 0x8;
    public static final int FILE_TYPE_AMR = 0x4;
    public static final int FILE_TYPE_APE = 0x3e9;
    public static final int FILE_TYPE_APK = 0x271b;
    public static final int FILE_TYPE_ARW = 0x130;
    public static final int FILE_TYPE_ASF = 0x1a;
    public static final int FILE_TYPE_AUDIO_3GPP = 0xb;
    public static final int FILE_TYPE_AVI = 0x1d;
    public static final int FILE_TYPE_AWB = 0x5;
    public static final int FILE_TYPE_BMP = 0x22;
    public static final int FILE_TYPE_CHM = 0x2725;
    public static final int FILE_TYPE_CR2 = 0x12d;
    public static final int FILE_TYPE_CSV = 0x2726;
    public static final int FILE_TYPE_CUE = 0x3eb;
    public static final int FILE_TYPE_DNG = 0x12c;
    public static final int FILE_TYPE_EBK = 0x272a;
    public static final int FILE_TYPE_EPUB = 0x272b;
    public static final int FILE_TYPE_FL = 0x33;
    public static final int FILE_TYPE_FLAC = 0xa;
    public static final int FILE_TYPE_FLV = 0x44d;
    public static final int FILE_TYPE_GIF = 0x20;
    public static final int FILE_TYPE_HEIF = 0x25;
    public static final int FILE_TYPE_HTML = 0x65;
    public static final int FILE_TYPE_HTTPLIVE = 0x2c;
    public static final int FILE_TYPE_ICS = 0x2727;
    public static final int FILE_TYPE_IMY = 0xd;
    public static final int FILE_TYPE_JAR = 0x2712;
    public static final int FILE_TYPE_JPEG = 0x1f;
    public static final int FILE_TYPE_M2TS = 0x450;
    public static final int FILE_TYPE_M3U = 0x29;
    public static final int FILE_TYPE_M4A = 0x2;
    public static final int FILE_TYPE_M4V = 0x16;
    public static final int FILE_TYPE_MID = 0xb;
    public static final int FILE_TYPE_MKA = 0x9;
    public static final int FILE_TYPE_MKV = 0x1b;
    public static final int FILE_TYPE_MOV = 0x44f;
    public static final int FILE_TYPE_MP2 = 0x3ea;
    public static final int FILE_TYPE_MP2PS = 0xc8;
    public static final int FILE_TYPE_MP2TS = 0x1c;
    public static final int FILE_TYPE_MP3 = 0x1;
    public static final int FILE_TYPE_MP4 = 0x15;
    public static final int FILE_TYPE_MS_EXCEL = 0x69;
    public static final int FILE_TYPE_MS_POWERPOINT = 0x6a;
    public static final int FILE_TYPE_MS_WORD = 0x68;
    public static final int FILE_TYPE_NEF = 0x12e;
    public static final int FILE_TYPE_NRW = 0x12f;
    public static final int FILE_TYPE_OGG = 0x7;
    public static final int FILE_TYPE_ORF = 0x132;
    public static final int FILE_TYPE_PDF = 0x66;
    public static final int FILE_TYPE_PEF = 0x134;
    public static final int FILE_TYPE_PLS = 0x2a;
    public static final int FILE_TYPE_PNG = 0x21;
    public static final int FILE_TYPE_QT = 0xc9;
    public static final int FILE_TYPE_RA = 0x3ec;
    public static final int FILE_TYPE_RAF = 0x133;
    public static final int FILE_TYPE_RAR = 0x2711;
    public static final int FILE_TYPE_RV = 0x44e;
    public static final int FILE_TYPE_RW2 = 0x131;
    public static final int FILE_TYPE_SMF = 0xc;
    public static final int FILE_TYPE_SRW = 0x135;
    public static final int FILE_TYPE_TEXT = 0x64;
    public static final int FILE_TYPE_VCF = 0x2728;
    public static final int FILE_TYPE_VCS = 0x2729;
    public static final int FILE_TYPE_WAV = 0x3;
    public static final int FILE_TYPE_WBMP = 0x23;
    public static final int FILE_TYPE_WEBM = 0x1e;
    public static final int FILE_TYPE_WEBP = 0x24;
    public static final int FILE_TYPE_WMA = 0x6;
    public static final int FILE_TYPE_WMV = 0x19;
    public static final int FILE_TYPE_WPL = 0x2b;
    public static final int FILE_TYPE_XML = 0x67;
    public static final int FILE_TYPE_ZIP = 0x6b;

    private static final int FIRST_APK_FILE_TYPE = 0x271b;
    private static final int LAST_APK_FILE_TYPE = 0x271b;
    private static final int FIRST_AUDIO_FILE_TYPE = 0x1;
    private static final int LAST_AUDIO_FILE_TYPE = 0xb;
    private static final int FIRST_COMPRESS_FILE_TYPE = 0x2711;
    private static final int LAST_COMPRESS_FILE_TYPE = 0x2712;
    private static final int FIRST_DOC_FILE_TYPE = 0x2725;
    private static final int LAST_DOC_FILE_TYPE = 0x272b;
    private static final int FIRST_POPULAR_DOC_FILE_TYPE = 0x64;
    private static final int LAST_POPULAR_DOC_FILE_TYPE = 0x6a;
    private static final int FIRST_DRM_FILE_TYPE = 0x33;
    private static final int LAST_DRM_FILE_TYPE = 0x33;
    private static final int FIRST_FFMPEG_AUDIO_FILE_TYPE = 0x3e9;
    private static final int LAST_FFMPEG_AUDIO_FILE_TYPE = 0x3ec;
    private static final int FIRST_FFMPEG_VIDEO_FILE_TYPE = 0x44d;
    private static final int LAST_FFMPEG_VIDEO_FILE_TYPE = 0x450;
    private static final int FIRST_IMAGE_FILE_TYPE = 0x1f;
    private static final int LAST_IMAGE_FILE_TYPE = 0x25;
    private static final int FIRST_RAW_IMAGE_FILE_TYPE = 0x12c;
    private static final int LAST_RAW_IMAGE_FILE_TYPE = 0x135;
    private static final int FIRST_MIDI_FILE_TYPE = 0xb;
    private static final int LAST_MIDI_FILE_TYPE = 0xd;
    private static final int FIRST_PLAYLIST_FILE_TYPE = 0x29;
    private static final int LAST_PLAYLIST_FILE_TYPE = 0x2c;
    private static final int FIRST_VIDEO_FILE_TYPE = 0x15;
    private static final int LAST_VIDEO_FILE_TYPE = 0x1e;
    private static final int FIRST_VIDEO_FILE_TYPE2 = 0xc8;
    private static final int LAST_VIDEO_FILE_TYPE2 = 0xc9;

    private static final HashMap<String, MediaFileType> sFileTypeMap = new HashMap<>();
    private static final HashMap<String, Integer> sMimeTypeMap = new HashMap<>();
    private static final HashMap<String, Integer> sFileTypeToFormatMap = new HashMap<>();
    private static final HashMap<String, Integer> sDeprecatedMimeTypeToFormatMap = new HashMap<>();
    private static final HashMap<Integer, String> sDeprecatedFormatToMimeTypeMap = new HashMap<>();
    private static final HashMap<String, Integer> sMimeTypeToFormatMap = new HashMap<>();
    private static final HashMap<Integer, String> sFormatToMimeTypeMap = new HashMap<>();

    public static class MediaFileType {
        public final int fileType;
        public final String mimeType;
        MediaFileType(int fileType, String mimeType) {
            this.fileType = fileType;
            this.mimeType = mimeType;
        }
    }

    static {
        addFileAndMineType("MP3", 0x1, "audio/mpeg", 0x3009, true);
        addFileAndMineType("MPGA", 0x1, "audio/mpeg", 0x3009, false);
        addFileAndMineType("M4A", 0x2, "audio/mp4", 0x300b, false);
        addFileAndMineType("WAV", 0x3, "audio/x-wav", 0x3008, true);
        addFileAndMineType("OGG", 0x7, "audio/ogg", 0xb902, false);
        addFileAndMineType("OGG", 0x7, "application/ogg", 0xb902, true);
        addFileAndMineType("OGA", 0x7, "application/ogg", 0xb902, false);
        addFileAndMineType("AAC", 0x8, "audio/aac", 0xb903, true);
        addFileAndMineType("AAC", 0x8, "audio/aac-adts", 0xb903, false);
        addFileAndMineType("MPEG", 0x15, "video/mpeg", 0x300b, true);
        addFileAndMineType("MPG", 0x15, "video/mpeg", 0x300b, false);
        addFileAndMineType("MP4", 0x15, "video/mp4", 0x300b, false);
        addFileAndMineType("M4V", 0x16, "video/mp4", 0x300b, false);
        addFileAndMineType("MOV", 0xc9, "video/quicktime", 0x300b, false);
        addFileAndMineType("3GP", 0x17, "video/3gpp", 0xb984, true);
        addFileAndMineType("3GPP", 0x17, "video/3gpp", 0xb984, false);
        addFileAndMineType("3G2", 0x18, "video/3gpp2", 0xb984, false);
        addFileAndMineType("3GPP2", 0x18, "video/3gpp2", 0xb984, false);
        addFileAndMineType("JPG", 0x1f, "image/jpeg", 0x3801, true);
        addFileAndMineType("JPEG", 0x1f, "image/jpeg", 0x3801, false);
        addFileAndMineType("GIF", 0x20, "image/gif", 0x3807, true);
        addFileAndMineType("PNG", 0x21, "image/png", 0x380b, true);
        addFileAndMineType("BMP", 0x22, "image/x-ms-bmp", 0x3804, true);
        addFileAndMineType("BMP", 0x22, "image/bmp", 0x3804, false);
        addFileAndMineType("WBMP", 0x23, "image/vnd.wap.wbmp", 0x3800, false);
        addFileAndMineType("WEBP", 0x24, "image/webp", 0x3800, false);
        addFileAndMineType("HEIC", 0x25, "image/heif", 0x3812, true);
        addFileAndMineType("HEIF", 0x25, "image/heif", 0x3812, false);
        addFileAndMineType("DNG", 0x12c, "image/x-adobe-dng", 0x3811, true);
        addFileAndMineType("CR2", 0x12d, "image/x-canon-cr2", 0x380d, false);
        addFileAndMineType("NEF", 0x12e, "image/x-nikon-nef", 0x3802, false);
        addFileAndMineType("NRW", 0x12f, "image/x-nikon-nrw", 0x380d, false);
        addFileAndMineType("ARW", 0x130, "image/x-sony-arw", 0x380d, false);
        addFileAndMineType("RW2", 0x131, "image/x-panasonic-rw2", 0x380d, false);
        addFileAndMineType("ORF", 0x132, "image/x-olympus-orf", 0x380d, false);
        addFileAndMineType("RAF", 0x133, "image/x-fuji-raf", 0x3800, false);
        addFileAndMineType("PEF", 0x134, "image/x-pentax-pef", 0x380d, false);
        addFileAndMineType("SRW", 0x135, "image/x-samsung-srw", 0x380d, false);
        addFileAndMineType("M3U", 0x29, "audio/x-mpegurl", 0xba11, true);
        addFileAndMineType("M3U", 0x29, "application/x-mpegurl", 0xba11, false);
        addFileAndMineType("PLS", 0x2a, "audio/x-scpls", 0xba14, true);
        addFileAndMineType("WPL", 0x2b, "application/vnd.ms-wpl", 0xba10, true);
        addFileAndMineType("TXT", 0x64, "text/plain", 0x3004, true);
        addFileAndMineType("HTM", 0x65, "text/html", 0x3005, true);
        addFileAndMineType("HTML", 0x65, "text/html", 0x3005, false);
        addFileAndMineType("DOC", 0x68, "application/msword", 0xba83, true);
        addFileAndMineType("XLS", 0x69, "application/vnd.ms-excel", 0xba85, true);
        addFileAndMineType("PPT", 0x6a, "application/mspowerpoint", 0xba86, true);
        addFileAndMineType("FLAC", 0xa, "audio/flac", 0xb906, true);
        addFileAndMineType("AMR", 0x4, "audio/amr");
        addFileAndMineType("3GPP", 0xb, "audio/3gpp");
        addFileAndMineType("AWB", 0x5, "audio/amr-wb");
        addFileAndMineType("MKA", 0x9, "audio/x-matroska");
        addFileAndMineType("MID", 0xb, "audio/mid");
        addFileAndMineType("MID", 0xb, "audio/midi");
        addFileAndMineType("MIDI", 0xb, "audio/midi");
        addFileAndMineType("XMF", 0xb, "audio/midi");
        addFileAndMineType("RTTTL", 0xb, "audio/midi");
        addFileAndMineType("SMF", 0xc, "audio/sp-midi");
        addFileAndMineType("IMY", 0xd, "audio/imelody");
        addFileAndMineType("RTX", 0xb, "audio/midi");
        addFileAndMineType("OTA", 0xb, "audio/midi");
        addFileAndMineType("MXMF", 0xb, "audio/midi");
        addFileAndMineType("MKV", 0x1b, "video/x-matroska");
        addFileAndMineType("WEBM", 0x1e, "video/webm");
        addFileAndMineType("TS", 0x1c, "video/mp2ts");
        addFileAndMineType("AVI", 0x1d, "video/avi");
        addFileAndMineType("ASF", 0x1a, "video/x-ms-asf");
        addFileAndMineType("M3U8", 0x2c, "application/vnd.apple.mpegurl");
        addFileAndMineType("M3U8", 0x2c, "audio/mpegurl");
        addFileAndMineType("M3U8", 0x2c, "audio/x-mpegurl");
        addFileAndMineType("FL", 0x33, "application/x-android-drm-fl");
        addFileAndMineType("PDF", 0x66, "application/pdf");
        addFileAndMineType("ZIP", 0x6b, "application/zip");
        addFileAndMineType("MPG", 0xc8, "video/mp2p");
        addFileAndMineType("MPEG", 0xc8, "video/mp2p");
        addFileAndMineType("APE", 0x3e9, "audio/ape");
        addFileAndMineType("MP2", 0x3ea, "audio/mpeg");
        addFileAndMineType("CUE", 0x3eb, "audio/cue");
        addFileAndMineType("FLV", 0x44d, "video/x-flv");
        addFileAndMineType("F4V", 0x44d, "video/x-flv");
        addFileAndMineType("MOV", 0x44f, "video/x-quicktime");
        addFileAndMineType("M2TS", 0x450, "video/m2ts");
        addFileAndMineType("DOCX", 0x68, "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        addFileAndMineType("XLSX", 0x69, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        addFileAndMineType("PPTX", 0x6a, "application/vnd.openxmlformats-officedocument.presentationml.presentation");
        addFileAndMineType("RAR", 0x2711, "application/rar");
        addFileAndMineType("JAR", 0x2712, "application/java-archive");
        addFileAndMineType("APK", 0x271b, "application/vnd.android.package-archive");
        addFileAndMineType("CHM", 0x2725, "application/x-expandedbook");
        addFileAndMineType("CSV", 0x2726, "text/comma-separated-values");
        addFileAndMineType("ICS", 0x2727, "text/calendar");
        addFileAndMineType("VCF", 0x2728, "text/x-vcard");
        addFileAndMineType("VCS", 0x2729, "text/x-vcalendar");
        addFileAndMineType("EBK2", 0x272a, "text/x-expandedbook");
        addFileAndMineType("EBK3", 0x272a, "text/x-expandedbook");
        addFileAndMineType("EPUB", 0x272b, "text/plain");
        addFileType(0x3009, "audio/mpeg");
        addFileType(0x3008, "audio/x-wav");
        addFileType(0xb901, "audio/x-ms-wma");
        addFileType(0xb902, "audio/ogg");
        addFileType(0xb903, "audio/aac");
        addFileType(0xb906, "audio/flac");
        addFileType(0x3007, "audio/x-aiff");
        addFileType(0xb983, "audio/mpeg");
        addFileType(0x300b, "video/mpeg");
        addFileType(0xb982, "video/mp4");
        addFileType(0xb984, "video/3gpp");
        addFileType(0xb984, "video/3gpp2");
        addFileType(0x300a, "video/avi");
        addFileType(0xb981, "video/x-ms-wmv");
        addFileType(0x300c, "video/x-ms-asf");
        addFileType(0x3801, "image/jpeg");
        addFileType(0x3807, "image/gif");
        addFileType(0x380b, "image/png");
        addFileType(0x3804, "image/x-ms-bmp");
        addFileType(0x3812, "image/heif");
        addFileType(0x3811, "image/x-adobe-dng");
        addFileType(0x380d, "image/tiff");
        addFileType(0x380d, "image/x-canon-cr2");
        addFileType(0x380d, "image/x-nikon-nrw");
        addFileType(0x380d, "image/x-sony-arw");
        addFileType(0x380d, "image/x-panasonic-rw2");
        addFileType(0x380d, "image/x-olympus-orf");
        addFileType(0x380d, "image/x-pentax-pef");
        addFileType(0x380d, "image/x-samsung-srw");
        addFileType(0x3802, "image/tiff");
        addFileType(0x3802, "image/x-nikon-nef");
        addFileType(0x380f, "image/jp2");
        addFileType(0x3810, "image/jpx");
        addFileType(0xba11, "audio/x-mpegurl");
        addFileType(0xba14, "audio/x-scpls");
        addFileType(0xba10, "application/vnd.ms-wpl");
        addFileType(0xba13, "video/x-ms-asf");
        addFileType(0x3004, "text/plain");
        addFileType(0x3005, "text/html");
        addFileType(0xba82, "text/xml");
        addFileType(0xba83, "application/msword");
        addFileType(0xba83, "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        addFileType(0xba85, "application/vnd.ms-excel");
        addFileType(0xba85, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        addFileType(0xba86, "application/vnd.ms-powerpoint");
        addFileType(0xba86, "application/vnd.openxmlformats-officedocument.presentationml.presentation");
        if (isWMAEnabled()) {
            addFileAndMineType("WMA", 0x6, "audio/x-ms-wma", 0xb901, true);
        }
        if (isWMVEnabled()) {
            addFileAndMineType("WMV", 0x19, "video/x-ms-wmv", 0xb981, true);
        }
    }

    private static void addFileAndMineType(String extension, int fileType, String mimeType,
            int mtpFormatCode, boolean primaryType) {
        addFileAndMineType(extension, fileType, mimeType);
        sFileTypeToFormatMap.put(extension, mtpFormatCode);
        sDeprecatedMimeTypeToFormatMap.put(mimeType, mtpFormatCode);
        if (primaryType && !sDeprecatedFormatToMimeTypeMap.containsKey(mtpFormatCode)) {
            sDeprecatedFormatToMimeTypeMap.put(mtpFormatCode, mimeType);
        }
    }

    static void addFileAndMineType(String extension, int fileType, String mimeType) {
        sFileTypeMap.put(extension, new MediaFileType(fileType, mimeType));
        sMimeTypeMap.put(mimeType, fileType);
    }

    private static void addFileType(int mtpFormatCode, String mimeType) {
        if (!sMimeTypeToFormatMap.containsKey(mimeType)) {
            sMimeTypeToFormatMap.put(mimeType, mtpFormatCode);
        }
        if (!sFormatToMimeTypeMap.containsKey(mtpFormatCode)) {
            sFormatToMimeTypeMap.put(mtpFormatCode, mimeType);
        }
    }

    public static MediaFileType getFileType(String path) {
        int lastDot = path.lastIndexOf('.');
        if (lastDot < 0) {
            return null;
        }
        String extension = path.substring(lastDot + 1).toUpperCase(Locale.ROOT);
        return sFileTypeMap.get(extension);
    }

    public static int getFileTypeForMimeType(String mimeType) {
        Integer value = sMimeTypeMap.get(mimeType);
        if (value == null) {
            return 0;
        }
        return value.intValue();
    }

    public static String getMimeType(String path, int formatCode) {
        String mimeType = getMimeTypeForFile(path);
        if (!"application/octet-stream".equals(mimeType)) {
            return mimeType;
        }
        return getMimeTypeForFormatCode(formatCode);
    }

    public static String getMimeTypeForFile(String path) {
        String extension = getFileExtension(path);
        String mimeType = guessMimeTypeFromExtension(extension);
        if (mimeType != null) {
            return mimeType;
        }
        return "application/octet-stream";
    }

    public static String getMimeTypeForFormatCode(int formatCode) {
        String mimeType = sFormatToMimeTypeMap.get(formatCode);
        if (mimeType != null) {
            return mimeType;
        }
        return "application/octet-stream";
    }

    public static String getFileExtension(String path) {
        if (path == null) {
            return null;
        }
        int lastDot = path.lastIndexOf('.');
        if (lastDot >= 0) {
            return path.substring(lastDot + 1);
        }
        return null;
    }

    public static String getFileTitle(String path) {
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0) {
            lastSlash++;
            if (lastSlash < path.length()) {
                path = path.substring(lastSlash);
            }
        }
        int lastDot = path.lastIndexOf('.');
        if (lastDot > 0) {
            path = path.substring(0, lastDot);
        }
        return path;
    }

    public static int getFormatCode(String path, String mimeType) {
        int formatCode = getFormatCodeForMimeType(mimeType);
        if (formatCode != 0x3000) {
            return formatCode;
        }
        return getFormatCodeForFile(path);
    }

    public static int getFormatCodeForFile(String path) {
        return getFormatCodeForMimeType(getMimeTypeForFile(path));
    }

    public static int getFormatCodeForMimeType(String mimeType) {
        if (mimeType == null) {
            return 0x3000;
        }
        Integer value = sMimeTypeToFormatMap.get(mimeType);
        if (value != null) {
            return value.intValue();
        }
        mimeType = normalizeMimeType(mimeType);
        value = sMimeTypeToFormatMap.get(mimeType);
        if (value != null) {
            return value.intValue();
        } else if (mimeType.startsWith("audio/")) {
            return 0xb900;
        } else if (mimeType.startsWith("video/")) {
            return 0xb980;
        } else if (mimeType.startsWith("image/")) {
            return 0x3800;
        } else {
            return 0x3000;
        }
    }

    public static android.net.Uri getDefaultAlarmUri(Context context) {
        return getUriFor(context, "oplus_customize_default_alarm");
    }

    public static android.net.Uri getDefaultNotificationUri(Context context) {
        return getUriFor(context, "oplus_customize_default_notification");
    }

    public static android.net.Uri getDefaultNotificationUriSIM2(Context context) {
        return getUriFor(context, "oplus_customize_default_notification_sim2");
    }

    public static android.net.Uri getDefaultRingtoneUri(Context context) {
        return getUriFor(context, "oplus_customize_default_ringtone");
    }

    public static android.net.Uri getDefaultRingtoneUriSIM2(Context context) {
        return getUriFor(context, "oplus_customize_default_ringtone_sim2");
    }

    public static android.net.Uri getDefaultSmsNotificationUri(Context context) {
        return getUriFor(context, "oplus_customize_default_sms_notification_sound");
    }

    private static android.net.Uri getUriFor(Context context, String name) {
        String value = Settings.System.getString(context.getContentResolver(), name);
        if (value != null) {
            return android.net.Uri.parse(value);
        }
        Log.e("MediaFile", name + " not set?!!!");
        return null;
    }

    public static boolean isApkFileType(int fileType) {
        return fileType == FIRST_APK_FILE_TYPE;
    }

    public static boolean isApkMimeType(String mimeType) {
        return normalizeMimeType(mimeType).equals("application/vnd.android.package-archive");
    }

    public static boolean isAudioFileType(int fileType) {
        if (fileType >= 0x1 && fileType <= 0xb) {
            return true;
        }
        if (fileType >= 0xb && fileType <= 0xd) {
            return true;
        }
        return fileType >= FIRST_FFMPEG_AUDIO_FILE_TYPE
                && fileType <= LAST_FFMPEG_AUDIO_FILE_TYPE;
    }

    public static boolean isAudioMimeType(String mimeType) {
        return normalizeMimeType(mimeType).startsWith("audio/");
    }

    public static boolean isCompressFileType(int fileType) {
        if (fileType >= FIRST_COMPRESS_FILE_TYPE && fileType <= LAST_COMPRESS_FILE_TYPE) {
            return true;
        }
        return fileType == FILE_TYPE_ZIP;
    }

    public static boolean isCompressMimeType(String mimeType) {
        String type = normalizeMimeType(mimeType);
        return "application/java-archive".equals(type)
                || "application/zip".equals(type)
                || "application/rar".equals(type);
    }

    public static boolean isDocFileType(int fileType) {
        if (fileType >= FIRST_DOC_FILE_TYPE && fileType <= LAST_DOC_FILE_TYPE) {
            return true;
        }
        return fileType >= FIRST_POPULAR_DOC_FILE_TYPE
                && fileType <= LAST_POPULAR_DOC_FILE_TYPE && fileType != 0x67;
    }

    public static boolean isDocMimeType(String mimeType) {
        String type = normalizeMimeType(mimeType);
        switch (type) {
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet":
            case "chemical/x-chemdraw":
            case "application/msword":
            case "text/plain":
            case "text/x-vcard":
            case "text/x-vcalendar":
            case "application/vnd.ms-excel":
            case "text/calendar":
            case "text/csv":
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document":
            case "application/vnd.ms-powerpoint":
            case "application/vnd.openxmlformats-officedocument.presentationml.presentation":
            case "text/html":
            case "application/pdf":
            case "application/epub+zip":
            case "text/comma-separated-values":
                return true;
            default:
                return false;
        }
    }

    public static boolean isDrmFileType(int fileType) {
        return fileType == FIRST_DRM_FILE_TYPE;
    }

    public static boolean isDrmMimeType(String mimeType) {
        return normalizeMimeType(mimeType).equals("application/x-android-drm-fl");
    }

    public static boolean isExifMimeType(String mimeType) {
        return isImageMimeType(mimeType);
    }

    public static boolean isImageFileType(int fileType) {
        if (fileType >= FIRST_IMAGE_FILE_TYPE && fileType <= LAST_IMAGE_FILE_TYPE) {
            return true;
        }
        return fileType >= FIRST_RAW_IMAGE_FILE_TYPE && fileType <= LAST_RAW_IMAGE_FILE_TYPE;
    }

    public static boolean isImageMimeType(String mimeType) {
        return normalizeMimeType(mimeType).startsWith("image/");
    }

    public static boolean isMediaMimeType(String mimeType) {
        return isAudioMimeType(mimeType) || isVideoMimeType(mimeType)
                || isImageMimeType(mimeType) || isPlayListMimeType(mimeType);
    }

    public static boolean isMimeTypeMedia(String mimeType) {
        int fileType = getFileTypeForMimeType(mimeType);
        return isAudioFileType(fileType) || isVideoFileType(fileType)
                || isImageFileType(fileType) || isPlayListFileType(fileType);
    }

    public static boolean isPlayListFileType(int fileType) {
        return fileType >= FIRST_PLAYLIST_FILE_TYPE && fileType <= LAST_PLAYLIST_FILE_TYPE;
    }

    public static boolean isPlayListMimeType(String mimeType) {
        switch (normalizeMimeType(mimeType)) {
            case "application/vnd.ms-wpl":
            case "audio/x-mpegurl":
            case "audio/mpegurl":
            case "application/vnd.apple.mpegurl":
            case "application/x-mpegurl":
            case "audio/x-scpls":
                return true;
            default:
                return false;
        }
    }

    public static boolean isRawImageFileType(int fileType) {
        return fileType >= FIRST_RAW_IMAGE_FILE_TYPE && fileType <= LAST_RAW_IMAGE_FILE_TYPE;
    }

    public static boolean isRawImageMimeType(String mimeType) {
        switch (normalizeMimeType(mimeType)) {
            case "image/tiff":
            case "image/x-adobe-dng":
            case "image/x-canon-cr2":
            case "image/x-nikon-nef":
            case "image/x-nikon-nrw":
            case "image/x-olympus-orf":
            case "image/x-panasonic-rw2":
            case "image/x-pentax-pef":
            case "image/x-samsung-srw":
            case "image/x-sony-arw":
                return true;
            default:
                return false;
        }
    }

    public static boolean isVideoFileType(int fileType) {
        if (fileType >= FIRST_VIDEO_FILE_TYPE && fileType <= LAST_VIDEO_FILE_TYPE) {
            return true;
        }
        if (fileType >= FIRST_VIDEO_FILE_TYPE2 && fileType <= LAST_VIDEO_FILE_TYPE2) {
            return true;
        }
        return fileType >= FIRST_FFMPEG_VIDEO_FILE_TYPE
                && fileType <= LAST_FFMPEG_VIDEO_FILE_TYPE;
    }

    public static boolean isVideoMimeType(String mimeType) {
        return normalizeMimeType(mimeType).startsWith("video/");
    }

    private static String normalizeMimeType(String mimeType) {
        MimeMap mimeMap = MimeMap.getDefault();
        String extension = mimeMap.guessExtensionFromMimeType(mimeType);
        if (extension != null) {
            String extensionMimeType = mimeMap.guessMimeTypeFromExtension(extension);
            if (extensionMimeType != null) {
                return extensionMimeType;
            }
        }
        return mimeType != null ? mimeType : "application/octet-stream";
    }

    private static String guessMimeTypeFromExtension(String extension) {
        String mimeType = null;
        if (extension != null) {
            mimeType = MimeMap.getDefault().guessMimeTypeFromExtension(extension);
        }
        if (mimeType != null) {
            if ("audio/x-pn-realaudio-plugin".equals(mimeType)
                    || "audio/x-pn-realaudio".equals(mimeType)) {
                return null;
            }
            return mimeType;
        }
        if (extension == null) {
            return null;
        }
        switch (extension.toLowerCase(Locale.ROOT)) {
            case "m2ts":
                return "video/m2ts";
            case "ebk3":
            case "ebk2":
                return "text/x-expandedbook";
            case "f4v":
                return "video/x-flv";
            case "cue":
                return "audio/cue";
            default:
                return null;
        }
    }

    private static boolean isWMAEnabled() {
        List<DecoderCapabilities.AudioDecoder> decoders = DecoderCapabilities.getAudioDecoders();
        for (int i = 0; i < decoders.size(); i++) {
            if (decoders.get(i) == DecoderCapabilities.AudioDecoder.AUDIO_DECODER_WMA) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWMVEnabled() {
        List<DecoderCapabilities.VideoDecoder> decoders = DecoderCapabilities.getVideoDecoders();
        for (int i = 0; i < decoders.size(); i++) {
            if (decoders.get(i) == DecoderCapabilities.VideoDecoder.VIDEO_DECODER_WMV) {
                return true;
            }
        }
        return false;
    }
}
