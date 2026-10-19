package com.example.appopt.data.repository

import com.example.appopt.AuthenticatorApp
import com.example.appopt.data.cloud.CloudVaultSyncManager
import com.example.appopt.data.cloud.GoogleDriveManager
import com.example.appopt.data.cloud.SharedServicesCloudClient
import com.example.appopt.data.local.AccountDao
import com.example.appopt.data.local.AccountEntity
import com.example.appopt.domain.model.AccountWithCode
import com.example.appopt.domain.model.OtpAlgorithm
import com.example.appopt.domain.model.OtpType
import com.example.appopt.domain.model.ParsedAccountPreview
import com.example.appopt.domain.model.TotpAccount
import com.example.appopt.domain.model.TransferQrChunk
import com.example.appopt.domain.repository.AccountRepository
import com.example.appopt.domain.totp.OtpUriParser
import com.example.appopt.domain.totp.TotpEngine
import com.example.appopt.security.CryptoManager
import com.example.appopt.security.SecurityConfig
import com.example.appopt.security.TransferCrypto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Implementación del repositorio de cuentas 2FA con descifrado bajo demanda y zeroización en RAM.
 *
 * @param accountDao DAO de acceso a la base de datos local Room.
 * @param cryptoManager Gestor criptográfico AES-256-GCM respaldado por Android Keystore.
 */
