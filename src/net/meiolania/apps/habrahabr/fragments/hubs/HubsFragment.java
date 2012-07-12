/*
Copyright 2012 Andrey Zaytsev

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package net.meiolania.apps.habrahabr.fragments.hubs;

import java.io.IOException;
import java.util.ArrayList;

import net.meiolania.apps.habrahabr.R;
import net.meiolania.apps.habrahabr.activities.HubsSearchActivity;
import net.meiolania.apps.habrahabr.activities.HubsShowActivity;
import net.meiolania.apps.habrahabr.adapters.HubsAdapter;
import net.meiolania.apps.habrahabr.data.HubsData;
import net.meiolania.apps.habrahabr.utils.ConnectionUtils;
import net.meiolania.apps.habrahabr.utils.UIUtils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockListFragment;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;

public class HubsFragment extends SherlockListFragment implements OnScrollListener{
    public final static String LOG_TAG = "HubsFragment";
    protected final ArrayList<HubsData> hubsDatas = new ArrayList<HubsData>();
    protected HubsAdapter hubsAdapter;
    protected int page = 0;
    protected boolean loadMoreData = true;
    protected boolean noMorePages = false;
    protected String url;

    public HubsFragment(){
    }

    public HubsFragment(String url){
        this.url = url;
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState){
        super.onActivityCreated(savedInstanceState);
        
        hubsAdapter = new HubsAdapter(getSherlockActivity(), hubsDatas);
        setListAdapter(hubsAdapter);
        
        getListView().setOnScrollListener(this);
    }
    
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater){
        inflater.inflate(R.menu.hubs_fragment, menu);
        
        final EditText searchQuery = (EditText) menu.findItem(R.id.search).getActionView().findViewById(R.id.search_query);
        searchQuery.setOnEditorActionListener(new OnEditorActionListener(){
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event){
                if(actionId == EditorInfo.IME_ACTION_SEARCH){
                    Intent intent = new Intent(getSherlockActivity(), HubsSearchActivity.class);
                    intent.putExtra(HubsSearchActivity.EXTRA_QUERY, searchQuery.getText().toString());
                    startActivity(intent);
                    return true;
                }
                return false;
            }
        });
        
        super.onCreateOptionsMenu(menu, inflater);
    }

    protected void loadList(){
    	if(ConnectionUtils.isConnected(getSherlockActivity())){
    		++page;
            getSherlockActivity().setSupportProgressBarIndeterminateVisibility(true);
            new LoadHubs().execute();
    	}
    }

    protected final class LoadHubs extends AsyncTask<Void, Void, Void>{

        @Override
        protected Void doInBackground(Void... params){
            try{
                Log.i(LOG_TAG, "Loading " + getUrl().replace("%page%", String.valueOf(page)));

                Document document = Jsoup.connect(getUrl().replace("%page%", String.valueOf(page))).get();
                Elements hubs = document.select("div.hub");
                
                if(hubs.size() <= 0){
                    noMorePages = true;
                    /*
                     * It's a solve for:
                     * java.lang.RuntimeException: Can't create handler inside thread that has not called Looper.prepare()
                     */
                    getSherlockActivity().runOnUiThread(new Runnable(){
                        public void run(){
                            Toast.makeText(getSherlockActivity(), R.string.no_more_pages, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
                
                for(Element hub : hubs){
                    HubsData hubsData = new HubsData();
                    
                    Element index = hub.select("div.habraindex").first();
                    Element category = hub.select("div.category").first();
                    Element title = hub.select("div.title > a").first();
                    Element stat = hub.select("div.stat").first();
                    
                    hubsData.setTitle(title.text());
                    hubsData.setUrl(title.attr("abs:href"));
                    hubsData.setCategory(category.text());
                    hubsData.setStat(stat.text());
                    hubsData.setIndex(index.text());
                    
                    hubsDatas.add(hubsData);
                }
            }
            catch(IOException e){
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result){
            getSherlockActivity().runOnUiThread(new Runnable(){
                public void run(){
                    if(!isCancelled())
                        hubsAdapter.notifyDataSetChanged();
                    loadMoreData = true;
                    getSherlockActivity().setSupportProgressBarIndeterminateVisibility(false);
                }
            });
        }

    }
    
    @Override
    public void onListItemClick(ListView list, View view, int position, long id){
        showHub(position);
    }
    
    protected void showHub(int position){
        HubsData hubsData = hubsDatas.get(position);
        final Intent intent = new Intent(getSherlockActivity(), HubsShowActivity.class);
        intent.putExtra(HubsShowActivity.EXTRA_URL, hubsData.getUrl());
        intent.putExtra(HubsShowActivity.EXTRA_TITLE, hubsData.getTitle());
        startActivity(intent);
    }

    public void setUrl(String url){
        this.url = url;
    }

    public String getUrl(){
        return url;
    }

    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount){
        if((firstVisibleItem + visibleItemCount) == totalItemCount && loadMoreData && !noMorePages){
            loadMoreData = false;
            loadList();
            Log.i(LOG_TAG, "Loading " + page + " page");
            //TODO: need to find a better way to display a notification for devices with Android < 3.0
            if(!UIUtils.isHoneycombOrHigher())
                Toast.makeText(getSherlockActivity(), getString(R.string.loading_page, page), Toast.LENGTH_SHORT).show();
        }
    }

    public void onScrollStateChanged(AbsListView view, int scrollState){

    }

}