package com.google.firebase.messaging;

import java.util.Map;

public final class MessageAccessors {

    private MessageAccessors() {
    }

    public static String tokenOf(Message message) {
        return message.getToken();
    }

    public static Map<String, String> dataOf(Message message) {
        return message.getData();
    }
}