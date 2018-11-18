package com.wallet.crypto.trustapp.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.arch.lifecycle.ViewModelProviders;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomSheetDialog;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.wallet.crypto.trustapp.C;
import com.wallet.crypto.trustapp.R;
import com.wallet.crypto.trustapp.entity.ErrorEnvelope;
import com.wallet.crypto.trustapp.entity.NetworkInfo;
import com.wallet.crypto.trustapp.entity.TokenInfo;
import com.wallet.crypto.trustapp.entity.Transaction;
import com.wallet.crypto.trustapp.entity.Wallet;
import com.wallet.crypto.trustapp.repository.TokenLocalSource;
import com.wallet.crypto.trustapp.ui.widget.adapter.TransactionsAdapter;
import com.wallet.crypto.trustapp.util.RootUtil;
import com.wallet.crypto.trustapp.viewmodel.AddTokenViewModel;
import com.wallet.crypto.trustapp.viewmodel.AddTokenViewModelFactory;
import com.wallet.crypto.trustapp.viewmodel.BaseNavigationActivity;
import com.wallet.crypto.trustapp.viewmodel.TransactionsViewModel;
import com.wallet.crypto.trustapp.viewmodel.TransactionsViewModelFactory;
import com.wallet.crypto.trustapp.widget.DepositView;
import com.wallet.crypto.trustapp.widget.EmptyTransactionsView;
import com.wallet.crypto.trustapp.widget.SystemView;

import java.util.Map;

import javax.inject.Inject;

import dagger.android.AndroidInjection;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.Single;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;

import static com.wallet.crypto.trustapp.C.ETHEREUM_NETWORK_NAME;
import static com.wallet.crypto.trustapp.C.ETH_SYMBOL;

public class TransactionsActivity extends BaseNavigationActivity implements View.OnClickListener {

    @Inject
    TransactionsViewModelFactory transactionsViewModelFactory;
    private TransactionsViewModel viewModel;

    private SystemView systemView;
    private TransactionsAdapter adapter;
    private Dialog dialog;

    @Inject
    TokenLocalSource tokenLocalSource;
//    protected AddTokenViewModelFactory addTokenViewModelFactory;
//    private AddTokenViewModel addTokenViewModel;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        AndroidInjection.inject(this);
        super.onCreate(savedInstanceState);

        // check YFT
//        addTokenViewModel = ViewModelProviders.of(this, addTokenViewModelFactory)
//                .get(AddTokenViewModel.class);
//        Wallet wallet = viewModel.defaultWallet().getValue();
//        long count = addTokenViewModel.count(wallet);
//        if (count == 0) {
//            addTokenViewModel.save("0xcbf011af08fadda5736bf5e645dbbfb149dc5d68", "YFT孝币", 18);
//        }

        setContentView(R.layout.activity_transactions);

        toolbar();
        setTitle(getString(R.string.unknown_balance_with_symbol));
        setSubtitle("");
        initBottomNavigation();
        dissableDisplayHomeAsUp();

        adapter = new TransactionsAdapter(this::onTransactionClick);
        SwipeRefreshLayout refreshLayout = findViewById(R.id.refresh_layout);
        systemView = findViewById(R.id.system_view);

        RecyclerView list = findViewById(R.id.list);

        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        systemView.attachRecyclerView(list);
        systemView.attachSwipeRefreshLayout(refreshLayout);

        viewModel = ViewModelProviders.of(this, transactionsViewModelFactory)
                .get(TransactionsViewModel.class);
        viewModel.progress().observe(this, systemView::showProgress);
        viewModel.error().observe(this, this::onError);
        viewModel.defaultNetwork().observe(this, this::onDefaultNetwork);
        viewModel.defaultWalletBalance().observe(this, this::onBalanceChanged);
        viewModel.defaultWallet().observe(this, this::onDefaultWallet);
        viewModel.transactions().observe(this, this::onTransactions);

