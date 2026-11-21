"""Build the Amar Iptv website (download page and legal pages) for GitHub Pages.

The legal pages are generated from the same Markdown files the app bundles, so the
website and the in-app text never drift apart. Standard library only.

    python scripts/build_site.py --out build/site
"""

import argparse
import html
import json
import re
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SITE = ROOT / "site"
DESIGN = ROOT / "core" / "designsystem" / "src" / "main"
LEGAL = DESIGN / "assets" / "legal"
LICENSES = DESIGN / "assets" / "licenses"
FONTS = DESIGN / "res" / "font"

REPOSITORY = "FireAmun/Iptv"
SITE_URL = "https://fireamun.github.io/Iptv/"
BASE_PATH = "/Iptv/"
APK_NAME = "amar-iptv.apk"
LATEST_APK = f"https://github.com/{REPOSITORY}/releases/latest/download/{APK_NAME}"
RELEASES = f"https://github.com/{REPOSITORY}/releases"
DEVELOPER = "Amar Elshaer"
YEAR = 2026
# Set to the Play listing URL once the app is published there; the badge stays hidden until then.
PLAY_URL = None

CSP = ("default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self'; font-src 'self'; "
       "connect-src https://api.github.com; base-uri 'none'; form-action 'none'")

NOTICE_LICENSES = [
    ("Apache License 2.0", "Apache-2.0.txt"),
    ("Manrope · SIL Open Font License 1.1", "Manrope-OFL.txt"),
    ("Cairo · SIL Open Font License 1.1", "Cairo-OFL.txt"),
]

DOWNLOAD_ICON = ('<svg viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="2.2" '
                 'stroke-linecap="round" stroke-linejoin="round"><path d="M12 3v12"/><path d="m7 10 5 5 5-5"/><path d="M5 21h14"/></svg>')

