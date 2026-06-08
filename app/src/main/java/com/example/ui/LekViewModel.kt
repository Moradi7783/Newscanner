package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

class LekViewModel(application: Application) : AndroidViewModel(application) {

    private val database = LekDatabase.getDatabase(application)
    private val repository = ScannerRepository(application, database)

    val scannedIps: StateFlow<List<ScannedIp>> = repository.scannedIps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteIps: StateFlow<List<ScannedIp>> = repository.favoriteIps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customRanges: StateFlow<List<CustomRange>> = repository.customRanges
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Terminal console states
    private val _terminalLogs = MutableStateFlow<List<String>>(emptyList())
    val terminalLogs: StateFlow<List<String>> = _terminalLogs.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanProgress = MutableStateFlow(0f)
    val scanProgress: StateFlow<Float> = _scanProgress.asStateFlow()

    private val _scannedStats = MutableStateFlow("منظر اقدام اسکن...")
    val scannedStats: StateFlow<String> = _scannedStats.asStateFlow()

    private val _activeOperator = MutableStateFlow("در حال شناسایی...")
    val activeOperator: StateFlow<String> = _activeOperator.asStateFlow()

    // Config Generator States
    val vlessTemplate = MutableStateFlow("vless://937f2a1a-4de1-41db-b53e-2fa189a08f51@CLEAN_IP:443?encryption=none&security=tls&sni=telewebion.com&type=tcp#LekScanner-Node")
    
    private var scanJob: Job? = null

    init {
        detectOperator()
        appendTerminalLog("[SYSTEM] نسخه لک اسکنر v2.8 فعال است.")
        appendTerminalLog("[SYSTEM] سیستم آماده اسکن آی پی های سالم Cloudflare، Akamai، Google و غیره...")
    }

    fun detectOperator() {
        viewModelScope.launch(Dispatchers.Default) {
            val op = repository.getActiveOperatorName()
            _activeOperator.value = op
            appendTerminalLog("[NETWORK] اپراتور شناسایی شده: $op")
        }
    }

    fun appendTerminalLog(message: String) {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val timeStr = sdf.format(Date())
        val formatted = "[$timeStr] $message"
        _terminalLogs.update { (it + formatted).takeLast(100) }
    }

    fun clearTerminalLogs() {
        _terminalLogs.value = emptyList()
    }

    fun startScanning(
        selectedCidrs: List<String>,
        limitPerCidr: Int = 50,
        concurrencyLimit: Int = 10,
        timeoutMs: Int = 1500
    ) {
        if (_isScanning.value) {
            appendTerminalLog("[WARN] اسکن در حال اجراست... ابتدا آن را متوقف کنید.")
            return
        }

        scanJob = viewModelScope.launch(Dispatchers.Default) {
            _isScanning.value = true
            _scanProgress.value = 0f
            
            val operator = _activeOperator.value
            appendTerminalLog("[START] شروع اسکن هوشمند متناسب با شبکه ($operator)...")
            appendTerminalLog("[START] حداکثر سهم هر رنج: $limitPerCidr آی پی تصادفی.")

            // Clear old non-favorite scans to avoid clutter
            repository.clearNonFavorites()

            val allIps = selectedCidrs.flatMap { cidr ->
                appendTerminalLog("[RESOLVING] در حال استخراج و نمونه برداری از رنج: $cidr...")
                repository.expandCidr(cidr, limitPerCidr)
            }.shuffled() // Shuffle to get more random sampling diversity

            val totalIps = allIps.size
            if (totalIps == 0) {
                appendTerminalLog("[ERROR] هیچ آی پی معتبری یافت نشد. رنج ها را بررسی کنید.")
                _isScanning.value = false
                return@launch
            }

            appendTerminalLog("[INFO] در قالب مجموعاً $totalIps آی پی کاندید اسکن می شوند...")
            
            var checkedCount = 0
            var successCount = 0
            val semaphore = Semaphore(concurrencyLimit)

            allIps.map { ip ->
                launch {
                    semaphore.withPermit {
                        if (!_isScanning.value) return@launch
                        
                        val result = repository.testIndividualIp(ip, 443, timeoutMs, operator)
                        checkedCount++
                        
                        _scanProgress.value = checkedCount.toFloat() / totalIps

                        if (result != null) {
                            successCount++
                            appendTerminalLog("[+] اسکن موفق: $ip | پینگ: ${result.rtt}ms | سرعت نسبی: ${String.format("%.1f", result.speedKbList)} KB/s")
                        } else {
                            // Only log details occasionally to avoid flood
                            if (Random.nextInt(5) == 0) {
                                appendTerminalLog("[-] تست ناموفق: $ip -> قطع یا مسدود")
                            }
                        }

                        _scannedStats.value = "تعداد اسکن شده: $checkedCount از $totalIps | آی پی های سالم پیدا شده: $successCount"
                    }
                }
            }.forEach { it.join() }

            _scanProgress.value = 1.0f
            _isScanning.value = false
            appendTerminalLog("[COMPLETED] اسکن با موفقیت به پایان رسید. $successCount آی پی زنده پیدا شد.")
            _scannedStats.value = "پایان اسکن. کل اسکن ها: $checkedCount | آی پی های سالم: $successCount"
        }
    }

    fun stopScanning() {
        if (_isScanning.value) {
            _isScanning.value = false
            scanJob?.cancel()
            appendTerminalLog("[STOP] اسکن توسط کاربر متوقف شد.")
            _scannedStats.value = "اسکن متوقف گردید."
        }
    }

    // Custom Subnet Operations
    fun addCustomRange(cidr: String, label: String) {
        viewModelScope.launch {
            repository.insertCustomRange(cidr, label)
            appendTerminalLog("[DB] رنج دستی ذخیره شد: $cidr ($label)")
        }
    }

    fun deleteCustomRange(range: CustomRange) {
        viewModelScope.launch {
            repository.deleteCustomRange(range)
            appendTerminalLog("[DB] رنج حذف شد: ${range.cidr}")
        }
    }

    // IP Star Operation
    fun toggleFavorite(ip: ScannedIp) {
        viewModelScope.launch {
            repository.saveToFavorites(ip, !ip.isFavorite)
            val actionMsg = if (!ip.isFavorite) "امتیاز با موفقیت ثبت شد" else "حذف از برگزیده ها"
            appendTerminalLog("[FAV] آی پی ${ip.ip} -> $actionMsg")
        }
    }

    fun deleteIp(ip: ScannedIp) {
        viewModelScope.launch {
            repository.deleteScannedIp(ip)
            appendTerminalLog("[DB] آی پی حذف شد: ${ip.ip}")
        }
    }

    fun clearAllIps() {
        viewModelScope.launch {
            repository.clearAll()
            appendTerminalLog("[DB] تمامی آی پی ها از پایگاه داده پاک شدند.")
        }
    }

    fun clearNonFavorites() {
        viewModelScope.launch {
            repository.clearNonFavorites()
            appendTerminalLog("[DB] آی پی ها معمولی پاک شدند. برگزیده ها حفظ شدند.")
        }
    }

    // Predefined Ranges helper
    val providerCidrs: Map<String, List<String>> get() = repository.providerRanges
}
