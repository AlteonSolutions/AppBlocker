# Device notes

Nothing below has been tested yet: AppGate has not been installed on any device. Fill in each row
after walking the setup in `README.md` on that device.

| Device | OS (API) | Installed | Blocker on | Uninstall protection | Videos bounced outside hours | Settings locked | Notes |
|---|---|---|---|---|---|---|---|
| Kindle Fire | Fire OS 6 (25) | | | | | | |
| Kindle Fire | Fire OS 7 (28) | | | | | | |
| Kindle Fire | Fire OS 8 (30) | | | | | | |
| Android Go tablet | | | | | | | `SYSTEM_ALERT_WINDOW` not grantable; the design doesn't need it |

## Things to check on every device

- Android 13+ greys out the accessibility switch for sideloaded apps until "Allow restricted
  settings" is turned on (Settings > Apps > AppGate > ⋮).
- On Fire, each kid profile needs its own install and setup: accessibility services are per user.
- Changing the clock, force-stopping AppGate and uninstalling should all be blocked by the Settings
  lock outside a 5-minute parent pass.