TEXT = {
    "en": {
        "dir": "ltr",
        "home_title": "Amar Iptv · Media player for Android phones and TV",
        "home_description": "Download Amar Iptv, a fast and calm player for your own M3U playlists, files and server logins on Android phones, tablets and Android TV.",
        "nav_download": "Download",
        "nav_privacy": "Privacy",
        "nav_terms": "Terms",
        "nav_notices": "Open-source notices",
        "nav_language": "العربية",
        "nav_language_name": "Arabic",
        "kicker": "For Android phones, tablets and TV",
        "headline": "Your playlists, beautifully played.",
        "lead": "Amar Iptv is a fast, calm player for the M3U playlists, files and server logins you already have. It works with a touch screen or a TV remote, in English and Arabic.",
        "download": "Download APK",
        "meta": "Latest version for Android 9 or later",
        "meta_template": "Version {version} · {size} MB · {date}",
        "no_release": "The first public download is on its way. Please check back soon.",
        "all_versions": "All versions and release notes",
        "checksum": "SHA-256",
        "play": "Get it on Google Play",
        "player_only": "Amar Iptv is a player only. It does not include or sell any channels, films or subscriptions.",
        "screens_tv": "Amar Iptv on a TV, showing the Movies page",
        "screens_phone": "Amar Iptv on a phone, showing the Movies page",
        "features_title": "Made for the couch and the commute",
        "features": [
            ("Live TV, films and series", "Browse by category, search everything and pick up where you left off with Continue Watching."),
            ("Built for the remote", "A TV layout with large, clear focus that works with the arrow keys alone."),
            ("Your picture, your sound", "Choose video quality, audio language and subtitles when your source offers them."),
            ("Arabic and English", "A full right-to-left layout, with light and dark appearance."),
            ("Private by design", "Your sources and logins are encrypted on your device. No account needed."),
        ],
        "install_title": "How to install",
        "install_phone": "Phone or tablet",
        "install_phone_steps": [
            "Tap Download APK on your phone or tablet.",
            "Open the downloaded file. If Android asks, allow your browser to install unknown apps.",
            "Tap Install, then open Amar Iptv and add your playlist.",
        ],
        "install_tv": "Android TV and Google TV",
        "install_tv_steps": [
            "Install a web browser or a downloader app from your TV's app store.",
            "Open fireamun.github.io/Iptv in it and choose Download APK.",
            "When asked, allow that app to install unknown apps, then install Amar Iptv.",
        ],
        "install_tv_note": "LG (webOS), Samsung (Tizen) and Hisense (VIDAA) TVs do not run Android apps, so Amar Iptv cannot be installed on them.",
        "updates_title": "Updates and safety",
        "updates": "To update, download the newest APK and install it over the current one. Your library and settings stay. Download Amar Iptv only from this page or the project's GitHub Releases, and compare the SHA-256 checksum if you want to be sure the file is unchanged.",
        "footer_player": "Amar Iptv does not provide channels or content.",
        "contact": "Contact",
        "not_found_title": "Page not found",
        "not_found": "This page does not exist. Go to the Amar Iptv home page.",
    },
    "ar": {
        "dir": "rtl",
        "home_title": "Amar Iptv · مشغّل وسائط لهواتف أندرويد والتلفزيون",
        "home_description": "نزّل Amar Iptv، المشغّل السريع والهادئ لقوائم M3U والملفات وتسجيلات الدخول إلى الخوادم الخاصة بك على هواتف أندرويد والأجهزة اللوحية و Android TV.",
        "nav_download": "التنزيل",
        "nav_privacy": "الخصوصية",
        "nav_terms": "الشروط",
        "nav_notices": "إشعارات المصادر المفتوحة",
        "nav_language": "English",
        "nav_language_name": "الإنجليزية",
        "kicker": "لهواتف أندرويد والأجهزة اللوحية والتلفزيون",
        "headline": "قوائمك، بعرضٍ جميل.",
        "lead": "Amar Iptv مشغّل سريع وهادئ لقوائم M3U والملفات وتسجيلات الدخول إلى الخوادم التي لديك بالفعل. يعمل باللمس أو بجهاز التحكم عن بُعد، بالعربية والإنجليزية.",
        "download": "تنزيل APK",
        "meta": "أحدث إصدار لأندرويد 9 أو أحدث",
        "meta_template": "الإصدار {version} · {size} ميغابايت · {date}",
        "no_release": "أول تنزيل عام في الطريق. يُرجى العودة قريبًا.",
        "all_versions": "كل الإصدارات وملاحظاتها",
        "checksum": "SHA-256",
        "play": "احصل عليه من Google Play",
        "player_only": "Amar Iptv مشغّل فقط، ولا يتضمن أو يبيع أي قنوات أو أفلام أو اشتراكات.",
        "screens_tv": "Amar Iptv على التلفزيون، وتظهر صفحة الأفلام",
        "screens_phone": "Amar Iptv على الهاتف، وتظهر صفحة الأفلام",
        "features_title": "مصمم للأريكة وللطريق",
        "features": [
            ("بث مباشر وأفلام ومسلسلات", "تصفّح حسب الفئة، وابحث في كل شيء، وتابع من حيث توقفت عبر «متابعة المشاهدة»."),
            ("مصمم لجهاز التحكم", "واجهة تلفزيون بتركيز كبير وواضح تعمل بأزرار الاتجاهات وحدها."),
            ("صورتك وصوتك", "اختر جودة الفيديو ولغة الصوت والترجمة عندما يوفرها مصدرك."),
            ("العربية والإنجليزية", "تخطيط كامل من اليمين إلى اليسار، مع مظهر فاتح وداكن."),
            ("الخصوصية أولًا", "مصادرك وبيانات دخولك مشفّرة على جهازك. لا حاجة إلى حساب."),
        ],
        "install_title": "طريقة التثبيت",
        "install_phone": "الهاتف أو الجهاز اللوحي",
        "install_phone_steps": [
            "اضغط «تنزيل APK» على هاتفك أو جهازك اللوحي.",
            "افتح الملف الذي تم تنزيله. إذا طلب أندرويد ذلك، فاسمح للمتصفح بتثبيت التطبيقات غير المعروفة.",
            "اضغط «تثبيت»، ثم افتح Amar Iptv وأضف قائمتك.",
        ],
        "install_tv": "Android TV و Google TV",
        "install_tv_steps": [
            "ثبّت متصفح ويب أو تطبيق تنزيل من متجر تطبيقات التلفزيون.",
            "افتح fireamun.github.io/Iptv فيه واختر «تنزيل APK».",
            "عند الطلب، اسمح لذلك التطبيق بتثبيت التطبيقات غير المعروفة، ثم ثبّت Amar Iptv.",
        ],
        "install_tv_note": "أجهزة تلفزيون LG ‏(webOS) و Samsung ‏(Tizen) و Hisense ‏(VIDAA) لا تشغّل تطبيقات أندرويد، لذلك لا يمكن تثبيت Amar Iptv عليها.",
        "updates_title": "التحديثات والأمان",
        "updates": "للتحديث، نزّل أحدث ملف APK وثبّته فوق الإصدار الحالي، وتبقى مكتبتك وإعداداتك كما هي. نزّل Amar Iptv فقط من هذه الصفحة أو من قسم الإصدارات في GitHub، وقارن رمز التحقق SHA-256 إن أردت التأكد من أن الملف لم يتغير.",
        "footer_player": "لا يوفّر Amar Iptv أي قنوات أو محتوى.",
        "contact": "التواصل",
        "not_found_title": "الصفحة غير موجودة",
        "not_found": "هذه الصفحة غير موجودة. انتقل إلى الصفحة الرئيسية لـ Amar Iptv.",
    },
}

