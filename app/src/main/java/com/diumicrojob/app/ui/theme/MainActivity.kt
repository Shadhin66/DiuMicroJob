package com.diumicrojob.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.diumicrojob.app.ui.theme.DiuMicroJobTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

private val db: FirebaseFirestore
    get() = FirebaseFirestore.getInstance()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DiuMicroJobTheme {
                var signedIn by remember {
                    mutableStateOf(FirebaseAuth.getInstance().currentUser != null)
                }
                if (signedIn) {
                    DashboardScreen(
                        onLogout = {
                            FirebaseAuth.getInstance().signOut()
                            signedIn = false
                        }
                    )
                } else {
                    LoginScreen(onLoginSuccess = { signedIn = true })
                }
            }
        }
    }
}

private fun createUserProfile(onComplete: (Boolean, String) -> Unit) {
    val user = FirebaseAuth.getInstance().currentUser
    if (user == null) {
        onComplete(false, "User not found.")
        return
    }

    val ref = db.collection("users").document(user.uid)
    ref.get()
        .addOnSuccessListener { doc ->
            if (doc.exists()) {
                onComplete(true, "Welcome back!")
            } else {
                val data = hashMapOf<String, Any>(
                    "uid" to user.uid,
                    "email" to (user.email ?: ""),
                    "name" to "",
                    "phone" to "",
                    "balance" to 0.0,
                    "totalEarnings" to 0.0
                )
                ref.set(data)
                    .addOnSuccessListener { onComplete(true, "Profile created!") }
                    .addOnFailureListener {
                        onComplete(false, it.localizedMessage ?: "Profile creation failed.")
                    }
            }
        }
        .addOnFailureListener {
            onComplete(false, it.localizedMessage ?: "Database error.")
        }
}

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    val auth = FirebaseAuth.getInstance()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isRegister by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "DiuMicroJob",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Text("Work • Earn • Grow")
        Spacer(Modifier.height(24.dp))
        Text(
            if (isRegister) "Create Account" else "Welcome Back",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it.filterNot { char -> char.isWhitespace() }
                message = ""
            },
            label = { Text("Email Address") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; message = "" },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(14.dp))
        Button(
            enabled = !loading,
            onClick = {
                val cleanEmail = email.trim().lowercase()
                val emailPattern =
                    Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

                when {
                    cleanEmail.isBlank() -> {
                        message = "Please enter your email."
                    }
                    !emailPattern.matches(cleanEmail) -> {
                        message = "Email format is incorrect. Example: miaa90032@gmail.com"
                    }
                    password.length < 6 -> {
                        message = "Password must be at least 6 characters."
                    }
                    else -> {
                        loading = true
                        message = "Connecting..."

                        val result = if (isRegister) {
                            auth.createUserWithEmailAndPassword(cleanEmail, password)
                        } else {
                            auth.signInWithEmailAndPassword(cleanEmail, password)
                        }

                        result.addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                createUserProfile { ok, text ->
                                    loading = false
                                    message = text
                                    if (ok) onLoginSuccess()
                                }
                            } else {
                                loading = false
                                message = task.exception?.localizedMessage
                                    ?: "Authentication failed."
                            }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (loading) "Please wait..."
                else if (isRegister) "Register" else "Login"
            )
        }
        TextButton(onClick = {
            isRegister = !isRegister
            message = ""
        }) {
            Text(
                if (isRegister) "Already have an account? Login"
                else "Create New Account"
            )
        }
        Text(message, color = MaterialTheme.colorScheme.error)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(onLogout: () -> Unit) {
    val user = FirebaseAuth.getInstance().currentUser
    var isAdmin by remember { mutableStateOf(false) }
    var adminChecked by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf("Home") }

    LaunchedEffect(user?.uid) {
        if (user != null) {
            db.collection("admins").document(user.uid).get()
                .addOnSuccessListener {
                    isAdmin = it.exists() && it.getString("role") == "admin"
                    adminChecked = true
                }
                .addOnFailureListener { adminChecked = true }
        } else {
            adminChecked = true
        }
    }

    val menu = listOf(
        "Home", "Available Jobs", "Post Job", "My Tasks",
        "My Posted Jobs", "Wallet", "Deposit", "Withdraw"
    ) + if (isAdmin) listOf("Admin Panel") else emptyList()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DiuMicroJob") },
                actions = { TextButton(onClick = onLogout) { Text("Logout") } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(10.dp)) {
            Text("Welcome, ${user?.email ?: "User"}", style = MaterialTheme.typography.titleMedium)
            if (!adminChecked) LinearProgressIndicator(Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                menu.forEach { item ->
                    if (selected == item) {
                        Button(
                            onClick = { selected = item },
                            contentPadding = PaddingValues(horizontal = 9.dp, vertical = 5.dp)
                        ) { Text(item) }
                    } else {
                        OutlinedButton(
                            onClick = { selected = item },
                            contentPadding = PaddingValues(horizontal = 9.dp, vertical = 5.dp)
                        ) { Text(item) }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            when (selected) {
                "Home" -> HomePanel()
                "Available Jobs" -> AvailableJobsPanel()
                "Post Job" -> PostJobPanel()
                "My Tasks" -> MyTasksPanel()
                "My Posted Jobs" -> MyPostedJobsPanel()
                "Wallet" -> WalletPanel()
                "Deposit" -> PaymentRequestPanel(isDeposit = true)
                "Withdraw" -> PaymentRequestPanel(isDeposit = false)
                "Admin Panel" -> if (isAdmin) AdminPanel() else Text("Admin access denied.")
            }
        }
    }
}

@Composable
private fun HomePanel() {
    var balance by remember { mutableStateOf("Loading...") }
    var earnings by remember { mutableStateOf("Loading...") }
    val uid = FirebaseAuth.getInstance().currentUser?.uid

    LaunchedEffect(uid) {
        if (uid != null) {
            db.collection("users").document(uid).get()
                .addOnSuccessListener {
                    balance = String.format("%.2f", it.getDouble("balance") ?: 0.0)
                    earnings = String.format("%.2f", it.getDouble("totalEarnings") ?: 0.0)
                }
                .addOnFailureListener {
                    balance = "Unavailable"
                    earnings = "Unavailable"
                }
        }
    }

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(18.dp)) {
            Text("Your Wallet", style = MaterialTheme.typography.titleMedium)
            Text("৳ $balance", style = MaterialTheme.typography.headlineLarge)
            Text("Total earnings: ৳ $earnings")
        }
    }
    Spacer(Modifier.height(14.dp))
    Text("Find jobs, post your own tasks, complete work and submit proof.")
}

