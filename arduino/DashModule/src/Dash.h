/* ===========================================================================
   Dash.h — the DASH module SDK umbrella header
   ---------------------------------------------------------------------------
   Include this one header to build a DASH module. It pulls in the module-type
   classes; construct the one that matches your module's type (module-sdk.md §4a):

     DashSystem    — produces standard DASH signals (this release, 1.4.15)
     DashListener  — consumes subscribed standard signals   (coming next)
     DashAccessory — ships a panel, REPORT/ACTION, TRIGGER   (coming next)

   The wire protocol (framing, HELLO, install, lifecycle, state reporting) lives
   in the library; you write only your own sensors and controls. See module-sdk.md
   for the locked SDK, and the examples/ folder for reference modules.
   =========================================================================== */
#ifndef DASH_H
#define DASH_H

#include "DashModule.h"
#include "DashSystem.h"
#include "DashListener.h"
// #include "DashAccessory.h"  — added in the ACCESSORY stage

#endif  // DASH_H
