import it.justwrote.kjob.InMem
import it.justwrote.kjob.Job
import it.justwrote.kjob.job.JobExecutionType
import it.justwrote.kjob.kjob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

// You can define as many properties as you like
// Currently the following data types are supported:
// - Integer by integer()
// - Double by double()
// - Long by long()
// - Boolean by bool()
// - String by string()
// - List<Integer> by integerList()
// - List<Double> by doubleList()
// - List<Long> by longList()
// - List<Boolean> by boolList()
// - List<String> by stringList()
object MySecondJob : Job("my-second-job") {
    val output = string("output").nullable()
}

suspend fun suspendFun() {
    // do some work
}

fun main() = runBlocking {
    // We start an in-memory kjob instance by overriding pre defined configuration values
    // For production please use e.g. Mongo
    val kjob = kjob(InMem) {
        maxRetries = 10
    }.start()

    kjob.register(MySecondJob) {
        executionType = JobExecutionType.NON_BLOCKING // Other type is JobExecutionType.BLOCKING
        maxRetries = 3 // Default is the one defined in the configuration
        execute {
            // We can access our defined properties here
            println(props[it.output])
            // And since we are in a suspend block we can even call suspend functions!
            suspendFun()
        }.onError {
            // We can also handle errors here when our code in 'execute' throws an exception
            println("Oh no! Our code for $jobName with id $jobId failed us with the exception $error")
        }.onComplete {
            // Also we can define a block that gets executed when our execution has been finished
            println("Everything for job $jobName with id $jobId worked out as planed!")
            println("The execution took ${time().toNanos()}ns")
        }
    }

    // Now we can schedule our code and set the properties we defined
    kjob.schedule(MySecondJob) {
        // By default a uuid is used as job id. You can define your own. This id must be unique
        // Scheduling a job with the same jobId will result in an exception
        // This helps preventing starting the execution for the same task twice
        jobId = "MyOwnBusinessId"
        // Now let's set our property
        props[it.output] = "Hello World"
    }

    kjob.schedule(MySecondJob)
    kjob.schedule(MySecondJob) {
        props[it.output] = "Bye!"
    }

    // That's it.
    // Console output: (the order of the messages might vary!)
    //
    // | Hello World
    // | Everything for job my-second-job with id MyOwnBusinessId worked out as planed!
    // | The execution took 532000ns
    // | null
    // | Everything for job my-second-job with id abedebd7-3966-4c0e-8890-e55e4c364676 worked out as planed!
    // | The execution took 200000ns
    // | Bye!
    // | Everything for job my-second-job with id b19b608f-2582-42b0-acf6-55dfa5217e1d worked out as planed!
    // | The execution took 179000ns


    delay(3300) // This is just to prevent a premature shutdown

    // After using our service we need to shut it down. Usually you would do
    // that when your application is shutting down
    kjob.shutdown()
}