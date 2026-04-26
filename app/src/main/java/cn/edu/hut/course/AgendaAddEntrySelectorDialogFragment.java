package cn.edu.hut.course;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.Calendar;

public class AgendaAddEntrySelectorDialogFragment extends BottomSheetDialogFragment {

    public static final String TAG = "AgendaAddEntrySelectorDialog";
    public static final String REQUEST_KEY = "agenda_add_entry_selector_request";
    public static final String RESULT_KEY_ACTION = "result_action";
    public static final String RESULT_KEY_PREFERRED_DATE_MILLIS = "result_preferred_date_millis";
    public static final String RESULT_KEY_SOURCE = "result_source";
    public static final String ACTION_AI = "action_ai";
    public static final String ACTION_MANUAL = "action_manual";

    private static final String ARG_PREFERRED_DATE_MILLIS = "arg_preferred_date_millis";
    private static final String ARG_SOURCE = "arg_source";

    @NonNull
    public static AgendaAddEntrySelectorDialogFragment newInstance(@Nullable Calendar preferredDate,
                                                                   @Nullable String sourceTag) {
        AgendaAddEntrySelectorDialogFragment fragment = new AgendaAddEntrySelectorDialogFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_PREFERRED_DATE_MILLIS, normalizeDateMillis(preferredDate));
        args.putString(ARG_SOURCE, safeText(sourceTag));
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View content = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_agenda_add_selector, null, false);

        MaterialCardView aiCard = content.findViewById(R.id.cardAgendaAddAi);
        MaterialCardView manualCard = content.findViewById(R.id.cardAgendaAddManual);

        applyCardStyle(aiCard, false);
        applyCardStyle(manualCard, false);

        aiCard.setOnClickListener(v -> dispatchChoice(ACTION_AI));
        manualCard.setOnClickListener(v -> dispatchChoice(ACTION_MANUAL));

        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        dialog.setContentView(content);
        dialog.setDismissWithAnimation(true);
        dialog.setCanceledOnTouchOutside(true);
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!(getDialog() instanceof BottomSheetDialog)) {
            return;
        }
        BottomSheetDialog dialog = (BottomSheetDialog) getDialog();
        FrameLayout sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet == null) {
            return;
        }

        ViewGroup.LayoutParams sheetLp = sheet.getLayoutParams();
        if (sheetLp != null && sheetLp.height != ViewGroup.LayoutParams.WRAP_CONTENT) {
            sheetLp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            sheet.setLayoutParams(sheetLp);
        }

        BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(sheet);
        behavior.setDraggable(true);
        behavior.setFitToContents(true);
        behavior.setSkipCollapsed(true);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);

        GradientDrawable background = new GradientDrawable();
        float radius = dp(28);
        background.setShape(GradientDrawable.RECTANGLE);
        int sheetSurface = MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorSurface, UiStyleHelper.resolvePageBackgroundColor(requireContext()));
        background.setColor(sheetSurface);
        background.setCornerRadii(new float[]{radius, radius, radius, radius, 0f, 0f, 0f, 0f});
        sheet.setBackground(background);

        View parent = (View) sheet.getParent();
        if (parent != null) {
            parent.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    private void applyCardStyle(@Nullable MaterialCardView card, boolean emphasize) {
        if (card == null) {
            return;
        }
        int accent = UiStyleHelper.resolveAccentColor(requireContext());
        int onSurface = UiStyleHelper.resolveOnSurfaceColor(requireContext());
        int surface = MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorSurface, UiStyleHelper.resolveGlassCardColor(requireContext()));

        int strokeColor = emphasize
                ? ColorUtils.setAlphaComponent(accent, 150)
                : ColorUtils.setAlphaComponent(onSurface, 56);
        int rippleColor = ColorUtils.setAlphaComponent(accent, emphasize ? 78 : 52);
        int backgroundColor = emphasize
            ? ColorUtils.blendARGB(surface, accent, 0.08f)
            : surface;

        card.setCardBackgroundColor(backgroundColor);
        card.setCardElevation(0f);
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(strokeColor);
        card.setRippleColor(ColorStateList.valueOf(rippleColor));
    }

    private void dispatchChoice(@NonNull String action) {
        Bundle result = new Bundle();
        Bundle args = getArguments();

        result.putString(RESULT_KEY_ACTION, action);
        result.putLong(
                RESULT_KEY_PREFERRED_DATE_MILLIS,
                args == null ? -1L : args.getLong(ARG_PREFERRED_DATE_MILLIS, -1L)
        );
        result.putString(
                RESULT_KEY_SOURCE,
                args == null ? "" : safeText(args.getString(ARG_SOURCE))
        );

        getParentFragmentManager().setFragmentResult(REQUEST_KEY, result);
        dismissAllowingStateLoss();
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        );
    }

    private static long normalizeDateMillis(@Nullable Calendar date) {
        if (date == null) {
            return -1L;
        }
        Calendar clone = (Calendar) date.clone();
        clone.set(Calendar.HOUR_OF_DAY, 0);
        clone.set(Calendar.MINUTE, 0);
        clone.set(Calendar.SECOND, 0);
        clone.set(Calendar.MILLISECOND, 0);
        return clone.getTimeInMillis();
    }

    @NonNull
    private static String safeText(@Nullable String text) {
        return text == null ? "" : text;
    }
}