def html_text(language):
    """UI strings for one language, HTML-escaped (titles and JSON use the raw TEXT values)."""
    def escape(value):
        if isinstance(value, str):
            return html.escape(value)
        if isinstance(value, (list, tuple)):
            return type(value)(escape(item) for item in value)
        return value
    return {key: escape(value) for key, value in TEXT[language].items()}


EMAIL = re.compile(r"[\w.+-]+@[\w-]+(?:\.[\w-]+)+")


def parse_markdown(source):
    """Same subset and rules as parseLegalMarkdown in AmarLegal.kt: '#', '##', '- ' and paragraphs."""
    blocks, paragraph = [], []

    def flush():
        if paragraph:
            blocks.append(("p", " ".join(paragraph)))
            paragraph.clear()

    for line in source.splitlines():
        line = line.strip()
        if not line:
            flush()
        elif line.startswith("## "):
            flush(); blocks.append(("h2", line[3:].strip()))
        elif line.startswith("# "):
            flush(); blocks.append(("h1", line[2:].strip()))
        elif line.startswith("- "):
            flush(); blocks.append(("li", line[2:].strip()))
        else:
            paragraph.append(line)
    flush()
    return blocks


def license_blocks(source):
    """Same reflow as licenseBlocks in AmarLegal.kt: one block per blank-line-separated paragraph."""
    parts = re.split(r"\n\s*\n", source.replace("\r\n", "\n"))
    return [("license", re.sub(r"\s+", " ", part.strip())) for part in parts if part.strip()]


def inline(text):
    escaped = html.escape(text, quote=False)
    return EMAIL.sub(lambda m: f'<a href="mailto:{m.group(0)}">{m.group(0)}</a>', escaped)


def render_blocks(blocks):
    out, items, first_paragraph = [], [], True

    def close_list():
        if items:
            out.append("<ul>" + "".join(f"<li>{i}</li>" for i in items) + "</ul>")
            items.clear()

    for kind, text in blocks:
        if kind == "li":
            items.append(inline(text)); continue
        close_list()
        if kind == "h1":
            out.append(f"<h1>{inline(text)}</h1>")
        elif kind == "h2":
            out.append(f"<h2>{inline(text)}</h2>"); first_paragraph = False
        elif kind == "license":
            out.append(f'<p class="license">{inline(text)}</p>')
        else:
            css = ' class="meta"' if first_paragraph else ""
            out.append(f"<p{css}>{inline(text)}</p>"); first_paragraph = False
    close_list()
    return "\n".join(out)


def document_title(blocks):
    return next(text for kind, text in blocks if kind == "h1")


def legal_blocks(language, name):
    path = LEGAL / language / f"{name}.md"
    blocks = parse_markdown(path.read_text(encoding="utf-8"))
    if name == "notices":
        for title, file in NOTICE_LICENSES:
            blocks.append(("h2", title))
            blocks.extend(license_blocks((LICENSES / file).read_text(encoding="utf-8")))
    return blocks


class Page:
    def __init__(self, path, language, title, description, body, alternate=None, current=None, absolute=False):
        self.path, self.language, self.title, self.description = path, language, title, description
        self.body, self.alternate, self.current, self.absolute = body, alternate, current, absolute

    @property
    def root(self):
        return BASE_PATH if self.absolute else "../" * self.path.count("/")

    def url(self, target):
        return self.root + target

    def public_url(self):
        return SITE_URL + self.path.removesuffix("index.html")


