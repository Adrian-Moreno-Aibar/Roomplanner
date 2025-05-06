package com.adrianmoreno.roomplanner.models
data class Hotel(
    val id: String = "",
    val name: String = "",
    val address: String = "",
    val createdBy: String = "",
    val photoUrl: String= ""
)
{
    override fun toString(): String{
        return "Hotel(id='$id',name='$name',address='$address',createdBy='$createdBy','photoUrl'='$photoUrl')"
    }
}