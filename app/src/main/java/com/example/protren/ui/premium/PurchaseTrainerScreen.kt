@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.protren.ui.premium

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.protren.data.UserPreferences
import com.example.protren.model.Trainer
import com.example.protren.network.ApiClient
import com.example.protren.network.PaymentApi
import com.example.protren.network.TrainerApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PayuOrderResult(
    val ok: Boolean,
    val message: String,
    val redirectUri: String? = null
)

@Composable
fun PurchaseTrainerScreen(
    navController: NavController,
    trainerId: String
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application

    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val vm: PurchaseTrainerViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PurchaseTrainerViewModel(app) as T
            }
        }
    )

    val trainer by vm.trainer.collectAsState()
    val loading by vm.loading.collectAsState()
    val purchasing by vm.purchasing.collectAsState()
    val error by vm.error.collectAsState()

    LaunchedEffect(trainerId) {
        vm.loadTrainer(trainerId)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Wstecz"
                        )
                    }
                },
                title = { Text("Wykup trenera") }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            when {
                loading && trainer == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                error != null && trainer == null -> {
                    Column(
                        Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Nie udało się pobrać danych trenera",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            error ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { vm.loadTrainer(trainerId) }) {
                            Text("Spróbuj ponownie")
                        }
                    }
                }

                trainer != null -> {
                    val t = trainer!!
                    val current = t.traineesCount ?: 0
                    val max = t.maxTrainees ?: 10
                    val isFull = current >= max
                    val price = t.priceMonth ?: 0.0

                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = t.name.ifBlank { "Trener" },
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                t.bio?.takeIf { it.isNotBlank() }?.let { bio ->
                                    Text(
                                        text = bio,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(Modifier.height(8.dp))

                                Text(
                                    text = "Cena miesięczna",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (price > 0.0)
                                        String.format("%.2f PLN / miesiąc", price)
                                    else
                                        "Brak ustawionej ceny",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )

                                Spacer(Modifier.height(8.dp))

                                Text(
                                    text = "Podopieczni: $current / $max",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isFull)
                                        MaterialTheme.colorScheme.error
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (isFull) {
                                    Text(
                                        text = "Ten trener nie ma już wolnych miejsc.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.weight(1f))

                        Button(
                            onClick = {
                                scope.launch {
                                    // dodatkowa blokada po stronie UI
                                    val tLocal = trainer
                                    if (tLocal != null) {
                                        val c = tLocal.traineesCount ?: 0
                                        val m = tLocal.maxTrainees ?: 10
                                        if (c >= m) {
                                            snackbarHost.showSnackbar(
                                                "Ten trener osiągnął maksymalną liczbę podopiecznych."
                                            )
                                            return@launch
                                        }
                                    }

                                    val result = vm.startPayuPurchase(trainerId)
                                    snackbarHost.showSnackbar(result.message)
                                    if (result.ok && !result.redirectUri.isNullOrBlank()) {
                                        try {
                                            val intent = Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse(result.redirectUri)
                                            )
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            snackbarHost.showSnackbar(
                                                "Nie udało się otworzyć strony płatności."
                                            )
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !purchasing && !loading && trainer != null
                        ) {
                            if (purchasing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Inicjowanie płatności…")
                            } else {
                                Text("Przejdź do płatności PayU")
                            }
                        }
                    }
                }

                else -> {
                    // nic
                }
            }
        }
    }
}

class PurchaseTrainerViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = UserPreferences(app)

    private val retrofit by lazy {
        ApiClient.createWithAuth(
            tokenProvider = { prefs.getAccessToken() },
            refreshTokenProvider = { prefs.getRefreshToken() },
            onTokensUpdated = { a, r -> prefs.setTokens(a, r) },
            onUnauthorized = { prefs.clearAll() }
        )
    }

    private val trainerApi by lazy { retrofit.create(TrainerApi::class.java) }
    private val paymentsApi by lazy { retrofit.create(PaymentApi::class.java) }

    private val _trainer = MutableStateFlow<Trainer?>(null)
    val trainer: StateFlow<Trainer?> = _trainer

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _purchasing = MutableStateFlow(false)
    val purchasing: StateFlow<Boolean> = _purchasing

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun loadTrainer(trainerId: String) {
        _loading.value = true
        _error.value = null

        viewModelScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    trainerApi.getTrainer(trainerId)
                }
                if (resp.isSuccessful) {
                    _trainer.value = resp.body()
                } else {
                    _error.value = "Błąd HTTP ${resp.code()}"
                }
            } catch (e: Exception) {
                _error.value = "Błąd sieci podczas pobierania trenera"
            } finally {
                _loading.value = false
            }
        }
    }

    suspend fun startPayuPurchase(trainerId: String): PayuOrderResult {
        _purchasing.value = true
        return try {
            val resp = withContext(Dispatchers.IO) {
                paymentsApi.createTrainerOrder(trainerId)
            }

            if (resp.isSuccessful) {
                val body = resp.body()
                val uri = body?.redirectUri
                if (uri.isNullOrBlank()) {
                    PayuOrderResult(
                        ok = false,
                        message = "Brak adresu płatności z PayU."
                    )
                } else {
                    PayuOrderResult(
                        ok = true,
                        message = "Przekierowuję do płatności PayU…",
                        redirectUri = uri
                    )
                }
            } else {
                if (resp.code() == 400) {
                    PayuOrderResult(
                        ok = false,
                        message = "Ten trener osiągnął maksymalną liczbę podopiecznych."
                    )
                } else {
                    PayuOrderResult(
                        ok = false,
                        message = "Błąd PayU: HTTP ${resp.code()}"
                    )
                }
            }
        } catch (e: Exception) {
            PayuOrderResult(
                ok = false,
                message = "Błąd sieci podczas inicjalizacji płatności."
            )
        } finally {
            _purchasing.value = false
        }
    }
}
