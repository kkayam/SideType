package io.github.sspanak.tt9.ime;

import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.sspanak.tt9.R;
import io.github.sspanak.tt9.commands.CmdNextLanguage;
import io.github.sspanak.tt9.commands.CmdShowSettings;
import io.github.sspanak.tt9.hacks.AppHacks;
import io.github.sspanak.tt9.ime.modes.InputMode;
import io.github.sspanak.tt9.languages.Language;
import io.github.sspanak.tt9.languages.LanguageCollection;
import io.github.sspanak.tt9.preferences.settings.SettingsStore;
import io.github.sspanak.tt9.ui.StatusIcon;
import io.github.sspanak.tt9.ui.main.MainView;
import io.github.sspanak.tt9.ui.tray.StatusBar;
import io.github.sspanak.tt9.util.Logger;
import io.github.sspanak.tt9.util.Text;
import io.github.sspanak.tt9.util.sys.DeviceInfo;
import io.github.sspanak.tt9.util.sys.FoldDetector;
import io.github.sspanak.tt9.util.sys.SystemSettings;

abstract class UiHandler extends AbstractHandler {
	private final static String LOG_TAG = "UiHandler";

	@NonNull protected final AppHacks appHacks = new AppHacks();
	@Nullable protected FoldDetector foldDetector = null;
	protected SettingsStore settings;

	protected int displayTextCase = InputMode.CASE_UNDEFINED;
	protected boolean isMainViewShown = false;
	protected MainView mainView = null;
	protected StatusBar statusBar = null;


	protected void cleanUp() {
		if (foldDetector != null) {
			foldDetector.destroy();
			foldDetector = null;
		}
	}


	@Override
	public boolean onEvaluateInputViewShown() {
		super.onEvaluateInputViewShown();
		if (!SystemSettings.isTT9Selected(this)) {
			isMainViewShown = false;
			return false;
		}

		setInputField(getCurrentInputEditorInfo());
		return isMainViewShown = shouldBeVisible();
	}


	@Override
	public boolean onEvaluateFullscreenMode() {
		return false;
	}


	@Override
	protected void onInit() {
		if (foldDetector == null) {
			foldDetector = new FoldDetector(this, () -> settings.setFolded(foldDetector == null || foldDetector.isFolded()));
		}

		if (mainView == null) {
			mainView = new MainView(getFinalContext());
			initTray();
		} else {
			mainView.destroy();
			mainView.getView();
		}
	}


	@Override
	protected boolean onStart(EditorInfo inputField, boolean restarting) {
		if (getFinalContext().getInputType().isOwnSwitchPreviewField()) {
			settings.setFolded(SettingsStore.isFoldedPreview);
		} else if (foldDetector != null) {
			settings.setFolded(foldDetector.isFolded());
		}
		return true;
	}


	protected void initTray() {
		mainView.getView();
		statusBar = new StatusBar(this, settings, mainView, this::resetStatus, () -> getSuggestionOps().cancelDelayedAccept());
		statusBar.setColorScheme();
		createSuggestionBar();
		getSuggestionOps().setColorScheme();
		wireQuickActionKeys();
	}


	/**
	 * Sidephone: the language chip is the strip's only button. Tap cycles the language, long-press
	 * opens Settings. See panel_small_status_bar.xml.
	 */
	private void wireQuickActionKeys() {
		View view = mainView.getView();
		if (view == null) {
			return;
		}

		View languageKey = view.findViewById(R.id.sidephone_language_key);
		if (languageKey != null) {
			languageKey.setOnClickListener(v -> {
				new CmdNextLanguage().run((TraditionalT9) this);
				refreshLanguageKey();
			});
			languageKey.setOnLongClickListener(v -> {
				new CmdShowSettings().run((TraditionalT9) this);
				return true;
			});
		}
		refreshLanguageKey();
	}


	// Sidephone SYM grid. Implemented in CommandHandler, where the text field and suggestions live;
	// the defaults keep the key dispatch (KeyPadHandler) compiling without it.
	protected void hideSymbolGrid() {}
	protected boolean isSymbolGridShown() { return false; }
	protected boolean onSymbolGridKey(int keyCode) { return false; }


