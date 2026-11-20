package io.github.joako601.finanzas.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Lo que Android le entrega a TalkBack: la raíz de los nodos de accesibilidad de la ventana
 * activa. Es distinto del árbol de pruebas de Compose, que muestra también nodos que nunca
 * llegan al lector de pantalla.
 */
fun raizDeAccesibilidad(regla: ComposeTestRule): AccessibilityNodeInfo {
    regla.waitForIdle()
    val automatizacion = InstrumentationRegistry.getInstrumentation().uiAutomation
    automatizacion.serviceInfo = automatizacion.serviceInfo.apply {
        flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
    }
    var raiz: AccessibilityNodeInfo? = null
    repeat(50) { if (raiz == null) raiz = automatizacion.rootInActiveWindow ?: null.also { Thread.sleep(100) } }
    return checkNotNull(raiz) { "Sin ventana activa" }
}

/** Todos los nodos debajo de [raiz], incluida ella. */
fun todosLosNodos(raiz: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
    val lista = mutableListOf<AccessibilityNodeInfo>()
    fun visitar(nodo: AccessibilityNodeInfo) {
        lista += nodo
        for (i in 0 until nodo.childCount) nodo.getChild(i)?.let(::visitar)
    }
    visitar(raiz)
    return lista
}
