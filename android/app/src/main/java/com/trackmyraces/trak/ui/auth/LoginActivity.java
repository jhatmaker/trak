package com.trackmyraces.trak.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.tabs.TabLayout;
import com.trackmyraces.trak.R;
import com.trackmyraces.trak.data.db.TrakDatabase;
import com.trackmyraces.trak.databinding.ActivityLoginBinding;
import com.trackmyraces.trak.ui.MainActivity;

/**
 * LoginActivity — shown when no auth token is present.
 * Handles both login and registration via a two-tab layout.
 * On success, navigates to MainActivity and finishes.
 */
public class LoginActivity extends AppCompatActivity {

    private static final int TAB_LOGIN    = 0;
    private static final int TAB_REGISTER = 1;

    private ActivityLoginBinding mBinding;
    private AuthViewModel        mViewModel;
    private int                  mActiveTab = TAB_LOGIN;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check for existing token before showing login UI.
        // If already authenticated, go straight to MainActivity.
        TrakDatabase.getInstance(this).getQueryExecutor().execute(() -> {
            String token = TrakDatabase.getInstance(this).runnerProfileDao().getAuthToken();
            if (token != null && !token.isEmpty()) {
                runOnUiThread(() -> {
                    startActivity(new Intent(this, MainActivity.class));
                    finish();
                });
                return;
            }
            runOnUiThread(this::showLoginUi);
        });
    }

    private void showLoginUi() {
        mBinding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());

        mViewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        setupTabs();
        setupSubmitButton();
        observeViewModel();
    }

    private void setupTabs() {
        mBinding.tabLayout.addTab(mBinding.tabLayout.newTab().setText(R.string.tab_login));
        mBinding.tabLayout.addTab(mBinding.tabLayout.newTab().setText(R.string.tab_register));

        mBinding.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                mActiveTab = tab.getPosition();
                mBinding.btnSubmit.setText(mActiveTab == TAB_LOGIN
                    ? R.string.action_login
                    : R.string.action_register);
                clearError();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupSubmitButton() {
        mBinding.btnSubmit.setOnClickListener(v -> submit());
        mBinding.etPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit();
                return true;
            }
            return false;
        });
    }

    private void submit() {
        String email    = mBinding.etEmail.getText() != null
            ? mBinding.etEmail.getText().toString().trim() : "";
        String password = mBinding.etPassword.getText() != null
            ? mBinding.etPassword.getText().toString() : "";

        clearError();

        if (email.isEmpty()) {
            mBinding.tilEmail.setError(getString(R.string.error_email_required));
            return;
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            mBinding.tilEmail.setError(getString(R.string.error_email_invalid));
            return;
        }
        if (password.isEmpty()) {
            mBinding.tilPassword.setError(getString(R.string.error_password_required));
            return;
        }
        if (mActiveTab == TAB_REGISTER && password.length() < 8) {
            mBinding.tilPassword.setError(getString(R.string.error_password_length));
            return;
        }

        if (mActiveTab == TAB_LOGIN) {
            mViewModel.login(email, password);
        } else {
            mViewModel.register(email, password);
        }
    }

    private void observeViewModel() {
        mViewModel.getState().observe(this, state -> {
            switch (state) {
                case LOADING:
                    mBinding.btnSubmit.setEnabled(false);
                    mBinding.progressIndicator.setVisibility(View.VISIBLE);
                    break;
                case SUCCESS:
                    startActivity(new Intent(this, MainActivity.class));
                    finish();
                    break;
                case ERROR:
                    mBinding.btnSubmit.setEnabled(true);
                    mBinding.progressIndicator.setVisibility(View.GONE);
                    break;
                case IDLE:
                default:
                    mBinding.btnSubmit.setEnabled(true);
                    mBinding.progressIndicator.setVisibility(View.GONE);
                    break;
            }
        });

        mViewModel.getError().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                mBinding.tvError.setText(error);
                mBinding.tvError.setVisibility(View.VISIBLE);
            }
        });
    }

    private void clearError() {
        mBinding.tvError.setVisibility(View.GONE);
        mBinding.tilEmail.setError(null);
        mBinding.tilPassword.setError(null);
    }
}
