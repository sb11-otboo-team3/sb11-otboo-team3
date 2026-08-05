package com.otboo.global.infrastructure.storage;

public enum StorageDirectory {

    PROFILES("profiles"),
    CLOTHES("clothes");

    private final String prefix;

    StorageDirectory(String prefix) {
        this.prefix = prefix;
    }

    public String getPrefix() {
        return prefix;
    }
}
