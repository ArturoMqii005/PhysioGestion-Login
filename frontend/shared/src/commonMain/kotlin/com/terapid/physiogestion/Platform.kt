package com.terapid.physiogestion

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform