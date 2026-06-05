package com.newbulaco.showdown.api.registry;

// returned by ContentRegistry register/tryRegister calls so a consumer can tell a
// fresh registration apart from one that overwrote an entry or was refused.
public enum RegistrationResult {
    REGISTERED,
    REPLACED,
    REJECTED
}
