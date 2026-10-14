package com.drew.aivillagers.chat;

import java.util.Locale;

/**
 * Turns a raw model reply into bubble text that always fits {@code maxChars} and never ends mid-sentence
 * when a complete sentence is available.
 */
public final class ReplyFormatter {
	private ReplyFormatter() {}

	/**
	 * @param raw            the model's reply
	 * @param maxChars       hard length limit of the result
	 * @param modelCutOff    true when the model stopped because it hit {@code num_predict}, so the last sentence is unfinished
	 * @param villagerName   stripped if the model prefixed its reply with it ("Gareth: ...")
	 */
	public static String fit(String raw, int maxChars, boolean modelCutOff, String villagerName) {
		String text = clean(raw, villagerName);
		maxChars = Math.max(10, maxChars);
		if (text.isEmpty()) return "";

		boolean fits = text.length() <= maxChars;
		if (fits && !modelCutOff) return text;

		String window = fits ? text : text.substring(0, maxChars);
		// The model was cut off, or the text is too long: keep whole sentences when there are any
		int end = lastSentenceEnd(window);
		if (end > 0 && (end >= window.length() / 3 || modelCutOff)) {
			return window.substring(0, end).trim();
		}
		if (text.length() < maxChars) {
			// One unfinished sentence that fits: show it, marked as trailing off
			return stripTrailingPunctuation(text) + "…";
		}
		// One long sentence: cut at a word boundary, leaving room for the ellipsis
		String head = text.substring(0, maxChars - 1);
		int space = head.lastIndexOf(' ');
		if (space >= maxChars / 2) head = head.substring(0, space);
		return stripTrailingPunctuation(head) + "…";
	}

	static String clean(String raw, String villagerName) {
		if (raw == null) return "";
		String s = raw.replace("**", "").replaceAll("\\s+", " ").trim();
		// "Gareth: Hello" / "Gareth says: Hello"
		if (villagerName != null && !villagerName.isBlank()) {
			String lower = s.toLowerCase(Locale.ROOT);
			String name = villagerName.trim().toLowerCase(Locale.ROOT);
			if (lower.startsWith(name + ":")) s = s.substring(name.length() + 1).trim();
			else if (lower.startsWith(name + " says:")) s = s.substring(name.length() + 6).trim();
		}
		// Small models sometimes repeat their instructions back ("Stay in character as Gareth.")
		s = s.replaceAll("(?i)\\(reply as [^)]*\\)|\\bstay in character( as [^.!?]*)?[.!?]?|\\bnever mention being an ai[.!?]?", " ").replaceAll("\\s+", " ").trim();
		// Wrapped in quotes
		if (s.length() >= 2 && (s.charAt(0) == '"' || s.charAt(0) == '“')) {
			char last = s.charAt(s.length() - 1);
			String inner = s.substring(1, s.length() - 1);
			if ((last == '"' || last == '”') && inner.indexOf('"') < 0) s = inner.trim();
		}
		return s;
	}

	/** Index just past the last '.', '!', '?' or '…' that ends a sentence (followed by a space, quote or the end). */
	private static int lastSentenceEnd(String s) {
		for (int i = s.length() - 1; i >= 0; i--) {
			char c = s.charAt(i);
			if (c == '.' || c == '!' || c == '?' || c == '…') {
				int next = i + 1;
				while (next < s.length() && (s.charAt(next) == '"' || s.charAt(next) == '”' || s.charAt(next) == ')' || s.charAt(next) == '*')) next++;
				if (next == s.length() || s.charAt(next) == ' ') {
					return next;
				}
			}
		}
		return -1;
	}

	private static String stripTrailingPunctuation(String s) {
		return s.replaceAll("[\\s,;:\\-–—]+$", "");
	}
}
