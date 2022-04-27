package com.asifddlks.takeimagefromcameraorgallery

import android.app.Activity
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.InputStream


class MainActivity : AppCompatActivity() {
    //Our variables
    private var mImageView: ImageView? = null
    private var mUri: Uri? = null
    //Our widgets
    private lateinit var btnCapture: Button
    private lateinit var btnChoose : Button

    private fun show(message: String) {
        Toast.makeText(this,message, Toast.LENGTH_SHORT).show()
    }
    private fun capturePhoto(){
        val capturedImage = File(externalCacheDir, "My_Captured_Photo.jpg")
        if(capturedImage.exists()) {
            capturedImage.delete()
        }
        capturedImage.createNewFile()
        mUri = if(Build.VERSION.SDK_INT >= 24){
            FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".fileprovider",
                capturedImage)
        } else {
            Uri.fromFile(capturedImage)
        }

        val intent = Intent("android.media.action.IMAGE_CAPTURE")
        intent.putExtra(MediaStore.EXTRA_OUTPUT, mUri)
        resultLauncher.launch(intent)
    }
    private fun openGallery(){
        val intent = Intent("android.intent.action.GET_CONTENT")
        intent.type = "image/*"
        resultLauncher.launch(intent)
    }
    private fun renderImage(imagePath: String?): Bitmap?{
        return if (imagePath != null) {
            BitmapFactory.decodeFile(imagePath)
        } else {
            show("ImagePath is null")
            null
        }
    }
    private fun getRealPathFromURI(contentURI: Uri, selection: String?): String? {
        val result: String?
        val filePathColumn = arrayOf(MediaStore.Images.Media.DATA)
        val cursor: Cursor? = contentResolver.query(contentURI, filePathColumn, selection, null, null)
        if (cursor == null) { // Source is Dropbox or other similar local file path
            result = contentURI.path
        } else {
            cursor.moveToFirst()
            val idx: Int = cursor.getColumnIndex(filePathColumn[0])
            result = cursor.getString(idx)
            cursor.close()
        }
        return result
    }

    private fun handleImage(data: Intent?): Bitmap? {
        var imagePath: String? = null
        val uri = data!!.data
        //DocumentsContract defines the contract between a documents provider and the platform.
        if (DocumentsContract.isDocumentUri(this, uri)){
            val docId = DocumentsContract.getDocumentId(uri)
            if ("com.android.providers.media.documents" == uri!!.authority){
                val id = docId.split(":")[1]
                val selection = MediaStore.Images.Media._ID + "=" + id
                //imagePath = getImagePath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, selsetion)
                imagePath = getRealPathFromURI(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, selection)
            }
            else if ("com.android.providers.downloads.documents" == uri.authority){
                val contentUri = ContentUris.withAppendedId(
                    Uri.parse(
                        "content://downloads/public_downloads"), java.lang.Long.valueOf(docId))
                imagePath = getRealPathFromURI(contentUri, null)
            }
        }
        else if ("content".equals(uri!!.scheme, ignoreCase = true)){
            imagePath = getRealPathFromURI(uri,null)
        }
        else if ("file".equals(uri.scheme, ignoreCase = true)){
            imagePath = uri.path
        }
        return renderImage(imagePath)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>
                                            , grantedResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantedResults)
        when(requestCode){
            1 ->
                if (grantedResults.isNotEmpty() && grantedResults.get(0) ==
                    PackageManager.PERMISSION_GRANTED){
                    openGallery()
                }else {
                    show("Unfortunately You are Denied Permission to Perform this Operataion.")
                }
        }
    }

    private var resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // There are no request codes
            val data: Intent? = result.data

            if(data!=null){
                mImageView!!.setImageBitmap(handleImage(data))
            }
            else{
                mUri?.let {
                    val bitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(it))
                    val input: InputStream? =
                        this.contentResolver.openInputStream(it)

                    val ei = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        input?.let { it1 -> ExifInterface(it1) }
                    } else {
                        it.path?.let { it1 -> ExifInterface(it1) }
                    }
                    val orientation: Int = ei?.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_UNDEFINED
                    ) ?: kotlin.run {
                        ExifInterface.ORIENTATION_NORMAL
                    }

                    var rotatedBitmap: Bitmap? = null
                    rotatedBitmap =
                        when (orientation) {
                            ExifInterface.ORIENTATION_ROTATE_90 -> rotateImage(bitmap, 90F)
                            ExifInterface.ORIENTATION_ROTATE_180 -> rotateImage(bitmap, 180F)
                            ExifInterface.ORIENTATION_ROTATE_270 -> rotateImage(bitmap, 270F)
                            ExifInterface.ORIENTATION_NORMAL -> bitmap
                            else -> bitmap
                        }


                    mImageView!!.setImageBitmap(rotatedBitmap)
                }
            }
        }
    }

    fun rotateImage(source: Bitmap, angle: Float): Bitmap? {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(
            source, 0, 0, source.width, source.height,
            matrix, true
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnCapture = findViewById(R.id.btnCapture)
        btnChoose = findViewById(R.id.btnChoose)
        mImageView = findViewById(R.id.mImageView)

        btnCapture.setOnClickListener{
            capturePhoto()
        }
        btnChoose.setOnClickListener{
            //check permission at runtime
            val checkSelfPermission = ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            if (checkSelfPermission != PackageManager.PERMISSION_GRANTED){
                //Requests permissions to be granted to this application at runtime
                ActivityCompat.requestPermissions(this,
                    arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
            }
            else{
                openGallery()
            }
        }
    }
}