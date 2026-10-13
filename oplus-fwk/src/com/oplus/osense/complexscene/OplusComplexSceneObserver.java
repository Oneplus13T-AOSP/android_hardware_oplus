package com.oplus.osense.complexscene;

import android.os.Bundle;
import java.util.List;

/** Compatibility callback for clients of the stock complex-scene API. */
public class OplusComplexSceneObserver extends IComplexSceneObserver.Stub {
    @Override
    public void onChanged(int eventId, List<Bundle> bundleList) {
    }
}
