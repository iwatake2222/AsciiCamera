/**
 * SeekBarPreference
 * related Files: layout_preference_seekbar.xml
 * caller: PreferenceXXX.xml
 * references: http://typea.info/blg/glob/2010/10/android-android-hacks.html
 * @author take.iwiw
 * @version 1.0.0
 */

package jp.take_iwiw.asciicamera;
import android.content.Context;
import android.content.res.TypedArray;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;


public class SeekBarPreference extends Preference implements OnSeekBarChangeListener {

    private static final int MAX_PROGRESS = 100;
    private static final int DEFAULT_PROGRESS = 50;
    private int currentProgress;
    private int oldProgress;

    public SeekBarPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWidgetLayoutResource(R.layout.layout_preference_seekbar);
    }

    /*
     * Preference が 呼び出されるときにデフォルト値が読み込まれる必要がある
     * 異なる Preference 型は異なる 値型 は持つはずなので、サブクラスはそれにあわせた型を返す必要がある
     */
    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getInteger(index, DEFAULT_PROGRESS);
    }

    /*
     * Preference の初期値を設定する
     * restorePersistedValue が true の場合、Preference 値を、SharedPreference からレストアすべき
     * false の場合 Preference 値にデフォルト値をセット
     * (SharedPreference の　shouldPersist() が true の場合、可能ならSharedPreferenceに値を格納)
     */
    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        if (restorePersistedValue) {
            currentProgress = getPersistedInt(currentProgress);
        } else {
            if(defaultValue != null){
                currentProgress = (Integer) defaultValue;
                persistInt(currentProgress);
            } else {
                currentProgress = DEFAULT_PROGRESS;
                persistInt(currentProgress);
            }
        }
        oldProgress = currentProgress;

    }

    /*
     * Preference のために、ビューとデータをバインドする
     * レイアウトからカスタムビューを参照しプロパティを設定するのに適する
     * スーパークラスの実装の呼び出しを確実に行うこと
     */
    @Override
    protected void onBindView(View view) {
        final SeekBar seekbar = (SeekBar) view.findViewById(R.id.seekBar_preference);
        if (seekbar != null) {
            DebugUtility.logDebug("onBindView_!null");
            seekbar.setProgress(currentProgress);
            seekbar.setMax(MAX_PROGRESS);
            seekbar.setOnSeekBarChangeListener(this);
        }
        super.onBindView(view);
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekbar) {}
    @Override
    public void onProgressChanged(SeekBar seekbar, int progress, boolean fromUser) {}

    @Override
    public void onStopTrackingTouch(SeekBar seekbar) {

        int progress = seekbar.getProgress();
        /* ユーザーが設定変更を行った後(内部的な値を設定する前)に呼び出す。 */
        currentProgress = (callChangeListener(progress))?progress:oldProgress;

        persistInt(currentProgress);
        oldProgress = currentProgress;
    }


}
