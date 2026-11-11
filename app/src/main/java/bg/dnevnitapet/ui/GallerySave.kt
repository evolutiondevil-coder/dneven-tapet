package bg.dnevnitapet.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import bg.dnevnitapet.model.StoredImage

/**
 * "Save to gallery" action. On Android 7–9 it first asks for the storage permission
 * (Android 10+ needs none) and then saves.
 */
@Composable
fun rememberGallerySave(vm: MainViewModel): (StoredImage) -> Unit {
    var pending by remember { mutableStateOf<StoredImage?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val image = pending
        pending = null
        when {
            granted && image != null -> vm.save(image)
            !granted -> vm.hint("Без разрешение за снимките не мога да запазя в галерията")
        }
    }
    return { image ->
        if (vm.galleryNeedsPermission()) {
            pending = image
            launcher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            vm.save(image)
        }
    }
}
