package com.example.open_nsfw_android

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.zwy.nsfw.api.NsfwHelper
import kotlinx.android.synthetic.main.activity_main.*
import java.io.FileInputStream


class MainActivity : AppCompatActivity() {
    private var nsfwHelper: NsfwHelper? = null
    private val REQUEST_CODE_GALLERY = 100
    private var b: Bitmap? = null

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        //assets 目录下的timg-10.jpeg为正常静态图片  ccc.gif 为动态正常图片 可用作测试
        b = BitmapFactory.decodeStream(resources.assets.open("icon.png"))
        iv.setImageBitmap(b)
        nsfwHelper = NsfwHelper.getInstance(this, true, 1)

        bt1.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, REQUEST_CODE_GALLERY)
        }

        bt_.setOnClickListener {
            //同步识别
            val nsfwBean = nsfwHelper?.scanBitmapSyn(b)
            Log.d("demo", nsfwBean.toString())
            tvv.text = "识别成功：\n\tSFW score : ${nsfwBean?.sfw}\n\tNSFW score : ${nsfwBean?.nsfw}"
            if (nsfwBean?.nsfw ?: 0f > 0.7) {
                tvv.text = "${tvv.text} \n \t - 色情图片"
            } else {
                tvv.text = "${tvv.text} \n \t - 正常图片"
            }
//            //异步识别，接口回调识别结果
//            nsfwHelper?.scanBitmap(b) { sfw, nsfw ->
//                Log.d("demo", "sfw:$sfw,nsfw:$nsfw")
//            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_GALLERY) {
            val uri = data?.data
            val path = UriToPathUtil.getRealFilePath(this, uri)
            Log.w("uri", uri.toString() + ", $path")

            b = BitmapFactory.decodeStream(FileInputStream(path))
            iv.setImageBitmap(b)
        }
    }

    object UriToPathUtil {
        fun getRealFilePath(context: Context, uri: Uri?): String? {
            if (null == uri)
                return null
            val scheme = uri.scheme
            var data: String? = null
            if (scheme == null)
                data = uri.path
            else if (ContentResolver.SCHEME_FILE == scheme) {
                data = uri.path
            } else if (ContentResolver.SCHEME_CONTENT == scheme) {
                val cursor = context.contentResolver
                    .query(uri, arrayOf(MediaStore.Images.ImageColumns.DATA), null, null, null)
                if (null != cursor) {
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA)
                        if (index > -1) {
                            data = cursor.getString(index)
                        }

                    }
                    cursor.close()
                }
                if (data == null) {
                    data = getImageAbsolutePath(context, uri)
                }

            }
            return data
        }

        /**
         * 根据Uri获取图片绝对路径，解决Android4.4以上版本Uri转换
         *
         */
        @TargetApi(19)
        fun getImageAbsolutePath(context: Context?, imageUri: Uri?): String? {
            if (context == null || imageUri == null)
                return null
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT && DocumentsContract.isDocumentUri(
                    context,
                    imageUri
                )
            ) {
                if (isExternalStorageDocument(imageUri)) {
                    val docId = DocumentsContract.getDocumentId(imageUri)
                    val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    val type = split[0]
                    if ("primary".equals(type, ignoreCase = true)) {
//                        return Environment.getExternalStorageDirectory() + "/" + split[1]
                        return ""
                    }
                } else if (isDownloadsDocument(imageUri)) {
                    val id = DocumentsContract.getDocumentId(imageUri)
                    val contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"),
                        java.lang.Long.valueOf(id)
                    )
                    return getDataColumn(context, contentUri, null, null)
                } else if (isMediaDocument(imageUri)) {
                    val docId = DocumentsContract.getDocumentId(imageUri)
                    val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    val type = split[0]
                    var contentUri: Uri? = null
                    if ("image" == type) {
                        contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    } else if ("video" == type) {
                        contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    } else if ("audio" == type) {
                        contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    }
                    val selection = MediaStore.Images.Media._ID + "=?"
                    val selectionArgs = arrayOf(split[1])
                    return getDataColumn(context, contentUri, selection, selectionArgs)
                }
            } // MediaStore (and general)
            else if ("content".equals(imageUri.scheme, ignoreCase = true)) {
                // Return the remote address
                return if (isGooglePhotosUri(imageUri)) imageUri.lastPathSegment else getDataColumn(
                    context,
                    imageUri,
                    null,
                    null
                )
            } else if ("file".equals(imageUri.scheme, ignoreCase = true)) {
                return imageUri.path
            }// File
            return null
        }

        fun getDataColumn(context: Context, uri: Uri?, selection: String?, selectionArgs: Array<String>?): String? {
            var cursor: Cursor? = null
            val column = MediaStore.Images.Media.DATA
            val projection = arrayOf(column)
            try {
                cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndexOrThrow(column)
                    return cursor.getString(index)
                }
            } finally {
                cursor?.close()
            }
            return null
        }

        /**
         * @param uri
         * The Uri to check.
         * @return Whether the Uri authority is ExternalStorageProvider.
         */
        private fun isExternalStorageDocument(uri: Uri): Boolean {
            return "com.android.externalstorage.documents" == uri.authority
        }

        /**
         * @param uri
         * The Uri to check.
         * @return Whether the Uri authority is DownloadsProvider.
         */
        private fun isDownloadsDocument(uri: Uri): Boolean {
            return "com.android.providers.downloads.documents" == uri.authority
        }

        /**
         * @param uri
         * The Uri to check.
         * @return Whether the Uri authority is MediaProvider.
         */
        private fun isMediaDocument(uri: Uri): Boolean {
            return "com.android.providers.media.documents" == uri.authority
        }

        /**
         * @param uri
         * The Uri to check.
         * @return Whether the Uri authority is Google Photos.
         */
        private fun isGooglePhotosUri(uri: Uri): Boolean {
            return "com.google.android.apps.photos.content" == uri.authority
        }
    }

}
