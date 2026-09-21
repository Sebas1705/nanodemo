package es.sebas1705.nanodemo.core.common

/**
 * Reemplaza todo el contenido del backstack de Navigation 3 (una
 * `SnapshotStateList`, que implementa `MutableList`) por un solo elemento —
 * el equivalente de `popUpTo(start) { launchSingleTop; restoreState }` de
 * Navigation 2, para la navegación tipo tab del nav bar/rail inferior.
 */
fun <T> MutableList<T>.pushAndFree(element: T) {
    clear()
    add(element)
}
