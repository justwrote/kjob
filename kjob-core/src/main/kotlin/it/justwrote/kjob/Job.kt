package it.justwrote.kjob

interface BaseJob {
    val name: String
}

abstract class KronJob(override val name: String, val cronExpression: String): BaseJob

abstract class Job(override val name: String): BaseJob {
    protected fun <J : Job> J.integer(name: String): Prop<J, Int> = Prop(name)
    protected fun <J : Job> J.double(name: String): Prop<J, Double> = Prop(name)
    protected fun <J : Job> J.long(name: String): Prop<J, Long> = Prop(name)
    protected fun <J : Job> J.bool(name: String): Prop<J, Boolean> = Prop(name)
    protected fun <J : Job> J.string(name: String): Prop<J, String> = Prop(name)

    protected fun <J : Job> J.integerList(name: String): Prop<J, List<Int>> = Prop(name)
    protected fun <J : Job> J.doubleList(name: String): Prop<J, List<Double>> = Prop(name)
    protected fun <J : Job> J.longList(name: String): Prop<J, List<Long>> = Prop(name)
    protected fun <J : Job> J.boolList(name: String): Prop<J, List<Boolean>> = Prop(name)
    protected fun <J : Job> J.stringList(name: String): Prop<J, List<String>> = Prop(name)


    protected fun <J : Job, T : Any> Prop<J, T>.nullable(): Prop<J, T?> = Prop(name)
}

data class Prop<J : Job, T> internal constructor(val name: String)