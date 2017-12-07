package pmstation;


import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.graphics.ColorUtils;
import android.support.v7.widget.CardView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

import pmstation.core.plantower.IPlanTowerObserver;
import pmstation.core.plantower.ParticulateMatterSample;
import pmstation.plantower.Settings;

public class ValuesFragment extends Fragment implements IPlanTowerObserver {
    private static final String TAG = "ValuesFragment";
    private CardView pm1Card;
    private CardView pm25Card;
    private CardView pm10Card;
    private TextView pm1;
    private TextView pm25;
    private TextView pm10;
    private TextView time;

    public ValuesFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_values, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        pm1Card = view.findViewById(R.id.pm1_card);
        pm25Card = view.findViewById(R.id.pm25_card);
        pm10Card = view.findViewById(R.id.pm10_card);
        pm1 = pm1Card.findViewById(R.id.pm_value);
        pm25 = pm25Card.findViewById(R.id.pm_value);
        pm10 = pm10Card.findViewById(R.id.pm_value);
        ((TextView) pm1Card.findViewById(R.id.pm_label)).setText(R.string.pm1);
        ((TextView) pm25Card.findViewById(R.id.pm_label)).setText(R.string.pm25);
        ((TextView) pm10Card.findViewById(R.id.pm_label)).setText(R.string.pm10);

        time = view.findViewById(R.id.time);

        List<ParticulateMatterSample> values = ((MainActivity) getActivity()).getValues();
        if (!values.isEmpty()) {
            ParticulateMatterSample sample = values.get(values.size() - 1);
            update(sample);
        }
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        MainActivity activity = (MainActivity) getActivity();
        activity.getSensor().addValueObserver(this);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        MainActivity activity = (MainActivity) getActivity();
        activity.getSensor().removeValueObserver(this);
    }

    @Override
    public void update(final ParticulateMatterSample sample) {
        FragmentActivity activity = getActivity();
        if (activity == null) {
            return;
        }
        activity.runOnUiThread(() -> {
            pm1.setText(String.format(Locale.getDefault(), "%d", sample.getPm1_0()));
            pm25.setText(String.format(Locale.getDefault(), "%d", sample.getPm2_5()));
            AQIColor pm25Color = AQIColor.fromPM25Level(sample.getPm2_5());
            pm1Card.setCardBackgroundColor(
                    ColorUtils.setAlphaComponent(pm25Color.getColor(), 136));
            pm25Card.setCardBackgroundColor(
                    ColorUtils.setAlphaComponent(pm25Color.getColor(), 136));
            pm10.setText(String.format(Locale.getDefault(), "%d", sample.getPm10()));
            pm10Card.setCardBackgroundColor(
                    ColorUtils.setAlphaComponent(AQIColor.fromPM10Level(sample.getPm10()).getColor(), 136));
            time.setText(Settings.dateFormat.format(sample.getDate()));
        });
    }
}