def header(page):
    t = html_text(page.language)
    prefix = "ar/" if page.language == "ar" else ""
    links = [("download", prefix, t["nav_download"]), ("privacy", prefix + "privacy/", t["nav_privacy"]),
             ("terms", prefix + "terms/", t["nav_terms"])]
    current = ' aria-current="page"'
    nav = "".join(f'<a href="{page.url(target)}"{current if key == page.current else ""}>{label}</a>'
                  for key, target, label in links)
    other = "en" if page.language == "ar" else "ar"
    switch = page.alternate if page.alternate is not None else ("" if other == "en" else "ar/")
    nav += (f'<a href="{page.url(switch)}" hreflang="{other}" lang="{other}" '
            f'title="{t["nav_language_name"]}">{t["nav_language"]}</a>')
    return (f'<header class="site-header"><div class="wrap">'
            f'<a class="brand" href="{page.url(prefix)}"><img src="{page.url("assets/icon.svg")}" alt="" width="36" height="36">'
            f'<span>Amar Iptv</span></a><nav class="site-nav" aria-label="Site">{nav}</nav></div></header>')


def footer(page):
    t = html_text(page.language)
    prefix = "ar/" if page.language == "ar" else ""
    email = html.escape(support_email())
    return (f'<footer class="site-footer"><div class="wrap">'
            f'<nav aria-label="Legal"><a href="{page.url(prefix + "privacy/")}">{t["nav_privacy"]}</a>'
            f'<a href="{page.url(prefix + "terms/")}">{t["nav_terms"]}</a>'
            f'<a href="{page.url("notices/")}">{t["nav_notices"]}</a>'
            f'<a href="mailto:{email}">{t["contact"]}</a></nav>'
            f'<p>{t["footer_player"]}</p>'
            f'<p>© {YEAR} <span class="ltr">{DEVELOPER}</span></p></div></footer>')


def render(page):
    t = TEXT[page.language]
    alternates = ""
    if page.alternate is not None:
        other = "en" if page.language == "ar" else "ar"
        alternates = (f'<link rel="alternate" hreflang="{page.language}" href="{page.public_url()}">'
                      f'<link rel="alternate" hreflang="{other}" href="{SITE_URL + page.alternate}">')
    canonical = "" if page.absolute else f'<link rel="canonical" href="{page.public_url()}">'
    return f"""<!doctype html>
<html lang="{page.language}" dir="{t['dir']}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta http-equiv="Content-Security-Policy" content="{CSP}">
<meta name="referrer" content="strict-origin-when-cross-origin">
<title>{html.escape(page.title)}</title>
<meta name="description" content="{html.escape(page.description)}">
<meta name="theme-color" content="#15140f">
<meta property="og:title" content="{html.escape(page.title)}">
<meta property="og:description" content="{html.escape(page.description)}">
<meta property="og:type" content="website">
{canonical}{alternates}
<link rel="icon" href="{page.url('assets/icon.svg')}" type="image/svg+xml">
<link rel="stylesheet" href="{page.url('assets/style.css')}">
</head>
<body>
{header(page)}
<main id="main">
{page.body}
</main>
{footer(page)}
</body>
</html>
"""


def support_email():
    """The contact address printed in the bundled English Privacy Policy (AmarLegalTest ties it to the app)."""
    match = EMAIL.search((LEGAL / "en" / "privacy.md").read_text(encoding="utf-8"))
    if not match:
        raise SystemExit("No contact email found in the bundled Privacy Policy")
    return match.group(0)


