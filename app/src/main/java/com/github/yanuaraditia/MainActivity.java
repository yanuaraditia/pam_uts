package com.github.yanuaraditia;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import com.mancj.materialsearchbar.MaterialSearchBar;
import com.github.yanuaraditia.adapter.SearchAdapter;
import com.github.yanuaraditia.api.APIClient;
import com.github.yanuaraditia.mvp.MainPresenter;
import com.github.yanuaraditia.mvp.MainView;
import com.github.yanuaraditia.mvp.model.search.ResultsItem;
import com.github.yanuaraditia.mvp.model.search.SearchModel;
import com.github.yanuaraditia.utils.AlarmReceiver;
import com.github.yanuaraditia.utils.DateTime;
import com.github.yanuaraditia.utils.upcoming.SchedulerTask;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static android.support.v7.widget.DividerItemDecoration.VERTICAL;

public class MainActivity extends AppCompatActivity
        implements MainView,
        MaterialSearchBar.OnSearchActionListener,
        SwipeRefreshLayout.OnRefreshListener,
        PopupMenu.OnMenuItemClickListener {

    @BindView(R.id.toolbar)
    Toolbar toolbar;

    @BindView(R.id.swipe_refresh)
    SwipeRefreshLayout swipe_refresh;

    @BindView(R.id.search_bar)
    MaterialSearchBar search_bar;

    @BindView(R.id.rv_movielist)
    RecyclerView rv_movielist;

    private SearchAdapter adapter;
    private List<ResultsItem> list = new ArrayList<>();

    private Call<SearchModel> apiCall;
    private APIClient apiClient = new APIClient();

    private String movie_title = "";
    private int currentPage = 1;
    private int totalPages = 1;

    private AlarmReceiver alarmReceiver = new AlarmReceiver();
    private SchedulerTask schedulerTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        alarmReceiver.setRepeatingAlarm(this, alarmReceiver.TYPE_REPEATING, "07:00", "Good morning! Ready to pick your new movies today?");

        schedulerTask = new SchedulerTask(this);
        schedulerTask.createPeriodicTask();

        ButterKnife.bind(this);
        setSupportActionBar(toolbar);
        search_bar.setOnSearchActionListener(this);
        swipe_refresh.setOnRefreshListener(this);

        search_bar.inflateMenu(R.menu.main);
        search_bar.getMenu().setOnMenuItemClickListener(this);

        MainPresenter presenter = new MainPresenter(this);

        setupList();
        setupListScrollListener();
        startRefreshing();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (apiCall != null) apiCall.cancel();
    }

    @Override
    public void onSearchStateChanged(boolean enabled) {

    }

    @Override
    public void onSearchConfirmed(CharSequence text) {
        movie_title = String.valueOf(text);
        onRefresh();
    }

    @Override
    public void onButtonClicked(int buttonCode) {

    }

    @Override
    public void onRefresh() {
        currentPage = 1;
        totalPages = 1;

        stopRefrehing();
        startRefreshing();
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.mn_refresh:
                onRefresh();
                break;
        }

        return false;
    }

    private void setupList() {
        adapter = new SearchAdapter();
        rv_movielist.addItemDecoration(new DividerItemDecoration(this, VERTICAL));
        rv_movielist.setLayoutManager(new LinearLayoutManager(this));
        rv_movielist.setAdapter(adapter);
    }

    private void setupListScrollListener() {
        rv_movielist.addOnScrollListener(new RecyclerView.OnScrollListener() {

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();

                int totalItems = layoutManager.getItemCount();
                int visibleItems = layoutManager.getChildCount();
                int pastVisibleItems = layoutManager.findFirstCompletelyVisibleItemPosition();

                if (pastVisibleItems + visibleItems >= totalItems) {
                    if (currentPage < totalPages) currentPage++;
                    startRefreshing();
                }
            }
        });
    }

    private void loadDummyData() {
        list.clear();
        for (int i = 0; i <= 10; i++) {
            ResultsItem item = new ResultsItem();
            item.setPosterPath("/vSNxAJTlD0r02V9sPYpOjqDZXUK.jpg");
            item.setTitle(getString(R.string.label_title_long) + i);
            item.setOverview(getString(R.string.label_overv_long) + i);
            item.setReleaseDate(DateTime.getLongDate("2018-04-1" + i));
            list.add(item);
        }
        adapter.replaceAll(list);
    }

    private void loadData(final String movie_title) {
        getSupportActionBar().setSubtitle("");

        if (movie_title.isEmpty()) apiCall = apiClient.getService().getPopularMovie(currentPage);
        else apiCall = apiClient.getService().getSearchMovie(currentPage, movie_title);

        apiCall.enqueue(new Callback<SearchModel>() {
            @Override
            public void onResponse(Call<SearchModel> call, Response<SearchModel> response) {
                if (response.isSuccessful()) {
                    totalPages = response.body().getTotalPages();
                    List<ResultsItem> items = response.body().getResults();
                    showResults(response.body().getTotalResults());

                    if (currentPage > 1) adapter.updateData(items);
                    else adapter.replaceAll(items);

                    stopRefrehing();
                } else loadFailed();
            }

            @Override
            public void onFailure(Call<SearchModel> call, Throwable t) {
                loadFailed();
            }
        });
    }

    private void loadFailed() {
        stopRefrehing();
        Toast.makeText(MainActivity.this, getString(R.string.failed), Toast.LENGTH_SHORT).show();
    }

    private void startRefreshing() {
        if (swipe_refresh.isRefreshing()) return;
        swipe_refresh.setRefreshing(true);

        loadData(movie_title);
    }

    private void stopRefrehing() {
        if (swipe_refresh.isRefreshing()) swipe_refresh.setRefreshing(false);
    }

    private void showResults(int totalResults) {
        String results;

        String formatResults = NumberFormat.getIntegerInstance().format(totalResults);

        if (totalResults > 0) {
            results = getString(R.string.found1) + " " + formatResults + " " + getString(R.string.found2);
        } else results = "Sorry! I can't find " + movie_title + " everywhere :(";

        getSupportActionBar().setSubtitle(results);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {

        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.changelang,menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item){
        switch (item.getItemId()){
            case R.id.changeLang:
                Intent browserIntent = new Intent(Settings.ACTION_LOCALE_SETTINGS);
                startActivity(browserIntent);

                return true;

        }
        return false;
    }
}
