package com.adrianmoreno.roomplanner.models

import com.google.firebase.Timestamp

data class ReservedRange(
    val from: Timestamp = Timestamp.now(),
    val to:   Timestamp = Timestamp.now()
)
