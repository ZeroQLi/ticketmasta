package com.github.zeroqli.ticketmasta.model

data class Ticket(
    val number: Int,
    val title: String,
    val body: String,
    val state: String,
    val labels: List<String>,
    val htmlUrl: String,
) {
    override fun toString(): String = "#$number $title"
}
