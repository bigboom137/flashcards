package com.flashcards

import com.flashcards.data.Settings
import com.flashcards.data.Store
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Reproduces what the Share over Wi-Fi switch does when it is flipped on. */
class SharingToggleTest {
    private fun viewModel(): AppViewModel {
        val dir = Files.createTempDirectory("flashcards-sharing").toFile()
        return AppViewModel(
            store = Store(File(dir, "data.json")),
            prefs = Settings(File(dir, "settings.properties")),
        )
    }

    @Test
    fun flippingTheSwitchStartsSharing() {
        val vm = viewModel()
        vm.shareOverWifi(true)
        try {
            println("sharing=${vm.sharing} code=${vm.syncCode} address=${vm.syncAddress} activity=${vm.syncActivity}")
            assertTrue("switch reported ${vm.sharing}, activity=${vm.syncActivity}", vm.sharing)
            assertTrue(vm.syncCode.length == 6)
            assertNotNull(vm.syncAddress)
        } finally {
            vm.shareOverWifi(false)
        }
    }
}
