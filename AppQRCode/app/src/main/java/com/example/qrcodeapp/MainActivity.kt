package com.example.qrcodeapp

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.qrcodeapp.ui.theme.AppQRCodeTheme
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    private lateinit var cameraExecutor: ExecutorService

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                Toast.makeText(this, "Permissão negada para acessar o armazenamento", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

    val scanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        BarcodeScanning.getClient(options)
    }

    val previewView = remember { androidx.camera.view.PreviewView(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!showPreview) {
            // Campo para texto
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                label = { Text("Digite algo para criar o QR Code") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Botão gerar QR
            Button(onClick = {
                if (inputText.isNotBlank()) {
                    try {
                        generatedBitmap = generateQRCode(inputText)
                        showSaveButton = true
                        showPreview = false
                    } catch (e: Exception) {
                        Toast.makeText(context, "Erro ao criar QR Code", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Digite algo para criar o QR Code", Toast.LENGTH_SHORT).show()
                }
            }) {
                Text("Criar QR Code")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Imagem do QR
            generatedBitmap?.let {
                Image(bitmap = it.asImageBitmap(), contentDescription = "QR Code criado", modifier = Modifier.size(256.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Botão salvar (aparece só se gerou QR)
            if (showSaveButton) {
                Button(onClick = {
                    generatedBitmap?.let { bmp ->
                        saveImageToMediaStore(context, bmp, "qrcode_${System.currentTimeMillis()}")
                    } ?: Toast.makeText(context, "Nenhum QR Code para salvar", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Guardar Imagem")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Botão para escanear QR Code
            Button(onClick = {
                showPreview = true
                showSaveButton = false
                generatedBitmap = null
            }) {
                Text("Ler QR Code")
            }
        } else {
            // Preview da câmera para escanear QR Code
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
                        processImageProxy(scanner, imageProxy, activity, previewView) {
                            // Quando detectar um QR, volta para a tela principal e mostra alerta
                            showPreview = false
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

// Geração do QR Code (igual seu código original)
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

// Salvar imagem no MediaStore
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
    activity: ComponentActivity,
    previewView: androidx.camera.view.PreviewView,
    onQRCodeDetected: () -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                if (barcodes.isNotEmpty()) {
                    val rawValue = barcodes[0].rawValue ?: ""
                    activity.runOnUiThread {
                        androidx.appcompat.app.AlertDialog.Builder(activity)
                            .setTitle("QR Code lido")
                            .setMessage(rawValue)
                            .setPositiveButton("OK") { dialog, _ ->
                                dialog.dismiss()
                                onQRCodeDetected()
                            }
                            .show()
                    }
                }
            }
            .addOnFailureListener {
                // erro ignorado
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    } else {
        imageProxy.close()
    }
}
