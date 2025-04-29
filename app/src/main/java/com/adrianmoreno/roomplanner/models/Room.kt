package com.adrianmoreno.roomplanner.models

data class Room(
    val id: String = "",
    val hotelRef: String = "",
    val number: String = "",
    val status: String = ""            // "OCCUPIED", "VACANT", "DIRTY", "CLEANING"
){
    override fun toString(): String{
        return "Room(id='$id',hotelRef='$hotelRef',number='$number',status='$status')"
    }
}