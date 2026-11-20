package io.framextv.lite;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native text-only TV browser. A video WebView is never created here. */
public final class MainActivity extends Activity {
    private enum Page { HOME, MOVIES, TV, SEARCH, SEASONS, EPISODES }

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private int generation = 0;
    private Page page = Page.HOME;
    private Page browsePage = Page.HOME;
    private List<Entry> allHome = new ArrayList<>();
    private List<Entry> searchRows = new ArrayList<>();
    private List<Entry> seasonRows = new ArrayList<>();
    private Entry activeShow;
    private int activeSeason;
    private TextView status;
    private TextView heading;
    private EditText searchField;
    private ListView list;
    private Rows adapter;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);
        buildUi();
        loadHome(Page.HOME);
    }

    private int dp(float px) {
        return (int) (px * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable shape(int color, int border) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(5));
        d.setStroke(dp(1), border);
        return d;
    }

    private StateListDrawable buttonStyle() {
        StateListDrawable d = new StateListDrawable();
        d.addState(new int[] {android.R.attr.state_focused}, shape(0xff276e91, Color.WHITE));
        d.addState(new int[] {android.R.attr.state_pressed}, shape(0xff276e91, Color.WHITE));
        d.addState(new int[] {}, shape(0xff20272d, 0xff4a545e));
        return d;
    }

    private Button nav(String text, View.OnClickListener action) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(15);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackground(buttonStyle());
        b.setPadding(dp(11), 0, dp(11), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(42));
        lp.rightMargin = dp(8);
        b.setLayoutParams(lp);
        b.setOnClickListener(action);
        return b;
    }

    private void buildUi() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(10), dp(20), dp(8));
        content.setBackgroundColor(0xff10151a);
        setContentView(content);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(controls, new LinearLayout.LayoutParams(-1, dp(50)));

        TextView brand = new TextView(this);
        brand.setText("FX LITE");
        brand.setTextColor(0xff7acbff);
        brand.setTextSize(21);
        brand.setTypeface(null, 1);
        LinearLayout.LayoutParams brandLp = new LinearLayout.LayoutParams(-2, -2);
        brandLp.rightMargin = dp(18);
        controls.addView(brand, brandLp);
        controls.addView(nav("Home", v -> showCategory(Page.HOME)));
        controls.addView(nav("Movies", v -> showCategory(Page.MOVIES)));
        controls.addView(nav("TV", v -> showCategory(Page.TV)));

        LinearLayout search = new LinearLayout(this);
        search.setOrientation(LinearLayout.HORIZONTAL);
        search.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        controls.addView(search, searchLp);

        searchField = new EditText(this);
        searchField.setSingleLine(true);
        searchField.setTextSize(16);
        searchField.setTextColor(Color.WHITE);
        searchField.setHintTextColor(0xff9aaab9);
        searchField.setHint("Search title or TMDB ID");
        searchField.setPadding(dp(9), 0, dp(9), 0);
        searchField.setBackground(shape(0xff1d2630, 0xff506174));
        searchField.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchField.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                runSearch();
                return true;
            }
            return false;
        });
        search.addView(searchField, new LinearLayout.LayoutParams(0, -1, 1f));
        Button find = nav("Find", v -> runSearch());
        LinearLayout.LayoutParams findLp = new LinearLayout.LayoutParams(-2, dp(42));
        findLp.leftMargin = dp(7);
        search.addView(find, findLp);

        heading = new TextView(this);
        heading.setTextSize(21);
        heading.setTypeface(null, 1);
        heading.setTextColor(Color.WHITE);
        heading.setPadding(0, dp(13), 0, dp(3));
        content.addView(heading);

        status = new TextView(this);
        status.setTextSize(14);
        status.setTextColor(0xffb2c1cb);
        status.setPadding(0, dp(1), 0, dp(9));
        content.addView(status);

        list = new ListView(this);
        list.setFastScrollEnabled(false);
        list.setDivider(new ColorDrawable(0xff28323c));
        list.setDividerHeight(dp(1));
        list.setCacheColorHint(Color.TRANSPARENT);
        list.setSelector(new ColorDrawable(0xff276e91));
        list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        list.setBackgroundColor(Color.TRANSPARENT);
        adapter = new Rows();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> open(adapter.items.get(position)));
        content.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));

        // Small, persistent on-screen attribution without adding images or a new screen.
        TextView credits = new TextView(this);
        credits.setText("All FrameXTV platform & player credit goes to the FrameXTV creators • Unofficial TV client");
        credits.setTextColor(0xff9ca9b3);
        credits.setTextSize(12);
        credits.setSingleLine(true);
        credits.setEllipsize(android.text.TextUtils.TruncateAt.END);
        credits.setPadding(0, dp(4), 0, 0);
        content.addView(credits, new LinearLayout.LayoutParams(-1, dp(22)));
    }

    private void showCategory(Page next) {
        generation++;
        if (allHome.isEmpty()) {
            loadHome(next);
            return;
        }
        page = next;
        ArrayList<Entry> rows = new ArrayList<>();
        for (Entry item : allHome) {
            if (next == Page.HOME ||
                    (next == Page.MOVIES && item.type.equals("movie")) ||
                    (next == Page.TV && item.type.equals("tv"))) rows.add(item);
        }
        show(next == Page.HOME ? "Featured" : next == Page.MOVIES ? "Movies" : "TV Series",
                rows.size() + " titles · OK to select", rows);
    }

    private interface DataTask { List<Entry> run() throws Exception; }
    private interface Success { void show(List<Entry> data); }

    private void fetch(String label, DataTask task, Success success) {
        final int request = ++generation;
        status.setText("Loading " + label + "…");
        worker.execute(() -> {
            List<Entry> result = Collections.emptyList();
            String error = null;
            try {
                result = task.run();
            } catch (Exception e) {
                error = e.getMessage() == null ? "network error" : e.getMessage();
            }
            final List<Entry> delivered = result;
            final String failure = error;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || request != generation) return;
                if (failure != null) {
                    status.setText("Could not load " + label + ": " + failure +
                            ". You can still enter a numeric TMDB ID above.");
                } else {
                    success.show(delivered);
                }
            });
        });
    }

    private void loadHome(Page category) {
        page = category;
        show("Featured", "Connecting to FrameXTV catalogue…", Collections.emptyList());
        fetch("catalogue", FrameApi::home, rows -> {
            allHome = rows;
            showCategory(category);
        });
    }

    private void runSearch() {
        String term = searchField.getText().toString().trim();
        if (term.isEmpty()) {
            status.setText("Enter a movie or series name, or a numeric TMDB ID.");
            return;
        }
        page = Page.SEARCH;
        browsePage = Page.SEARCH;
        if (term.matches("[0-9]{1,12}")) {
            generation++;
            searchRows = new ArrayList<>();
            searchRows.add(new Entry(term, "Play TMDB " + term + " as a movie", "movie", "Direct player", 0, 0));
            searchRows.add(new Entry(term, "Open TMDB " + term + " as a TV show", "tv", "Season list", 0, 0));
            show("Direct ID", "Choose whether this TMDB ID is a movie or a TV series.", searchRows);
            return;
        }
        show("Search results", "Searching…", Collections.emptyList());
        fetch("search", () -> FrameApi.search(term), rows -> {
            searchRows = rows;
            show("Search results", rows.isEmpty() ? "No matching titles" : rows.size() + " results", rows);
        });
    }

    private void open(Entry item) {
        if (item.type.equals("movie")) {
            play(item, 0, 0);
        } else if (item.type.equals("tv")) {
            browsePage = page;
            activeShow = item;
            page = Page.SEASONS;
            show("Seasons · " + item.name, "Loading seasons…", Collections.emptyList());
            fetch("seasons", () -> FrameApi.seasons(item), rows -> {
                seasonRows = rows;
                show("Seasons · " + item.name, "Choose a season", rows);
            });
        } else if (item.type.equals("season")) {
            activeSeason = item.season;
            page = Page.EPISODES;
            show("Season " + activeSeason + " · " + activeShow.name, "Loading episodes…", Collections.emptyList());
            fetch("episodes", () -> FrameApi.episodes(activeShow, activeSeason), rows ->
                show("Season " + activeSeason + " · " + activeShow.name,
                     "Choose an episode", rows));
        } else if (item.type.equals("episode")) {
            play(activeShow, item.season, item.episode);
        }
    }

    private void play(Entry item, int season, int episode) {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("id", item.id);
        intent.putExtra("title", item.name);
        intent.putExtra("season", season);
        intent.putExtra("episode", episode);
        startActivity(intent);
    }

    private void show(String title, String info, List<Entry> rows) {
        heading.setText(title);
        status.setText(info);
        adapter.replace(rows);
        list.setSelection(0);
    }

    @Override public void onBackPressed() {
        if (page == Page.EPISODES) {
            generation++;
            page = Page.SEASONS;
            show("Seasons · " + activeShow.name, "Choose a season", seasonRows);
        } else if (page == Page.SEASONS) {
            generation++;
            if (browsePage == Page.SEARCH) {
                page = Page.SEARCH;
                show("Search results", searchRows.size() + " results", searchRows);
            } else {
                showCategory(browsePage);
            }
        } else if (page != Page.HOME) {
            showCategory(Page.HOME);
        } else {
            super.onBackPressed();
        }
    }

    @Override protected void onDestroy() {
        generation++;
        worker.shutdownNow();
        super.onDestroy();
    }

    private final class Rows extends BaseAdapter {
        List<Entry> items = Collections.emptyList();

        void replace(List<Entry> data) {
            items = new ArrayList<>(data);
            notifyDataSetChanged();
        }
        @Override public int getCount() { return items.size(); }
        @Override public Entry getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override public View getView(int position, View reused, android.view.ViewGroup parent) {
            LinearLayout row;
            TextView name;
            TextView meta;
            if (reused == null) {
                row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(dp(12), dp(9), dp(12), dp(9));
                row.setMinimumHeight(dp(61));
                name = new TextView(MainActivity.this);
                name.setTextColor(Color.WHITE);
                name.setTextSize(18);
                name.setSingleLine(true);
                name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                row.addView(name);
                meta = new TextView(MainActivity.this);
                meta.setTextColor(0xffb5c8d5);
                meta.setTextSize(13);
                meta.setSingleLine(true);
                meta.setEllipsize(android.text.TextUtils.TruncateAt.END);
                row.addView(meta);
                row.setTag(new TextView[] {name, meta});
            } else {
                row = (LinearLayout) reused;
                TextView[] views = (TextView[]) row.getTag();
                name = views[0];
                meta = views[1];
            }
            Entry current = getItem(position);
            name.setText(current.name);
            meta.setText(current.description);
            return row;
        }
    }
}
