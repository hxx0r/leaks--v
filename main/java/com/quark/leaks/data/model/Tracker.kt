package com.quark.leaks.data.model

data class Tracker(
    val id: String,
    val name: String,
    val category: String,
    val domains: List<String>,
    val ips: List<String> = emptyList(),
    val description: String? = null,
    val severity: Int = 1 // 1-3: low, medium, high
) {
    fun matchesDomain(query: String): Boolean {
        return domains.any { domain ->
            query.contains(domain, ignoreCase = true) ||
                    domain.contains(query, ignoreCase = true)
        }
    }

    fun matchesIp(ip: String): Boolean {
        return ips.contains(ip)
    }
}