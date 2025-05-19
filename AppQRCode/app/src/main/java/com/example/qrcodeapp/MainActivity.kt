package com.example.qrcodeapp

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.qrcodeapp.ui.theme.AppQRCodeTheme
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.*
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var cameraExecutor: ExecutorService

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                Toast.makeText(this, "Permissão negada para acessar o armazenamento", Toast.LENGTH_SHORT).show()
            }
        }
    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                Toast.makeText(this, "Permissão da câmera negada", Toast.LENGTH_SHORT).show()
            }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) { // Android 9 (Pie) e abaixo
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        setContent {
            AppQRCodeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    QRCodeApp(cameraExecutor, this)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

@Composable
fun QRCodeApp(
    cameraExecutor: ExecutorService,
    activity: ComponentActivity
) {
    val context = LocalContext.current

    var inputText by remember { mutableStateOf("") }
    var generatedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showPreview by remember { mutableStateOf(false) }
    var showSaveButton by remember { mutableStateOf(false) }
    var scannedText by remember { mutableStateOf("") }

    val scanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        BarcodeScanning.getClient(options)
    }

    val previewView = remember { androidx.camera.view.PreviewView(context) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, it)
                    ImageDecoder.decodeBitmap(source)
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                }

                val image = InputImage.fromBitmap(bitmap, 0)

                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        if (barcodes.isNotEmpty()) {
                            val value = barcodes[0].rawValue ?: ""
                            if (value.isNotEmpty()) {
                                scannedText = value
                                showPreview = false
                            }
                        } else {
                            Toast.makeText(context, "Nenhum QR Code encontrado na imagem", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener {
                        Toast.makeText(context, "Erro ao processar imagem", Toast.LENGTH_SHORT).show()
                    }

            } catch (e: Exception) {
                Toast.makeText(context, "Erro ao abrir imagem: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Toast para QR Code detectado
    if (scannedText.isNotEmpty()) {
        LaunchedEffect(scannedText) {
            Toast.makeText(context, "QR Code detectado: $scannedText", Toast.LENGTH_LONG).show()
            scannedText = ""
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        val isTablet = maxWidth > 600.dp

        if (!showPreview) {
            // Aqui seu layout original, por exemplo, a área para criar QR e os botões
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                QRTextAndButtons(
                    inputText = inputText,
                    onTextChange = { inputText = it },
                    onGenerate = {
                        generatedBitmap = generateQRCode(inputText)
                        showSaveButton = true
                        showPreview = false
                        scannedText = ""
                    },
                    onScan = { showPreview = true },
                    showSaveButton = showSaveButton,
                    onSave = {
                        generatedBitmap?.let { bmp ->
                            saveImageToMediaStore(context, bmp, "QRCode_${System.currentTimeMillis()}")
                        }
                    },
                    onReadFromGallery = {
                        imagePickerLauncher.launch("image/*")
                    }
                )

                Spacer(modifier = Modifier.width(16.dp))

                generatedBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "QR Code Gerado",
                        modifier = Modifier.size(300.dp)
                    )
                }
            }
        } else {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize()) { view ->
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(view.surfaceProvider)
                    }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImageProxy(scanner, imageProxy) { qrValue ->
                            if (qrValue.isNotEmpty()) {
                                scannedText = qrValue
                                showPreview = false
                            }
                        }
                    }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            activity,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                    } catch (e: Exception) {
                        Toast.makeText(context, "Erro ao iniciar a câmera", Toast.LENGTH_SHORT).show()
                    }
                }, ContextCompat.getMainExecutor(context))
            }
        }
    }
}

@Composable
fun QRTextAndButtons(
    inputText: String,
    onTextChange: (String) -> Unit,
    onGenerate: () -> Unit,
    onScan: () -> Unit,
    showSaveButton: Boolean,
    onSave: () -> Unit,
    onReadFromGallery: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(
            value = inputText,
            onValueChange = onTextChange,
            label = { Text("Digite algo para criar o QR Code") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onGenerate, modifier = Modifier.fillMaxWidth()) {
            Text("Criar QR Code")
        }

        if (showSaveButton) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Text("Guardar Imagem")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
            Text("Ler QR Code com Câmera")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onReadFromGallery, modifier = Modifier.fillMaxWidth()) {
            Text("Ler QR Code da Galeria")
        }
    }
}

fun generateQRCode(text: String): Bitmap {
    val writer = QRCodeWriter()
    val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, 512, 512)
    val width = bitMatrix.width
    val height = bitMatrix.height
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
    for (x in 0 until width) {
        for (y in 0 until height) {
            bmp.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
        }
    }
    return bmp
}

fun saveImageToMediaStore(context: android.content.Context, bitmap: Bitmap, displayName: String) {
    val contentValues = ContentValues().apply {
        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "$displayName.png")
        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
    }

    val uri = context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
    if (uri != null) {
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            Toast.makeText(context, "Imagem salva na galeria!", Toast.LENGTH_SHORT).show()
        }
    } else {
        Toast.makeText(context, "Erro ao salvar imagem.", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalGetImage::class)
fun processImageProxy(
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    imageProxy: ImageProxy,
    onQRCodeDetected: (String) -> Unit
) {
    Log.d("QRCode", "Analisando imagem")
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                Log.d("QRCode", "Encontrados ${barcodes.size} códigos")
                if (barcodes.isNotEmpty()) {
                    val rawValue = barcodes[0].rawValue ?: ""
                    Log.d("QRCode", "QR Code detectado: $rawValue")
                    if (rawValue.isNotEmpty()) {
                        onQRCodeDetected(rawValue)
                    }
                }
            }
            .addOnFailureListener {
                // Ignora erro
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    } else {
        imageProxy.close()
    }
}
