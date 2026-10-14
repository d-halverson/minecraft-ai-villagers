package com.drew.aivillagers;

import com.drew.aivillagers.chat.ChatListener;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ChatListenerTest {

	@Test
	public void testCleanAndTruncateWithinLimit() {
		String input = "Hello there!";
		String output = ChatListener.cleanAndTruncate(input, 60);
		assertEquals("Hello there!", output);
	}

	@Test
	public void testCleanAndTruncateQuotesAndNewlines() {
		String input = "\"Good morning,\nhow are you\rtoday?\"";
		String output = ChatListener.cleanAndTruncate(input, 60);
		assertEquals("Good morning, how are you today?", output);
	}

	@Test
	public void testCleanAndTruncateExceedingLimit() {
		String input = "This is a very long response from an LLM that exceeds our maximum display limit";
		String output = ChatListener.cleanAndTruncate(input, 20);
		assertTrue(output.length() <= 20, "Output length should be <= 20 but was " + output.length());
		assertTrue(output.endsWith("…"), "Output should end with ellipsis");
	}

	@Test
	public void testCleanAndTruncateNullOrEmpty() {
		assertEquals("", ChatListener.cleanAndTruncate(null, 60));
		assertEquals("", ChatListener.cleanAndTruncate("", 60));
		assertEquals("", ChatListener.cleanAndTruncate("   ", 60));
	}
}