def home_body(language, root):
    t = html_text(language)
    raw = TEXT[language]
    features = "".join(f'<div class="card"><h3>{a}</h3><p>{b}</p></div>' for a, b in t["features"])
    phone_steps = "".join(f"<li>{s}</li>" for s in t["install_phone_steps"])
    tv_steps = "".join(f"<li>{s}</li>" for s in t["install_tv_steps"])
    data = html.escape(json.dumps({"repo": REPOSITORY, "asset": APK_NAME, "lang": language,
                                   "template": raw["meta_template"], "none": raw["no_release"],
                                   "releases": RELEASES}, ensure_ascii=False))
    play = (f'<a href="{html.escape(PLAY_URL)}">{t["play"]}</a>' if PLAY_URL else "")
    return f"""<section class="hero">
<div class="wrap">
<p class="kicker">{t['kicker']}</p>
<h1>{t['headline']}</h1>
<p class="lead">{t['lead']}</p>
<div class="download" id="download" data-release="{data}">
<a class="button" data-role="button" href="{LATEST_APK}">{DOWNLOAD_ICON}<span>{t['download']}</span></a>
<p class="release-meta" data-role="meta" aria-live="polite">{t['meta']}</p>
<p class="checksum" data-role="checksum" hidden>{t['checksum']}: <code class="ltr"></code></p>
<p class="release-links"><a href="{RELEASES}">{t['all_versions']}</a>{play}</p>
</div>
<p class="note">{t['player_only']}</p>
<div class="screens">
<img class="tv" src="{root}assets/screens/tv-{language}.webp" width="1200" height="675" alt="{t['screens_tv']}">
<img class="phone" src="{root}assets/screens/phone-{language}.webp" width="540" height="861" alt="{t['screens_phone']}" loading="lazy">
</div>
</div>
</section>
<section class="block"><div class="wrap">
<h2>{t['features_title']}</h2>
<div class="grid">{features}</div>
</div></section>
<section class="block" id="install"><div class="wrap">
<h2>{t['install_title']}</h2>
<div class="grid">
<div class="card"><h3>{t['install_phone']}</h3><ol>{phone_steps}</ol></div>
<div class="card"><h3>{t['install_tv']}</h3><ol>{tv_steps}</ol><p class="note-inline">{t['install_tv_note']}</p></div>
</div>
</div></section>
<section class="block"><div class="wrap">
<h2>{t['updates_title']}</h2>
<p class="prose-panel">{t['updates']}</p>
</div></section>
<script src="{root}assets/download.js" defer></script>"""


def legal_body(language, name):
    blocks = legal_blocks(language, name)
    return document_title(blocks), f'<div class="legal"><div class="wrap"><article>{render_blocks(blocks)}</article></div></div>'


def pages():
    result = []
    for language, prefix, other_prefix in (("en", "", "ar/"), ("ar", "ar/", "")):
        t = TEXT[language]
        root = "../" if prefix else ""
        result.append(Page(prefix + "index.html", language, t["home_title"], t["home_description"],
                           home_body(language, root), alternate=other_prefix, current="download"))
        for name in ("privacy", "terms"):
            title, body = legal_body(language, name)
            result.append(Page(f"{prefix}{name}/index.html", language, f"{title} · Amar Iptv",
                               t["home_description"], body, alternate=f"{other_prefix}{name}/", current=name))
    title, body = legal_body("en", "notices")
    result.append(Page("notices/index.html", "en", f"{title} · Amar Iptv", TEXT["en"]["home_description"], body))
    t = TEXT["en"]
    result.append(Page("404.html", "en", f"{t['not_found_title']} · Amar Iptv", t["home_description"],
                       f'<div class="legal"><div class="wrap"><article><h1>{html.escape(t["not_found_title"])}</h1>'
                       f'<p><a href="{BASE_PATH}">{html.escape(t["not_found"])}</a></p></article></div></div>', absolute=True))
    return result


MARKER = ".amar-site"


def build(out):
    out = Path(out).resolve()
    if out == ROOT or out in ROOT.parents or out == SITE or SITE in out.parents:
        raise SystemExit(f"Refusing to build into {out}; choose an output folder such as build/site")
    if out.exists():
        if any(out.iterdir()) and not (out / MARKER).is_file():
            raise SystemExit(f"{out} is not empty and was not made by this script; choose another folder")
        shutil.rmtree(out)
    shutil.copytree(SITE / "assets", out / "assets")
    (out / MARKER).write_text("Built by scripts/build_site.py; safe to delete.\n", encoding="utf-8")
    (out / "assets" / "fonts").mkdir()
    for font in ("manrope.ttf", "cairo.ttf"):
        shutil.copy2(FONTS / font, out / "assets" / "fonts" / font)
    written = []
    for page in pages():
        target = out / page.path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(render(page), encoding="utf-8", newline="\n")
        written.append(page.path)
    (out / "robots.txt").write_text(f"User-agent: *\nAllow: /\nSitemap: {SITE_URL}sitemap.xml\n", encoding="utf-8", newline="\n")
    urls = "".join(f"<url><loc>{SITE_URL + p.removesuffix('index.html')}</loc></url>" for p in written if p != "404.html")
    (out / "sitemap.xml").write_text('<?xml version="1.0" encoding="UTF-8"?>\n'
                                     f'<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">{urls}</urlset>\n',
                                     encoding="utf-8", newline="\n")
    return written


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--out", type=Path, default=ROOT / "build" / "site")
    out = parser.parse_args().out
    written = build(out)
    print(f"Built {len(written)} pages into {out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