        refreshLayout.setOnRefreshListener(viewModel::fetchTransactions);

//        checkYFT();
    }

    private void onTransactionClick(View view, Transaction transaction) {
        viewModel.showDetails(view.getContext(), transaction);
    }

    @Override
    protected void onResume() {
        super.onResume();

        setTitle(getString(R.string.unknown_balance_without_symbol));
        setSubtitle("");
        adapter.clear();
        viewModel.prepare();
        checkRoot();
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        checkYFT();
//        getMenuInflater().inflate(R.menu.menu_settings, menu);

        NetworkInfo networkInfo = viewModel.defaultNetwork().getValue();
        if (networkInfo != null && networkInfo.name.equals(ETHEREUM_NETWORK_NAME)) {
//            getMenuInflater().inflate(R.menu.menu_deposit, menu);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings: {
                viewModel.showSettings(this);
            } break;
            case R.id.action_deposit: {
                openExchangeDialog();
            } break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.try_again: {
                viewModel.fetchTransactions();
            } break;
            case R.id.action_buy: {
                openExchangeDialog();
            }
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_my_address: {
                viewModel.showMyAddress(this);
                return true;
            }
            case R.id.action_my_tokens: {
                viewModel.showTokens(this);
                return true;
            }
            case R.id.action_send: {
                viewModel.showSend(this);
                return true;
            }
            case R.id.action_settings: {
                viewModel.showSettings(this);
            }
        }
        return false;
    }

    private void onBalanceChanged(Map<String, String> balance) {
        ActionBar actionBar = getSupportActionBar();
        NetworkInfo networkInfo = viewModel.defaultNetwork().getValue();
        Wallet wallet = viewModel.defaultWallet().getValue();
        if (actionBar == null || networkInfo == null || wallet == null) {
            return;
        }
        if (TextUtils.isEmpty(balance.get(C.USD_SYMBOL))) {
            actionBar.setTitle(balance.get(networkInfo.symbol) + " " + networkInfo.symbol);
            actionBar.setSubtitle("");
        } else {
            actionBar.setTitle("$" + balance.get(C.USD_SYMBOL));
            actionBar.setSubtitle(balance.get(networkInfo.symbol) + " " + networkInfo.symbol);
        }
    }

    private void onTransactions(Transaction[] transaction) {
        if (transaction == null || transaction.length == 0) {
            EmptyTransactionsView emptyView = new EmptyTransactionsView(this, this);
            emptyView.setNetworkInfo(viewModel.defaultNetwork().getValue());
            systemView.showEmpty(emptyView);
        }
        adapter.addTransactions(transaction);
        invalidateOptionsMenu();
    }

    private void onDefaultWallet(Wallet wallet) {
        adapter.setDefaultWallet(wallet);
    }

    private void onDefaultNetwork(NetworkInfo networkInfo) {
        adapter.setDefaultNetwork(networkInfo);
        setBottomMenu(R.menu.menu_main_network);
    }

    private void onError(ErrorEnvelope errorEnvelope) {
        systemView.showError(getString(R.string.error_fail_load_transaction), this);
    }

    private void checkRoot() {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        if (RootUtil.isDeviceRooted() && pref.getBoolean("should_show_root_warning", true)) {
            pref.edit().putBoolean("should_show_root_warning", false).apply();
            new AlertDialog.Builder(this)
                    .setTitle(R.string.root_title)
                    .setMessage(R.string.root_body)
                    .setNegativeButton(R.string.ok, (dialog, which) -> {
                    })
                    .show();
        }
    }

    private void openExchangeDialog() {
        Wallet wallet = viewModel.defaultWallet().getValue();
        if (wallet == null) {
            Toast.makeText(this, getString(R.string.error_wallet_not_selected), Toast.LENGTH_SHORT)
                    .show();
        } else {
            BottomSheetDialog dialog = new BottomSheetDialog(this);
            DepositView view = new DepositView(this, wallet);
            view.setOnDepositClickListener(this::onDepositClick);
            dialog.setContentView(view);
            dialog.show();
            this.dialog = dialog;
        }
    }

    private void onDepositClick(View view, Uri uri) {
        viewModel.openDeposit(this, uri);
    }

    private void checkYFT () {
        NetworkInfo networkInfo = viewModel.defaultNetwork().getValue();
        Wallet wallet = viewModel.defaultWallet().getValue();
//        Observable.just("").map(new Function<String, String>() {
//            public String apply(String s){
//
//                return null;
//            }
//        }).subscribeOn(Schedulers.io());

        Observable.just("111").subscribe(new Observer<String>() {
            @Override
            public void onSubscribe(@NonNull Disposable d) {
                Log.i("YFT", "onSubscribe -> ");
            }

            @Override
            public void onNext(@NonNull String s) {
                Log.i("YFT", "onNext -> s=" + s);
                if (networkInfo == null || wallet == null)
                    return;
                Single<TokenInfo[]> ret = tokenLocalSource.fetch(networkInfo, wallet);
                if (ret != null && ret.blockingGet()!=null && ret.blockingGet().length > 0) {
                    TokenInfo[] list = ret.blockingGet();
                    for (TokenInfo item : list) {
                        System.out.println(item.symbol+ item.name);
                        if ("YFT".equals(item.symbol)) {
                            return ;
                        }
                    }
                }
                TokenInfo tokenInfo = new TokenInfo("0xcbf011af08fadda5736bf5e645dbbfb149dc5d68", "孝币", "YFT", 18);
                tokenLocalSource.put(networkInfo, wallet, tokenInfo).subscribe();
            }

            @Override
            public void onError(@NonNull Throwable e) {
                Log.i("YFT", "onError -> e=" + e);
            }

            @Override
            public void onComplete() {
                Log.i("YFT", "onComplete -> ");

            }
        });
    }
}
