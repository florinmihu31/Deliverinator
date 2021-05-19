package com.example.deliverinator.restaurant

import android.Manifest
import android.app.Activity.RESULT_OK
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.deliverinator.IMAGE_PICK_CODE
import com.example.deliverinator.PERMISSION_CODE
import com.example.deliverinator.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.android.synthetic.main.restaurant_add_item_dialog.view.*
import kotlinx.android.synthetic.main.restaurant_fragment_menu.view.*

class MenuFragment : Fragment(), MenuAdapter.OnItemClickListener {
    private lateinit var mAdapter: MenuAdapter
    private lateinit var mMenuItems: ArrayList<UploadMenuItem>
    private lateinit var mDialogImageView: ImageView
    private lateinit var mItemName: TextView
    private lateinit var mItemDescription: TextView
    private lateinit var mAuth: FirebaseAuth
    private lateinit var mStorageRef: StorageReference
    private lateinit var mDatabaseRef: DatabaseReference
    private var mImageUri: Uri? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.restaurant_fragment_menu, container, false)
        val listener = this

        mAuth = FirebaseAuth.getInstance()
        mStorageRef = FirebaseStorage.getInstance().getReference(mAuth.currentUser?.uid!!)
        mDatabaseRef = FirebaseDatabase.getInstance().getReference(mAuth.currentUser?.uid!!)

        mMenuItems = ArrayList()

        mDatabaseRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (postSnapshot in snapshot.children) {
                    val upload = postSnapshot.getValue(UploadMenuItem::class.java)

                    if (upload != null) {
                        mMenuItems.add(
                            UploadMenuItem(
                                upload.imageUrl,
                                upload.itemName,
                                upload.itemDescription,
                                upload.isAvailable
                            )
                        )
                    }
                }

                mAdapter = MenuAdapter(context!!, mMenuItems, listener)

                view.restaurant_fragment_recyclerView.adapter = mAdapter
                view.restaurant_fragment_recyclerView.layoutManager = LinearLayoutManager(context)
                view.restaurant_fragment_recyclerView.setHasFixedSize(true)
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(context, error.message, Toast.LENGTH_SHORT).show()
            }
        })

        view.restaurant_fragment_fab.setOnClickListener {
            addMenuItem(view)
        }

        return view
    }

    private fun addMenuItem(view: View) {
        val builder = AlertDialog.Builder(view.context)
        val inflater = layoutInflater
        val dialogLayout = inflater.inflate(R.layout.restaurant_add_item_dialog, null)

        mDialogImageView = dialogLayout.restaurant_add_dialog_imageView
        mItemName = dialogLayout.restaurant_add_name
        mItemDescription = dialogLayout.restaurant_add_description

        dialogLayout.restaurant_add_button.setOnClickListener {
            chooseImage()
        }

        builder.setTitle(R.string.add_item)
            .setPositiveButton(R.string.add) { _, _ ->
                mImageUri = null
            }
            .setNegativeButton(R.string.cancel, null)

        val dialog = builder.create()

        dialog.setView(dialogLayout)
        dialog.show()
    }

    private fun uploadFile() {
        val fileReference = mStorageRef.child(System.currentTimeMillis().toString() + "." +
                getFileExtension(mImageUri!!))

        if (mImageUri != null) {
            fileReference.putFile(mImageUri!!)
                .addOnSuccessListener {
                    /*val upload = UploadMenuItem(
                        it.storage.downloadUrl.toString(),
                        mItemName.text.toString().trim(),
                        mItemDescription.text.toString().trim(),
                        true
                    )
                    val uploadId = mDatabaseRef.push().key

                    if (uploadId != null) {
                        mDatabaseRef.child(uploadId).setValue(upload)
                    }*/
                    val urlTask = it.storage.downloadUrl
                    while (!urlTask.isSuccessful) {}

                    val downloadUrl = urlTask.result

                    val upload = UploadMenuItem(
                        downloadUrl.toString(),
                        mItemName.text.toString().trim(),
                        mItemDescription.text.toString().trim(),
                        true
                    )

                    val uploadId = mDatabaseRef.push().key

                    if (uploadId != null) {
                        mDatabaseRef.child(uploadId).setValue(upload)
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show()
                }
                .addOnProgressListener {
                    Toast.makeText(context, R.string.uploading_image, Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun getFileExtension(uri: Uri): String? {
        val contentResolver = context?.contentResolver
        val mime = MimeTypeMap.getSingleton()

        return mime.getExtensionFromMimeType(contentResolver?.getType(uri))
    }

    override fun onItemClick(position: Int) {
        mMenuItems.removeAt(position)
        mAdapter.notifyItemRemoved(position)
    }

    private fun chooseImage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val permission = context?.let { context ->
                ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
            }

            if (permission == PackageManager.PERMISSION_DENIED) {
                // Request permission
                val permissions = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

                // Show pop-up
                requestPermissions(permissions, PERMISSION_CODE)
            } else {
                chooseImageFromGallery()
            }
        } else {
            chooseImageFromGallery()
        }
    }

    private fun chooseImageFromGallery() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, IMAGE_PICK_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    chooseImageFromGallery()
                } else {
                    Toast.makeText(context, R.string.permission_denied, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == RESULT_OK && requestCode == IMAGE_PICK_CODE) {
            mDialogImageView.setImageURI(data?.data)
            mImageUri = data?.data!!
        }
    }
}