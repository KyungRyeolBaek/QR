package com.example.qr.repository

import com.example.qr.data.dao.UserDao
import com.example.qr.data.entities.User
import kotlinx.coroutines.flow.Flow
class UserRepository(
    private val userDao: UserDao
) {
    fun getAllActiveUsers(): Flow<List<User>> = userDao.getAllActiveUsers()

    fun getAllUsers(): Flow<List<User>> = userDao.getAllUsers()

    suspend fun getUserById(userId: String): User? = userDao.getUserById(userId)

    suspend fun getUserByQrCode(qrCode: String): User? = userDao.getUserByQrCode(qrCode)

    suspend fun getUserByPhoneNumber(phoneNumber: String): User? = userDao.getUserByPhoneNumber(phoneNumber)

    suspend fun getActiveUserCount(): Int = userDao.getActiveUserCount()

    suspend fun updateSmsStatus(userId: String, status: String) = userDao.updateSmsStatus(userId, status)

    suspend fun updateUserStatus(userId: String, isActive: Boolean) = userDao.updateUserStatus(userId, isActive)

    suspend fun insertUser(user: User): Long = userDao.insertUser(user)

    suspend fun updateUser(user: User) = userDao.updateUser(user)

    suspend fun deleteUser(user: User) = userDao.deleteUser(user)

    suspend fun deactivateUser(userId: String) = updateUserStatus(userId, false)

    suspend fun activateUser(userId: String) = updateUserStatus(userId, true)
}