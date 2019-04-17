package com.example.chatnearby.nearby

import android.content.pm.PackageManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import com.example.chatnearby.R
import com.google.android.gms.location.*
import kotlinx.android.synthetic.main.activity_get_location.*
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.chatnearby.messages.ChatLogActivity
import com.example.chatnearby.models.User
import com.example.chatnearby.views.BigImageDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.Item
import com.xwray.groupie.ViewHolder
import kotlinx.android.synthetic.main.user_row_new_message.view.*

class GetLocationActivity : AppCompatActivity() {

    private var permissions = arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION)
    private var REQUEST_CODE = 1001
    private lateinit var fusedLocationClient : FusedLocationProviderClient
    private lateinit var locationRequest : LocationRequest
    private lateinit var locationCallback : LocationCallback

    private var locationUpdateState = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_get_location)

        if(ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE)
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        createLocationRequest()

        locationCallback = object :LocationCallback() {
            override fun onLocationResult(p0: LocationResult) {
                super.onLocationResult(p0)

                var lastLocation = p0.lastLocation
                var myLat: Double = lastLocation.latitude

                var myLong: Double = lastLocation.longitude

            }
        }

        startLocationUpdates()


        swiperefresh.setColorSchemeColors(ContextCompat.getColor(this, R.color.colorAccent))

        supportActionBar?.title = "Select User"

        fetchUsers()

        swiperefresh.setOnRefreshListener {
            fetchUsers()
        }
    }

    override fun onResume() {
        super.onResume()
        if(locationUpdateState) {
            startLocationUpdates()
        }
    }

    override fun onPause() {
        super.onPause()
        this.fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun startLocationUpdates() {
        if(ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        this.fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if(requestCode == REQUEST_CODE) {
            if(grantResults.size == 2 && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates()
            }
        }
    }

    private fun createLocationRequest() {
        locationRequest = LocationRequest()

        locationRequest.interval = 10000

        locationRequest.fastestInterval = 5000
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        var builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)

        val client = LocationServices.getSettingsClient(this)
        val task = client.checkLocationSettings(builder.build())

        task.addOnSuccessListener {
            locationUpdateState = true
            startLocationUpdates()
        }
    }


    // distance calculate algorithm
    private fun getDistandce(myLat: Double, myLong: Double, hisLat: Double, hisLong: Double) : Double {
        // 角度转为弧度
        var lat1 :Double = myLat * Math.PI/180.0
        var lon1: Double = myLong * Math.PI/180.0
        var lat2: Double = hisLat * Math.PI/180.0
        var lon2: Double = hisLong * Math.PI/180.0


        val a = 6378137.0 // WGS84 major axis
        val b = 6356752.3142 // WGS84 semi-major axis
        val f = (a - b) / a
        val aSqMinusBSqOverBSq = (a * a - b * b) / (b * b)

        val L = lon2 - lon1
        var A = 0.0
        val U1 = Math.atan((1.0 - f) * Math.tan(lat1))
        val U2 = Math.atan((1.0 - f) * Math.tan(lat2))

        val cosU1 = Math.cos(U1)
        val cosU2 = Math.cos(U2)
        val sinU1 = Math.sin(U1)
        val sinU2 = Math.sin(U2)
        val cosU1cosU2 = cosU1 * cosU2
        val sinU1sinU2 = sinU1 * sinU2

        var sigma = 0.0
        var deltaSigma = 0.0
        var cosSqAlpha = 0.0
        var cos2SM = 0.0
        var cosSigma = 0.0
        var sinSigma = 0.0
        var cosLambda = 0.0
        var sinLambda = 0.0

        var lambda = L // initial guess
        for (iter in 0 until 20) {
            val lambdaOrig = lambda
            cosLambda = Math.cos(lambda)
            sinLambda = Math.sin(lambda)
            val t1 = cosU2 * sinLambda
            val t2 = cosU1 * sinU2 - sinU1 * cosU2 * cosLambda
            val sinSqSigma = t1 * t1 + t2 * t2 // (14)
            sinSigma = Math.sqrt(sinSqSigma)
            cosSigma = sinU1sinU2 + cosU1cosU2 * cosLambda // (15)
            sigma = Math.atan2(sinSigma, cosSigma) // (16)
            val sinAlpha = if (sinSigma == 0.0)
                0.0
            else
                cosU1cosU2 * sinLambda / sinSigma // (17)
            cosSqAlpha = 1.0 - sinAlpha * sinAlpha
            cos2SM = if (cosSqAlpha == 0.0)
                0.0
            else
                cosSigma - 2.0 * sinU1sinU2 / cosSqAlpha // (18)

            val uSquared = cosSqAlpha * aSqMinusBSqOverBSq // defn
            A = 1 + uSquared / 16384.0 * // (3)
                    (4096.0 + uSquared * (-768 + uSquared * (320.0 - 175.0 * uSquared)))
            val B = uSquared / 1024.0 * // (4)
                    (256.0 + uSquared * (-128.0 + uSquared * (74.0 - 47.0 * uSquared)))
            val C = f / 16.0 *
                    cosSqAlpha *
                    (4.0 + f * (4.0 - 3.0 * cosSqAlpha)) // (10)
            val cos2SMSq = cos2SM * cos2SM
            deltaSigma = B * sinSigma * // (6)

                    (cos2SM + B / 4.0 * (cosSigma * (-1.0 + 2.0 * cos2SMSq) - B / 6.0 * cos2SM *
                            (-3.0 + 4.0 * sinSigma * sinSigma) *
                            (-3.0 + 4.0 * cos2SMSq)))

            lambda = L + (1.0 - C) * f * sinAlpha *
                    (sigma + C * sinSigma *
                            (cos2SM + C * cosSigma *
                                    (-1.0 + 2.0 * cos2SM * cos2SM))) // (11)

            val delta = (lambda - lambdaOrig) / lambda
            if (Math.abs(delta) < 1.0e-12) {
                break
            }
        }
        return b * A * (sigma - deltaSigma)
    }

    companion object {
        const val USER_KEY = "USER_KEY"
        private val TAG = GetLocationActivity::class.java.simpleName
    }



    private fun fetchUsers() {
        swiperefresh.isRefreshing = true

        val ref = FirebaseDatabase.getInstance().getReference("/users")
        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onCancelled(databaseError: DatabaseError) {

            }

            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val adapter = GroupAdapter<ViewHolder>()
                var myLong = 0.0
                var myLat = 0.0
                dataSnapshot.children.forEach {
                    Log.d(TAG, it.toString())
                    @Suppress("NestedLambdaShadowedImplicitParameter")
                    it.getValue(User::class.java)?.let {
                        var id  = FirebaseAuth.getInstance().uid

                        if (it.uid == id) {
                            myLong = it.lon
                            myLat = it.lat

                        }
                        if (it.uid != id){
                            //here judge the lat and lon
                            var dis = getDistandce(myLat,myLong,it.lat,it.lon)
                            if (dis < 10000000.0) {
                            adapter.add(UserItem(it, this@GetLocationActivity))
                            }
                        }
                    }
                }

                adapter.setOnItemClickListener { item, view ->
                    val userItem = item as UserItem
                    val intent = Intent(view.context, ChatLogActivity::class.java)
                    intent.putExtra(USER_KEY, userItem.user)
                    startActivity(intent)
                    finish()
                }

                recyclerview_newmessage.adapter = adapter
                swiperefresh.isRefreshing = false
            }

        })
    }
}

class UserItem(val user: User, val context: Context) : Item<ViewHolder>() {

    override fun bind(viewHolder: ViewHolder, position: Int) {
        viewHolder.itemView.username_textview_new_message.text = user.name

        if (!user.profileImageUrl!!.isEmpty()) {
            val requestOptions = RequestOptions().placeholder(R.drawable.no_image2)


            Glide.with(viewHolder.itemView.imageview_new_message.context)
                .load(user.profileImageUrl)
                .apply(requestOptions)
                .into(viewHolder.itemView.imageview_new_message)

            viewHolder.itemView.imageview_new_message.setOnClickListener {
                BigImageDialog.newInstance(user?.profileImageUrl!!).show((context as Activity).fragmentManager
                    , "")
            }
        }
    }

    override fun getLayout(): Int {
        return R.layout.user_row_new_message
    }

}



