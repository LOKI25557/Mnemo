package com.mnemo.core.model

/**
 * Enumeration representing all local data sources accessible by Mnemo on the device.
 * Strict offline principle: All data sources represent local Android system data.
 */
enum class DataSourceType {
    CONTACT,
    CALENDAR,
    MESSAGE,
    FILE,
    INSTALLED_APP
}
