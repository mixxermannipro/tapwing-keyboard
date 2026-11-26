// SPDX-License-Identifier: LicenseRef-Tapwing-Source-Available

package com.tapwing.keyboard.ai

import com.tapwing.keyboard.dictionary.OfflineDictionaryPackManager

data class OpenSourceNotice(
    val name: String,
    val license: String,
    val copyrightOrAuthor: String,
    val url: String,
    val note: String = ""
)

/**
 * Central catalog of bundled third-party libraries and separately downloaded on-device models /
 * dictionaries with their upstream licenses, attribution notices, and links.
 */
object OpenSourceLicensesCatalog {

    val libraries: List<OpenSourceNotice> = listOf(
        OpenSourceNotice(
            name = "AndroidX & Jetpack Compose",
            license = "Apache-2.0",
            copyrightOrAuthor = "The Android Open Source Project",
            url = "https://developer.android.com/jetpack",
            note = "Core-KTX, Lifecycle, Activity-Compose, DataStore, Autofill, CameraX, Material 3"
        ),
        OpenSourceNotice(
            name = "Kotlin Standard Library & Coroutines",
            license = "Apache-2.0",
            copyrightOrAuthor = "JetBrains s.r.o. and Kotlin Contributors",
            url = "https://github.com/JetBrains/kotlinx.coroutines"
        ),
        OpenSourceNotice(
            name = "Sherpa-ONNX (k2-fsa)",
            license = "Apache-2.0",
            copyrightOrAuthor = "Xiaomi Corp. / k2-fsa contributors",
            url = "https://github.com/k2-fsa/sherpa-onnx",
            note = "Includes statically linked ONNX Runtime (MIT License, Microsoft Corporation)"
        ),
        OpenSourceNotice(
            name = "Llamatik & llama.cpp",
            license = "MIT / Apache-2.0",
            copyrightOrAuthor = "Llamatik & Georgi Gerganov / llama.cpp contributors",
            url = "https://github.com/ggerganov/llama.cpp"
        ),
        OpenSourceNotice(
            name = "OkHttp & Okio",
            license = "Apache-2.0",
            copyrightOrAuthor = "Square, Inc.",
            url = "https://square.github.io/okhttp/"
        ),
        OpenSourceNotice(
            name = "Apache Commons Compress",
            license = "Apache-2.0",
            copyrightOrAuthor = "The Apache Software Foundation",
            url = "https://commons.apache.org/proper/commons-compress/"
        ),
        OpenSourceNotice(
            name = "Google Play Services ML Kit (Text Recognition & GenAI)",
            license = "Android Software Development Kit License / Google APIs Terms",
            copyrightOrAuthor = "Google LLC",
            url = "https://developers.google.com/ml-kit/terms"
        )
    )

    val downloadableModels: List<OpenSourceNotice> by lazy {
        LocalModelCatalog.allModels.map { spec ->
            OpenSourceNotice(
                name = spec.displayName,
                license = spec.licenseName,
                copyrightOrAuthor = spec.attributionNotice,
                url = spec.licenseUrl.ifBlank { spec.repoUrl },
                note = spec.commercialUseNote
            )
        }
    }

    val dictionaryPacks: List<OpenSourceNotice> by lazy {
        OfflineDictionaryPackManager.availablePacks.map { pack ->
            OpenSourceNotice(
                name = "FrequencyWords — ${pack.displayName}",
                license = pack.licenseName,
                copyrightOrAuthor = "Hermit Dave / OpenSubtitles FrequencyWords",
                url = pack.licenseUrl
            )
        }
    }
}
