package com.skyeshade.skyesight.client.portal;

import com.skyeshade.skyesight.client.render.SecondaryViewContext;

public final class PortalRenderView {
    private final PortalFrame entrancePortal;
    private final PortalFrame exitPortal;
    private final SecondaryViewContext viewContext;
    private final DebugPortalRenderConfig renderConfig;

    public PortalRenderView(
            PortalFrame entrancePortal,
            PortalFrame exitPortal,
            SecondaryViewContext viewContext,
            DebugPortalRenderConfig renderConfig
    ) {
        this.entrancePortal = entrancePortal;
        this.exitPortal = exitPortal;
        this.viewContext = viewContext;
        this.renderConfig = renderConfig;
    }

    public PortalFrame entrancePortal() {
        return this.entrancePortal;
    }

    public PortalFrame exitPortal() {
        return this.exitPortal;
    }

    public SecondaryViewContext viewContext() {
        return this.viewContext;
    }

    public DebugPortalRenderConfig renderConfig() {
        return this.renderConfig;
    }
}
