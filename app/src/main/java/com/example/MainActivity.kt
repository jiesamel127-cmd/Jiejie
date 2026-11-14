package com.example

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.auth.GoogleAuthHelper
import com.example.auth.JiegoAuthConfig
import com.example.data.importer.GoogleSheetsFetcher
import com.example.data.model.MemberOrder
import com.example.data.repository.GroupOrderRepository
import com.example.notifications.LocalNotificationHelper
import com.example.notifications.NotificationSyncTracker
import com.example.notifications.NotificationSyncWorker
import com.example.ui.components.GroupOrderFormDialog
import com.example.ui.components.ImportRekapanDialog
import com.example.ui.components.JiegoContactsDialog
import com.example.ui.components.JiegoRatesCalculatorDialog
import com.example.ui.components.QrisPaymentDialog
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CatalogScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.GroupOrderDetailScreen
import com.example.ui.screens.MyOrdersScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.PhotosScreen
import com.example.ui.theme.KPopGOTheme
import com.example.ui.viewmodel.KPopGoData
import com.example.ui.viewmodel.KPopGoViewModel
import com.example.ui.viewmodel.UiEvent
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

private const val ROUTE_DASHBOARD = "dashboard"
private const val ROUTE_CATALOG = "catalog"
private const val ROUTE_PHOTOS = "photos"
private const val ROUTE_MY_ORDERS = "my_orders"
private const val ROUTE_NOTIFICATIONS = "notifications"
private const val ROUTE_ORDER_DETAIL = "order_detail/{orderId}"

private enum class AppViewMode(val label: String) {
  OWNER("Pemilik"),
  BUYER("Pembeli"),
}

private data class QrisModalRequest(
  val title: String = "QRIS Pembelian JIEGO",
  val subtitle: String? = null,
  val amountToPay: Long? = null,
  val orderNameForPayment: String? = null,
  val memberTarget: MemberOrder? = null,
)

