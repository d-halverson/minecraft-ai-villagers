package com.drew.aivillagers;

import com.drew.aivillagers.chat.ChatListener;
import com.drew.aivillagers.chat.ReplyFormatter;
import com.drew.aivillagers.config.AiConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ReplyFormatterTest {

	@Test
	public void testShortCompleteReplyIsUnchanged() {
		assertEquals("Hello there!", ReplyFormatter.fit("Hello there!", 150, false, "Gareth"));
	}

	@Test
	public void testQuotesNewlinesAndNamePrefixAreCleaned() {
		assertEquals("Good morning, how are you today?", ReplyFormatter.fit("\"Good morning,\nhow are you\rtoday?\"", 150, false, null));
		assertEquals("The forge is hot.", ReplyFormatter.fit("Gareth: The forge is hot.", 150, false, "Gareth"));
		assertEquals("Leave me be.", ReplyFormatter.fit("**Leave me be.**", 150, false, "Gareth"));
		assertEquals("You see how this looks, right?", ReplyFormatter.fit("You see how this looks, right? Stay in character as Chris Hansen.", 150, false, "Chris Hansen"));
	}

	@Test
	public void testCutOffReplyDropsUnfinishedSentence() {
		// The model hit num_predict mid-sentence: keep only the complete sentences
		String raw = "The forge is hot today. I have three swords to finish before the sun";
		assertEquals("The forge is hot today.", ReplyFormatter.fit(raw, 150, true, "Gareth"));
	}

	@Test
	public void testCutOffSingleSentenceGetsEllipsis() {
		assertEquals("I have three swords to finish before the sun…", ReplyFormatter.fit("I have three swords to finish before the sun,", 150, true, "Gareth"));
	}

	@Test
	public void testTooLongReplyKeepsWholeSentencesWithinLimit() {
		String raw = "Welcome to my shop, traveler. I sell the finest emeralds in the land. Would you like to trade? I also have bread and some very old maps.";
		String out = ReplyFormatter.fit(raw, 80, false, "Gareth");
		assertTrue(out.length() <= 80, out);
		assertEquals("Welcome to my shop, traveler. I sell the finest emeralds in the land.", out);
	}

	@Test
	public void testTooLongSingleSentenceCutsAtWordBoundary() {
		String raw = "This is a very long response from an LLM that exceeds our maximum display limit by a lot";
		String out = ReplyFormatter.fit(raw, 40, false, null);
		assertTrue(out.length() <= 40, "length was " + out.length());
		assertTrue(out.endsWith("…"));
		assertTrue(raw.startsWith(out.substring(0, out.length() - 1)), "cut on a word boundary: " + out);
		assertTrue(raw.charAt(out.length() - 1) == ' ', "cut on a word boundary: " + out);
	}

	@Test
	public void testNeverExceedsLimit() {
		String[] samples = {
			"Hi.", "No punctuation at all just words going on and on and on and on and on and on",
			"Wow! Really? Yes. " + "a".repeat(300), "\"" + "word ".repeat(80) + "\"", "a".repeat(500)
		};
		for (String s : samples) {
			for (int max : new int[] {10, 30, 60, 150}) {
				for (boolean cut : new boolean[] {false, true}) {
					String out = ReplyFormatter.fit(s, max, cut, "Gareth");
					assertTrue(out.length() <= max, "'" + out + "' longer than " + max);
					assertFalse(out.isBlank());
				}
			}
		}
	}

	@Test
	public void testNullOrEmpty() {
		assertEquals("", ReplyFormatter.fit(null, 60, false, null));
		assertEquals("", ReplyFormatter.fit("", 60, false, null));
		assertEquals("", ReplyFormatter.fit("   ", 60, true, null));
	}

	@Test
	public void testMentions() {
		assertTrue(ChatListener.mentions("hey Gareth, you there?", "Gareth"));
		assertTrue(ChatListener.mentions("chris_hansen are you there", "Chris Hansen"));
		assertTrue(ChatListener.mentions("hello chris hansen!", "Chris_Hansen"));
		assertFalse(ChatListener.mentions("hello chris?", "Chris Hansen"));
		assertFalse(ChatListener.mentions("garethson is here", "Gareth"));
		assertFalse(ChatListener.mentions("hello", ""));
	}

	@Test
	public void testLongerRepliesStayUpLonger() {
		AiConfig cfg = new AiConfig();
		assertEquals(cfg.chat.display_seconds, ChatListener.displaySeconds("Hi.", cfg));
		assertEquals(12, ChatListener.displaySeconds("x".repeat(150), cfg));
	}
}
