package com.rupio.app

import com.rupio.app.timer.RestTimerController
import com.rupio.app.timer.restAlertLoop
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** El aviso de fin de descanso suena exactamente una vez por descanso, aunque cambie a medio camino. */
class RestAlertTest {
    private val clock = { System.nanoTime() / 1_000_000 }

    @Test fun suenaUnaSolaVezPorDescanso() = runBlocking {
        val timer = RestTimerController(clock)
        val fired = AtomicInteger()
        val job = launch { restAlertLoop(timer, clock) { fired.incrementAndGet() } }
        delay(50)

        // 1) Descanso normal → 1 aviso
        timer.start(1, "s1", "Serie 2")
        delay(1_300)
        assertEquals(1, fired.get())

        // 2) Marcas, desmarcas y vuelves a marcar (reinicia) + ajustas ±15 s → sigue siendo 1 aviso más
        timer.start(1, "s2", "Serie 3")
        delay(200)
        timer.cancelIfSource("s2")
        timer.start(1, "s2", "Serie 3")
        timer.adjust(15); timer.adjust(-15)
        delay(1_400)
        assertEquals(2, fired.get())

        // 3) Saltar el descanso → no suena
        timer.start(1, "s3", "Serie 4")
        delay(200)
        timer.cancel()
        delay(1_200)
        assertEquals(2, fired.get())

        job.cancelAndJoin()
    }
}
