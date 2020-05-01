import it.justwrote.kjob.InMem
import it.justwrote.kjob.Job
import it.justwrote.kjob.kjob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

// First of all we need to define a job
// The shortest possible job definition looks like this:
object MyFirstJob : Job("my-first-job")


fun main() = runBlocking {
    // This is the shortest possible way to initialize kjob
    // InMem is used in this example which saves all data into memory - NEVER USE IT IN PRODUCTION
    // For production there is 'kjob-mongo'
    // Other persistence implementation may follow (contributions are welcome!)
    val kjob = kjob(InMem).start() // or kjob(Mongo).start()

    // Now we need register our previously defined job
    kjob.register(MyFirstJob) {
        // And define what should be executed when scheduled
        execute {
            // This block contains the code that gets executed
            println("Hello World!")
        }
    }

    // After starting kjob and registering it we can now schedule the job
    kjob.schedule(MyFirstJob)

    // That's it. The job will print 'Hello World' on the console

    delay(2200) // This is just to prevent a premature shutdown

    // After using our service we need to shut it down. Usually you would do
    // that when your application is shutting down
    kjob.shutdown()

    // After a shutdown you cannot use this kjob instance again and need to initialize a new one
}