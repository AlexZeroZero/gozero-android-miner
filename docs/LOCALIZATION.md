# Localization

The app supports `zh`, `en`, `ru` and `ja`. The default is Chinese, preserving the original installation behavior. The in-app selector stores the choice in the `app` preference file under `language`; it does not change the system locale or restart the mining service.

Edit `tools/localization/catalog.json` and run:

```sh
python tools/localization/generate.py
```

The JSON maps canonical Chinese display phrases to English, Russian and Japanese. The generated `Translations.java` is committed so an ordinary Android build requires no generation step. Existing mining states, parsers, thermal decisions and counters retain their canonical values. Translation occurs only at UI, notification and dialog display boundaries; input fields, payout addresses, protocol data, native English diagnostics and license text are not rewritten.

Exact entries take priority; composed messages use a single pass with longest-phrase matching. Japanese output is never recursively translated. Keep complete UI sentences where possible and include fragments for dynamic numbers. Run the JVM tests after changes; they cover catalog completeness, dynamic telemetry, thermal messages, address/protocol preservation and locale-independent numeric formatting.

Device verification covers all four languages, saved language after process restart, unsaved form preservation and live switching with unchanged app/core PIDs.