	/**
	 * Updates the status-bar language chip to the current language's code (EN / FI / DE …). Shown
	 * whenever there is a language at all — it used to hide unless two or more were enabled, but it
	 * now also carries the long-press to Settings, and an affordance that vanishes for
	 * single-language users is no affordance.
	 */
	protected void refreshLanguageKey() {
		View view = mainView.getView();
		if (view == null) {
			return;
		}

		TextView languageKey = view.findViewById(R.id.sidephone_language_key);
		if (languageKey == null) {
			return;
		}

		// Shown whenever there is a language at all, even if it is the only one. It used to hide unless
		// two or more were enabled, but it now carries the long-press to Settings — and a settings
		// affordance that disappears for single-language users is no affordance. Tapping it simply does
		// nothing when there is nowhere to cycle to.
		Language language = getFinalContext().getLanguage();
		languageKey.setVisibility(language != null ? View.VISIBLE : View.GONE);
		if (language != null) {
			languageKey.setText(language.getCode().toUpperCase(language.getLocale()));
		}
	}


	protected void initUi(InputMode inputMode) {
		if (mainView.create()) {
			initTray();
			setCurrentView();
		} else {
			getSuggestionOps().setColorScheme();
		}
		setStatusIcon(inputMode, getFinalContext().getLanguage());
		statusBar.setColorScheme().setText(inputMode);
		mainView.showKeyboard();
		mainView.render();

		SystemSettings.setNavigationBarBackground(getWindow().getWindow(), settings, mainView.isBackgroundBlendingEnabled());

		if (appHacks.isBrutalForceShowNeeded()) {
			brutalForceShowWindow();
		} else if (!isInputViewShown()) {
			updateInputViewShown();
		}
	}


	public void setCurrentView() {
		setInputView(onCreateInputView());
	}


	public int getDisplayTextCase(@Nullable Language language, int modeTextCase) {
		boolean hasUpperCase = language != null && language.hasUpperCase();
		if (!hasUpperCase) {
			return displayTextCase = InputMode.CASE_UNDEFINED;
		}

		if (modeTextCase == InputMode.CASE_UPPER) {
			return displayTextCase = InputMode.CASE_UPPER;
		}

		Text currentWord = new Text(language, getSuggestionOps().getCurrent());
		if (currentWord.isEmpty() || !currentWord.isAlphabetic()) {
			return displayTextCase = modeTextCase;
		}

		final int wordTextCase = currentWord.getTextCase();
		return displayTextCase = wordTextCase == InputMode.CASE_UPPER ? InputMode.CASE_CAPITALIZE : wordTextCase;
	}


	public void setStatusIcon(@Nullable InputMode mode, @Nullable Language language) {
		if (!settings.isStatusIconEnabled()) {
			return;
		}

		final int resId = new StatusIcon(settings.isStatusIconEnabled() ? mode : null, language, displayTextCase).resourceId;
		if (resId == 0) {
			hideStatusIcon();
		} else {
			showStatusIcon(resId);
		}
	}


	protected boolean shouldBeVisible() {
		return determineInputModeId() != InputMode.MODE_PASSTHROUGH && !settings.isMainLayoutStealth();
	}


	/**
	 * forceShowWindow
	 * Some applications may hide our window, and it remains invisible until the screen is touched or OK is pressed.
	 * This is fine for touchscreen keyboards, but the hardware keyboard allows typing even when the window and the suggestions
	 * are invisible. This function forces the InputMethodManager to show our window.
	 * WARNING! Calling this may cause a restart, which will cause InputMode to be recreated. Depending
	 * on how much time the restart takes, this may erase the current user input.
	 */
	public void forceShowWindow() {
		if (isInputViewShown() || !shouldBeVisible()) {
			return;
		}

		if (DeviceInfo.AT_LEAST_ANDROID_9) {
			requestShowSelf(DeviceInfo.isSonimGen2(getApplicationContext()) ? 0 : InputMethodManager.SHOW_IMPLICIT);
		} else {
			showWindow(true);
		}
	}


	/**
	 * Shows the IME window using brutal force, ignoring IME flags and state, and any (invalid) app
	 * requests for passthrough mode. Note that this should not be randomly used, because it will
	 * cause the UI to appear in calculators, banking apps or others where it is not desired.
	 * Reported problems (in chronological order):
	 *	- <a href="https://github.com/sspanak/tt9/issues/920">Google search field in Firefox on Android 16</a>
	 *	- <a href="https://github.com/sspanak/tt9/issues/963">Gmail reply/forward on Android 16</a>
	 */
	private void brutalForceShowWindow() {
		if (!isShowInputRequested() || !isMainViewShown) {
			forceShowWindow();
		}

		if (!isShowInputRequested() || !isMainViewShown) {
			Logger.d(LOG_TAG, "InputMethodManager refused show request. Forcing visibility with showWindow().");
			showWindow(true);
		}
	}
}
