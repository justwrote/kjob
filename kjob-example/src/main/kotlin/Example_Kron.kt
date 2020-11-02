import it.justwrote.kjob.InMem
import it.justwrote.kjob.KronJob
import it.justwrote.kjob.kjob
import it.justwrote.kjob.kron.Kron
import it.justwrote.kjob.kron.KronModule
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.time.Instant

object PrintStuff : KronJob("print-stuff", "* * * ? * * *")
object PrintMoreStuff : KronJob("print-more-stuff", "*/10 * * ? * * *")

fun main() = runBlocking {
    val kjob = kjob(InMem) {
        extension(KronModule)
    }.start()

    kjob(Kron).kron(PrintStuff) {
        maxRetries = 3
        execute {
            println("${Instant.now()}: executing kron task '${it.name}' with jobId '$jobId'")
        }
    }

    kjob(Kron).kron(PrintMoreStuff) {
        execute {
            println("${Instant.now()}: executing kron task '${it.name}' with jobId '$jobId'")
        }
    }

    delay(25000) // This is just to prevent a premature shutdown
    kjob.shutdown()
}
