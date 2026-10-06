package com.buddygo.buddygoserver

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class BuddygoServerApplication

fun main(args: Array<String>) {
    runApplication<BuddygoServerApplication>(*args)
}
