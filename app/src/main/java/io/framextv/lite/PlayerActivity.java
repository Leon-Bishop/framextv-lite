package io.framextv.lite;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceResponse;
import android.webkit.CookieManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.net.Uri;

/**
 * Loads ONLY a single FrameXTV embed for playback; never the full home page.
 * FrameXTV owns the video pipeline, subtitles and provider fallback.
 */
public final class PlayerActivity extends Activity {
    private FrameLayout root;
    private WebView browser;
    private TextView hint;
    private View fullScreenView;
    private WebChromeClient.CustomViewCallback fullScreenCallback;
    private String id;
    private int season;
    private int episode;
    private boolean fallbackTried;
    private boolean paused;
    private boolean reloading;
    private int providerIndex;
    private static final String[] PROVIDERS = {"auto", "barbarian", "archer", "goblin"};

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        id = getIntent().getStringExtra("id");
        season = getIntent().getIntExtra("season", 0);
        episode = getIntent().getIntExtra("episode", 0);
        if (id == null || !id.matches("[0-9]{1,12}")) {
            finish();
            return;
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        immersive();
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);

        browser = new WebView(this);
        browser.setBackgroundColor(Color.BLACK);
        browser.setFocusable(true);
        browser.setFocusableInTouchMode(true);
        browser.setOverScrollMode(View.OVER_SCROLL_NEVER);
        browser.getSettings().setJavaScriptEnabled(true); // required by the official embed
        browser.getSettings().setDomStorageEnabled(true);
        browser.getSettings().setMediaPlaybackRequiresUserGesture(false);
        browser.getSettings().setAllowFileAccess(false);
        browser.getSettings().setAllowContentAccess(false);
        browser.getSettings().setBuiltInZoomControls(false);
        browser.getSettings().setDisplayZoomControls(false);
        browser.getSettings().setSupportMultipleWindows(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(browser, true);
        browser.getSettings().setJavaScriptCanOpenWindowsAutomatically(false);
        if (Build.VERSION.SDK_INT >= 21) {
            browser.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        }
        browser.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return !isEmbedPage(url);
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !isEmbedPage(request.getUrl().toString());
            }
            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request,
                                                       WebResourceResponse response) {
                if (Build.VERSION.SDK_INT >= 23 && request.isForMainFrame()
                        && response.getStatusCode() >= 400) fallback();
            }
            @Override public void onReceivedError(WebView view, int code, String description, String url) {
                if (url != null && url.startsWith("https://framextv.xyz/embed/")) fallback();
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (Build.VERSION.SDK_INT >= 23 && request.isForMainFrame()) fallback();
            }
        });
        browser.setWebChromeClient(new WebChromeClient() {
            @Override public void onShowCustomView(View view, CustomViewCallback callback) {
                if (fullScreenView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                fullScreenView = view;
                fullScreenCallback = callback;
                root.addView(view, new FrameLayout.LayoutParams(-1, -1));
                immersive();
            }
            @Override public void onHideCustomView() {
                clearCustomView();
            }
        });
        root.addView(browser, new FrameLayout.LayoutParams(-1, -1));

        hint = new TextView(this);
        hint.setTextColor(Color.WHITE);
        hint.setTextSize(14);
        hint.setBackgroundColor(0xbb151d23);
        hint.setPadding(dp(14), dp(8), dp(14), dp(8));
        hint.setText("Loading player…  |  OK Play/Pause  •  ◀▶ Skip 10s  •  Menu Server  •  Hold Menu Reload  •  Back Exit");
        FrameLayout.LayoutParams hints = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        hints.topMargin = dp(12);
        root.addView(hint, hints);
        showHint(hint.getText().toString());
        browser.loadUrl(embedUrl("framextv.xyz"));
        browser.requestFocus();
    }

    private int dp(float n) {
        return (int) (n * getResources().getDisplayMetrics().density + .5f);
    }

    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private String embedUrl(String host) {
        Uri.Builder builder = new Uri.Builder().scheme("https").authority(host).appendPath("embed").appendPath(id);
        if (season > 0 && episode > 0) {
            builder.appendPath(String.valueOf(season));
            builder.appendPath(String.valueOf(episode));
        }
        return builder.appendQueryParameter("autoplay", "1")
                .appendQueryParameter("muted", "0")
                .appendQueryParameter("controls", "1")
                .build().toString();
    }

    private boolean isEmbedPage(String url) {
        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        String path = uri.getPath();
        return "https".equalsIgnoreCase(uri.getScheme()) &&
                ("framextv.xyz".equalsIgnoreCase(host) || "framextv.cc".equalsIgnoreCase(host)) &&
                path != null && path.startsWith("/embed/");
    }

    private void fallback() {
        if (fallbackTried || browser == null || isFinishing()) return;
        fallbackTried = true;
        showHint("Trying FrameXTV alternative host…");
        browser.loadUrl(embedUrl("framextv.cc"));
    }

    private void send(String payload) {
        if (browser == null || isFinishing()) return;
        // Control an accessible HTML5 video directly when the embed exposes one.
        // Still send the FrameXTV message for players within cross-origin frames.
        String action = payload.contains("seekBy") ? "seek" :
                payload.contains("setProvider") ? "provider" :
                payload.contains("pause") ? "pause" : "play";
        String js = "(function(){try{var v=document.querySelector('video');" +
                (action.equals("seek") ? "if(v){v.currentTime=Math.max(0,v.currentTime+" +
                        (payload.contains("-10") ? "-10" : "10") + ");}" :
                 action.equals("pause") ? "if(v){v.pause();}" :
                 action.equals("play") ? "if(v){v.play().catch(function(){});}" : "") +
                "window.postMessage(" + payload + ",'*');}catch(e){}})();";
        browser.evaluateJavascript(js, null);
    }

    private void reloadPlayer() {
        if (browser == null || isFinishing() || reloading) return;
        reloading = true;
        paused = false;
        showHint("Reloading frozen player…");
        clearCustomView();
        browser.stopLoading();
        browser.loadUrl(embedUrl(fallbackTried ? "framextv.cc" : "framextv.xyz"));
        browser.postDelayed(() -> reloading = false, 1200);
        browser.requestFocus();
    }

    // A fullscreen WebView can consume onKeyDown before Activity receives it.
    // dispatchKeyEvent is the host-level fallback for physical TV remotes.
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int key = event.getKeyCode();
            if (key == KeyEvent.KEYCODE_BACK || key == KeyEvent.KEYCODE_ESCAPE) {
                finish();
                return true;
            }
            if (key == KeyEvent.KEYCODE_MENU && event.isLongPress()) {
                reloadPlayer();
                return true;
            }
            if (key == KeyEvent.KEYCODE_MENU && event.getRepeatCount() > 0) {
                return true;
            }
            if (event.getRepeatCount() == 0 && isPlayerKey(key)) {
                return handlePlayerKey(key);
            }
            if (isPlayerKey(key)) return true;
        }
        if (event.getAction() == KeyEvent.ACTION_UP &&
                (isPlayerKey(event.getKeyCode()) ||
                 event.getKeyCode() == KeyEvent.KEYCODE_BACK ||
                 event.getKeyCode() == KeyEvent.KEYCODE_ESCAPE)) return true;
        return super.dispatchKeyEvent(event);
    }

    private boolean isPlayerKey(int key) {
        switch (key) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            case KeyEvent.KEYCODE_MENU:
                return true;
            default:
                return false;
        }
    }
    private boolean handlePlayerKey(int key) {

    private void showHint(String value) {
        if (hint == null) return;
        hint.setText(value);
        hint.setVisibility(View.VISIBLE);
        hint.removeCallbacks(hideHint);
        hint.postDelayed(hideHint, 5500);
    }

    private final Runnable hideHint = () -> {
        if (hint != null) hint.setVisibility(View.GONE);
    };

        switch (key) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                paused = key == KeyEvent.KEYCODE_MEDIA_PLAY ? false :
                        key == KeyEvent.KEYCODE_MEDIA_PAUSE ? true : !paused;
                send("{action:'" + (paused ? "pause" : "play") + "'}");
                showHint(paused ? "Paused  •  OK to resume" : "Playing  •  ◀▶ Skip 10s  •  Menu Change server");
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                send("{action:'seekBy', seconds:-10}");
                showHint("-10 seconds");
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                send("{action:'seekBy', seconds:10}");
                showHint("+10 seconds");
                return true;
            case KeyEvent.KEYCODE_MENU:
                providerIndex = (providerIndex + 1) % PROVIDERS.length;
                String provider = PROVIDERS[providerIndex];
                send("{action:'setProvider', provider:'" + provider + "'}");
                showHint("Server: " + provider + "  •  Menu to switch again");
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                showHint("OK Play/Pause  •  ◀▶ Skip 10s  •  Hold Menu Reload  •  Back Exit");
                return true;
            default:
                return false;
        }
    }

    private void clearCustomView() {
        if (fullScreenView == null) return;
        root.removeView(fullScreenView);
        fullScreenView = null;
        if (fullScreenCallback != null) {
            WebChromeClient.CustomViewCallback callback = fullScreenCallback;
            fullScreenCallback = null;
            callback.onCustomViewHidden();
        }
        immersive();
    }

    @Override protected void onPause() {
        if (browser != null) browser.onPause();
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        if (browser != null) browser.onResume();
    }

    @Override protected void onDestroy() {
        if (hint != null) hint.removeCallbacks(hideHint);
        clearCustomView();
        if (browser != null) {
            browser.stopLoading();
            browser.loadUrl("about:blank");
            root.removeView(browser);
            browser.destroy();
            browser = null;
        }
        super.onDestroy();
    }
}
