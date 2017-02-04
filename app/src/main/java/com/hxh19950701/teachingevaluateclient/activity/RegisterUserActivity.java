package com.hxh19950701.teachingevaluateclient.activity;

import android.content.Context;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.TextInputLayout;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;

import com.afollestad.materialdialogs.MaterialDialog;
import com.hxh19950701.teachingevaluateclient.R;
import com.hxh19950701.teachingevaluateclient.base.BaseActivity;
import com.hxh19950701.teachingevaluateclient.bean.service.User;
import com.hxh19950701.teachingevaluateclient.event.UserRegisterCompleteEvent;
import com.hxh19950701.teachingevaluateclient.impl.TextWatcherImpl;
import com.hxh19950701.teachingevaluateclient.manager.EventManager;
import com.hxh19950701.teachingevaluateclient.network.SimpleServiceCallback;
import com.hxh19950701.teachingevaluateclient.network.api.UserApi;
import com.hxh19950701.teachingevaluateclient.utils.InputMethodUtils;
import com.hxh19950701.teachingevaluateclient.utils.MD5Utils;
import com.hxh19950701.teachingevaluateclient.utils.TextInputLayoutUtils;
import com.lidroid.xutils.exception.HttpException;
import com.lidroid.xutils.http.HttpHandler;

import java.util.HashMap;
import java.util.Map;

public class RegisterUserActivity extends BaseActivity {

    private CoordinatorLayout clRegister;
    private TextInputLayout tilUsername;
    private TextInputLayout tilPassword;
    private TextInputLayout tilPasswordRetype;
    private Button btnRegister;

    private HttpHandler<String> httpHandler = null;
    private Map<String, Boolean> existence = new HashMap<>(20);

    private final TextWatcher USERNAME_WATCHER = new TextWatcherImpl() {
        @Override
        public void afterTextChanged(Editable s) {
            String username = s.toString();
            if (httpHandler != null) {
                httpHandler.cancel();
            }
            if (username.isEmpty()) {
                TextInputLayoutUtils.setErrorEnabled(tilUsername, false);
                refreshOperationEnable();
            } else if (username.length() < 6) {
                tilUsername.setError("用户名由6~16个数字和字母组成。");
                refreshOperationEnable();
            } else {
                Boolean exist = existence.get(username);
                if (exist == null) {
                    checkUsernameExistence();
                } else {
                    setupUsernameExistence(exist);
                    refreshOperationEnable();
                }
            }
        }
    };

    private final TextWatcher PASSWORD_WATCHER = new TextWatcherImpl() {
        @Override
        public void afterTextChanged(Editable s) {
            if (tilPassword.getEditText().getText().toString().isEmpty()) {
                tilPasswordRetype.getEditText().setEnabled(false);
                TextInputLayoutUtils.setErrorEnabled(tilPassword, false);
            } else if (s.length() < 6) {
                tilPasswordRetype.getEditText().setEnabled(false);
                tilPassword.setError("密码由6~16个数字，字母和符号组成。");
            } else {
                tilPasswordRetype.getEditText().setEnabled(true);
                TextInputLayoutUtils.setErrorEnabled(tilPassword, false);
            }
            tilPasswordRetype.getEditText().setText("");
            refreshOperationEnable();
        }
    };

    private final TextWatcher PASSWORD_RETYPE_WATCHER = new TextWatcherImpl() {
        @Override
        public void afterTextChanged(Editable s) {
            String password = tilPassword.getEditText().getText().toString();
            if (password.startsWith(s.toString())) {
                TextInputLayoutUtils.setErrorEnabled(tilPasswordRetype, false);
            } else {
                tilPasswordRetype.setError(getText(R.string.passwordInconsistent));
            }
            refreshOperationEnable();
        }
    };

    @Override
    public void initView() {
        setContentView(R.layout.activity_register_user);
        btnRegister = (Button) findViewById(R.id.btnRegister);
        clRegister = (CoordinatorLayout) findViewById(R.id.clRegister);
        tilUsername = (TextInputLayout) findViewById(R.id.tilUsername);
        tilPassword = (TextInputLayout) findViewById(R.id.tilPassword);
        tilPasswordRetype = (TextInputLayout) findViewById(R.id.tilPasswordRetype);
    }

    @Override
    public void initListener() {
        btnRegister.setOnClickListener(this);
        tilUsername.getEditText().addTextChangedListener(USERNAME_WATCHER);
        tilPassword.getEditText().addTextChangedListener(PASSWORD_WATCHER);
        tilPasswordRetype.getEditText().addTextChangedListener(PASSWORD_RETYPE_WATCHER);
    }

    @Override
    public void initData() {
        displayHomeAsUp();
        tilPasswordRetype.getEditText().setEnabled(false);
        refreshOperationEnable();

        //弹出键盘
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_NOT_ALWAYS);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btnRegister:
                register();
        }
    }

    private void setupUsernameExistence(boolean isExist) {
        if (isExist) {
            CharSequence errorText = getResources().getText(R.string.usernameInUse);
            tilUsername.setError(errorText);
        } else {
            TextInputLayoutUtils.setErrorEnabled(tilUsername, false);
        }
    }

    protected void checkUsernameExistence() {
        final String username = tilUsername.getEditText().getText().toString();
        httpHandler = UserApi.hasExist(username, new SimpleServiceCallback<Boolean>(clRegister) {
            @Override
            public void onStart() {
                tilUsername.setError("正在检测该用户名是否可用，请稍后……");
            }

            @Override
            public void onAfter() {
                refreshOperationEnable();
            }

            @Override
            public void onGetDataSuccessful(Boolean isExist) {
                existence.put(username, isExist);
                setupUsernameExistence(isExist);
            }

            @Override
            public void onFailure(HttpException e, String s) {
                tilUsername.setError("我们无法检测该用户名是否可用。");
            }

            @Override
            public void onGetDataFailed(int code, String msg) {
                tilUsername.setError("我们无法检测该用户名是否可用。");
            }

            @Override
            public void onJsonSyntaxException(String s) {
                tilUsername.setError("我们无法检测该用户名是否可用。");
            }
        });
    }

    protected void register() {
        final MaterialDialog dialog = new MaterialDialog.Builder(this).title("正在注册").content("请稍后...")
                .cancelable(false).progressIndeterminateStyle(false).progress(true, 0).build();
        final String username = tilUsername.getEditText().getText().toString();
        final String password = MD5Utils.encipher(tilPassword.getEditText().getText().toString());

        UserApi.registerStudent(username, password, new SimpleServiceCallback<User>(clRegister,dialog) {

            @Override
            public void onGetDataSuccessful(User user) {
                EventManager.postEvent(new UserRegisterCompleteEvent(username, password));
                finish();
            }
        });
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        InputMethodUtils.hideSoftInputFromWindow(clRegister.getWindowToken());
    }

    private void refreshOperationEnable() {
        final String password = tilPassword.getEditText().getText().toString();
        final String passwordRetype = tilPasswordRetype.getEditText().getText().toString();
        btnRegister.setEnabled(TextInputLayoutUtils.isInputComplete(tilUsername)
                && TextInputLayoutUtils.isInputComplete(tilPassword)
                && TextInputLayoutUtils.isInputComplete(tilPasswordRetype)
                && password.equals(passwordRetype));
    }
}