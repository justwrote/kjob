package it.justwrote.kjob.repository

import it.justwrote.kjob.job.Lock
import java.util.*

interface LockRepository {

    suspend fun ping(id: UUID): Lock

    suspend fun exists(id: UUID): Boolean
}