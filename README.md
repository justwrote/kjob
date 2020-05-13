# kjob

[![Bintray](https://img.shields.io/badge/dynamic/json.svg?label=latest%20release&url=https%3A%2F%2Fapi.bintray.com%2F%2Fpackages%2Fjustwrote%2Fmaven%2Fkjob-core%2Fversions%2F_latest&query=name&colorB=328998&style=flat)](https://bintray.com/justwrote/maven/kjob-core)
![GitHub Build Status](https://img.shields.io/github/workflow/status/justwrote/kjob/CI/master?style=flat)

A coroutine based persistent background scheduler written in Kotlin.

## Features

* Persist scheduled jobs (e.g. mongoDB)
* Nice DSL for registering and scheduling jobs
* Multiple instances possible
* Failed jobs will be rescheduled
* Other instances will restart the work of a crashed one
* Pool for blocking and non-blocking jobs
* Pool size can be defined to go easy on resources

## Installation

Just add the following lines to your `build.gradle`. For `<version>` see the button above or [releases/latest](https://github.com/justwrote/kjob/releases/latest) for the current version number.

```groovy
repositories {
  jcenter()
}

dependencies {
  implementation "it.justwrote:kjob-core:<version>"
  implementation "it.justwrote:kjob-mongo:<version>" // for mongoDB persistence
}
```

## Using kjob

```kotlin
import it.justwrote.kjob.Mongo
import it.justwrote.kjob.Job
import it.justwrote.kjob.job.JobExecutionType
import it.justwrote.kjob.kjob

object OrderCreatedEmail : Job("order-created-email") {
    val recipient = string("recipient")
}

// ...

// start kjob with mongoDB persistence and default configuration
val kjob = kjob(Mongo).start()

// ...

kjob.register(OrderCreatedEmail) {
    executionType = JobExecutionType.NON_BLOCKING // our fake email client is non blocking
    maxRetries = 3
    execute {
        val to = props[it.recipient] // getting address from customer
        client.sendTo(to, subject, body)
    }.onError {
        // errors will automatically logged but we might want to do some metrics or something 
    }
}

// ...

kjob.schedule(OrderCreatedEmail) {
    props[it.recipient] = "customer@example.com"
}
```

For more details please take a look at the [examples](https://github.com/justwrote/kjob/blob/master/kjob-example/src/main/kotlin)

## Starting kjob

Multiple schedulers are running in the background after starting kjob. There is one looking for new jobs every second 
(period can be defined in the configuration). If a job has been found that has not yet been started (or reset after an error)
and the kjob instance is currently not executing too many other jobs of the same kind (there are blocking and non-blocking jobs)
kjob will process it. The second scheduler is handling the locking. It indirectly tells the other kjob instances that 
this one is still alive. The last scheduler is cleaning up locked jobs of other not responding kjob instances to make the jobs
available again for execution.

## Multiple kjob instances

To be fault tolerant you sometimes want to have multiple instances of your job processor. This might be in the same app or on 
different nodes. Therefore, every kjob instances has a unique id which will be added to the job it is currently executing.
This actually locks a job to a specific kjob instance. If the kjob instance is somehow dying while executing a job another 
kjob instance will remove the lock after a specific time (which can be defined in the configuration) and will pick it up again. 

## Changing Configuration

Changing the config is fairly easy. There is not another config file and everything will be done in code - so you can use
your own configuration.

```kotlin
kjob(InMem) {
    nonBlockingMaxJobs = 10 // how many non-blocking jobs will be executed at max in parallel per instance
    blockingMaxJobs = 3 // same for blocking jobs
    maxRetries = 5 // how often will a job be retried until it fails
    defaultJobExecutor = JobExecutionType.BLOCKING // default job execution type
        
    exceptionHandler = { t: Throwable -> logger.error("Unhandled exception", t) } // default error handler for coroutines
    keepAliveExecutionPeriodInSeconds = 60 // the time between 'I am alive' notifications
    jobExecutionPeriodInSeconds = 1 // the time between new job executions
    cleanupPeriodInSeconds = 300 // the time between job clean ups
    cleanupSize = 50 // the amount of jobs that will be cleaned up per schedule
}.start()
```

## MongoDB Configuration

Despite the configuration above there are some mongoDB specific settings that will be shown next.

```kotlin
kjob(Mongo) {
    // all the config above plus those:
    connectionString = "mongodb://localhost" // the mongoDB specific connection string 
    client = null // if a client is specified the 'connectionString' will be ignored
    databaseName = "kjob" // the database where the collections below will be created
    jobCollection = "kjob-jobs" // the collection for all jobs
    lockCollection = "kjob-locks" // the collection for the locking
    expireLockInMinutes = 5L // using the TTL feature of mongoDB to expire a lock
}.start()
```

## Roadmap

Here is an unordered list of features that I would like to see in kjob. If you 
consider one of them important please open an issue.

- Priority support
- Cron features
- Backoff algorithm for failed jobs
- REST API
- Dashboard

## License

kjob is licensed under the [Apache 2.0 License](https://github.com/justwrote/kjob/blob/master/LICENSE).