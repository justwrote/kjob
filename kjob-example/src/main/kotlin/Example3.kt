import it.justwrote.kjob.Job
import it.justwrote.kjob.KJob
import it.justwrote.kjob.Mongo
import it.justwrote.kjob.job.JobExecutionType
import it.justwrote.kjob.kjob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

interface EmailClient {
    suspend fun sendTo(to: String, subject: String, body: String): Unit
}

class PrintlnEmailClient : EmailClient {
    override suspend fun sendTo(to: String, subject: String, body: String) {
        println("sending email to '$to' with subject: $subject")
    }
}

object OrderCreatedEmail : Job("order-created-email") {
    val recipient = string("recipient")
    val orderId = string("orderId")
}

class EmailToCustomer(private val kjob: KJob, private val client: EmailClient) {

    init {
        kjob.register(OrderCreatedEmail) {
            executionType = JobExecutionType.NON_BLOCKING // our email client is non blocking
            maxRetries = 3
            execute {
                val orderId = props[it.orderId]
                val subject = "Order confirmation $orderId"
                val body = "..."
                val to = props[it.recipient] // getting address from customer
                client.sendTo(to, subject, body)
            }.onError {
                // errors will automatically logged but we might want to do some metrics or something
            }
        }
    }

    suspend fun scheduleEmailToCustomer(recipient: String, orderId: String) {
        kjob.schedule(OrderCreatedEmail) {
            jobId = orderId // prevent the same 'job' scheduled twice.
            props[it.orderId] = orderId
            props[it.recipient] = recipient
        }
    }
}

fun main() {
    // start kjob with mongoDB persistence and default configuration
    val kjob = kjob(Mongo).start()

    try {
        runBlocking {
            val client: EmailClient = PrintlnEmailClient()

            val emailToCustomer = EmailToCustomer(kjob, client)
            emailToCustomer.scheduleEmailToCustomer("customer@example.com", "4711")

            delay(1100) // This is just to prevent a premature shutdown
        }
    } finally {
        kjob.shutdown()
    }
}
