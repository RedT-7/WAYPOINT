package com.example.data.remote

import android.util.Log
import com.example.data.model.DeviceState
import com.example.data.model.LocationPoint
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FirebaseLocationManager {

    private val tag = "FirebaseLocationManager"

    private var databaseReference: DatabaseReference? = null
    private var activeRoomCode: String? = null
    private var myDeviceId: String? = null
    private var roomValueEventListener: ValueEventListener? = null

    private val _partnerState = MutableStateFlow<DeviceState?>(null)
    val partnerState: StateFlow<DeviceState?> = _partnerState.asStateFlow()

    private val _connectionLatencyMs = MutableStateFlow<Long>(0L)
    val connectionLatencyMs: StateFlow<Long> = _connectionLatencyMs.asStateFlow()

    private val _isFirebaseConnected = MutableStateFlow(false)
    val isFirebaseConnected: StateFlow<Boolean> = _isFirebaseConnected.asStateFlow()

    fun initialize(customUrl: String? = null) {
        try {
            val db = if (!customUrl.isNullOrBlank()) {
                FirebaseDatabase.getInstance(customUrl)
            } else {
                FirebaseDatabase.getInstance()
            }
            databaseReference = db.reference
            _isFirebaseConnected.value = true
            Log.d(tag, "Firebase Realtime Database initialized successfully")
        } catch (e: Exception) {
            Log.w(tag, "Firebase Realtime Database initialization warning: ${e.message}")
            _isFirebaseConnected.value = false
        }
    }

    fun joinRoom(roomCode: String, deviceId: String, deviceName: String, avatarColorIndex: Int) {
        if (databaseReference == null) {
            initialize()
        }

        leaveCurrentRoom()

        activeRoomCode = roomCode
        myDeviceId = deviceId

        try {
            val rootRef = databaseReference ?: return
            val roomRef = rootRef.child("rooms").child(roomCode)
            val myDeviceRef = roomRef.child("devices").child(deviceId)

            // Presence management
            myDeviceRef.child("isOnline").setValue(true)
            myDeviceRef.child("deviceName").setValue(deviceName)
            myDeviceRef.child("avatarColorIndex").setValue(avatarColorIndex)
            myDeviceRef.child("lastSeenTimestamp").setValue(System.currentTimeMillis())

            try {
                myDeviceRef.child("isOnline").onDisconnect().setValue(false)
            } catch (_: Exception) {}

            // Listen to partner devices in this room
            roomValueEventListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val devicesSnapshot = snapshot.child("devices")
                    for (child in devicesSnapshot.children) {
                        val otherDeviceId = child.key ?: continue
                        if (otherDeviceId != deviceId) {
                            // This is the partner!
                            val pName = child.child("deviceName").getValue(String::class.java) ?: "Partner"
                            val pAvatar = child.child("avatarColorIndex").getValue(Long::class.java)?.toInt() ?: 1
                            val pIsSharing = child.child("isSharing").getValue(Boolean::class.java) ?: true
                            val pIsOnline = child.child("isOnline").getValue(Boolean::class.java) ?: true
                            val pLastSeen = child.child("lastSeenTimestamp").getValue(Long::class.java) ?: 0L

                            val locSnapshot = child.child("location")
                            val lat = locSnapshot.child("latitude").getValue(Double::class.java) ?: 0.0
                            val lng = locSnapshot.child("longitude").getValue(Double::class.java) ?: 0.0
                            val alt = locSnapshot.child("altitude").getValue(Double::class.java) ?: 0.0
                            val acc = locSnapshot.child("accuracy").getValue(Double::class.java)?.toFloat() ?: 0f
                            val spd = locSnapshot.child("speed").getValue(Double::class.java)?.toFloat() ?: 0f
                            val brg = locSnapshot.child("bearing").getValue(Double::class.java)?.toFloat() ?: 0f
                            val ts = locSnapshot.child("timestamp").getValue(Long::class.java) ?: pLastSeen
                            val bat = locSnapshot.child("batteryLevel").getValue(Long::class.java)?.toInt() ?: 100
                            val chg = locSnapshot.child("isCharging").getValue(Boolean::class.java) ?: false

                            // Calculate network transmission latency
                            val currentNow = System.currentTimeMillis()
                            val latency = (currentNow - ts).coerceAtLeast(0L)
                            _connectionLatencyMs.value = latency

                            val partnerLocation = LocationPoint(
                                latitude = lat,
                                longitude = lng,
                                altitude = alt,
                                accuracy = acc,
                                speed = spd,
                                bearing = brg,
                                timestamp = ts,
                                batteryLevel = bat,
                                isCharging = chg
                            )

                            _partnerState.value = DeviceState(
                                deviceId = otherDeviceId,
                                deviceName = pName,
                                avatarColorIndex = pAvatar,
                                location = partnerLocation,
                                lastSeenTimestamp = pLastSeen,
                                isSharing = pIsSharing,
                                isOnline = pIsOnline
                            )
                            return
                        }
                    }
                    // No partner yet
                    if (devicesSnapshot.childrenCount <= 1) {
                        _partnerState.value = null
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(tag, "Firebase Database Error: ${error.message}")
                }
            }

            roomRef.addValueEventListener(roomValueEventListener!!)
        } catch (e: Exception) {
            Log.e(tag, "Failed to join room: ${e.message}")
        }
    }

    fun publishLocation(
        location: LocationPoint,
        deviceName: String,
        avatarColorIndex: Int,
        isSharing: Boolean
    ) {
        val roomCode = activeRoomCode ?: return
        val deviceId = myDeviceId ?: return
        val rootRef = databaseReference ?: return

        try {
            val deviceRef = rootRef.child("rooms").child(roomCode).child("devices").child(deviceId)
            val updates = HashMap<String, Any>()
            updates["deviceName"] = deviceName
            updates["avatarColorIndex"] = avatarColorIndex
            updates["isSharing"] = isSharing
            updates["isOnline"] = true
            updates["lastSeenTimestamp"] = System.currentTimeMillis()

            val locMap = HashMap<String, Any>()
            locMap["latitude"] = location.latitude
            locMap["longitude"] = location.longitude
            locMap["altitude"] = location.altitude
            locMap["accuracy"] = location.accuracy
            locMap["speed"] = location.speed
            locMap["bearing"] = location.bearing
            locMap["timestamp"] = location.timestamp
            locMap["batteryLevel"] = location.batteryLevel
            locMap["isCharging"] = location.isCharging

            updates["location"] = locMap

            deviceRef.updateChildren(updates)
        } catch (e: Exception) {
            Log.w(tag, "Error publishing location to Firebase: ${e.message}")
        }
    }

    fun leaveCurrentRoom() {
        try {
            val roomCode = activeRoomCode
            val deviceId = myDeviceId
            if (roomCode != null && deviceId != null && databaseReference != null) {
                val roomRef = databaseReference!!.child("rooms").child(roomCode)
                roomValueEventListener?.let { roomRef.removeEventListener(it) }
                roomRef.child("devices").child(deviceId).child("isOnline").setValue(false)
            }
        } catch (e: Exception) {
            Log.w(tag, "Error leaving room: ${e.message}")
        } finally {
            activeRoomCode = null
            roomValueEventListener = null
            _partnerState.value = null
            _connectionLatencyMs.value = 0L
        }
    }

    /**
     * Fallback / Simulator support: Allows previewing or testing paired location exchange
     * even without a live Firebase network connection.
     */
    fun injectSimulatedPartnerLocation(point: LocationPoint, partnerName: String = "Partner Device") {
        _partnerState.value = DeviceState(
            deviceId = "simulated_partner_1",
            deviceName = partnerName,
            avatarColorIndex = 2,
            location = point,
            lastSeenTimestamp = System.currentTimeMillis(),
            isSharing = true,
            isOnline = true
        )
        _connectionLatencyMs.value = 45L
    }
}
