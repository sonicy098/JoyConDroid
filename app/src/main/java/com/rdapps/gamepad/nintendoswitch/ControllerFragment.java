package com.rdapps.gamepad.nintendoswitch;

import static android.app.Activity.RESULT_OK;
import static android.os.VibrationEffect.DEFAULT_AMPLITUDE;
import static android.view.KeyEvent.KEYCODE_DPAD_DOWN;
import static android.view.KeyEvent.KEYCODE_DPAD_LEFT;
import static android.view.KeyEvent.KEYCODE_DPAD_RIGHT;
import static android.view.KeyEvent.KEYCODE_DPAD_UP;
import static com.rdapps.gamepad.util.ControllerActionUtils.getAxisMapping;
import static com.rdapps.gamepad.util.ControllerActionUtils.getButtonMapping;
import static com.rdapps.gamepad.util.ControllerActionUtils.getJoystickMapping;
import static com.rdapps.gamepad.util.EventUtils.getCenteredAxis;
import static com.rdapps.gamepad.util.EventUtils.getJoyStickEvent;
import static com.rdapps.gamepad.util.EventUtils.getTouchDownEvent;
import static com.rdapps.gamepad.util.EventUtils.getTouchUpEvent;
import com.rdapps.gamepad.sensor.AccelerometerEvent;
import com.rdapps.gamepad.sensor.GyroscopeEvent;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.ImageButton;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;
import com.erz.joysticklibrary.JoyStick;
import com.rdapps.gamepad.device.ButtonType;
import com.rdapps.gamepad.device.JoystickType;
import com.rdapps.gamepad.led.LedState;
import com.rdapps.gamepad.model.ControllerAction;
import com.rdapps.gamepad.protocol.JoyController;
import com.rdapps.gamepad.util.Pair;
import com.rdapps.gamepad.util.PreferenceUtils;
import com.rdapps.gamepad.vibrator.VibrationPattern;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.List;
import lombok.Setter;
import org.apache.commons.io.IOUtils;

public abstract class ControllerFragment extends Fragment {
    private Context context;
    private SensorManager sensorManager;
    private Sensor senAccelerometer;
    private Sensor senGyroscope;

    @Setter
    protected JoyController device;

    private Map<List<Integer>, ButtonType> buttonMap;
    private Map<Pair<Integer, Integer>, ButtonType> axisMap;
    private Map<JoystickType, ControllerAction> joystickMap;
    
    private final java.util.Set<Integer> activeKeys = new java.util.HashSet<>();
    private final java.util.Set<ButtonType> activeVirtualButtons = new java.util.HashSet<>();

    protected Boolean hapticFeedBackEnabled;
    protected Vibrator vibrator;
    
	    // Variabel untuk menyimpan posisi stik Fake Gyro pada milidetik sebelumnya
    private float prevFakeGyroX = 0f;
    private float prevFakeGyroY = 0f;

    private float prevRightX = 0;
    private float prevRightY = 0;
    private float prevLeftX = 0;
    private float prevLeftY = 0;

