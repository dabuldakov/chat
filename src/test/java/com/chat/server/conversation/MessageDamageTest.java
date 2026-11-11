package com.chat.server.conversation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageDamageTest {

    @Test
    void hitAddsSixtyPercentOfPower() {
        assertEquals(30, MessageDamage.afterHit(0, 50));
        assertEquals(60, MessageDamage.afterHit(0, 100));
    }

    @Test
    void damageAccumulatesAcrossHits() {
        assertEquals(30, MessageDamage.afterHit(0, 50));
        assertEquals(60, MessageDamage.afterHit(30, 50));
    }

    @Test
    void damageIsCappedAtHundred() {
        assertEquals(100, MessageDamage.afterHit(90, 100));
    }

    @Test
    void weakHitAddsAtLeastOne() {
        assertEquals(1, MessageDamage.afterHit(0, 1));
    }

    @Test
    void zeroPowerDoesNotDamage() {
        assertEquals(40, MessageDamage.afterHit(40, 0));
    }

    @Test
    void negativeAndOverflowValuesAreNormalized() {
        assertEquals(0, MessageDamage.afterHit(-50, 0));
        assertEquals(100, MessageDamage.afterHit(100, 100));
    }
}
