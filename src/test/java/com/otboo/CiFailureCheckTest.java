package com.otboo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CiFailureCheckTest {

    @Test
    void CI_실패_시_Merge가_차단된다() {
        assertEquals(1, 2);
    }
}