private fun orderDetailRoute(orderId: String) = "order_detail/$orderId"

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
  val listener = FirebaseAuth.AuthStateListener { auth ->
    trySend(auth.currentUser)
  }
  addAuthStateListener(listener)
  awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      KPopGOTheme {
        AppNavigation()
      }
    }
  }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
  val context = LocalContext.current
  val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
  val credentialManager = remember(context) { CredentialManager.create(context) }

  LaunchedEffect(Unit) {
    // Allow first frame SurfaceSyncGroup transaction to settle cleanly before IPC / auto sign-in
    withFrameNanos { }
    withContext(Dispatchers.IO) {
      LocalNotificationHelper.ensureChannel(context.applicationContext)
    }
    if (auth.currentUser == null) {
      GoogleAuthHelper.attemptAutoSignIn(
        context = context,
        credentialManager = credentialManager,
        onAuthSuccess = {},
        onUnauthenticated = {},
        scope = this,
      )
    }
  }

  val user = currentUser
  if (user == null) {
    AuthScreen(onAuthSuccess = {})
  } else {
    key(user.uid) {
      MainAuthenticatedShell(
        currentUserId = user.uid,
        userDisplayName = user.displayName ?: user.email ?: "Pembeli K-Pop GO",
        userEmail = user.email ?: "",
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAuthenticatedShell(
  currentUserId: String,
  userDisplayName: String,
  userEmail: String = "",
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }
  val snackbarHostState = remember { SnackbarHostState() }

  // Only jiego4sho@gmail.com may access Owner mode; all other emails are locked to Buyer mode
  val isOwnerAccount = remember(userEmail) { JiegoAuthConfig.isOwnerEmail(userEmail) }

  val viewModel: KPopGoViewModel = viewModel(
    key = "$currentUserId|$isOwnerAccount",
    factory = viewModelFactory {
      initializer {
        val app = checkNotNull(this[APPLICATION_KEY]) {
          "APPLICATION_KEY missing from CreationExtras"
        }
        val databaseId = app.getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(databaseId)
        KPopGoViewModel(
          repository = GroupOrderRepository(db, Firebase.auth),
          currentUserId = currentUserId,
          isOwnerAccount = isOwnerAccount,
        )
      }
    },
  )

  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

  // Register signed-in user's email & schedule WorkManager background notification check off Main Thread
  LaunchedEffect(currentUserId, userEmail, userDisplayName) {
    withContext(Dispatchers.IO) {
      LocalNotificationHelper.ensureChannel(context.applicationContext)
      NotificationSyncWorker.schedule(context.applicationContext)
    }
    if (userEmail.isNotBlank()) {
      viewModel.registerNotificationSubscriber(email = userEmail, displayName = userDisplayName)
    }
  }

  // Listen for newly arriving remote notifications while app is open & trigger Android bar notification
  LaunchedEffect(uiState, currentUserId) {
    val success = uiState as? UiState.Success<KPopGoData> ?: return@LaunchedEffect
    val unseen = withContext(Dispatchers.IO) {
      NotificationSyncTracker.consumeNewNotifications(
        context = context.applicationContext,
        userId = currentUserId,
        notifications = success.data.notifications,
      )
    }
    if (unseen.isNotEmpty() && LocalNotificationHelper.hasNotificationPermission(context)) {
      if (unseen.size <= 3) {
        unseen.forEach { notif ->
          LocalNotificationHelper.showNotification(
            context = context,
            title = notif.title,
            body = notif.message,
            notificationId = notif.id.hashCode(),
          )
        }
      } else {
        val latest = unseen.first()
        LocalNotificationHelper.showNotification(
          context = context,
          title = "${unseen.size} Pemberitahuan Baru JIEGO",
          body = "${latest.title}: ${latest.message}",
          notificationId = "jiego_summary_${currentUserId.hashCode()}".hashCode(),
        )
      }
    }
  }

  LaunchedEffect(viewModel) {
    viewModel.events.collect { event ->
      when (event) {
        is UiEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
        is UiEvent.TriggerLocalNotification -> {
          LocalNotificationHelper.showNotification(context, event.title, event.body)
        }
      }
    }
  }

  val navController = rememberNavController()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = navBackStackEntry?.destination?.route
  val isDetailScreen = currentRoute?.startsWith("order_detail/") == true

  var showCreateGoDialog by remember { mutableStateOf(false) }
  var showImportRekapanDialog by remember { mutableStateOf(false) }
  var showContactsDialog by remember { mutableStateOf(false) }
  var showRatesDialog by remember { mutableStateOf(false) }
  var qrisModalRequest by remember { mutableStateOf<QrisModalRequest?>(null) }
  val prefs = remember(context) { context.getSharedPreferences("jiego_prefs", Context.MODE_PRIVATE) }
  val viewModeKey = remember(currentUserId) { "view_mode_$currentUserId" }
  var viewMode by remember(currentUserId, isOwnerAccount) {
    val saved = if (isOwnerAccount) prefs.getString(viewModeKey, AppViewMode.OWNER.name) else AppViewMode.BUYER.name
    mutableStateOf(
      if (!isOwnerAccount) AppViewMode.BUYER
      else AppViewMode.entries.firstOrNull { it.name == saved } ?: AppViewMode.OWNER
    )
  }
  val effectiveViewMode = if (isOwnerAccount) viewMode else AppViewMode.BUYER
  val isBuyerMode = !isOwnerAccount || effectiveViewMode == AppViewMode.BUYER

  var savedSheetUrl by remember(currentUserId) {
    mutableStateOf(
      prefs.getString("saved_google_sheet_url", GoogleSheetsFetcher.DEFAULT_JIEGO_SHEET_URL)
        ?.takeIf { it.isNotBlank() }
        ?: GoogleSheetsFetcher.DEFAULT_JIEGO_SHEET_URL
    )
  }
  var autoSyncSheetsEnabled by remember(currentUserId) {
    mutableStateOf(prefs.getBoolean("auto_sync_sheets_enabled", true))
  }

  LaunchedEffect(uiState, currentUserId, isOwnerAccount) {
    if (!isOwnerAccount) return@LaunchedEffect
    val success = uiState as? UiState.Success<KPopGoData> ?: return@LaunchedEffect
    val seedKey = "lngshot_seeded_$currentUserId"
    if (success.data.catalogItems.isEmpty() && !prefs.getBoolean(seedKey, false)) {
      prefs.edit().putBoolean(seedKey, true).apply()
      viewModel.seedLngshotLightstick(silent = true)
    }
  }

  var didAutoHealSession by remember(currentUserId) { mutableStateOf(false) }

  // Automatically check Google Sheets for newly added batches, buyers, items, or status updates when Owner opens app
  LaunchedEffect(currentUserId, isOwnerAccount, isBuyerMode, autoSyncSheetsEnabled, uiState) {
    if (!isOwnerAccount || isBuyerMode) return@LaunchedEffect
    val success = uiState as? UiState.Success<KPopGoData> ?: return@LaunchedEffect
    val hasEmptyImportedGos = success.data.groupOrders.any { go ->
      (go.id.startsWith("imp_go_") || go.notes.startsWith("Diimpor dari rekapan")) &&
        success.data.members.none { it.orderId == go.id }
    }
    val shouldHealNow = hasEmptyImportedGos && !didAutoHealSession
    if (!autoSyncSheetsEnabled && !shouldHealNow) return@LaunchedEffect

    val lastSyncKey = "last_auto_sync_ms_$currentUserId"
    val nowMs = System.currentTimeMillis()
    val lastSyncMs = prefs.getLong(lastSyncKey, 0L)
    if (shouldHealNow || nowMs - lastSyncMs >= 120_000L) {
      if (shouldHealNow) didAutoHealSession = true
      prefs.edit().putLong(lastSyncKey, nowMs).apply()
      viewModel.syncGoogleSheets(sheetUrl = savedSheetUrl, isAutoSync = true)
    }
  }

  if (showCreateGoDialog) {
    GroupOrderFormDialog(
      existingOrder = null,
      onDismiss = { showCreateGoDialog = false },
      onConfirm = { name, artist, batch, closing, deadline, notes ->
        viewModel.createGroupOrder(name, artist, batch, closing, deadline, notes)
        showCreateGoDialog = false
      },
    )
  }

  if (showImportRekapanDialog) {
    ImportRekapanDialog(
      onDismiss = { showImportRekapanDialog = false },
      onConfirmImport = { parsed ->
        viewModel.importRekapanResult(parsed)
        showImportRekapanDialog = false
      },
      initialSheetUrl = savedSheetUrl,
      initialAutoSyncEnabled = autoSyncSheetsEnabled,
      onSaveSheetSettings = { url, autoEnabled ->
        savedSheetUrl = url
        autoSyncSheetsEnabled = autoEnabled
        prefs.edit()
          .putString("saved_google_sheet_url", url)
          .putBoolean("auto_sync_sheets_enabled", autoEnabled)
          .apply()
      },
    )
  }

  if (showContactsDialog) {
    JiegoContactsDialog(onDismiss = { showContactsDialog = false })
  }

  if (qrisModalRequest != null) {
    val req = qrisModalRequest!!
    QrisPaymentDialog(
      title = req.title,
      subtitle = req.subtitle,
      amountToPay = req.amountToPay,
      isBuyerMode = isBuyerMode,
      onMarkPaidViaQris = if (!isBuyerMode && req.memberTarget != null && req.orderNameForPayment != null) {
        {
          val targetMember = req.memberTarget
          viewModel.recordMemberPayment(
            orderName = req.orderNameForPayment,
            member = targetMember,
            newPaidAmount = targetMember.totalCost,
            paymentMethod = "QRIS",
          )
          qrisModalRequest = null
        }
      } else null,
      onDismiss = { qrisModalRequest = null },
    )
  }

  val successData = (uiState as? UiState.Success<KPopGoData>)?.data ?: KPopGoData()
  val badgeCount = remember(successData) {
    successData.unreadNotificationsCount + successData.computeDeadlineAlerts().size
  }

  if (showRatesDialog) {
    JiegoRatesCalculatorDialog(
      rates = successData.exchangeRates,
      isBuyerMode = isBuyerMode,
      onSaveRates = { updated -> viewModel.saveExchangeRates(updated) },
      onResetRates = { viewModel.resetExchangeRatesToDefault() },
      onDismiss = { showRatesDialog = false },
    )
  }

  val navigateTopTab: (String) -> Unit = { route ->
    navController.navigate(route) {
      popUpTo(navController.graph.findStartDestination().id) {
        saveState = true
      }
      launchSingleTop = true
      restoreState = true
    }
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useNavRail = maxWidth >= 700.dp

    Row(modifier = Modifier.fillMaxSize()) {
      if (useNavRail && !isDetailScreen) {
        NavigationRail(
          header = {
            if (!isBuyerMode) {
              FloatingActionButton(
                onClick = { showCreateGoDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                  .padding(top = 12.dp)
                  .testTag("rail_create_go_fab"),
              ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(id = R.string.cd_add_group_order))
              }
            }
          },
        ) {
          Spacer(Modifier.height(12.dp))
          NavigationRailItem(
            selected = currentRoute == ROUTE_DASHBOARD,
            onClick = { navigateTopTab(ROUTE_DASHBOARD) },
            icon = {
              Icon(
                if (currentRoute == ROUTE_DASHBOARD) Icons.Filled.Dashboard else Icons.Outlined.Dashboard,
                contentDescription = null,
              )
            },
            label = { Text(stringResource(id = R.string.nav_dashboard)) },
          )
          NavigationRailItem(
            selected = currentRoute == ROUTE_CATALOG,
            onClick = { navigateTopTab(ROUTE_CATALOG) },
            icon = {
              Icon(
                if (currentRoute == ROUTE_CATALOG) Icons.Filled.Storefront else Icons.Outlined.Storefront,
                contentDescription = null,
              )
            },
            label = { Text(stringResource(id = R.string.nav_catalog)) },
          )
          NavigationRailItem(
            selected = currentRoute == ROUTE_PHOTOS,
            onClick = { navigateTopTab(ROUTE_PHOTOS) },
            icon = {
              Icon(
                if (currentRoute == ROUTE_PHOTOS) Icons.Filled.PhotoLibrary else Icons.Outlined.PhotoLibrary,
                contentDescription = null,
              )
            },
            label = { Text(stringResource(id = R.string.nav_photos)) },
          )
          NavigationRailItem(
            selected = currentRoute == ROUTE_MY_ORDERS,
            onClick = { navigateTopTab(ROUTE_MY_ORDERS) },
            icon = {
              Icon(
                if (currentRoute == ROUTE_MY_ORDERS) Icons.Filled.ShoppingBag else Icons.Outlined.ShoppingBag,
                contentDescription = null,
              )
            },
            label = { Text(stringResource(id = R.string.nav_my_orders)) },
          )
          NavigationRailItem(
            selected = currentRoute == ROUTE_NOTIFICATIONS,
            onClick = { navigateTopTab(ROUTE_NOTIFICATIONS) },
            icon = {
              BadgedBox(
                badge = {
                  if (badgeCount > 0) {
                    Badge { Text(badgeCount.toString()) }
                  }
                }
              ) {
                Icon(
                  if (currentRoute == ROUTE_NOTIFICATIONS) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                  contentDescription = null,
                )
              }
            },
            label = { Text(stringResource(id = R.string.nav_notifications)) },
          )
        }
      }

      Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
          if (!isDetailScreen) {
            TopAppBar(
              title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Image(
                    painter = painterResource(id = R.drawable.img_jiego_mascot),
                    contentDescription = stringResource(id = R.string.cd_jiego_logo),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                      .size(36.dp)
                      .clip(CircleShape)
                      .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                  )
                  Spacer(Modifier.width(10.dp))
                  Column {
                    Text(
                      text = when (currentRoute) {
                        ROUTE_CATALOG -> "Katalog JIEGO"
                        ROUTE_PHOTOS -> "Foto JIEGO"
                        ROUTE_MY_ORDERS -> "Belanjaan Saya"
                        ROUTE_NOTIFICATIONS -> "Notifikasi JIEGO"
                        else -> "JIEGO"
                      },
                      style = MaterialTheme.typography.titleLarge,
                      fontWeight = FontWeight.Bold,
                    )
                    Text(
                      text = when {
                        !isOwnerAccount -> "Pembeli • ${userEmail.ifBlank { userDisplayName }}"
                        isBuyerMode -> "Preview Pembeli • ${userEmail.ifBlank { userDisplayName }}"
                        else -> "Pemilik JIEGO • ${userEmail.ifBlank { userDisplayName }}"
                      },
                      style = MaterialTheme.typography.labelSmall,
                      color = if (isBuyerMode) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                }
              },
              actions = {
                if (!isBuyerMode && (currentRoute == ROUTE_DASHBOARD || currentRoute == null)) {
                  IconButton(
                    onClick = { showImportRekapanDialog = true },
                    modifier = Modifier.testTag("topbar_import_rekapan_button"),
                  ) {
                    Icon(
                      imageVector = Icons.Default.UploadFile,
                      contentDescription = "Impor rekapan Excel/CSV",
                    )
                  }
                }
                IconButton(
                  onClick = { showRatesDialog = true },
                  modifier = Modifier.testTag("topbar_rates_button"),
                ) {
                  Icon(
                    imageVector = Icons.Default.Calculate,
                    contentDescription = "Kalkulator harga kotor & rate",
                  )
                }
                IconButton(
                  onClick = {
                    qrisModalRequest = QrisModalRequest(
                      title = "QRIS Pembelian JIEGO",
                      subtitle = "Scan QRIS resmi JIEGO atau pilih foto QRIS dari galeri Anda.",
                    )
                  },
                  modifier = Modifier.testTag("topbar_qris_button"),
                ) {
                  Icon(
                    imageVector = Icons.Default.QrCode2,
                    contentDescription = "Foto QRIS pembelian",
                  )
                }
                IconButton(
                  onClick = { showContactsDialog = true },
                  modifier = Modifier.testTag("topbar_contacts_button"),
                ) {
                  Icon(
                    imageVector = Icons.Default.SupportAgent,
                    contentDescription = "Kontak WhatsApp & Shopee JIEGO",
                  )
                }
                IconButton(
                  onClick = {
                    NotificationSyncWorker.cancel(context)
                    GoogleAuthHelper.signOut(credentialManager, scope)
                  },
                  modifier = Modifier.testTag("sign_out_button"),
                ) {
                  Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = stringResource(id = R.string.cd_sign_out),
                  )
                }
              },
            )
          }
        },
        bottomBar = {
          if (!useNavRail && !isDetailScreen) {
            NavigationBar {
              NavigationBarItem(
                selected = currentRoute == ROUTE_DASHBOARD,
                onClick = { navigateTopTab(ROUTE_DASHBOARD) },
                icon = {
                  Icon(
                    if (currentRoute == ROUTE_DASHBOARD) Icons.Filled.Dashboard else Icons.Outlined.Dashboard,
                    contentDescription = null,
                  )
                },
                label = {
                  Text(
                    text = stringResource(id = R.string.nav_dashboard),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                },
                modifier = Modifier.testTag("nav_tab_dashboard"),
              )
              NavigationBarItem(
                selected = currentRoute == ROUTE_CATALOG,
                onClick = { navigateTopTab(ROUTE_CATALOG) },
                icon = {
                  Icon(
                    if (currentRoute == ROUTE_CATALOG) Icons.Filled.Storefront else Icons.Outlined.Storefront,
                    contentDescription = null,
                  )
                },
                label = {
                  Text(
                    text = stringResource(id = R.string.nav_catalog),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                },
                modifier = Modifier.testTag("nav_tab_catalog"),
              )
              NavigationBarItem(
                selected = currentRoute == ROUTE_PHOTOS,
                onClick = { navigateTopTab(ROUTE_PHOTOS) },
                icon = {
                  Icon(
                    if (currentRoute == ROUTE_PHOTOS) Icons.Filled.PhotoLibrary else Icons.Outlined.PhotoLibrary,
                    contentDescription = null,
                  )
                },
                label = {
                  Text(
                    text = stringResource(id = R.string.nav_photos),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                },
                modifier = Modifier.testTag("nav_tab_photos"),
              )
              NavigationBarItem(
                selected = currentRoute == ROUTE_MY_ORDERS,
                onClick = { navigateTopTab(ROUTE_MY_ORDERS) },
                icon = {
                  Icon(
                    if (currentRoute == ROUTE_MY_ORDERS) Icons.Filled.ShoppingBag else Icons.Outlined.ShoppingBag,
                    contentDescription = null,
                  )
                },
                label = {
                  Text(
                    text = "Pesanan",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                },
                modifier = Modifier.testTag("nav_tab_my_orders"),
              )
              NavigationBarItem(
                selected = currentRoute == ROUTE_NOTIFICATIONS,
                onClick = { navigateTopTab(ROUTE_NOTIFICATIONS) },
                icon = {
                  BadgedBox(
                    badge = {
                      if (badgeCount > 0) {
                        Badge { Text(badgeCount.toString()) }
                      }
                    }
                  ) {
                    Icon(
                      if (currentRoute == ROUTE_NOTIFICATIONS) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                      contentDescription = null,
                    )
                  }
                },
                label = {
                  Text(
                    text = stringResource(id = R.string.nav_notifications),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                },
                modifier = Modifier.testTag("nav_tab_notifications"),
              )
            }
          }
        },
        floatingActionButton = {
          if (!useNavRail && !isBuyerMode && currentRoute == ROUTE_DASHBOARD) {
            ExtendedFloatingActionButton(
              onClick = { showCreateGoDialog = true },
              icon = { Icon(Icons.Default.Add, contentDescription = null) },
              text = { Text("Buat GO") },
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
              shape = RoundedCornerShape(16.dp),
              modifier = Modifier.testTag("fab_create_go"),
            )
          }
        },
        modifier = Modifier.weight(1f),
      ) { innerPadding ->
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        ) {
          if (!isDetailScreen) {
            if (isOwnerAccount) {
              RoleModeSwitcherBar(
                currentMode = effectiveViewMode,
                onSelectMode = { nextMode ->
                  viewMode = nextMode
                  prefs.edit().putString(viewModeKey, nextMode.name).apply()
                },
              )
            } else {
              BuyerAccountStatusBar(
                userLabel = userEmail.ifBlank { userDisplayName },
              )
            }
          }
          if (isBusy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(3.dp))
          }

          when (val state = uiState) {
            is UiState.Loading -> {
              Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize(),
              ) {
                Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                  CircularProgressIndicator()
                  Text("Memuat data grup order...", style = MaterialTheme.typography.bodyMedium)
                }
              }
            }

            is UiState.Error -> {
              Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                  .fillMaxSize()
                  .padding(24.dp),
              ) {
                Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                  Text("Gagal Memuat Data", style = MaterialTheme.typography.titleLarge)
                  Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Button(onClick = {
                    GoogleAuthHelper.signOut(credentialManager, scope)
                  }) {
                    Text("Keluar & Masuk Ulang")
                  }
                }
              }
            }

            is UiState.Success -> {
              val data = state.data
              NavHost(
                navController = navController,
                startDestination = ROUTE_DASHBOARD,
                modifier = Modifier.fillMaxSize(),
              ) {
                composable(ROUTE_DASHBOARD) {
                  DashboardScreen(
                    data = data,
                    onOrderClick = { id -> navController.navigate(orderDetailRoute(id)) },
                    onOpenCreateOrder = { showCreateGoDialog = true },
                    onOpenImportRekapan = { showImportRekapanDialog = true },
                    onSeedSampleData = { viewModel.seedSampleKPopData() },
                    onOpenNotifications = { navigateTopTab(ROUTE_NOTIFICATIONS) },
                    isBuyerMode = isBuyerMode,
                    onOpenCatalog = { navigateTopTab(ROUTE_CATALOG) },
                    onOpenMyOrders = { navigateTopTab(ROUTE_MY_ORDERS) },
                    onOpenRatesCalculator = { showRatesDialog = true },
                    onSyncGoogleSheetsNow = {
                      prefs.edit().putLong("last_auto_sync_ms_$currentUserId", System.currentTimeMillis()).apply()
                      viewModel.syncGoogleSheets(sheetUrl = savedSheetUrl, isAutoSync = false)
                    },
                  )
                }

                composable(ROUTE_CATALOG) {
                  CatalogScreen(
                    data = data,
                    onAddCatalogItem = { title, artist, cat, price, dp, stock, comp, imgBase64, notes ->
                      viewModel.addCatalogItem(title, artist, cat, price, dp, stock, comp, imgBase64, notes)
                    },
                    onUpdateCatalogItem = { item, title, artist, cat, price, dp, stock, comp, imgKey, imgBase64, notes ->
                      viewModel.updateCatalogItemDetails(item, title, artist, cat, price, dp, stock, comp, imgKey, imgBase64, notes)
                    },
                    onUpdateCatalogPhoto = { item, imgKey, imgBase64 ->
                      viewModel.updateCatalogItemPhoto(item, imgKey, imgBase64)
                    },
                    onUpdateStock = { catId, newStock -> viewModel.updateCatalogItemStock(catId, newStock) },
                    onDeleteCatalogItem = { catId -> viewModel.deleteCatalogItem(catId) },
                    onSeedLngshot = { viewModel.seedLngshotLightstick(silent = false) },
                    onOrderCatalogItem = { catItem, go, name, handle, isMe, paid, method, notes ->
                      viewModel.placeCatalogOrderIntoGroupOrder(catItem, go, name, handle, isMe, paid, method, notes)
                    },
                    onOpenQrisDialog = { amount, sub ->
                      qrisModalRequest = QrisModalRequest(
                        title = "QRIS Pembelian JIEGO",
                        subtitle = sub,
                        amountToPay = amount,
                      )
                    },
                    onOpenRatesCalculator = { showRatesDialog = true },
                    isBuyerMode = isBuyerMode,
                  )
                }

                composable(ROUTE_PHOTOS) {
                  PhotosScreen(
                    data = data,
                    isBuyerMode = isBuyerMode,
                    onAddPhoto = { cat, role, name, phone, orderId, orderName, caption, base64 ->
                      viewModel.addPhotoUpdate(cat, role, name, phone, orderId, orderName, caption, base64)
                    },
                    onUpdatePhoto = { existing, cat, name, phone, orderId, orderName, caption, base64 ->
                      viewModel.updatePhotoUpdate(existing, cat, name, phone, orderId, orderName, caption, base64)
                    },
                    onDeletePhoto = { photoId -> viewModel.deletePhotoUpdate(photoId) },
                  )
                }

                composable(ROUTE_MY_ORDERS) {
                  MyOrdersScreen(
                    data = data,
                    onOpenOrderDetail = { id -> navController.navigate(orderDetailRoute(id)) },
                    onRecordPayment = { orderName, member, newPaid, method ->
                      viewModel.recordMemberPayment(orderName, member, newPaid, method)
                    },
                    onOpenQrisPayment = { orderName, member ->
                      qrisModalRequest = QrisModalRequest(
                        title = "Bayar Tagihan via QRIS",
                        subtitle = "${member.memberName} • $orderName",
                        amountToPay = member.remainingBalance,
                        orderNameForPayment = orderName,
                        memberTarget = member,
                      )
                    },
                    onOpenGeneralQris = {
                      qrisModalRequest = QrisModalRequest(
                        title = "QRIS Pembelian JIEGO",
                        subtitle = "Gunakan QRIS di bawah untuk membayar tagihan belanjaan Anda.",
                      )
                    },
                    onNavigateToDashboard = { navigateTopTab(ROUTE_DASHBOARD) },
                    isBuyerMode = isBuyerMode,
                  )
                }

                composable(ROUTE_NOTIFICATIONS) {
                  NotificationsScreen(
                    data = data,
                    onOpenOrderDetail = { id -> navController.navigate(orderDetailRoute(id)) },
                    onOpenPhotos = { navigateTopTab(ROUTE_PHOTOS) },
                    onOpenCatalog = { navigateTopTab(ROUTE_CATALOG) },
                    onMarkRead = { id, isRead -> viewModel.markNotificationRead(id, isRead) },
                    onMarkAllRead = { list -> viewModel.markAllNotificationsRead(list) },
                    isOwnerMode = isOwnerAccount && !isBuyerMode,
                    onSendOwnerAnnouncement = { title, message ->
                      viewModel.sendOwnerAnnouncement(title, message)
                    },
                  )
                }

                composable(
                  route = ROUTE_ORDER_DETAIL,
                  arguments = listOf(navArgument("orderId") { type = NavType.StringType }),
                ) { backStackEntry ->
                  val orderId = backStackEntry.arguments?.getString("orderId") ?: ""
                  GroupOrderDetailScreen(
                    orderId = orderId,
                    data = data,
                    onBack = { navController.popBackStack() },
                    onEditOrderInfo = { order, name, artist, batch, closing, deadline, notes ->
                      viewModel.updateGroupOrderInfo(order, name, artist, batch, closing, deadline, notes)
                    },
                    onUpdateOrderStatus = { order, newStatus ->
                      viewModel.updateGroupOrderStatus(order, newStatus)
                    },
                    onDeleteOrder = { id ->
                      viewModel.deleteGroupOrder(id) {
                        navController.popBackStack()
                      }
                    },
                    onAddMemberWithItems = { order, mName, mHandle, isMe, ship, paid, method, notes, items ->
                      viewModel.addMemberWithItems(order, mName, mHandle, isMe, ship, paid, method, notes, items)
                    },
                    onAddOrderItem = { goId, memId, itemName, variant, qty, price ->
                      viewModel.addOrderItem(goId, memId, itemName, variant, qty, price)
                    },
                    onDeleteOrderItem = { goId, memId, itemId ->
                      viewModel.deleteOrderItem(goId, memId, itemId)
                    },
                    onRecordPayment = { orderName, member, newPaid, method ->
                      viewModel.recordMemberPayment(orderName, member, newPaid, method)
                    },
                    onUpdateMemberDetailsAndStatus = { orderName, member, mName, mHandle, isMe, ship, newStatus, notes ->
                      viewModel.updateMemberDetailsAndStatus(orderName, member, mName, mHandle, isMe, ship, newStatus, notes)
                    },
                    onSendPaymentReminder = { order, member ->
                      viewModel.sendPaymentReminder(order, member)
                    },
                    isBuyerMode = isBuyerMode,
                    onOpenQrisForMember = { order, member ->
                      qrisModalRequest = QrisModalRequest(
                        title = "Bayar Tagihan via QRIS",
                        subtitle = "${member.memberName} • ${order.name}",
                        amountToPay = member.remainingBalance,
                        orderNameForPayment = order.name,
                        memberTarget = member,
                      )
                    },
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun RoleModeSwitcherBar(
  currentMode: AppViewMode,
  onSelectMode: (AppViewMode) -> Unit,
) {
  Surface(
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 1.dp,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
      SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth(),
      ) {
        AppViewMode.entries.forEachIndexed { index, mode ->
          val selected = currentMode == mode
          SegmentedButton(
            selected = selected,
            onClick = { onSelectMode(mode) },
            shape = SegmentedButtonDefaults.itemShape(
              index = index,
              count = AppViewMode.entries.size,
            ),
            icon = {
              Icon(
                imageVector = if (mode == AppViewMode.OWNER) {
                  Icons.Default.AdminPanelSettings
                } else {
                  Icons.Default.Person
                },
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
            },
            label = {
              Text(
                text = if (mode == AppViewMode.OWNER) "Tampilan Pemilik" else "Tampilan Pembeli",
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            },
            modifier = Modifier
              .height(48.dp)
              .testTag(if (mode == AppViewMode.OWNER) "mode_toggle_owner" else "mode_toggle_buyer"),
          )
        }
      }
    }
  }
}

@Composable
private fun BuyerAccountStatusBar(userLabel: String) {
  Surface(
    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
    tonalElevation = 1.dp,
    modifier = Modifier
      .fillMaxWidth()
      .testTag("buyer_locked_mode_bar"),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
      Icon(
        imageVector = Icons.Default.Person,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.size(18.dp),
      )
      Text(
        text = "Mode Pembeli • $userLabel",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onTertiaryContainer,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
    }
  }
}
