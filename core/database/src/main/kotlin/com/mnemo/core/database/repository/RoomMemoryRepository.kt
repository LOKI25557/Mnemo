package com.mnemo.core.database.repository

import com.mnemo.core.database.MnemoDatabase
import com.mnemo.core.database.dao.MemoryDao
import com.mnemo.core.database.entity.MemoryEntity
import com.mnemo.core.model.DataSourceType
import com.mnemo.core.model.MemoryItem
import com.mnemo.core.model.repository.MemoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform

/**
 * Concrete implementation of [MemoryRepository] backed by local Room database.
 * Thread-safe and concurrency-aware: all operations are coordinated through [MnemoDatabase.withOperation]
 * to prevent concurrent execution during secure wipe / data erasure.
 */
class RoomMemoryRepository(
    private val memoryDao: MemoryDao
) : MemoryRepository {

    override suspend fun insert(item: MemoryItem) {
        MnemoDatabase.withOperation {
            memoryDao.insert(MemoryEntity.fromDomainModel(item))
        }
    }

    override suspend fun insertAll(items: List<MemoryItem>) {
        MnemoDatabase.withOperation {
            memoryDao.insertAll(items.map { MemoryEntity.fromDomainModel(it) })
        }
    }

    override suspend fun search(query: String, limit: Int): List<MemoryItem> {
        return MnemoDatabase.withOperation {
            memoryDao.searchSimple(query, limit).map { it.toDomainModel() }
        }
    }

    override suspend fun getById(id: String): MemoryItem? {
        return MnemoDatabase.withOperation {
            memoryDao.getById(id)?.toDomainModel()
        }
    }

    override suspend fun getBySource(sourceType: DataSourceType, limit: Int): List<MemoryItem> {
        return MnemoDatabase.withOperation {
            memoryDao.getBySource(sourceType.name, limit).map { it.toDomainModel() }
        }
    }

    override suspend fun deleteById(id: String): Boolean {
        return MnemoDatabase.withOperation {
            memoryDao.deleteById(id) > 0
        }
    }

    override suspend fun deleteBySource(sourceType: DataSourceType): Int {
        return MnemoDatabase.withOperation {
            memoryDao.deleteBySource(sourceType.name)
        }
    }

    override suspend fun deleteAll() {
        MnemoDatabase.withOperation {
            memoryDao.deleteAll()
        }
    }

    override suspend fun count(): Int {
        return MnemoDatabase.withOperation {
            memoryDao.count()
        }
    }

    override fun observeCount(): Flow<Int> {
        return memoryDao.observeCount().transform { count ->
            if (MnemoDatabase.isWiping()) {
                throw IllegalStateException("Database operation rejected: secure wipe in progress")
            }
            emit(count)
        }
    }
}
