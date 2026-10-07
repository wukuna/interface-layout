package com.interfacelayout;

import com.interfacelayout.util.WidgetStateStore;
import net.runelite.api.widgets.Widget;
import net.runelite.api.Client;
import org.junit.Test;
import org.mockito.InOrder;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class WidgetStateStoreTest
{
    @Test public void bothJournalsRecognizeFreePositionMinimapHidingWithoutMistakingItForGameHiding()
    {
        Widget mask = mock(Widget.class);
        boolean[] hidden = {false};
        when(mask.isSelfHidden()).thenAnswer(i -> hidden[0]);
        doAnswer(i -> { hidden[0] = i.getArgument(0); return null; }).when(mask).setHidden(anyBoolean());
        WidgetStateStore compact = new WidgetStateStore(), free = new WidgetStateStore();
        free.visibility(mask, true); compact.visibility(mask, true);
        assertFalse(compact.isHiddenByGame(mask, free));
        compact.restoreVisibility(); free.restoreVisibility(); hidden[0] = true;
        assertTrue(compact.isHiddenByGame(mask, free));
    }
    @Test public void gameHiddenDetectionDistinguishesPluginHidingAndHiddenAncestors()
    {
        Widget parent = mock(Widget.class), child = mock(Widget.class);
        when(child.getParent()).thenReturn(parent);
        WidgetStateStore journal = new WidgetStateStore();
        journal.visibility(child, true);
        assertFalse(journal.isHiddenByGame(child));
        when(parent.isSelfHidden()).thenReturn(true);
        assertTrue(journal.isHiddenByGame(child));
        when(parent.isSelfHidden()).thenReturn(false);
        journal.restoreVisibility(); when(child.isSelfHidden()).thenReturn(true);
        assertTrue(journal.isHiddenByGame(child));
    }
    @Test public void showRequestsNeverRevealGameHiddenComponents()
    {
        Widget widget = mock(Widget.class); when(widget.isSelfHidden()).thenReturn(true);
        WidgetStateStore journal = new WidgetStateStore();
        journal.visibility(widget, false);
        verify(widget, never()).setHidden(anyBoolean());
        journal.visibility(widget, true); journal.visibility(widget, false);
        verify(widget, never()).setHidden(false);
    }
    @Test public void scriptVisibilityChangeSurvivesShowRequestAndShutdown()
    {
        Widget widget = mock(Widget.class);
        WidgetStateStore journal = new WidgetStateStore();
        journal.visibility(widget, true); journal.restoreVisibility();
        // A native script now hides the component; showing our setting cannot undo it.
        when(widget.isSelfHidden()).thenReturn(true);
        clearInvocations(widget);
        journal.visibility(widget, false); journal.position(widget, 10, 20); journal.restore();
        verify(widget, never()).setHidden(anyBoolean());
    }
    @Test public void hidingCanBeUndoneWhenThePluginOwnsTheChange()
    {
        Widget widget = mock(Widget.class);
        WidgetStateStore journal = new WidgetStateStore();
        journal.visibility(widget, true); clearInvocations(widget);
        journal.visibility(widget, false);
        verify(widget).setHidden(false);
        clearInvocations(widget); journal.restore();
        verify(widget, never()).setHidden(anyBoolean());
    }
    @Test public void restoresHiddenNativeStateInsteadOfBlindlyUnhiding()
    {
        Widget widget = mock(Widget.class);
        when(widget.isSelfHidden()).thenReturn(true);
        WidgetStateStore journal = new WidgetStateStore();
        journal.hide(widget, true);
        clearInvocations(widget);
        journal.restore();
        verify(widget).setHidden(true);
        verify(widget).setForcedPosition(-1, -1);
    }
    @Test public void firstCaptureSurvivesRepeatedPositionChanges()
    {
        Widget widget = mock(Widget.class);
        when(widget.getOriginalWidth()).thenReturn(211, 800);
        when(widget.getOriginalHeight()).thenReturn(207, 500);
        WidgetStateStore journal = new WidgetStateStore();
        journal.position(widget, 10, 20); journal.position(widget, 100, 200);
        clearInvocations(widget);
        journal.restore();
        verify(widget).setOriginalWidth(211); verify(widget).setOriginalHeight(207);
        verify(widget, never()).setHidden(anyBoolean());
    }
    @Test public void parentsRevalidateBeforeDescendants()
    {
        Widget parent = mock(Widget.class), child = mock(Widget.class);
        when(child.getParent()).thenReturn(parent);
        WidgetStateStore journal = new WidgetStateStore();
        journal.capture(child); journal.capture(parent);
        clearInvocations(parent, child);
        journal.restore();
        InOrder order = inOrder(parent, child);
        order.verify(parent).revalidate(); order.verify(child).revalidate();
    }
    @Test public void restoreIsIdempotentAndReleasesOwnedReferences()
    {
        Widget widget = mock(Widget.class);
        WidgetStateStore journal = new WidgetStateStore();
        journal.position(widget, 10, 10); journal.restore();
        clearInvocations(widget); journal.restore();
        verifyNoInteractions(widget);
    }
    @Test public void nullWidgetsAndFalseHideRequestsAreHarmless()
    {
        Widget widget = mock(Widget.class);
        WidgetStateStore journal = new WidgetStateStore();
        journal.capture(null); journal.hide(null, true); journal.position(null, 1, 1);
        journal.hide(widget, false); journal.restore();
        verifyNoInteractions(widget);
    }
    @Test public void positioningDoesNotRestoreStaleHoverOrSelectedSprites()
    {
        Widget widget = mock(Widget.class);
        WidgetStateStore journal = new WidgetStateStore();
        journal.position(widget, 10, 20); journal.restore();
        verify(widget, never()).setSpriteId(anyInt());
        verify(widget, never()).setOpacity(anyInt());
        verify(widget, never()).setType(anyInt());
    }
    @Test public void renderingRestoresOnlyWhenExplicitlyOwned()
    {
        Widget widget = mock(Widget.class);
        when(widget.getType()).thenReturn(5);
        when(widget.getContentType()).thenReturn(1338);
        when(widget.getSpriteId()).thenReturn(42);
        WidgetStateStore journal = new WidgetStateStore();
        journal.captureRendering(widget); journal.restore();
        verify(widget).setType(5); verify(widget).setContentType(1338); verify(widget).setSpriteId(42);
    }
    @Test public void replacedNativeWidgetsAreReleasedWithoutMutatingRetiredInstances()
    {
        Widget old = mock(Widget.class), replacement = mock(Widget.class);
        when(old.getId()).thenReturn(42); when(old.getIndex()).thenReturn(-1);
        Client client = mock(Client.class); when(client.getWidget(42)).thenReturn(replacement);
        WidgetStateStore journal = new WidgetStateStore();
        journal.capture(old); journal.discardRetired(client);
        clearInvocations(old); journal.restore();
        verifyNoInteractions(old);
    }
}
