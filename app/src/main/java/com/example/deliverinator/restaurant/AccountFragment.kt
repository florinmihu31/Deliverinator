package com.example.deliverinator.restaurant

import android.Manifest
import android.app.Activity.RESULT_OK
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.method.PasswordTransformationMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.example.deliverinator.*
import com.example.deliverinator.Utils.Companion.hideKeyboard
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.restaurant_fragment_account.view.*

class AccountFragment : Fragment() {
    private lateinit var mAccountImageView: ImageView
    private lateinit var mRestaurantName: TextView
    private lateinit var mDescription: TextView
    private lateinit var mChangePassword: TextView
    private lateinit var mChooseImage: Button
    private lateinit var mApplyChanges: Button
    private lateinit var mProgressBar: ProgressBar
    private lateinit var mAuth: FirebaseAuth
    private lateinit var mStore: FirebaseFirestore
    private lateinit var mStorageRef: StorageReference
    private lateinit var mDocRestaurantsRef: DocumentReference
    private lateinit var mDatabaseRef: DatabaseReference
    private var mImageUri: Uri? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.restaurant_fragment_account, container, false)

        mAccountImageView = view.account_fragment_imageView
        mRestaurantName = view.account_fragment_name
        mDescription = view.account_fragment_description
        mChangePassword = view.account_fragment_change_button
        mChooseImage = view.account_fragment_choose_button
        mApplyChanges = view.account_fragment_apply_changes
        mProgressBar = view.account_fragment_progressBar
        mAuth  = FirebaseAuth.getInstance()
        mStore = FirebaseFirestore.getInstance()
        mStorageRef = FirebaseStorage.getInstance().getReference(mAuth.currentUser?.uid!!)
        mDocRestaurantsRef = mStore.collection(RESTAURANTS).document(mAuth.currentUser?.uid!!)
        mDatabaseRef = FirebaseDatabase.getInstance().getReference(mAuth.currentUser?.uid!!)

        val user = mAuth.currentUser
        val docUsersRef = mStore.collection(USERS).document(user?.uid!!)

        mDocRestaurantsRef.get().addOnSuccessListener {
            mRestaurantName.text = it.getString(NAME)

            if (it.getString(RESTAURANT_DESCRIPTION)!!.isNotEmpty()) {
                mDescription.text = it.getString(RESTAURANT_DESCRIPTION)
            }

            if (it.getString(RESTAURANT_IMAGE) != null) {
                Picasso.with(context)
                    .load(it.getString(RESTAURANT_IMAGE))
                    .placeholder(R.drawable.ic_restaurant)
                    .fit()
                    .centerCrop()
                    .into(view.account_fragment_imageView)
            }
        }

        mChooseImage.setOnClickListener {
            chooseImage()
        }

        mApplyChanges.setOnClickListener {
            applyChanges(mDocRestaurantsRef, docUsersRef)
        }

        mChangePassword.setOnClickListener {
            sendChangePasswordEmail()
        }

        return view
    }

    private fun uploadFile() {
        val fileReference = mStorageRef.child(System.currentTimeMillis().toString() + "." +
                getFileExtension(mImageUri!!))

        fileReference.putFile(mImageUri!!)
            .addOnSuccessListener {
                val urlTask = it.storage.downloadUrl

                while (!urlTask.isSuccessful) {}

                val downloadUrl = urlTask.result
                val restaurantImage = mutableMapOf(RESTAURANT_IMAGE to downloadUrl.toString())

                mDocRestaurantsRef.set(restaurantImage, SetOptions.merge())
            }
            .addOnFailureListener {
                Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show()
            }
            .addOnProgressListener {
                Toast.makeText(context, R.string.uploading_image, Toast.LENGTH_SHORT).show()
            }
    }

    private fun getFileExtension(uri: Uri): String? {
        val contentResolver = context?.contentResolver
        val mime = MimeTypeMap.getSingleton()

        return mime.getExtensionFromMimeType(contentResolver?.getType(uri))
    }

    private fun applyChanges(docRestaurantRef: DocumentReference, docUsersRef: DocumentReference) {
        val restaurantName = mRestaurantName.text
        val description = mDescription.text

        docRestaurantRef.get().addOnSuccessListener { docSnap ->
            if (restaurantName.toString() == docSnap.getString(NAME) &&
                description.toString() == docSnap.getString(ADDRESS)
            ) {
                mProgressBar.visibility = View.VISIBLE

                Toast.makeText(context, R.string.no_changes, Toast.LENGTH_SHORT).show()

                mProgressBar.visibility = View.INVISIBLE
            } else {
                if (restaurantName.isEmpty()) {
                    mRestaurantName.error = getString(R.string.empty_field)
                    return@addOnSuccessListener
                }

                if (description.isEmpty()) {
                    mDescription.error = getString(R.string.empty_field)
                    return@addOnSuccessListener
                }

                mProgressBar.visibility = View.VISIBLE

                val restaurantInfo = HashMap<String, Any>()

                restaurantInfo[NAME] = restaurantName.toString()
                restaurantInfo[RESTAURANT_DESCRIPTION] = description.toString()

                hideKeyboard()

                docRestaurantRef.update(restaurantInfo).addOnSuccessListener {
                    docUsersRef.update(restaurantInfo).addOnSuccessListener {
                        Toast.makeText(context, R.string.updated_account, Toast.LENGTH_SHORT).show()

                        mProgressBar.visibility = View.INVISIBLE
                    }
                }
            }
        }
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

    private fun sendChangePasswordEmail() {
        mAuth.sendPasswordResetEmail(mAuth.currentUser!!.email!!)
            .addOnSuccessListener {
                Toast.makeText(context, R.string.reset_link_sent, Toast.LENGTH_SHORT)
                    .show()
            }
            .addOnFailureListener {
                Toast.makeText(context, getString(R.string.link_not_sent) + it.message, Toast.LENGTH_LONG)
                    .show()
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

        mImageUri = if (resultCode == RESULT_OK && requestCode == IMAGE_PICK_CODE) {
            mAccountImageView.setImageURI(data?.data)
            data?.data
        } else {
            null
        }

        if (mImageUri != null) {
            uploadFile()
        }
    }
}