class AccountRepositoryImpl(
    private val accountDao: AccountDao,
    private val cryptoManager: CryptoManager
) : AccountRepository {

    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val accountsFlow: SharedFlow<List<TotpAccount>> = accountDao.getAllAccounts()
        .map { list -> list.map { it.toDomain() } }
        .shareIn(repositoryScope, SharingStarted.Eagerly, replay = 1)

    private val deletedAccountsFlow: SharedFlow<List<TotpAccount>> = accountDao.getDeletedAccounts()
        .map { list -> list.map { it.toDomain() } }
        .shareIn(repositoryScope, SharingStarted.Eagerly, replay = 1)

    private data class CachedOtp(val step: Long, val counter: Long, val code: String)

    private val otpCodeCache = ConcurrentHashMap<String, CachedOtp>()

    init {
        repositoryScope.launch {
            accountsFlow.collect { accounts ->
                if (accounts.isNotEmpty()) computeAccountsWithCodes(accounts, System.currentTimeMillis())
            }
        }
        repositoryScope.launch { purgeExpiredTrash() }
    }

    /** Limpia de forma segura la caché de códigos OTP y archivos en memoria RAM al bloquear la bóveda. */
    override fun clearMemoryCache() {
        otpCodeCache.clear()
        GoogleDriveManager.clearDownloadCache()
    }

    /** Obtiene la lista de cuentas como modelos de dominio sin exponer secretos (replay=1). */
    override fun getAccounts(): Flow<List<TotpAccount>> = accountsFlow

    /** Obtiene el flujo reactivo de cuentas en la papelera de reciclaje temporal. */
    override fun getDeletedAccounts(): Flow<List<TotpAccount>> = deletedAccountsFlow

    /**
     * Calcula sincrónicamente los códigos OTP para una lista de cuentas en memoria en [currentTimeMillis],
     * utilizando una caché de pasos de tiempo (RFC 6238) en segundo plano.
     */
    override suspend fun computeAccountsWithCodes(
        accounts: List<TotpAccount>,
        currentTimeMillis: Long
    ): List<AccountWithCode> = withContext(Dispatchers.Default) {
        val needsDecryption = accounts.any { domainAccount ->
            val cached = otpCodeCache[domainAccount.id]
            if (domainAccount.type == OtpType.TOTP) {
                cached == null || cached.step != (currentTimeMillis / 1000L / domainAccount.period)
            } else {
                cached == null || cached.counter != domainAccount.counter
            }
        }
        val entitiesMap = if (needsDecryption) {
            accountDao.getAllAccountsSync().associateBy { it.id }
        } else {
            emptyMap()
        }
        accounts.map { domainAccount ->
            val step = if (domainAccount.type == OtpType.TOTP) currentTimeMillis / 1000L / domainAccount.period else 0L
            val cached = otpCodeCache[domainAccount.id]
            val isCacheHit = if (domainAccount.type == OtpType.TOTP) {
                cached != null && cached.step == step
            } else {
                cached != null && cached.counter == domainAccount.counter
            }
            val code = if (isCacheHit && cached != null) {
                cached.code
            } else {
                entitiesMap[domainAccount.id]?.let {
                    decryptAndGenerateCode(it, domainAccount, currentTimeMillis, step)
                } ?: "------"
            }
            AccountWithCode(account = domainAccount, code = code)
        }
    }

    private fun decryptAndGenerateCode(
        entity: AccountEntity,
        domainAccount: TotpAccount,
        currentTimeMillis: Long,
        step: Long
    ): String {
        var secretBytes: ByteArray? = null
        return try {
            secretBytes = cryptoManager.decrypt(entity.encryptedSecret, entity.iv)
            val computed = if (domainAccount.type == OtpType.TOTP) {
                TotpEngine.generateTotp(
                    secretBytes = secretBytes,
                    timeMillis = currentTimeMillis,
                    periodSeconds = domainAccount.period,
                    digits = domainAccount.digits,
                    algorithm = domainAccount.algorithm
                )
            } else {
                TotpEngine.generateHotp(
                    secretBytes = secretBytes,
                    counter = domainAccount.counter,
                    digits = domainAccount.digits,
                    algorithm = domainAccount.algorithm
                )
            }
            otpCodeCache[domainAccount.id] = CachedOtp(step, domainAccount.counter, computed)
            computed
        } catch (_: Exception) {
            "------"
        } finally {
            secretBytes?.let { CryptoManager.zeroize(it) }
        }
    }

    /** Obtiene una cuenta por su identificador único. */
    override suspend fun getAccountById(id: String): TotpAccount? = accountDao.getAccountById(id)?.toDomain()

    /** Cifra el secreto en memoria mediante AES-256-GCM y almacena la nueva cuenta en la base de datos local. */
    override suspend fun saveAccount(
        issuer: String,
        accountName: String,
        secretBytes: ByteArray,
        algorithm: OtpAlgorithm,
        digits: Int,
        period: Int,
        type: OtpType,
        counter: Long,
        isFavorite: Boolean
    ): TotpAccount {
        val payload = cryptoManager.encrypt(secretBytes)
        val now = System.currentTimeMillis()
        val entity = AccountEntity(
            id = UUID.randomUUID().toString(),
            issuer = issuer.trim(),
            accountName = accountName.trim(),
            encryptedSecret = payload.ciphertext,
            iv = payload.iv,
            algorithm = algorithm.name,
            digits = digits,
            period = period,
            type = type.name,
            counter = counter,
            isFavorite = isFavorite,
            createdAt = now,
            updatedAt = now
        )
        accountDao.insertAccount(entity)
        CloudVaultSyncManager.triggerReactiveSync(AuthenticatorApp.instance)
        return entity.toDomain()
    }

    /** Actualiza el nombre del emisor/servicio y cuenta/usuario de una cuenta existente. */
    override suspend fun updateAccount(id: String, issuer: String, accountName: String) {
        otpCodeCache.remove(id)
        accountDao.updateMetadata(id, issuer.trim(), accountName.trim(), System.currentTimeMillis())
        CloudVaultSyncManager.triggerReactiveSync(AuthenticatorApp.instance)
    }

    /** Alterna el estado de favorito de una cuenta. */
    override suspend fun toggleFavorite(id: String) {
        val account = accountDao.getAccountById(id) ?: return
        accountDao.updateAccount(account.copy(isFavorite = !account.isFavorite, updatedAt = System.currentTimeMillis()))
        CloudVaultSyncManager.triggerReactiveSync(AuthenticatorApp.instance)
    }

    /** Actualiza el orden secuencial de una lista de cuentas persistiendo los nuevos índices. */
    override suspend fun reorderAccounts(orderedIds: List<String>) {
        accountDao.updateAccountsOrder(orderedIds)
    }

    /** Elimina una cuenta trasladándola a la papelera de reciclaje temporal (30 días). */
    override suspend fun deleteAccount(id: String) {
        moveToTrash(id)
    }

    /** Traslada una cuenta a la papelera temporal por 30 días y revoca en la nube permisos compartidos asociados. */
    override suspend fun moveToTrash(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            otpCodeCache.remove(id)
            accountDao.moveToTrash(id, System.currentTimeMillis())
            val app = AuthenticatorApp.instance
            CloudVaultSyncManager.triggerReactiveSync(app)
            SharedServicesCloudClient.revokeAllGrantsForAccountsIfPossible(app, setOf(id))
        }
    }

    /** Restaura una cuenta desde la papelera de reciclaje a la bóveda activa. */
    override suspend fun restoreFromTrash(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            accountDao.restoreFromTrash(id, System.currentTimeMillis())
            CloudVaultSyncManager.triggerReactiveSync(AuthenticatorApp.instance)
        }
    }

    /** Elimina permanentemente una cuenta de la base de datos y revoca permisos compartidos remanentes. */
    override suspend fun permanentlyDelete(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            otpCodeCache.remove(id)
            accountDao.deleteAccountById(id)
            val app = AuthenticatorApp.instance
            CloudVaultSyncManager.triggerReactiveSync(app)
            SharedServicesCloudClient.revokeAllGrantsForAccountsIfPossible(app, setOf(id))
        }
    }

    /** Vacía completamente la papelera de reciclaje y revoca permisos compartidos asociados. */
    override suspend fun emptyTrash(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val deletedIds = accountDao.getDeletedAccounts().first().map { it.id }.toSet()
            val count = accountDao.emptyTrash()
            if (count > 0) {
                val app = AuthenticatorApp.instance
                CloudVaultSyncManager.triggerReactiveSync(app)
                SharedServicesCloudClient.revokeAllGrantsForAccountsIfPossible(app, deletedIds)
            }
            count
        }
    }

    /** Purga automáticamente las cuentas cuya estancia en papelera supere los 30 días. */
    override suspend fun purgeExpiredTrash(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            accountDao.purgeExpiredTrash(System.currentTimeMillis() - SecurityConfig.TRASH_RETENTION_MILLIS)
        }
    }

    /** Incrementa el contador de un token HOTP. */
    override suspend fun incrementHotpCounter(id: String) {
        otpCodeCache.remove(id)
        val account = accountDao.getAccountById(id) ?: return
        accountDao.updateAccount(account.copy(counter = account.counter + 1, updatedAt = System.currentTimeMillis()))
        CloudVaultSyncManager.triggerReactiveSync(AuthenticatorApp.instance)
    }

    /** Exporta las cuentas de la bóveda para migración y transferencia por código QR o copia en la nube. */
    override suspend fun exportAccountsForTransfer(selectedAccountIds: Set<String>?, pin: CharArray?): String {
        val allEntities = accountDao.getAllAccounts().first()
        val entities = if (selectedAccountIds != null) allEntities.filter { it.id in selectedAccountIds } else allEntities
        return AccountBackupSerializer.serializeAccountsForTransfer(entities, cryptoManager, pin)
    }

    /** Exporta las cuentas seleccionadas bajo el esquema criptográfico "Todo o Nada" (v2) en lotes multi-QR. */
    override suspend fun exportAccountsInBatches(
        selectedAccountIds: Set<String>?,
        pin: CharArray,
        batchSize: Int
    ): List<String> {
        val allEntities = accountDao.getAllAccounts().first()
        val entities = if (selectedAccountIds != null) allEntities.filter { it.id in selectedAccountIds } else allEntities
        return AccountBackupSerializer.serializeAndEncryptChunks(entities, cryptoManager, pin)
    }

    /** Importa una o múltiples cuentas a partir de los datos escaneados de un código QR de transferencia. */
    override suspend fun importAccountsFromTransfer(transferPayload: String, pin: CharArray?): Result<Int> {
        val trimmed = transferPayload.trim()
        val effectivePayload = if (trimmed.startsWith(TransferCrypto.QR_TRANSFER_PREFIX, ignoreCase = true)) {
            if (pin == null) {
                return Result.failure(TransferCrypto.InvalidPinException("Se requiere PIN para descifrar esta transferencia"))
            }
            val decryptResult = TransferCrypto.decryptTransferPayload(trimmed, pin)
            if (decryptResult.isFailure) {
                return Result.failure(decryptResult.exceptionOrNull() ?: TransferCrypto.InvalidPinException())
            }
            decryptResult.getOrThrow()
        } else {
            trimmed
        }

        val effectiveTrimmed = effectivePayload.trim()
        if (effectiveTrimmed.startsWith("otpauth://", ignoreCase = true)) {
            val parseResult = OtpUriParser.parse(effectiveTrimmed)
            if (parseResult.isFailure) {
                return Result.failure(parseResult.exceptionOrNull() ?: Exception("QR OTP no válido"))
            }
            val data = parseResult.getOrThrow()
            return withContext(Dispatchers.IO) {
                runCatching {
                    AccountMergeEngine.mergeSingleAccount(
                        issuer = data.issuer,
                        accountName = data.accountName,
                        secretBytes = data.secretBytes,
                        algorithm = data.algorithm,
                        digits = data.digits,
                        period = data.period,
                        type = data.type,
                        counter = data.counter,
                        accountDao = accountDao,
                        cryptoManager = cryptoManager,
                        onInvalidateCache = { id -> otpCodeCache.remove(id) }
                    ).also { CloudVaultSyncManager.triggerReactiveSync(AuthenticatorApp.instance) }
                }
            }
        }

        return withContext(Dispatchers.IO) {
            AccountMergeEngine.mergeRemoteBackup(
                remoteBackupJson = effectiveTrimmed,
                accountDao = accountDao,
                cryptoManager = cryptoManager,
                onInvalidateCache = { id -> otpCodeCache.remove(id) }
            ).onSuccess { CloudVaultSyncManager.triggerReactiveSync(AuthenticatorApp.instance) }
        }
    }

    /** Importa y fusiona cuentas a partir de un conjunto completo de fragmentos "Todo o Nada" (v2). */
    override suspend fun importAccountsFromChunks(chunks: List<TransferQrChunk>, pin: CharArray): Result<Int> = withContext(Dispatchers.IO) {
        val decryptResult = TransferCrypto.decryptAssembledChunks(chunks, pin)
        if (decryptResult.isFailure) return@withContext Result.failure(decryptResult.exceptionOrNull() ?: TransferCrypto.InvalidPinException())
        AccountMergeEngine.mergeRemoteBackup(
            remoteBackupJson = decryptResult.getOrThrow().trim(),
            accountDao = accountDao,
            cryptoManager = cryptoManager,
            onInvalidateCache = { id -> otpCodeCache.remove(id) }
        ).onSuccess { CloudVaultSyncManager.triggerReactiveSync(AuthenticatorApp.instance) }
    }

    /** Fusiona de forma no destructiva las cuentas provenientes de una copia remota de Google Drive. */
    override suspend fun mergeAccountsFromRemote(remoteBackupJson: String): Result<Int> = withContext(Dispatchers.IO) {
        AccountMergeEngine.mergeRemoteBackup(remoteBackupJson, accountDao, cryptoManager) { otpCodeCache.remove(it) }
    }

    /** Parsea un payload JSON estructurado para previsualizar las cuentas antes de su importación. */
    override suspend fun parseAccountsForPreview(jsonString: String): List<ParsedAccountPreview> = withContext(Dispatchers.IO) {
        AccountMergeEngine.parseAccountsForPreview(jsonString, accountDao, cryptoManager)
    }

    /** Importa y fusiona en un solo lote ($O(M)$) una lista seleccionada de cuentas previsualizadas. */
    override suspend fun importSelectedAccounts(accounts: List<ParsedAccountPreview>): Int = withContext(Dispatchers.IO) {
        AccountMergeEngine.mergeSelectedAccounts(accounts, accountDao, cryptoManager) { otpCodeCache.remove(it) }.also { changesCount ->
            if (changesCount > 0) CloudVaultSyncManager.triggerReactiveSync(AuthenticatorApp.instance)
        }
    }

    private fun AccountEntity.toDomain(): TotpAccount = TotpAccount(
        id = id, issuer = issuer, accountName = accountName, type = OtpType.fromString(type),
        algorithm = OtpAlgorithm.fromString(algorithm), digits = digits, period = period, counter = counter,
        isFavorite = isFavorite, orderIndex = orderIndex, isDeleted = isDeleted, deletedAt = deletedAt,
        createdAt = createdAt, updatedAt = updatedAt
    )
}
