#!/usr/bin/env python3
"""Extract AutoScribe desktop data verbatim into Kotlin sources.

Source: https://github.com/rasool-mrr/AutoScribe
Commit: 5e6bf53a345fd3d4059adf5f107043a5590f3720 (2026-10-11)
License: MIT with a removal-on-request condition (see ../LICENSE).

What is extracted WITHOUT changes:
  * locations.json -> EMBEDDED_POPS, using the desktop LocationManager rules:
    skip pops with status != 1, skip empty names, split "City - Node" on the
    first " - ". (201 pops in the file, 199 online at this snapshot.)
  * i18n.py STRINGS (199 keys x en/fa/ru) and REGIONS, verbatim.

What is OVERRIDDEN (desktop-only wording that would be wrong on Android):
  * keyboard-shortcut hints (Ctrl+Enter / Esc / Ctrl+L ...) removed
  * "What works here" reworded from per-protocol to per-port (WireGuard only)
  * CLI / reports-folder wording replaced by Android equivalents
Overrides live in OVERRIDES below, in all three languages, and are marked
in the generated file header. New Android-only keys live in NEW_KEYS.

Re-run:  python3 tools/extract_autoscribe_data.py /path/to/AutoScribe
Output: app/src/main/java/com/autoscribe/android/data/LocationsData.kt
        app/src/main/java/com/autoscribe/android/data/StringsData.kt
"""

import json
import sys
from pathlib import Path

OVERRIDES = {
    "empty_results": {
        "en": "No results yet.\nPress Start to begin testing.",
        "fa": "هنوز نتیجه‌ای نیست.\nبرای شروع تست روی «شروع» بزنید.",
        "ru": "Пока нет результатов.\nНажмите «Старт», чтобы начать тестирование.",
    },
    "press_start": {
        "en": "Press Start",
        "fa": "روی «شروع» بزنید",
        "ru": "Нажмите «Старт»",
    },
    "start_tip": {
        "en": "Start testing",
        "fa": "شروع تست",
        "ru": "Начать тестирование",
    },
    "change_tip": {
        "en": "Search and pick countries, cities or servers",
        "fa": "جستجو و انتخاب کشور، شهر یا سرور",
        "ru": "Поиск и выбор стран, городов или серверов",
    },
    "locations_tip": {"en": "Locations", "fa": "مکان‌ها", "ru": "Локации"},
    "save_profile_tip": {
        "en": "Save settings as a profile",
        "fa": "ذخیرهٔ تنظیمات به‌عنوان پروفایل",
        "ru": "Сохранить настройки как профиль",
    },
    "export_tip": {
        "en": "Save all stored results as a CSV file",
        "fa": "ذخیرهٔ همهٔ نتایج به‌صورت فایل CSV",
        "ru": "Сохранить все результаты в CSV-файл",
    },
    "search_tip": {
        "en": "Filter the results",
        "fa": "فیلتر نتایج",
        "ru": "Фильтр результатов",
    },
    "stop": {"en": "Stop", "fa": "توقف", "ru": "Стоп"},
    "works_sub": {
        "en": "Success rate by port",
        "fa": "درصد موفقیت هر پورت",
        "ru": "Процент успеха по портам",
    },
    "works_empty": {
        "en": "Once tests run, this shows which ports get through on your network.",
        "fa": "پس از اجرای تست‌ها، اینجا نشان می‌دهد کدام پورت‌ها در شبکهٔ شما کار می‌کنند.",
        "ru": "После тестов здесь будет видно, какие порты работают в вашей сети.",
    },
    "tip_one": {
        "en": "Tip: port {names} rarely connects here. Turn it off to save time.",
        "fa": "پیشنهاد: پورت {names} اینجا به‌ندرت وصل می‌شود. برای صرفه‌جویی در زمان، خاموشش کنید.",
        "ru": "Совет: порт {names} здесь редко подключается. Отключите его, чтобы сэкономить время.",
    },
    "tip_many": {
        "en": "Tip: ports {names} rarely connect here. Turn them off to save time.",
        "fa": "پیشنهاد: پورت‌های {names} اینجا به‌ندرت وصل می‌شوند. برای صرفه‌جویی در زمان، خاموششان کنید.",
        "ru": "Совет: порты {names} здесь редко подключаются. Отключите их, чтобы сэкономить время.",
    },
}

