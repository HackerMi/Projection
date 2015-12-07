the project implement the function like airplay projection.

related modification:
project_code/miui/frameworks/opt/ToggleManager/src/miui/app/ToggleManager.java

diff --git a/src/miui/app/ToggleManager.java b/src/miui/app/ToggleManager.java
index facf2ff..d458907 100644
--- a/src/miui/app/ToggleManager.java
+++ b/src/miui/app/ToggleManager.java
@@ -1088,6 +1088,7 @@ public class ToggleManager {
             break;
         case TOGGLE_FLIGHT_MODE:
             toggleFlightMode();
+            mustCollapse = true;
             break;
         case TOGGLE_GPS:
             toggleGps();
@@ -1477,21 +1478,23 @@ public class ToggleManager {
     private void toggleFlightMode() {
         mFlightMode = !mFlightMode;
         Settings.Global.putInt(mResolver, Settings.Global.AIRPLANE_MODE_ON, mFlightMode ? 1 : 0);
-        Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
-        intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
+        Intent intent = new Intent("xiaomi.intent.action.PROJECTION");
+        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
+        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
+        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
         intent.putExtra("state", mFlightMode);
-        mContext.sendBroadcast(intent);
+        mContext.startActivity(intent);
     }
 
     private void updateFlightModeToggle() {
         mFlightMode = Settings.Global.getInt(mResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
 
-        updateToggleStatus(TOGGLE_FLIGHT_MODE, mFlightMode);
+//        updateToggleStatus(TOGGLE_FLIGHT_MODE, mFlightMode);
         updateToggleImage(TOGGLE_FLIGHT_MODE, mFlightMode
                 ? R.drawable.status_bar_toggle_flight_mode_on
                 : R.drawable.status_bar_toggle_flight_mode_off);
 
-        updateDataToggle();
+        //updateDataToggle();
     }
 
     private boolean togglePrivacyMode() {


dependency:
* UpnpCommon - 公共类都放在这里  
* UpnpOnLan - 标准Upnp协议实现  
* UpnpstackService - 用Android Service封装的UPNP框架  