    private final ActivityResultLauncher<Intent> selectFileResultLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK) {
                            onFileSelected(result.getData());
                        }
                    });

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        this.context = context;
    }


    @Override
    public Context getContext() {
        Context context = super.getContext();
        if (Objects.nonNull(context)) {
            this.context = context;
        }
        return this.context;
    }

    public boolean isHapticFeedbackEnabled() {
        if (Objects.nonNull(hapticFeedBackEnabled)) {
            return hapticFeedBackEnabled;
        } else {
            Context context = getContext();
            if (Objects.nonNull(context)) {
                return PreferenceUtils.getHapticFeedBackEnabled(context);
            }
        }
        return false;
    }

    public Optional<Vibrator> getVibrator(boolean checkHapticFeedback) {
        if (checkHapticFeedback && !isHapticFeedbackEnabled()) {
            return Optional.empty();
        }
        if (Objects.isNull(vibrator)) {
            Context context = getContext();
            if (Objects.nonNull(context)) {
                this.vibrator = context.getSystemService(Vibrator.class);
            }
        }
        return Optional.ofNullable(this.vibrator);
    }

    @Override
    public void onStart() {
        super.onStart();
        buttonMap = getButtonMapping(getContext());
    }

    public Map<List<Integer>, ButtonType> getButtonMap() {
        if (Objects.isNull(buttonMap)) {
            buttonMap = getButtonMapping(getContext());
        }
        return buttonMap;
    }

    public Map<JoystickType, ControllerAction> getJoystickMap() {
        if (Objects.isNull(joystickMap)) {
            joystickMap = getJoystickMapping(getContext());
        }
        return joystickMap;
    }

    public Map<Pair<Integer, Integer>, ButtonType> getAxisMap() {
        if (Objects.isNull(axisMap)) {
            axisMap = getAxisMapping(getContext());
        }
        return axisMap;
    }


    public abstract ImageButton getImageButtonA();

    public abstract ImageButton getImageButtonB();

    public abstract ImageButton getImageButtonX();

    public abstract ImageButton getImageButtonY();

    public abstract ImageButton getImageButtonSl();

    public abstract ImageButton getImageButtonSr();

    public abstract ImageButton getImageButtonL();

    public abstract ImageButton getImageButtonR();

    public abstract ImageButton getImageButtonZl();

    public abstract ImageButton getImageButtonZr();

    public abstract ImageButton getImageButtonMinus();

    public abstract ImageButton getImageButtonPlus();

    public abstract ImageButton getImageButtonHome();

    public abstract ImageButton getImageButtonCapture();

    public abstract ImageButton getImageButtonLeft();

    public abstract ImageButton getImageButtonRight();

    public abstract ImageButton getImageButtonUp();

    public abstract ImageButton getImageButtonDown();

    public abstract ImageButton getImageButtonSync();

    public abstract JoyStick getLeftJoyStick();

    public abstract JoyStick getRightJoyStick();

    public abstract boolean setLeftStickPress(boolean pressed);

    public abstract boolean setRightStickPress(boolean pressed);

    public abstract boolean reverseJoystickXy();

    public boolean handleKey(int keyCode, KeyEvent keyEvent) {
        boolean isDown = keyEvent.getAction() == KeyEvent.ACTION_DOWN;
        boolean isUp = keyEvent.getAction() == KeyEvent.ACTION_UP;

        // Abaikan event selain pencet (DOWN) atau lepas (UP)
        if (!isDown && !isUp) {
            return false;
        }

        // 1. Update status riwayat tombol fisik 
        if (isDown) {
            activeKeys.add(keyCode);
        } else {
            activeKeys.remove(keyCode);
        }

        // 2. Hitung tombol virtual apa saja yang seharusnya aktif saat ini
        java.util.Set<ButtonType> desiredButtons = new java.util.HashSet<>();
        java.util.Set<Integer> consumedKeys = new java.util.HashSet<>();
        Map<List<Integer>, ButtonType> map = getButtonMap();

        // Urutkan mapping dari yang butuh kombinasi terbanyak ke tersedikit (misal: 3 tombol > 2 tombol > 1 tombol)
        List<Map.Entry<List<Integer>, ButtonType>> sortedEntries = new java.util.ArrayList<>(map.entrySet());
        sortedEntries.sort((e1, e2) -> {
            int size1 = e1.getKey() == null ? 0 : e1.getKey().size();
            int size2 = e2.getKey() == null ? 0 : e2.getKey().size();
            return Integer.compare(size2, size1);
        });

        for (Map.Entry<List<Integer>, ButtonType> entry : sortedEntries) {
            List<Integer> keys = entry.getKey();
            if (keys != null && !keys.isEmpty() && activeKeys.containsAll(keys)) {
                // Pastikan tombol-tombol fisik penyusunnya belum dipakai oleh kombinasi yang lebih panjang
                boolean alreadyConsumed = false;
                for (Integer k : keys) {
                    if (consumedKeys.contains(k)) {
                        alreadyConsumed = true;
                        break;
                    }
                }
                
                // Jika belum terpakai, aktifkan tombol virtual kombinasi ini, lalu tandai fisiknya sebagai terpakai
                if (!alreadyConsumed) {
                    desiredButtons.add(entry.getValue());
                    consumedKeys.addAll(keys);
                }
            }
        }

        boolean handled = false;

        // 3. Lepas (UP) tombol virtual yang sebelumnya tertekan, tapi sekarang sudah ditarik/tergantikan kombinasi
        for (ButtonType btn : new java.util.HashSet<>(activeVirtualButtons)) {
            if (!desiredButtons.contains(btn)) {
                handled |= dispatchButton(new KeyEvent(KeyEvent.ACTION_UP, 0), getTouchUpEvent(), btn);
                activeVirtualButtons.remove(btn);
            }
        }

        // 4. Tekan (DOWN) tombol virtual baru yang masuk kualifikasi
        for (ButtonType btn : desiredButtons) {
            if (!activeVirtualButtons.contains(btn)) {
                handled |= dispatchButton(new KeyEvent(KeyEvent.ACTION_DOWN, 0), getTouchDownEvent(), btn);
                activeVirtualButtons.add(btn);
            }
        }

        return handled;
    }

    private boolean dispatchButton(KeyEvent keyEvent, MotionEvent event, ButtonType buttonType) {
        return switch (buttonType) {
            case LEFT -> dispatchEvent(getImageButtonLeft(), event);
            case RIGHT -> dispatchEvent(getImageButtonRight(), event);
            case UP -> dispatchEvent(getImageButtonUp(), event);
            case DOWN -> dispatchEvent(getImageButtonDown(), event);
            case B -> dispatchEvent(getImageButtonB(), event);
            case A -> dispatchEvent(getImageButtonA(), event);
            case Y -> dispatchEvent(getImageButtonY(), event);
            case X -> dispatchEvent(getImageButtonX(), event);
            case R -> dispatchEvent(getImageButtonR(), event);
            case ZR -> dispatchEvent(getImageButtonZr(), event);
            case RIGHT_SR, LEFT_SR -> dispatchEvent(getImageButtonSr(), event);
            case L -> dispatchEvent(getImageButtonL(), event);
            case ZL -> dispatchEvent(getImageButtonZl(), event);
            case RIGHT_SL, LEFT_SL -> dispatchEvent(getImageButtonSl(), event);
            case PLUS -> dispatchEvent(getImageButtonPlus(), event);
            case MINUS -> dispatchEvent(getImageButtonMinus(), event);
            case HOME -> dispatchEvent(getImageButtonHome(), event);
            case CAPTURE -> dispatchEvent(getImageButtonCapture(), event);
            case LEFT_STICK -> setLeftStickPress(keyEvent.getAction() == KeyEvent.ACTION_DOWN);
            case RIGHT_STICK -> setRightStickPress(keyEvent.getAction() == KeyEvent.ACTION_DOWN);
            case SYNC -> dispatchEvent(getImageButtonSync(), event);
            default -> false;
        };
    }

    private static boolean dispatchEvent(ImageButton button, MotionEvent event) {
        return Optional.ofNullable(button)
                .map(b -> b.dispatchTouchEvent(event))
                .orElse(false);
    }

    public boolean handleGenericMotionEvent(MotionEvent motionEvent) {
        if (motionEvent == null) {
            return false;
        }

        InputDevice device = motionEvent.getDevice();
        boolean reverse = reverseJoystickXy();
        Map<JoystickType, ControllerAction> joystickMap = getJoystickMap();
        ControllerAction rightJoystickAction = joystickMap.get(JoystickType.RIGHT_JOYSTICK);
        ControllerAction leftJoystickAction = joystickMap.get(JoystickType.LEFT_JOYSTICK);
        
        // --- MULAI KODE FAKE GYRO ---
        if (PreferenceUtils.getFakeGyroEnabled(getContext())) {
            
            // KUNCI UTAMA: Jika bola sedang dilempar, abaikan input stik sepenuhnya!
            if (!isThrowingMacro) {
                ControllerAction fakeGyroMapping = joystickMap.get(JoystickType.FAKE_GYRO);
                if (fakeGyroMapping != null && fakeGyroMapping.getAxisX() != 0) {
                    float inputX = motionEvent.getAxisValue(fakeGyroMapping.getAxisX());
                    float inputY = motionEvent.getAxisValue(fakeGyroMapping.getAxisY());

                    float deadzone = 0.5f; 
                    int multiplier = PreferenceUtils.getFakeGyroMultiplier(getContext());

                    // Cukup sentak stik ke arah mana saja melebihi 50%
                    if (Math.abs(inputX) > deadzone || Math.abs(inputY) > deadzone) {
                        // Picu rentetan rekaman gerakan otomatis
                        executePerfectThrow(multiplier);
                    } else {
                        // Stik diam, aman untuk mengirim data netral
                        sendFakeSensorEvent(android.hardware.Sensor.TYPE_GYROSCOPE, new float[]{0f, 0f, 0f});
                        sendFakeSensorEvent(android.hardware.Sensor.TYPE_ACCELEROMETER, new float[]{0f, 0f, 9.8f});
                    }
                }
            }
        }
        // --- AKHIR KODE FAKE GYRO ---

        float rightStickX = 0;
        float rightStickY = 0;
        if (rightJoystickAction != null) {
            rightStickX = (reverse ? -1 : 1) * getCenteredAxis(motionEvent, device,
                    reverse ? rightJoystickAction.getAxisY() : rightJoystickAction.getAxisX());
            rightStickY = getCenteredAxis(motionEvent, device,
                    reverse ? rightJoystickAction.getAxisX() : rightJoystickAction.getAxisY());
            rightStickX = rightStickX * rightJoystickAction.getDirectionX();
            rightStickY = rightStickY * rightJoystickAction.getDirectionY() * -1;
        }

        float leftStickX = 0;
        float leftStickY = 0;
        if (leftJoystickAction != null) {
            leftStickX = (reverse ? -1 : 1) * getCenteredAxis(motionEvent, device,
                    reverse ? leftJoystickAction.getAxisY() : leftJoystickAction.getAxisX());
            leftStickY = getCenteredAxis(motionEvent, device,
                    reverse ? leftJoystickAction.getAxisX() : leftJoystickAction.getAxisY());
            leftStickX = leftStickX * leftJoystickAction.getDirectionX();
            leftStickY = leftStickY * leftJoystickAction.getDirectionY() * -1;
        }

        JoyStick leftJoyStick = getLeftJoyStick();
        if (leftJoyStick != null) {
            float radius = leftJoyStick.getRadius();
            float centerX = leftJoyStick.getCenterX();
            float centerY = leftJoyStick.getCenterY();
            MotionEvent joyStickEvent = getJoyStickEvent(leftStickX, leftStickY,
                    radius, centerX, centerY);
            leftJoyStick.dispatchTouchEvent(joyStickEvent);
        }
        
        JoyStick rightJoyStick = getRightJoyStick();
        if (rightJoyStick != null) {
            float radius = rightJoyStick.getRadius();
            float centerX = rightJoyStick.getCenterX();
            float centerY = rightJoyStick.getCenterY();
            rightJoyStick.dispatchTouchEvent(
                    getJoyStickEvent(rightStickX, rightStickY,
                            radius, centerX, centerY));
        }

        final boolean processed = Float.compare(leftStickX, prevLeftX) != 0
                || Float.compare(leftStickY, prevLeftY) != 0
                || Float.compare(rightStickX, prevRightX) != 0
                || Float.compare(rightStickY, prevRightY) != 0;

        prevLeftX = leftStickX;
        prevLeftY = leftStickY;
        prevRightX = rightStickX;
        prevRightY = rightStickY;

        if (!processed) {
            processAxisHatX(motionEvent);
            processAxisHatY(motionEvent);
        }

        Map<Pair<Integer, Integer>, ButtonType> axisMap = getAxisMap();
        for (Map.Entry<Pair<Integer, Integer>, ButtonType> axisEntry : axisMap.entrySet()) {
            Pair<Integer, Integer> key = axisEntry.getKey();
            float centeredAxis = getCenteredAxis(motionEvent, device, key.getKey());
            MotionEvent event;
            KeyEvent keyEvent;
            if (Math.signum(centeredAxis) == key.getValue()) {
                event = getTouchDownEvent();
                keyEvent = new KeyEvent(KeyEvent.ACTION_DOWN, 0);
            } else {
                event = getTouchUpEvent();
                keyEvent = new KeyEvent(KeyEvent.ACTION_UP, 0);
            }
            dispatchButton(keyEvent, event, axisEntry.getValue());
        }
        return true;
    }

    private int prevXkeyCode = -1;
    private int prevYkexCode = -1;

    private void processAxisHatX(MotionEvent motionEvent) {
        float xaxis = motionEvent.getAxisValue(MotionEvent.AXIS_HAT_X);
        int keyCode;
        if (Float.compare(xaxis, -1.0f) == 0) {
            keyCode = KEYCODE_DPAD_LEFT;
        } else if (Float.compare(xaxis, 1.0f) == 0) {
            keyCode = KEYCODE_DPAD_RIGHT;

        } else {
            keyCode = -1;
        }

        if (prevXkeyCode != keyCode && prevXkeyCode != -1) {
            handleKey(prevXkeyCode, new KeyEvent(KeyEvent.ACTION_UP, prevXkeyCode));
        }
        prevXkeyCode = keyCode;
        if (keyCode != -1) {
            handleKey(keyCode, new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
        }
    }

    private void processAxisHatY(MotionEvent motionEvent) {
        float yaxis = motionEvent.getAxisValue(MotionEvent.AXIS_HAT_Y);
        int keyCode;
        if (Float.compare(yaxis, -1.0f) == 0) {
            keyCode = KEYCODE_DPAD_UP;
        } else if (Float.compare(yaxis, 1.0f) == 0) {
            keyCode = KEYCODE_DPAD_DOWN;
        } else {
            keyCode = -1;
        }

        if (prevYkexCode != keyCode && prevYkexCode != -1) {
            handleKey(prevYkexCode, new KeyEvent(KeyEvent.ACTION_UP, prevYkexCode));
        }
        prevYkexCode = keyCode;
        if (keyCode != -1) {
            handleKey(keyCode, new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
        }
    }

    public SensorManager getSensorManager() {
        if (Objects.isNull(sensorManager)) {
            sensorManager = Optional.ofNullable(getContext())
                    .map(context -> (SensorManager)
                            context.getSystemService(Context.SENSOR_SERVICE))
                    .orElse(null);
        }
        return sensorManager;
    }

    public void registerAccelerometerListener() {
        if (Objects.isNull(device)) {
            return;
        }
        SensorManager sensorManager = getSensorManager();
        if (Objects.nonNull(sensorManager)) {
            senAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (Objects.nonNull(senAccelerometer)
                    && device.isAccelerometerEnabled()
                    && PreferenceUtils.getAccelerometerEnabled(getContext())
            ) {
                sensorManager.registerListener(
                        device, senAccelerometer, SwitchController.SAMPLING_INTERVAL);
            }
        }
    }

    public void unregisterAccelerometerListener() {
        if (Objects.isNull(device)) {
            return;
        }
        SensorManager sensorManager = getSensorManager();
        if (Objects.nonNull(sensorManager)) {
            senAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (Objects.nonNull(senAccelerometer) && !device.isAccelerometerEnabled()) {
                sensorManager.unregisterListener(device, senAccelerometer);
            }
        }
    }

    public void registerGyroscopeListener() {
        if (Objects.isNull(device)) {
            return;
        }
        SensorManager sensorManager = getSensorManager();
        if (Objects.nonNull(sensorManager)) {
            // Coba ambil hardware gyro standar
            senGyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            
            // Jika null (biasanya karena software gyro), coba ambil versi Uncalibrated
            if (senGyroscope == null) {
                senGyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE_UNCALIBRATED);
            }

            if (senGyroscope != null) {
                if (device.isGyroscopeEnabled() && PreferenceUtils.getGyroscopeEnabled(getContext())) {
                    sensorManager.registerListener(device, senGyroscope, SwitchController.SAMPLING_INTERVAL);
                }
            } else {
                // Munculkan peringatan agar Anda tahu bahwa OS benar-benar memblokir akses gyro
                android.widget.Toast.makeText(getContext(), "Gyroscope tidak terdeteksi oleh sistem OS!", android.widget.Toast.LENGTH_LONG).show();
            }
        }
    }

    public void unregisterGyroscopeListener() {
        if (Objects.isNull(device)) {
            return;
        }
        SensorManager sensorManager = getSensorManager();
        if (Objects.nonNull(sensorManager)) {
            senGyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            if (Objects.nonNull(senGyroscope) && !device.isGyroscopeEnabled()) {
                sensorManager.unregisterListener(device, senGyroscope);
            }
        }
    }

    public void showAmiiboPicker() {
    }

    protected void vibrate(VibrationPattern vibrationPattern) {
        getVibrator(true).ifPresent(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                v.vibrate(vibrationPattern.getVibrationEffect(), new VibrationAttributes.Builder()
                        .setUsage(VibrationAttributes.USAGE_MEDIA)
                        .build());
            } else {
                v.vibrate(vibrationPattern.getVibrationEffect());
            }
        });
    }

    public void rumble(int androidAmplitude) {
        getVibrator(false).ifPresent(v -> {
            if (androidAmplitude <= 0) {
                v.cancel();
            } else {
                int effectAmplitude = v.hasAmplitudeControl()
                        ? androidAmplitude : DEFAULT_AMPLITUDE;
                VibrationEffect effect = VibrationPattern.rumble(effectAmplitude);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    v.vibrate(effect, new VibrationAttributes.Builder()
                            .setUsage(VibrationAttributes.USAGE_MEDIA)
                            .build());
                } else {
                    v.vibrate(effect);
                }
            }
        });
    }

    protected void openFileSelectionDialog() {
        openFileSelectionDialog(true);
    }

    protected void openFileSelectionDialog(boolean binaryOnly) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(binaryOnly ? "application/octet-stream" : "*/*");

        selectFileResultLauncher.launch(intent);
    }

    protected void onFileSelected(Intent data) {
        if (data != null) {
            Context context = getContext();
            Uri uri = data.getData();
            try (InputStream is = context.getContentResolver().openInputStream(uri)) {
                byte[] bytes = IOUtils.toByteArray(is);
                PreferenceUtils.setAmiiboFileName(context, uri);
                PreferenceUtils.setAmiiboFileUri(context, uri);
                device.setAmiiboBytes(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public abstract void setPlayerLights(
            LedState led1, LedState led2, LedState led3, LedState led4);
            
    // Variabel gembok agar pergerakan stik diabaikan saat animasi lemparan sedang berjalan
    private boolean isThrowingMacro = false;

    private void executePerfectThrow(int multiplier) {
        isThrowingMacro = true;
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        
        // Batasi batas aman agar tidak terjadi Integer Overflow di protokol Bluetooth
        float safeForce = Math.min(30f, 15f * multiplier); 

        // Meniru persis gerakan tangan "Bawah -> Kanan -> Atas" yang Anda temukan

        // Frame 1 (0ms): Bawah (Wind-up / Tarikan pergelangan tangan ke belakang)
        handler.postDelayed(() -> {
            sendFakeSensorEvent(android.hardware.Sensor.TYPE_GYROSCOPE, new float[]{-safeForce, 0f, 0f});
            sendFakeSensorEvent(android.hardware.Sensor.TYPE_ACCELEROMETER, new float[]{0f, 0f, safeForce});
        }, 0);

        // Frame 2 (40ms): Kanan (Rotasi / Curveball transisi)
        handler.postDelayed(() -> {
            sendFakeSensorEvent(android.hardware.Sensor.TYPE_GYROSCOPE, new float[]{0f, safeForce, 0f});
            sendFakeSensorEvent(android.hardware.Sensor.TYPE_ACCELEROMETER, new float[]{0f, 0f, safeForce});
        }, 40);

        // Frame 3 (80ms): Atas (Sentakan pelontaran ke depan)
        handler.postDelayed(() -> {
            sendFakeSensorEvent(android.hardware.Sensor.TYPE_GYROSCOPE, new float[]{safeForce + 10f, 0f, 0f});
            sendFakeSensorEvent(android.hardware.Sensor.TYPE_ACCELEROMETER, new float[]{0f, 0f, safeForce + 10f});
        }, 80);

        // Frame 4 (150ms): Tangan berhenti mendadak (Memicu pelepasan Pokéball)
        handler.postDelayed(() -> {
            sendFakeSensorEvent(android.hardware.Sensor.TYPE_GYROSCOPE, new float[]{0f, 0f, 0f});
            sendFakeSensorEvent(android.hardware.Sensor.TYPE_ACCELEROMETER, new float[]{0f, 0f, 9.8f});
            
            // Buka gembok agar Anda bisa melempar lagi
            isThrowingMacro = false; 
        }, 150);
    }
            
    private void sendFakeSensorEvent(int sensorType, float[] values) {
        if (this.device == null) return;
        try {
            // 1. Buat instansiasi objek SensorEvent milik OS Android (konstruktor butuh ukuran array 3)
            java.lang.reflect.Constructor<android.hardware.SensorEvent> constructor = 
                    android.hardware.SensorEvent.class.getDeclaredConstructor(Integer.TYPE);
            constructor.setAccessible(true);
            android.hardware.SensorEvent event = constructor.newInstance(3);

            // 2. Curi referensi perangkat keras sensor untuk mengelabui validasi JoyController
            SensorManager sm = getSensorManager();
            android.hardware.Sensor sensor = sm.getDefaultSensor(sensorType);
            if (sensor == null && sensorType == android.hardware.Sensor.TYPE_GYROSCOPE) {
                sensor = sm.getDefaultSensor(android.hardware.Sensor.TYPE_GYROSCOPE_UNCALIBRATED);
            }

            // 3. Suntikkan referensi sensor dan kalkulasi matriks ke dalam event
            java.lang.reflect.Field sensorField = android.hardware.SensorEvent.class.getDeclaredField("sensor");
            sensorField.setAccessible(true);
            sensorField.set(event, sensor);

            event.timestamp = System.nanoTime();
            event.values[0] = values[0];
            event.values[1] = values[1];
            event.values[2] = values[2];

            // 4. Paksa JoyController mengeksekusi event ini agar datanya dikirim via Bluetooth
            ((android.hardware.SensorEventListener) this.device).onSensorChanged(event);
        } catch (Exception e) {
            android.util.Log.e("FAKE_GYRO", "Gagal memalsukan sensor OS", e);
        }
    }

}