NEW_KEYS = {
    "wg_title": {"en": "WireGuard", "fa": "WireGuard", "ru": "WireGuard"},
    "wg_not_installed": {
        "en": "WireGuard app is not installed.",
        "fa": "اپ WireGuard نصب نیست.",
        "ru": "Приложение WireGuard не установлено.",
    },
    "wg_installed": {
        "en": "WireGuard app installed",
        "fa": "اپ WireGuard نصب است",
        "ru": "Приложение WireGuard установлено",
    },
    "wg_permission_granted": {
        "en": "Control permission granted",
        "fa": "مجوز کنترل داده شده",
        "ru": "Разрешение на управление выдано",
    },
    "wg_permission_missing": {
        "en": "Control permission not granted yet",
        "fa": "مجوز کنترل هنوز داده نشده",
        "ru": "Разрешение на управление ещё не выдано",
    },
    "wg_request_permission": {
        "en": "Grant control permission",
        "fa": "دادن مجوز کنترل",
        "ru": "Выдать разрешение",
    },
    "wg_remote_hint": {
        "en": "In the WireGuard app, open Settings and turn on “Allow remote control apps”. Then toggle any tunnel once by hand and accept the VPN permission prompt.",
        "fa": "در اپ WireGuard، به تنظیمات بروید و «Allow remote control apps» را روشن کنید. بعد یک‌بار یک تانل را دستی روشن کنید و درخواست مجوز VPN را قبول کنید.",
        "ru": "В приложении WireGuard откройте настройки и включите «Allow remote control apps». Затем один раз вручную включите любой туннель и разрешите VPN-подключение.",
    },
    "wg_open_app": {
        "en": "Open WireGuard",
        "fa": "باز کردن WireGuard",
        "ru": "Открыть WireGuard",
    },
    "wg_install_hint": {
        "en": "Install WireGuard from Google Play or F-Droid first.",
        "fa": "اول WireGuard را از Google Play یا F-Droid نصب کنید.",
        "ru": "Сначала установите WireGuard из Google Play или F-Droid.",
    },
    "tunnels_tab": {"en": "Tunnels", "fa": "تانل‌ها", "ru": "Туннели"},
    "test_tab": {"en": "Test", "fa": "تست", "ru": "Тест"},
    "tunnel_setup_title": {
        "en": "Tunnel setup",
        "fa": "راه‌اندازی تانل‌ها",
        "ru": "Настройка туннелей",
    },
    "tunnel_howto": {
        "en": "Each test needs one WireGuard tunnel named exactly as shown. Generate a config for that location and port in your Windscribe account (Config Generator, Pro accounts), save it with the file name shown, and import it into the WireGuard app. Tick the tunnels you imported — only ticked tunnels are tested.",
        "fa": "هر تست به یک تانل WireGuard با دقیقاً همین نام نیاز دارد. در حساب Windscribe خود (Config Generator، حساب Pro) برای آن مکان و پورت یک کانفیگ بسازید، با همین نام فایل ذخیره‌اش کنید و در اپ WireGuard ایمپورت کنید. تانل‌هایی را که ایمپورت کرده‌اید تیک بزنید — فقط تانل‌های تیک‌خورده تست می‌شوند.",
        "ru": "Для каждого теста нужен туннель WireGuard с точным именем, как показано. Создайте конфиг для этого местоположения и порта в своём аккаунте Windscribe (Config Generator, Pro-аккаунты), сохраните его с указанным именем файла и импортируйте в приложение WireGuard. Отметьте импортированные туннели — тестируются только отмеченные.",
    },
    "tunnel_mark_all_shown": {
        "en": "Mark shown as imported",
        "fa": "علامت‌زدن موارد نمایش‌داده‌شده",
        "ru": "Отметить показанные",
    },
    "tunnel_clear_shown": {
        "en": "Clear shown",
        "fa": "پاک کردن موارد نمایش‌داده‌شده",
        "ru": "Снять отметки",
    },
    "tunnel_copy_names": {
        "en": "Copy tunnel file names",
        "fa": "کپی نام فایل‌های تانل",
        "ru": "Скопировать имена файлов",
    },
    "tunnel_names_copied": {
        "en": "{n} tunnel file names copied.",
        "fa": "{n} نام فایل تانل کپی شد.",
        "ru": "Скопировано имён файлов: {n}.",
    },
    "tunnel_probe": {"en": "Probe", "fa": "آزمایش", "ru": "Проверка"},
    "tunnel_probe_ok": {
        "en": "Probe OK: VPN came up for {x}.",
        "fa": "آزمایش موفق: VPN برای {x} بالا آمد.",
        "ru": "Проверка успешна: VPN поднялся для {x}.",
    },
    "tunnel_probe_fail": {
        "en": "Probe failed for {x}: VPN did not come up. Check the tunnel name in the WireGuard app.",
        "fa": "آزمایش {x} ناموفق بود: VPN بالا نیامد. نام تانل را در اپ WireGuard بررسی کنید.",
        "ru": "Проверка {x} не удалась: VPN не поднялся. Проверьте имя туннеля в приложении WireGuard.",
    },
    "tunnel_probe_running": {
        "en": "Probing {x}…",
        "fa": "در حال آزمایش {x}…",
        "ru": "Проверка {x}…",
    },
    "import_list": {
        "en": "Import locations.json",
        "fa": "ایمپورت locations.json",
        "ru": "Импорт locations.json",
    },
    "import_list_hint": {
        "en": "Import a newer locations.json (for example, one refreshed by the desktop AutoScribe app). The built-in list stays as fallback.",
        "fa": "یک locations.json جدیدتر ایمپورت کنید (مثلاً فایلی که اپ دسکتاپ AutoScribe به‌روز کرده است). فهرست داخلی به‌عنوان پشتیبان می‌ماند.",
        "ru": "Импортируйте более новый locations.json (например, обновлённый настольным AutoScribe). Встроенный список останется запасным.",
    },
    "import_list_ok": {
        "en": "Imported {n} servers in {c} countries.",
        "fa": "{n} سرور در {c} کشور ایمپورت شد.",
        "ru": "Импортировано серверов: {n} в {c} странах.",
    },
    "import_list_failed": {
        "en": "Could not import that file: {e}",
        "fa": "ایمپورت این فایل ممکن نشد: {e}",
        "ru": "Не удалось импортировать файл: {e}",
    },
    "list_source_builtin": {
        "en": "Built-in list",
        "fa": "فهرست داخلی",
        "ru": "Встроенный список",
    },
    "list_source_imported": {
        "en": "Imported list · {d}",
        "fa": "فهرست ایمپورت‌شده · {d}",
        "ru": "Импортированный список · {d}",
    },
    "only_imported": {
        "en": "Only test imported tunnels",
        "fa": "فقط تانل‌های ایمپورت‌شده تست شوند",
        "ru": "Тестировать только импортированные туннели",
    },
    "only_imported_tip": {
        "en": "Unticked tunnels are skipped. Turn this off only if you imported every tunnel in the plan.",
        "fa": "تانل‌های تیک‌نخورده رد می‌شوند. فقط وقتی خاموشش کنید که همهٔ تانل‌های طرح را ایمپورت کرده باشید.",
        "ru": "Неотмеченные туннели пропускаются. Отключайте, только если импортировали все туннели плана.",
    },
    "need_wg": {
        "en": "Install WireGuard and grant the control permission first (see the Tunnels tab).",
        "fa": "اول WireGuard را نصب کنید و مجوز کنترل را بدهید (برگهٔ تانل‌ها را ببینید).",
        "ru": "Сначала установите WireGuard и выдайте разрешение на управление (см. вкладку «Туннели»).",
    },
    "need_imported": {
        "en": "No imported tunnels in this plan. Mark the tunnels you imported on the Tunnels tab, or turn off “Only test imported tunnels”.",
        "fa": "در این طرح هیچ تانل ایمپورت‌شده‌ای نیست. تانل‌های ایمپورت‌شده را در برگهٔ تانل‌ها علامت بزنید، یا «فقط تانل‌های ایمپورت‌شده» را خاموش کنید.",
        "ru": "В этом плане нет импортированных туннелей. Отметьте их на вкладке «Туннели» или отключите «Только импортированные туннели».",
    },
    "battery_hint": {
        "en": "For long runs, turn off battery optimization for AutoScribe and WireGuard so Android does not pause them.",
        "fa": "برای تست‌های طولانی، بهینه‌سازی مصرف باتری را برای AutoScribe و WireGuard خاموش کنید تا اندروید آن‌ها را متوقف نکند.",
        "ru": "Для длинных прогонов отключите оптимизацию батареи для AutoScribe и WireGuard.",
    },
    "version_badge": {
        "en": "v1 • Android • WireGuard only",
        "fa": "v1 • اندروید • فقط WireGuard",
        "ru": "v1 • Android • только WireGuard",
    },
    "sort_label": {"en": "Sort", "fa": "مرتب‌سازی", "ru": "Сортировка"},
    "sort_ping": {
        "en": "Ping (fastest first)",
        "fa": "پینگ (سریع‌ترین اول)",
        "ru": "Пинг (сначала быстрые)",
    },
    "sort_newest": {
        "en": "Newest first",
        "fa": "جدیدترین اول",
        "ru": "Сначала новые",
    },
    "sort_city": {"en": "City A–Z", "fa": "شهر (الفبا)", "ru": "Город (А–Я)"},
    "sort_port": {"en": "Port", "fa": "پورت", "ru": "Порт"},
    "clear_results": {
        "en": "Clear results",
        "fa": "پاک کردن نتایج",
        "ru": "Очистить результаты",
    },
    "clear_results_q": {
        "en": "Delete all {n} stored results?",
        "fa": "همهٔ {n} نتیجهٔ ذخیره‌شده حذف شود؟",
        "ru": "Удалить все {n} сохранённых результатов?",
    },
    "run_notification": {
        "en": "Testing {x}",
        "fa": "در حال تست {x}",
        "ru": "Тестирование {x}",
    },
    "notification_channel": {
        "en": "Test runs",
        "fa": "تست‌ها",
        "ru": "Тесты",
    },
    "no_tunnels_shown": {
        "en": "No tunnels match this filter.",
        "fa": "تانلی با این فیلتر پیدا نشد.",
        "ru": "Нет туннелей по этому фильтру.",
    },
    "search_tunnels": {
        "en": "Search tunnels…",
        "fa": "جستجوی تانل‌ها…",
        "ru": "Поиск туннелей…",
    },
    "plan_summary": {
        "en": "{s} servers × {r} ports · {n} imported",
        "fa": "{s} سرور × {r} پورت · {n} ایمپورت‌شده",
        "ru": "{s} серверов × {r} портов · импортировано: {n}",
    },
}


