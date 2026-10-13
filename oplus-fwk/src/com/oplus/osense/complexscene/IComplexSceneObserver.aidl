package com.oplus.osense.complexscene;

import android.os.Bundle;

oneway interface IComplexSceneObserver {
    void onChanged(int eventId, in List<Bundle> bundleList);
}
