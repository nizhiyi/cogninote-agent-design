package com.itqianchen.agentdesign.domain.model;

public class ModelConfigurationException extends RuntimeException {

    public ModelConfigurationException(String message) {
        super(message);
    }

    public ModelConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}