def kotlin_str(s: str) -> str:
    out = (
        s.replace("\\", "\\\\")
        .replace('"', '\\"')
        .replace("$", "\\$")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
    )
    return f'"{out}"'


def main() -> None:
    src = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("/tmp/autoscribe")
    sys.path.insert(0, str(src))
    import i18n  # noqa: E402  (from the AutoScribe checkout)

    root = Path(__file__).resolve().parents[1]
    data_dir = root / "app/src/main/java/com/autoscribe/android/data"
    data_dir.mkdir(parents=True, exist_ok=True)

    # ---- Locations -------------------------------------------------------
    raw = json.loads((src / "locations.json").read_text(encoding="utf-8"))
    pops = []
    for entry in raw["data"]["locations"].values():
        country = (entry.get("name") or "Unknown").strip()
        cc = (entry.get("country_code") or "").strip()
        continent = (entry.get("continent") or "Unknown").strip()
        for pop in entry.get("pops", []):
            if pop.get("status", 1) != 1:
                continue
            name = (pop.get("name") or "").strip()
            if not name:
                continue
            if " - " in name:
                city, node = name.split(" - ", 1)
            else:
                city, node = name, ""
            pops.append(
                (country, cc, continent, city.strip(), node.strip(),
                 bool(pop.get("is_premium", 0)), bool(pop.get("is_p2p", 0)))
            )
    pops.sort(key=lambda p: (p[0], p[3], p[4]))
    lines = [
        "package com.autoscribe.android.data",
        "",
        "// GENERATED by tools/extract_autoscribe_data.py — do not edit by hand.",
        "// Source: rasool-mrr/AutoScribe @ 5e6bf53, locations.json snapshot.",
        "// Flattening rules are the desktop LocationManager rules (see the script).",
        f"// {len(pops)} online pops in {len({p[0] for p in pops})} countries.",
        "",
        "import com.autoscribe.android.engine.Pop",
        "",
        "val EMBEDDED_POPS: List<Pop> = listOf(",
    ]
    for country, cc, continent, city, node, premium, p2p in pops:
        lines.append(
            "    Pop(%s, %s, %s, %s, %s, %s, %s),"
            % (
                kotlin_str(country), kotlin_str(cc), kotlin_str(continent),
                kotlin_str(city), kotlin_str(node),
                "true" if premium else "false", "true" if p2p else "false",
            )
        )
    lines.append(")")
    lines.append("")
    (data_dir / "LocationsData.kt").write_text("\n".join(lines), encoding="utf-8")
    print(f"locations: {len(pops)} pops, {len({p[0] for p in pops})} countries")

    # ---- Strings ---------------------------------------------------------
    strings = dict(i18n.STRINGS)
    for key, val in OVERRIDES.items():
        assert key in strings, key
        strings[key] = val
    for key, val in NEW_KEYS.items():
        assert key not in strings, key
        strings[key] = val
    for key, val in strings.items():
        assert set(val) == {"en", "fa", "ru"}, key

    lines = [
        "package com.autoscribe.android.data",
        "",
        "// GENERATED by tools/extract_autoscribe_data.py — do not edit by hand.",
        "// 199 keys verbatim from i18n.py (rasool-mrr/AutoScribe @ 5e6bf53),",
        f"// except {len(OVERRIDES)} Android-adapted overrides (see the script),",
        f"// plus {len(NEW_KEYS)} Android-only keys. All keys have en/fa/ru.",
        "",
        "val STRING_TABLE: Map<String, Map<String, String>> = mapOf(",
    ]
    for key in sorted(strings):
        val = strings[key]
        lines.append(
            "    %s to mapOf(\"en\" to %s, \"fa\" to %s, \"ru\" to %s),"
            % (kotlin_str(key), kotlin_str(val["en"]), kotlin_str(val["fa"]), kotlin_str(val["ru"]))
        )
    lines.append(")")
    lines.append("")
    lines.append("val REGION_NAMES: Map<String, Map<String, String>> = mapOf(")
    for region in sorted(i18n.REGIONS):
        val = i18n.REGIONS[region]
        lines.append(
            "    %s to mapOf(\"en\" to %s, \"fa\" to %s, \"ru\" to %s),"
            % (kotlin_str(region), kotlin_str(region),
               kotlin_str(val.get("fa", region)), kotlin_str(val.get("ru", region)))
        )
    lines.append(")")
    lines.append("")
    (data_dir / "StringsData.kt").write_text("\n".join(lines), encoding="utf-8")
    print(f"strings: {len(strings)} keys ({len(OVERRIDES)} overrides, {len(NEW_KEYS)} new)")
    print(f"regions: {len(i18n.REGIONS)}")


if __name__ == "__main__":
    main()
