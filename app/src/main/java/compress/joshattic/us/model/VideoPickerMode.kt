package compress.joshattic.us.model

enum class VideoPickerMode {
    /** Android system photo picker (no storage permission needed). */
    PHOTO_PICKER,

    /** Any installed app that can open videos (MT Manager, Files, gallery, ...). */
    GALLERY_APP,

    /**
     * In-app file browser backed by the "All files access" permission, so no
     * system picker or HyperOS secure-access dialog is involved.
     */
    FILE_BROWSER
}