@Composable
private fun PostJobPanel() {
    val user = FirebaseAuth.getInstance().currentUser ?: return
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var reward by remember { mutableStateOf("") }
    var workerLimit by remember { mutableStateOf("1") }
    var message by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    Column {
        Text("Post a New Job", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(title, { title = it }, label = { Text("Job title") },
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(description, { description = it },
            label = { Text("Job description / instructions") },
            modifier = Modifier.fillMaxWidth(), minLines = 3)
        OutlinedTextField(reward, { reward = it },
            label = { Text("Reward per worker (BDT)") },
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(workerLimit, { workerLimit = it },
            label = { Text("Number of workers") },
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(8.dp))
        Button(
            enabled = !saving,
            onClick = {
                val amount = reward.toDoubleOrNull()
                val count = workerLimit.toIntOrNull()
                if (title.isBlank() || description.isBlank() || amount == null ||
                    amount <= 0 || count == null || count <= 0
                ) {
                    message = "Fill all fields. Reward and worker count must be greater than zero."
                } else {
                    saving = true
                    val data = hashMapOf<String, Any>(
                        "title" to title.trim(),
                        "description" to description.trim(),
                        "reward" to amount,
                        "workerLimit" to count,
                        "postedBy" to user.uid,
                        "employerEmail" to (user.email ?: ""),
                        "status" to "active",
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                    db.collection("jobs").add(data)
                        .addOnSuccessListener {
                            saving = false
                            message = "Job published successfully!"
                            title = ""
                            description = ""
                            reward = ""
                            workerLimit = "1"
                        }
                        .addOnFailureListener {
                            saving = false
                            message = it.localizedMessage ?: "Could not publish job."
                        }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (saving) "Publishing..." else "Publish Job") }
        Text(message)
        Text("Note: This posts a task listing. It does not automatically reserve or transfer money.")
    }
}

@Composable
private fun AvailableJobsPanel() {
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val email = FirebaseAuth.getInstance().currentUser?.email ?: ""
    var jobs by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var message by remember { mutableStateOf("Loading jobs...") }

    fun loadJobs() {
        db.collection("jobs").whereEqualTo("status", "active").get()
            .addOnSuccessListener { result ->
                jobs = result.documents.map { doc ->
                    (doc.data ?: emptyMap()).toMutableMap().apply { put("documentId", doc.id) }
                }
                message = if (jobs.isEmpty()) "No active jobs available." else ""
            }
            .addOnFailureListener { message = it.localizedMessage ?: "Could not load jobs." }
    }

    LaunchedEffect(Unit) { loadJobs() }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Available Jobs", style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f))
            TextButton(onClick = { loadJobs() }) { Text("Refresh") }
        }
        Text(message)
        LazyColumn {
            items(jobs) { job ->
                val jobId = job["documentId"]?.toString() ?: ""
                val employerUid = job["postedBy"]?.toString() ?: ""
                val jobTitle = job["title"]?.toString() ?: "Job"
                Card(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(jobTitle, style = MaterialTheme.typography.titleMedium)
                        Text(job["description"]?.toString() ?: "")
                        Text("Reward: ৳${job["reward"] ?: 0}")
                        Text("Workers needed: ${job["workerLimit"] ?: 1}")
                        Button(
                            enabled = employerUid.isNotBlank() && employerUid != uid,
                            onClick = {
                                if (employerUid == uid) {
                                    message = "You cannot apply to your own job."
                                } else {
                                    db.collection("jobApplications")
                                        .whereEqualTo("uid", uid)
                                        .whereEqualTo("jobId", jobId)
                                        .get()
                                        .addOnSuccessListener { existing ->
                                            if (!existing.isEmpty) {
                                                message = "You have already applied to this job."
                                            } else {
                                                val application = hashMapOf<String, Any>(
                                                    "uid" to uid,
                                                    "email" to email,
                                                    "jobId" to jobId,
                                                    "jobTitle" to jobTitle,
                                                    "employerUid" to employerUid,
                                                    "description" to (job["description"]?.toString() ?: ""),
                                                    "reward" to (job["reward"] ?: 0.0),
                                                    "status" to "pending",
                                                    "createdAt" to FieldValue.serverTimestamp()
                                                )
                                                db.collection("jobApplications").add(application)
                                                    .addOnSuccessListener { message = "Application submitted." }
                                                    .addOnFailureListener {
                                                        message = it.localizedMessage ?: "Apply failed."
                                                    }
                                            }
                                        }
                                        .addOnFailureListener {
                                            message = it.localizedMessage ?: "Could not check previous application."
                                        }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (employerUid == uid) "Your Job" else "Apply Now") }
                    }
                }
            }
        }
    }
}

@Composable
private fun MyTasksPanel() {
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    var tasks by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var message by remember { mutableStateOf("Loading your applications...") }
    var proofInputs by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    fun load() {
        db.collection("jobApplications").whereEqualTo("uid", uid).get()
            .addOnSuccessListener { result ->
                tasks = result.documents.map { doc ->
                    (doc.data ?: emptyMap()).toMutableMap().apply { put("documentId", doc.id) }
                }
                message = if (tasks.isEmpty()) "You have not applied to any jobs yet." else ""
            }
            .addOnFailureListener { message = it.localizedMessage ?: "Could not load your tasks." }
    }
    LaunchedEffect(uid) { load() }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("My Tasks", style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f))
            TextButton(onClick = { load() }) { Text("Refresh") }
        }
        Text(message)
        LazyColumn {
            items(tasks) { task ->
                val id = task["documentId"]?.toString() ?: ""
                val status = task["status"]?.toString() ?: "pending"
                val proof = proofInputs[id] ?: ""
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(task["jobTitle"]?.toString() ?: "Job",
                            style = MaterialTheme.typography.titleMedium)
                        Text("Status: $status")
                        Text("Reward: ৳${task["reward"] ?: 0}")
                        if (status == "pending") {
                            OutlinedTextField(
                                value = proof,
                                onValueChange = { proofInputs = proofInputs + (id to it) },
                                label = { Text("Proof URL / work details") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                enabled = proof.isNotBlank(),
                                onClick = {
                                    db.collection("jobApplications").document(id)
                                        .update(
                                            mapOf(
                                                "proofUrl" to proof.trim(),
                                                "submittedAt" to FieldValue.serverTimestamp(),
                                                "status" to "submitted"
                                            )
                                        )
                                        .addOnSuccessListener {
                                            proofInputs = proofInputs - id
                                            load()
                                        }
                                        .addOnFailureListener {
                                            message = it.localizedMessage ?: "Proof submission failed."
                                        }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Submit Proof") }
                        } else {
                            Text("Proof: ${task["proofUrl"] ?: "-"}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MyPostedJobsPanel() {
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    var jobs by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var message by remember { mutableStateOf("Loading your posted jobs...") }
    var selectedJob by remember { mutableStateOf<Map<String, Any>?>(null) }
    var applicants by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }

    fun loadJobs() {
        db.collection("jobs").whereEqualTo("postedBy", uid).get()
            .addOnSuccessListener { result ->
                jobs = result.documents.map { doc ->
                    (doc.data ?: emptyMap()).toMutableMap().apply { put("documentId", doc.id) }
                }
                message = if (jobs.isEmpty()) "You have not posted any jobs yet." else ""
            }
            .addOnFailureListener { message = it.localizedMessage ?: "Could not load posted jobs." }
    }

    fun loadApplicants(jobId: String) {
        db.collection("jobApplications").whereEqualTo("jobId", jobId)
            .whereEqualTo("employerUid", uid).get()
            .addOnSuccessListener { result ->
                applicants = result.documents.map { doc ->
                    (doc.data ?: emptyMap()).toMutableMap().apply { put("documentId", doc.id) }
                }
            }
            .addOnFailureListener { message = it.localizedMessage ?: "Could not load applicants." }
    }

    LaunchedEffect(uid) { loadJobs() }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("My Posted Jobs", style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f))
            TextButton(onClick = { loadJobs() }) { Text("Refresh") }
        }
        Text(message)
        LazyColumn {
            items(jobs) { job ->
                val id = job["documentId"]?.toString() ?: ""
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(job["title"]?.toString() ?: "Job",
                            style = MaterialTheme.typography.titleMedium)
                        Text("Reward: ৳${job["reward"] ?: 0} • Status: ${job["status"] ?: ""}")
                        Button(
                            onClick = {
                                selectedJob = job
                                loadApplicants(id)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("View Applicants") }
                    }
                }
            }
        }
        selectedJob?.let { job ->
            val jobId = job["documentId"]?.toString() ?: ""
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Applicants for ${job["title"]}", style = MaterialTheme.typography.titleMedium)
            if (applicants.isEmpty()) Text("No applications found yet.")
            applicants.forEach { applicant ->
                val appId = applicant["documentId"]?.toString() ?: ""
                val status = applicant["status"]?.toString() ?: "pending"
                Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Column(Modifier.padding(10.dp)) {
                        Text(applicant["email"]?.toString()
                            ?: applicant["uid"]?.toString() ?: "Worker")
                        Text("Status: $status")
                        Text("Proof: ${applicant["proofUrl"] ?: "Not submitted"}")
                        if (status == "submitted") {
                            Row {
                                TextButton(onClick = {
                                    db.collection("jobApplications").document(appId)
                                        .update("status", "approved")
                                        .addOnSuccessListener { loadApplicants(jobId) }
                                        .addOnFailureListener {
                                            message = it.localizedMessage ?: "Approval failed."
                                        }
                                }) { Text("Approve") }
                                TextButton(onClick = {
                                    db.collection("jobApplications").document(appId)
                                        .update("status", "rejected")
                                        .addOnSuccessListener { loadApplicants(jobId) }
                                        .addOnFailureListener {
                                            message = it.localizedMessage ?: "Rejection failed."
                                        }
                                }) { Text("Reject") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WalletPanel() {
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    var balance by remember { mutableStateOf("Loading...") }
    var earnings by remember { mutableStateOf("Loading...") }
    LaunchedEffect(uid) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener {
                balance = String.format("%.2f", it.getDouble("balance") ?: 0.0)
                earnings = String.format("%.2f", it.getDouble("totalEarnings") ?: 0.0)
            }
            .addOnFailureListener { balance = "Unavailable"; earnings = "Unavailable" }
    }
    Column {
        Text("My Wallet", style = MaterialTheme.typography.titleLarge)
        Text("Available Balance: ৳$balance")
        Text("Total Earnings: ৳$earnings")
        Text("Balance is not automatically credited by job approval in this prototype.")
    }
}

@Composable
private fun PaymentRequestPanel(isDeposit: Boolean) {
    val user = FirebaseAuth.getInstance().currentUser ?: return
    var amount by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("bKash") }
    var phone by remember { mutableStateOf("") }
    var trxId by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    val collection = if (isDeposit) "deposits" else "withdrawals"

    Column {
        Text(if (isDeposit) "Deposit Request" else "Withdraw Request",
            style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(amount, { amount = it },
            label = { Text("Amount (BDT)") }, modifier = Modifier.fillMaxWidth())
        Text("Payment method")
        Row(verticalAlignment = Alignment.CenterVertically) {
            listOf("bKash", "Nagad").forEach {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = method == it, onClick = { method = it })
                    Text(it)
                }
            }
        }
        OutlinedTextField(phone, { phone = it },
            label = { Text("Mobile number") }, modifier = Modifier.fillMaxWidth())
        if (isDeposit) {
            OutlinedTextField(trxId, { trxId = it },
                label = { Text("Transaction ID") }, modifier = Modifier.fillMaxWidth())
        }
        Button(
            onClick = {
                val value = amount.toDoubleOrNull()
                if (value == null || value <= 0 || phone.isBlank() ||
                    (isDeposit && trxId.isBlank())
                ) {
                    message = "Enter a valid amount, mobile number${if (isDeposit) " and transaction ID" else ""}."
                } else {
                    val data = hashMapOf<String, Any>(
                        "uid" to user.uid,
                        "email" to (user.email ?: ""),
                        "amount" to value,
                        "paymentMethod" to method,
                        "phone" to phone.trim(),
                        "status" to "pending",
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                    if (isDeposit) data["transactionId"] = trxId.trim()
                    db.collection(collection).add(data)
                        .addOnSuccessListener {
                            message = "Request submitted. Wait for admin review."
                            amount = ""
                            phone = ""
                            trxId = ""
                        }
                        .addOnFailureListener {
                            message = it.localizedMessage ?: "Request failed."
                        }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Submit Request") }
        Text(message)
        Text("Payment requests are manual review only; no payment gateway is connected.")
    }
}

@Composable
private fun AdminPanel() {
    var section by remember { mutableStateOf("Jobs") }
    Column {
        Text("Admin Panel", style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary)
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf("Jobs", "Applications", "Deposits", "Withdrawals").forEach {
                TextButton(onClick = { section = it }) { Text(it) }
            }
        }
        when (section) {
            "Jobs" -> AdminJobsPanel()
            "Applications" -> AdminReviewPanel("jobApplications")
            "Deposits" -> AdminReviewPanel("deposits")
            "Withdrawals" -> AdminReviewPanel("withdrawals")
        }
    }
}

@Composable
private fun AdminJobsPanel() {
    var jobs by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var message by remember { mutableStateOf("") }

    fun load() {
        db.collection("jobs").get()
            .addOnSuccessListener { result ->
                jobs = result.documents.map { doc ->
                    (doc.data ?: emptyMap()).toMutableMap().apply { put("documentId", doc.id) }
                }
            }
            .addOnFailureListener { message = it.localizedMessage ?: "Could not load jobs." }
    }
    LaunchedEffect(Unit) { load() }

    Column {
        Text("All Jobs", style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = { load() }) { Text("Refresh") }
        Text(message)
        LazyColumn {
            items(jobs) { job ->
                val id = job["documentId"]?.toString() ?: ""
                val status = job["status"]?.toString() ?: "active"
                Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Column(Modifier.padding(10.dp)) {
                        Text(job["title"]?.toString() ?: "Job")
                        Text("Posted by: ${job["employerEmail"] ?: job["postedBy"] ?: "-"}")
                        Text("৳${job["reward"] ?: 0} • $status")
                        Row {
                            TextButton(onClick = {
                                db.collection("jobs").document(id)
                                    .update("status", if (status == "active") "paused" else "active")
                                    .addOnSuccessListener { load() }
                                    .addOnFailureListener {
                                        message = it.localizedMessage ?: "Update failed."
                                    }
                            }) { Text(if (status == "active") "Pause" else "Activate") }
                            TextButton(onClick = {
                                db.collection("jobs").document(id).delete()
                                    .addOnSuccessListener { load() }
                                    .addOnFailureListener {
                                        message = it.localizedMessage ?: "Delete failed."
                                    }
                            }) { Text("Delete") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminReviewPanel(collection: String) {
    var records by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var message by remember { mutableStateOf("Loading...") }

    fun load() {
        db.collection(collection).get()
            .addOnSuccessListener { result ->
                records = result.documents.map { doc ->
                    (doc.data ?: emptyMap()).toMutableMap().apply { put("documentId", doc.id) }
                }
                message = if (records.isEmpty()) "No records found." else ""
            }
            .addOnFailureListener {
                message = it.localizedMessage ?: "Could not load records."
            }
    }
    LaunchedEffect(collection) { load() }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(collection, style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f))
            TextButton(onClick = { load() }) { Text("Refresh") }
        }
        Text(message)
        LazyColumn {
            items(records) { record ->
                val id = record["documentId"]?.toString() ?: ""
                val status = record["status"]?.toString() ?: "pending"
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            record["jobTitle"]?.toString()
                                ?: record["email"]?.toString() ?: "Request",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text("Amount / reward: ৳${record["amount"] ?: record["reward"] ?: 0}")
                        Text("Status: $status")
                        if (status == "pending") {
                            Row {
                                TextButton(onClick = {
                                    db.collection(collection).document(id)
                                        .update("status", "approved")
                                        .addOnSuccessListener { load() }
                                        .addOnFailureListener {
                                            message = it.localizedMessage ?: "Update failed."
                                        }
                                }) { Text("Approve") }
                                TextButton(onClick = {
                                    db.collection(collection).document(id)
                                        .update("status", "rejected")
                                        .addOnSuccessListener { load() }
                                        .addOnFailureListener {
                                            message = it.localizedMessage ?: "Update failed."
                                        }
                                }) { Text("Reject") }
                            }
                        }
                    }
                }
            }
        }
    }
}