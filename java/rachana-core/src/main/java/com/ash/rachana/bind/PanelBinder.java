/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.bind;

import com.ash.rachana.model.Panel;
import com.ash.rachana.view.PanelData;

/**
 * A profile panel binder (split step A13): the data of a panel kind a profile adds (a host's profile: {@code links},
 * {@code provenance}). Registered per kind in {@link BindHooks}; a kind without one binds to an error the panel shows.
 */
@FunctionalInterface
public interface PanelBinder {

    PanelData bind(Panel panel, BindContext context, Binder binder);
}
