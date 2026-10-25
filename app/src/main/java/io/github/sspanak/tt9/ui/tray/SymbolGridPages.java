package io.github.sspanak.tt9.ui.tray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import io.github.sspanak.tt9.languages.Language;
import io.github.sspanak.tt9.preferences.settings.SettingsStore;
import io.github.sspanak.tt9.ui.EmojiData;
import io.github.sspanak.tt9.util.chars.Characters;

/**
 * What the SYM grid shows, split into pages of {@link SymbolGrid#CELLS}.
 * <p>
 * Emojis start with the most recently used ones. Symbols: the language's own punctuation first (the "1" key list and its extra group, then the
 * currency group), followed by the curated table in {@link EmojiData}. Emojis: every category in
 * {@link EmojiData}, in order. Both are deduplicated and keep only single glyphs, which also drops
 * the "(…)" show-more markers the suggestion bar uses.
 */
public class SymbolGridPages {
	public static final int PAGE_SIZE = SymbolGrid.CELLS;

	@Nullable private static ArrayList<String> emojiCache;


	@NonNull
	public static ArrayList<String> symbols(@NonNull SettingsStore settings, @Nullable Language language) {
		LinkedHashSet<String> unique = new LinkedHashSet<>();

		if (language != null) {
			addGlyphs(unique, settings.getChars1AsList(language));
			addGlyphs(unique, settings.getCharsExtraAsList(language, SettingsStore.CHARS_GROUP_1));
			addGlyphs(unique, settings.getCharsExtraAsList(language, SettingsStore.CHARS_GROUP_0));
		}
		addGlyphs(unique, List.of(EmojiData.SYMBOLS));

		return new ArrayList<>(unique);
	}


	/**
	 * The most recently picked emojis fill the first page on their own; the catalogue starts on the
	 * next page. Filtering the catalogue by font support
	 * (EmojiData.getEmojiForCategory) walks thousands of code points, so that part is built once per
	 * process.
	 */
	@NonNull
	public static ArrayList<String> emojis(@NonNull SettingsStore settings) {
		if (emojiCache == null) {
			LinkedHashSet<String> unique = new LinkedHashSet<>();
			for (int category = 0; category < EmojiData.CATEGORIES.length; category++) {
				unique.addAll(EmojiData.getEmojiForCategory(category));
			}
			emojiCache = new ArrayList<>(unique);
		}

		// The recent emojis are a page of their own: however many there are, the catalogue starts on
		// page 2, so the first page is always "recent" and nothing else.
		ArrayList<String> all = new ArrayList<>(settings.getRecentEmojis());
		if (!all.isEmpty()) {
			while (all.size() % PAGE_SIZE != 0) {
				all.add("");
			}
		}
		all.addAll(emojiCache);
		return all;
	}


	public static int pageCount(@NonNull List<String> all) {
		return Math.max(1, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE);
	}


	@NonNull
	public static List<String> page(@NonNull List<String> all, int page) {
		final int start = Math.min(page * PAGE_SIZE, all.size());
		final int end = Math.min(start + PAGE_SIZE, all.size());
		return all.subList(start, end);
	}


	private static void addGlyphs(@NonNull LinkedHashSet<String> target, @NonNull List<String> source) {
		for (String s : source) {
			if (
				s != null
				&& !s.isEmpty()
				&& s.codePointCount(0, s.length()) == 1
				&& !Character.isWhitespace(s.codePointAt(0))
				&& !Characters.PLACEHOLDER.equals(s)
			) {
				target.add(s);
			}
		}
	}
}
