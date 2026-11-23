package com.chuansuo.app;

import android.app.Application;
import android.content.Context;
import android.graphics.Rect;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import io.noties.markwon.Markwon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class UpdateDialogLayoutTest {
    @Test
    public void compactDialogKeepsActionsVisibleWhileLongReleaseNotesScroll() {
        Context context = new ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.AppTheme);
        ViewGroup dialog = (ViewGroup) LayoutInflater.from(context).inflate(R.layout.update, null);
        ScrollView scroll = dialog.findViewById(R.id.update_scroll);
        TextView notes = dialog.findViewById(R.id.update_content);
        View ignore = dialog.findViewById(R.id.update_ignore);
        View download = dialog.findViewById(R.id.update_agree);
        ImageView emblem = dialog.findViewById(R.id.update_stars_emblem);

        StringBuilder releaseNotes = new StringBuilder("## 更新内容\n\n");
        for (int i = 0; i < 30; i++) {
            releaseNotes.append("- **路线编辑** 支持在地图上调整途经点。\n");
        }
        Markwon.create(context).setMarkdown(notes, releaseNotes.toString());

        float density = context.getResources().getDisplayMetrics().density;
        int width = Math.round(288 * density);
        int maxHeight = Math.round(340 * density);
        dialog.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST));
        dialog.layout(0, 0, dialog.getMeasuredWidth(), dialog.getMeasuredHeight());

        assertTrue(dialog.getHeight() <= maxHeight);
        assertTrue(scroll.getHeight() > 0);
        assertTrue(scroll.getChildAt(0).getHeight() > scroll.getHeight());
        assertNotNull(emblem.getDrawable());
        assertTrue(emblem.getWidth() > 0 && emblem.getHeight() > 0);

        Rect scrollBounds = boundsInDialog(dialog, scroll);
        Rect ignoreBounds = assertActionVisible(dialog, ignore, scrollBounds.bottom);
        Rect downloadBounds = assertActionVisible(dialog, download, scrollBounds.bottom);

        scroll.scrollTo(0, scroll.getChildAt(0).getHeight());

        assertTrue(scroll.getScrollY() > 0);
        assertEquals(ignoreBounds, boundsInDialog(dialog, ignore));
        assertEquals(downloadBounds, boundsInDialog(dialog, download));
    }

    private static Rect assertActionVisible(ViewGroup dialog, View action, int contentBottom) {
        Rect bounds = boundsInDialog(dialog, action);
        assertEquals(View.VISIBLE, action.getVisibility());
        assertTrue(bounds.width() > 0 && bounds.height() > 0);
        assertTrue(bounds.left >= 0 && bounds.right <= dialog.getWidth());
        assertTrue(bounds.top >= contentBottom && bounds.bottom <= dialog.getHeight());
        return bounds;
    }

    private static Rect boundsInDialog(ViewGroup dialog, View descendant) {
        Rect bounds = new Rect(0, 0, descendant.getWidth(), descendant.getHeight());
        dialog.offsetDescendantRectToMyCoords(descendant, bounds);
        return bounds;
    }
}
