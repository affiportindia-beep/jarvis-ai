package com.jarvisx.core

import com.jarvisx.core.lang.LanguageDetector
import com.jarvisx.core.lang.ReplyLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageDetectorTest {

    @Test fun english() {
        assertEquals(ReplyLanguage.ENGLISH, LanguageDetector.detect("What is the capital of Australia?"))
        assertEquals(ReplyLanguage.ENGLISH, LanguageDetector.detect("Explain how a transformer model works"))
        assertEquals(ReplyLanguage.ENGLISH, LanguageDetector.detect("Hi"))
    }

    @Test fun hindiDevanagari() {
        assertEquals(ReplyLanguage.HINDI, LanguageDetector.detect("भारत की राजधानी क्या है?"))
        assertEquals(ReplyLanguage.HINDI, LanguageDetector.detect("मुझे Python सीखना है"))
    }

    @Test fun hinglish() {
        assertEquals(ReplyLanguage.HINGLISH, LanguageDetector.detect("Mujhe ek business idea batao"))
        assertEquals(ReplyLanguage.HINGLISH, LanguageDetector.detect("ye code kaam kyun nahi kar raha?"))
        assertEquals(ReplyLanguage.HINGLISH, LanguageDetector.detect("kya haal hai"))
        assertEquals(ReplyLanguage.HINGLISH, LanguageDetector.detect("Android app banane ke liye kaunsi language best hai"))
    }

    @Test fun englishWithAmbiguousWordsStaysEnglish() {
        // "main", "to", "do" are English words too and must not trigger Hinglish.
        assertEquals(ReplyLanguage.ENGLISH, LanguageDetector.detect("What is the main reason to do this?"))
    }

    @Test fun emptyOrDigits() {
        assertEquals(ReplyLanguage.ENGLISH, LanguageDetector.detect(""))
        assertEquals(ReplyLanguage.ENGLISH, LanguageDetector.detect("12 + 5"))
    }
}
