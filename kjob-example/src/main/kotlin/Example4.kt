import it.justwrote.kjob.InMem
import it.justwrote.kjob.Job
import it.justwrote.kjob.kjob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

object MyFirstDelayedJob : Job("my-first-delayed-job") {
    val index = integer("index")
}

@ExperimentalTime
fun main() = runBlocking {
    val kjob = kjob(InMem).start()

    kjob.register(MyFirstDelayedJob) {
        execute {
            println("[${props[it.index]}] Hello delayed World!")
        }
    }

    kjob.schedule(MyFirstDelayedJob, 4.seconds) {
        props[it.index] = 1
    }
    kjob.schedule(MyFirstDelayedJob, 2.seconds) {
        props[it.index] = 2
    }
    kjob.schedule(MyFirstDelayedJob, 1.seconds) {
        props[it.index] = 3
    }
    kjob.schedule(MyFirstDelayedJob, 3.seconds) {
        props[it.index] = 4
    }

    // The job will print 'Hello delayed World' in 2 seconds on the console

    delay(5000) // This is just to prevent a premature shutdown

    kjob.shutdown()
}