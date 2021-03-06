package com.hxh19950701.teachingevaluateclient.manager;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import com.google.gson.reflect.TypeToken;
import com.hxh19950701.teachingevaluateclient.base.ResponseData;
import com.hxh19950701.teachingevaluateclient.bean.response.Clazz;
import com.hxh19950701.teachingevaluateclient.bean.response.Department;
import com.hxh19950701.teachingevaluateclient.bean.response.Subject;
import com.hxh19950701.teachingevaluateclient.event.DepartmentInfoUpdateSuccessfullyEvent;
import com.hxh19950701.teachingevaluateclient.interfaces.ManagerInitializeListener;
import com.hxh19950701.teachingevaluateclient.network.api.DepartmentApi;
import com.hxh19950701.teachingevaluateclient.utils.GsonUtils;
import com.hxh19950701.teachingevaluateclient.utils.IdRecordUtils;
import com.hxh19950701.teachingevaluateclient.utils.ToastUtils;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class DepartmentInfoManager {

    private static final String TAG = DepartmentInfoManager.class.getSimpleName();

    private static final String KEY = "4C14A42286964298654B147CFF57EA9E";
    private static final String FILE_NAME = "DepartmentInfo.json";

    private DepartmentInfoManager() {
        throw new UnsupportedOperationException("This class cannot be instantiated, and its methods must be called directly.");
    }

    private static final List<Department> DEPARTMENTS = new ArrayList<>(30);
    private static final List<Subject> SUBJECTS = new ArrayList<>(7);
    private static final List<Clazz> CLASSES = new ArrayList<>(5);

    private static final ManagerInitializeListener defaultInitializeListener = new ManagerInitializeListener() {

        @Override
        public void onSuccess(boolean fromCache) {
            DepartmentInfoManager.printAllClasses();
            EventManager.postEvent(new DepartmentInfoUpdateSuccessfullyEvent(fromCache));
        }

        @Override
        public void onFailure(Exception initException, Exception updateException) {
            initException.printStackTrace();
            updateException.printStackTrace();
            ToastUtils.show("更新系部班级信息失败，软件可能工作不正常");
        }
    };

    private static ManagerInitializeListener initializeListener = null;

    private static class Initializer extends Thread implements ManagerInitializeListener {

        private static final Handler HANDLER = new Handler();
        private static final Type TYPE = new TypeToken<ResponseData<List<Clazz>>>() {}.getType();

        private Context context = null;

        @Override
        public void run() {
            super.run();
            init();
        }

        public void init() {
            Exception initException = initFromLocal();
            Exception updateException = update();
            if (updateException == null) {
                onSuccess(false);
            } else if (initException == null && updateException != null) {
                onSuccess(true);
            } else {
                onFailure(initException, updateException);
            }
        }

        private Exception update() {
            try {
                String jsonString = DepartmentApi.getClazzListSync().readString();
                ResponseData<List<Clazz>> response = GsonUtils.fromJson(jsonString, TYPE);
                if (response.getData() == null) {
                    return null;
                } else {
                    initData(response.getData());
                    try {
                        saveJsonToLocal(jsonString);                                        //缓存到本地，缓存失败的话，版本号也不会变
                        //PrefUtils.putString(Constant.KEY_AREA_UP_KEY, areaData.data.upkey); //更新版本号
                    } catch (IOException e) {
                        Log.e(TAG, "已更新到最新，但是缓存失败");
                    }
                    return null;
                }
            } catch (Exception e) {
                return e;
            }
        }

        private void initData(List<Clazz> data) {
            DEPARTMENTS.clear();
            SUBJECTS.clear();
            CLASSES.clear();
            for (Clazz clazz : data) {
                CLASSES.add(clazz);

                int subjectId = clazz.getSubject().getId();
                Subject subject = getSubjectById(subjectId);
                if (subject == null) {
                    subject = clazz.getSubject();
                    subject.setClasses(new ArrayList<Clazz>(7));
                    SUBJECTS.add(subject);
                }
                subject.getClasses().add(clazz);
                clazz.setSubject(subject);

                int departmentId = clazz.getSubject().getDepartment().getId();
                Department department = getDepartmentById(departmentId);
                if (department == null) {
                    department = clazz.getSubject().getDepartment();
                    department.setSubjects(new ArrayList<Subject>(7));
                    DEPARTMENTS.add(department);
                }
                if (IdRecordUtils.findIdRecord(department.getSubjects(), subjectId) == null) {
                    department.getSubjects().add(subject);
                    subject.setDepartment(department);
                }
            }
        }

        public Exception initFromLocal() {
            try {
                String jsonString = getLocalJson();
                ResponseData<List<Clazz>> response = GsonUtils.fromJson(jsonString, TYPE);
                initData(response.getData());
                return null;
            } catch (Exception e) {
                return e;
            }
        }

        private void saveJsonToLocal(String jsonString) throws IOException {
            FileOutputStream fos = context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE);
            byte[] bytes = jsonString.getBytes();
            fos.write(bytes);
            fos.close();
        }

        private String getLocalJson() throws IOException {
            FileInputStream in = context.openFileInput(FILE_NAME);
            int size = in.available();
            byte[] buffer = new byte[size];
            in.read(buffer);
            in.close();
            return new String(buffer);
        }

        public void onSuccess(final boolean fromCache) {
            if (initializeListener != null) {
                HANDLER.post(new Runnable() {
                    @Override
                    public void run() {
                        initializeListener.onSuccess(fromCache);
                    }
                });
            }
        }

        @Override
        public void onFailure(final Exception initException, final Exception updateException) {
            if (initializeListener != null) {
                HANDLER.post(new Runnable() {
                    @Override
                    public void run() {
                        initializeListener.onFailure(initException, updateException);
                    }
                });
            }
        }
    }

    public static void init(Context context) {
        Initializer initializer = new Initializer();
        initializer.context = context;
        initializer.start();
    }

    public static void setInitializeListener(ManagerInitializeListener initializeListener) {
        DepartmentInfoManager.initializeListener = initializeListener;
    }

    public static ManagerInitializeListener getDefaultInitializeListener() {
        return defaultInitializeListener;
    }

    public static Clazz getClazzById(int id) {
        return (Clazz) IdRecordUtils.findIdRecord(CLASSES, id);
    }

    public static Subject getSubjectById(int id) {
        return (Subject) IdRecordUtils.findIdRecord(SUBJECTS, id);
    }

    public static Department getDepartmentById(int id) {
        return (Department) IdRecordUtils.findIdRecord(DEPARTMENTS, id);
    }

    public static List<Clazz> getClasses() {
        return CLASSES;
    }

    public static List<Subject> getSubjects() {
        return SUBJECTS;
    }

    public static List<Department> getDepartments() {
        return DEPARTMENTS;
    }

    public static void printAllClasses() {
        for (Department department : DEPARTMENTS) {
            Log.d(TAG, department.getName());
            for (Subject subject : department.getSubjects()) {
                Log.d(TAG, " " + subject.getName());
                for (Clazz clazz : subject.getClasses()) {
                    Log.d(TAG, "  " + clazz.getName() + clazz.getYear());
                }
            }
        }
    }
}
