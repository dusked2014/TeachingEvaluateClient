package com.hxh19950701.teachingevaluateclient.activity;

import android.support.annotation.NonNull;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.TabLayout;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.hxh19950701.teachingevaluateclient.R;
import com.hxh19950701.teachingevaluateclient.adapter.FirstTargetViewPagerAdapter;
import com.hxh19950701.teachingevaluateclient.base.BaseActivity;
import com.hxh19950701.teachingevaluateclient.bean.response.CourseAndEvaluatedItem;
import com.hxh19950701.teachingevaluateclient.bean.service.Course;
import com.hxh19950701.teachingevaluateclient.bean.service.StudentCourseEvaluate;
import com.hxh19950701.teachingevaluateclient.bean.service.StudentCourseInfo;
import com.hxh19950701.teachingevaluateclient.constant.Constant;
import com.hxh19950701.teachingevaluateclient.fragment.FirstTargetFragment;
import com.hxh19950701.teachingevaluateclient.internet.SimpleServiceCallback;
import com.hxh19950701.teachingevaluateclient.internet.api.EvaluateApi;
import com.hxh19950701.teachingevaluateclient.manager.EvaluateTargetManager;
import com.hxh19950701.teachingevaluateclient.utils.SnackBarUtils;
import com.hxh19950701.teachingevaluateclient.utils.ToastUtils;

import java.util.Arrays;

public class StudentEvaluateActivity extends BaseActivity implements FirstTargetFragment.OnItemScoreUpdateListener {

    private static final String TAG = StudentEvaluateActivity.class.getSimpleName();

    private final int THIRD_TARGET_COUNT = EvaluateTargetManager.getThirdTargets().size();

    private CoordinatorLayout clEvaluate;
    private TabLayout tlFirstTarget;
    private ViewPager vpFirstTarget;

    private Course course;
    private float score[] = new float[THIRD_TARGET_COUNT];

    {
        Arrays.fill(score, -1.0f);
    }

    @Override
    protected void initView() {
        setContentView(R.layout.activity_student_evaluate);
        clEvaluate = (CoordinatorLayout) findViewById(R.id.clEvaluate);
        tlFirstTarget = (TabLayout) findViewById(R.id.tlFirstTarget);
        vpFirstTarget = (ViewPager) findViewById(R.id.vpFirstTarget);
    }

    @Override
    protected void initListener() {
    }

    @Override
    protected void initData() {
        displayHomeAsUp();
        loadEvaluatedItem();
    }

    private void loadEvaluatedItem() {
        final int courseId = getIntent().getIntExtra(Constant.KEY_COURSE_ID, -1);
        if (courseId > 0) {
            EvaluateApi.getStudentAllEvaluatedItemsByCourse(courseId,
                    new SimpleServiceCallback<CourseAndEvaluatedItem>(clEvaluate) {
                        @Override
                        public void onGetDataSuccess(CourseAndEvaluatedItem data) {
                            initCourseAndEvaluatedItem(data);
                        }
                    });
        } else {
            ToastUtils.show("缺少必要的信息，Activity启动失败。");
            finish();
        }
    }

    private void initCourseAndEvaluatedItem(CourseAndEvaluatedItem data) {
        course = data.getCourse();
        if (data.getItem() != null) {
            for (StudentCourseEvaluate item : data.getItem()) {
                score[item.getItem().getId()] = item.getScore();
            }
        }
        setTitle("评价：" + course.getName());
        PagerAdapter adapter = new FirstTargetViewPagerAdapter(getSupportFragmentManager(), score, this);
        vpFirstTarget.setAdapter(adapter);
        tlFirstTarget.setupWithViewPager(vpFirstTarget);
    }

    @Override
    public void onClick(View view) {
    }

    private float getTotalScore() {
        float totalScore = 0.0f;
        for (float itemScore : score) {
            if (itemScore < 0.0f) {
                return -1.0f;
            }
            totalScore += itemScore;
        }
        return totalScore;
    }

    private void showResultDialog() {
        float totalScore = getTotalScore();
        if (totalScore < 0.0f) {
            SnackBarUtils.showLong(clEvaluate, "存在未评价的项目。");
        } else {
            StringBuilder content = new StringBuilder();
            content.append("课程：").append(course.getName()).append("\n");
            content.append("得分：").append(totalScore);
            new MaterialDialog.Builder(this).title("结果").content(content)
                    .positiveText("提交").onPositive(new MaterialDialog.SingleButtonCallback() {
                @Override
                public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                    commitScore();
                }
            }).show();
        }
    }

    private void commitScore() {
        final MaterialDialog dialog = new MaterialDialog.Builder(this)
                .title("正在提交").content("请稍后...").cancelable(false).build();
        EvaluateApi.commitEvaluate(course.getId(), new SimpleServiceCallback<StudentCourseInfo>(clEvaluate) {

            @Override
            public void onStart() {
                dialog.show();
            }

            @Override
            public void onAfter() {
                dialog.dismiss();
            }

            @Override
            public void onGetDataSuccess(StudentCourseInfo studentCourseInfo) {
                finish();
            }
        });
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.commit, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_commit) {
            showResultDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onItemScoreUpdate(final TextView textView, final int itemId, final float newScore) {
        float currentScore = score[itemId];
        if (currentScore != newScore) {
            EvaluateApi.updateItemScore(course.getId(), itemId, newScore,
                    new SimpleServiceCallback<StudentCourseEvaluate>(clEvaluate) {
                        @Override
                        public void onGetDataSuccess(StudentCourseEvaluate data) {
                            score[itemId] = data.getScore();
                            textView.setText(data.getScore() + "分");
                        }
                    });
        }
    }
}