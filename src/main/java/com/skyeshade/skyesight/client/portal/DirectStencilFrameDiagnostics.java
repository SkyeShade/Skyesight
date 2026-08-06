package com.skyeshade.skyesight.client.portal;

final class DirectStencilFrameDiagnostics {
    volatile int instancesRendered;
    volatile boolean stencilAttempted;
    volatile boolean stencilSucceeded;
    volatile boolean stencilFallbackUsed;
    volatile String lastStencilException = "";
    volatile boolean directRenderAttempted;
    volatile boolean directRenderSucceeded;
    volatile boolean directRenderFallbackUsed;
    volatile boolean anySecondaryTextureTargetBindDuringDirect;
    volatile boolean directRenderFailed;
    volatile String portalMaskDepthMode = "n/a";
    volatile boolean portalMaskBufferFlushAttempted;
    volatile String portalMaskBufferFlushException = "";
    volatile int framebufferBeforeDirect = -1;
    volatile int framebufferAfterDirect = -1;
    volatile String directRenderUnexpectedBind = "n/a";
    volatile String lastDirectRenderException = "";
    volatile boolean directRenderUsedPortalContext;
    volatile boolean afterDirectStencilEnabled;
    volatile boolean afterDirectColorMaskRestored;
    volatile int afterDirectDepthFunc;
    volatile boolean afterDirectDepthMask;
    volatile int beforeDirectDepthFunc = -1;
    volatile boolean beforeDirectDepthMask;
    volatile boolean directPortalDepthClearRan;
    volatile String directMaskStage = "n/a";
    volatile String directStage = "n/a";
    volatile boolean portalMaskWroteThisFrame;
    volatile String portalScreenRect = "n/a";
    volatile String portalCornerNdc = "n/a";
    volatile String portalSecondaryRotationMode = "n/a";
    volatile boolean portalViewportRestored = true;
    volatile boolean directRenderUsedExistingStencil;
    volatile boolean apertureDrawUsedEventPoseStack;
    volatile boolean apertureDrawUsedManualCameraMatrix;
    volatile boolean directTerrainRan;
    volatile boolean afterDirectProjectionRestored;
    volatile boolean afterDirectModelViewRestored;
    volatile boolean afterDirectFramebufferRestored;
    volatile boolean afterDirectViewportRestored;
    volatile String lastStateRestoreException = "";
    volatile String lastException = "";
    private String lastLoggedException = "";
    private boolean loggedStencilUnavailable;

    void resetStencilMaskFrame(String portalMaskDepthMode) {
        this.portalMaskWroteThisFrame = false;
        this.stencilAttempted = false;
        this.stencilSucceeded = false;
        this.stencilFallbackUsed = false;
        this.lastStencilException = "";
        this.portalMaskDepthMode = portalMaskDepthMode;
        this.directMaskStage = "AFTER_BLOCK_ENTITIES";
        this.apertureDrawUsedEventPoseStack = true;
        this.apertureDrawUsedManualCameraMatrix = false;
    }

    void resetDirectFrame(String portalSecondaryRotationMode, boolean stencilMaskAtWorldStage) {
        this.instancesRendered = 0;
        this.stencilAttempted = false;
        this.stencilSucceeded = false;
        this.stencilFallbackUsed = false;
        this.lastStencilException = "";
        this.directRenderAttempted = false;
        this.directRenderSucceeded = false;
        this.directRenderFallbackUsed = false;
        this.anySecondaryTextureTargetBindDuringDirect = false;
        this.directRenderFailed = false;
        this.portalSecondaryRotationMode = portalSecondaryRotationMode;
        this.directTerrainRan = false;
        this.directStage = "AFTER_LEVEL";
        this.framebufferBeforeDirect = -1;
        this.framebufferAfterDirect = -1;
        this.directRenderUnexpectedBind = "n/a";
        this.lastDirectRenderException = "";
        this.directRenderUsedPortalContext = false;
        this.directRenderUsedExistingStencil = false;
        if (!stencilMaskAtWorldStage) {
            this.directMaskStage = "AFTER_LEVEL";
            this.apertureDrawUsedEventPoseStack = true;
            this.apertureDrawUsedManualCameraMatrix = false;
        }
        this.afterDirectStencilEnabled = false;
        this.afterDirectColorMaskRestored = false;
        this.afterDirectDepthFunc = -1;
        this.afterDirectDepthMask = false;
        this.afterDirectProjectionRestored = false;
        this.afterDirectModelViewRestored = false;
        this.afterDirectFramebufferRestored = false;
        this.afterDirectViewportRestored = false;
        this.lastStateRestoreException = "";
    }

    void recordStencilResult(SecondaryPortalCompositePass.StencilResult result) {
        this.stencilAttempted = result.attempted();
        this.stencilSucceeded = result.succeeded();
        this.stencilFallbackUsed = result.fallbackUsed();
        this.lastStencilException = result.exception();
    }

    void accumulateStencilResult(SecondaryPortalCompositePass.StencilResult result) {
        this.stencilAttempted |= result.attempted();
        this.stencilSucceeded |= result.succeeded();
        this.stencilFallbackUsed |= result.fallbackUsed();
        if (!result.exception().isBlank() && this.lastStencilException.isBlank()) {
            this.lastStencilException = result.exception();
        }
    }

    boolean shouldLogStencilUnavailable() {
        if (this.loggedStencilUnavailable) {
            return false;
        }
        this.loggedStencilUnavailable = true;
        return true;
    }

    boolean recordLateDirectException(String exceptionSummary) {
        this.lastException = exceptionSummary;
        if (this.lastException.equals(this.lastLoggedException)) {
            return false;
        }
        this.lastLoggedException = this.lastException;
        return true;
    }

    void recordSecondaryTargetBindObservation(int nonSecondaryBindCount, String lastNonSecondaryBind) {
        this.anySecondaryTextureTargetBindDuringDirect = nonSecondaryBindCount > 0;
        this.directRenderUnexpectedBind = lastNonSecondaryBind;
        if (this.anySecondaryTextureTargetBindDuringDirect) {
            this.directRenderSucceeded = false;
            this.directRenderFailed = true;
            this.lastDirectRenderException = "unexpected target bind " + this.directRenderUnexpectedBind;
        }
    }

    void accumulateSecondaryTargetBindObservation(int nonSecondaryBindCount, String lastNonSecondaryBind) {
        this.anySecondaryTextureTargetBindDuringDirect |= nonSecondaryBindCount > 0;
        this.directRenderUnexpectedBind = lastNonSecondaryBind;
        if (this.anySecondaryTextureTargetBindDuringDirect && this.directRenderSucceeded) {
            this.directRenderSucceeded = false;
            this.directRenderFailed = true;
            this.lastDirectRenderException = "unexpected target bind " + this.directRenderUnexpectedBind;
        }
    }
}
