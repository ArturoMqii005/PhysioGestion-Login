"""Xochi, el ajolote de PhysioGestion: una sola lista de figuras que se exporta a SVG
(web y PNG) y a VectorDrawable (icono adaptable de Android)."""
import math

PIEL, PIEL_OSCURA, HOCICO = "#C4B3FF", "#A994FF", "#E9E2FF"
BRANQUIA, BRANQUIA_CLARA = "#FF8CC6", "#FFD3E8"
OJO, MEJILLA = "#1A1430", "#FF8CC6"
FONDO_A, FONDO_B = "#7A57FF", "#4A2BD0"

def figuras():
    f = []
    # Branquias: (x base, y base, ángulo, largo, grosor), del lado izquierdo; el derecho es su espejo.
    bases = [(48, 90, 38, 42, 17), (40, 108, 8, 46, 17), (44, 126, -24, 40, 16)]
    for lado in (-1, 1):
        for (bx, by, ang, L, w) in bases:
            if lado > 0:
                bx, ang = 200 - bx, -ang
            x0 = bx - L if lado < 0 else bx
            grupo = {"rot": ang, "pivote": (bx, by), "hijos": []}
            grupo["hijos"].append(("rrect", x0, by - w / 2, L, w, w / 2, BRANQUIA, 1))
            for k in range(3):  # flecos
                t = 0.38 + 0.22 * k
                px = bx - L * t if lado < 0 else bx + L * t
                grupo["hijos"].append(("circulo", px, by - w / 2 + 1, w * 0.3, BRANQUIA, 1))
            xr = bx - L + w * 0.45 if lado < 0 else bx + L * 0.15
            grupo["hijos"].append(("rrect", xr, by - w * 0.12, L * 0.4, w * 0.24, w * 0.12, BRANQUIA_CLARA, 0.75))
            f.append(("grupo", grupo))
    f.append(("elipse", 100, 114, 66, 52, PIEL, 1))
    f.append(("elipse", 100, 140, 60, 26, PIEL_OSCURA, 0.35))    # sombra de la barbilla
    f.append(("elipse", 100, 134, 42, 24, HOCICO, 0.95))         # hocico
    f.append(("elipse", 78, 84, 18, 8, "#FFFFFF", 0.35))         # brillo
    for ex in (70, 130):
        f.append(("circulo", ex, 108, 10, OJO, 1))
        f.append(("circulo", ex + 3.5, 104.5, 3.6, "#FFFFFF", 1))
    for mx in (58, 142):
        f.append(("elipse", mx, 127, 9, 5.5, MEJILLA, 0.75))
    f.append(("trazo", "M84,131 Q100,145 116,131", OJO, 5))
    return f

# ------------------------------------------------------------------ SVG
def _svg(fig):
    k = fig[0]
    if k == "elipse":
        _, cx, cy, rx, ry, c, o = fig
        return f'<ellipse cx="{cx}" cy="{cy}" rx="{rx}" ry="{ry}" fill="{c}"' + (f' opacity="{o}"' if o < 1 else "") + "/>"
    if k == "circulo":
        _, cx, cy, r, c, o = fig
        return f'<circle cx="{cx:.2f}" cy="{cy:.2f}" r="{r:.2f}" fill="{c}"' + (f' opacity="{o}"' if o < 1 else "") + "/>"
    if k == "rrect":
        _, x, y, w, hh, r, c, o = fig
        return f'<rect x="{x:.2f}" y="{y:.2f}" width="{w:.2f}" height="{hh:.2f}" rx="{r:.2f}" fill="{c}"' + (f' opacity="{o}"' if o < 1 else "") + "/>"
    if k == "trazo":
        _, d, c, w = fig
        return f'<path d="{d}" fill="none" stroke="{c}" stroke-width="{w}" stroke-linecap="round"/>'
    if k == "grupo":
        g = fig[1]
        px, py = g["pivote"]
        return f'<g transform="rotate({g["rot"]} {px} {py})">' + "".join(_svg(h) for h in g["hijos"]) + "</g>"

def svg(fondo=True, escala=0.84, tam=None, redondeo=46):
    medida = f' width="{tam}" height="{tam}"' if tam else ""
    partes = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200"{medida}>']
    if fondo:
        partes.append(f'<defs><linearGradient id="fondo-xochi" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="{FONDO_A}"/>'
                      f'<stop offset="1" stop-color="{FONDO_B}"/></linearGradient></defs>')
        partes.append(f'<rect width="200" height="200" rx="{redondeo}" fill="url(#fondo-xochi)"/>')
    d = 100 * (1 - escala)
    partes.append(f'<g transform="translate({d:.2f} {d + 4:.2f}) scale({escala})">' + "".join(_svg(x) for x in figuras()) + "</g>")
    partes.append("</svg>")
    return "".join(partes)

# ------------------------------------------------------------------ VectorDrawable
def _color(c, o=1):
    a = round(o * 255)
    return f"#{a:02X}{c[1:].upper()}"

def _rrect_path(x, y, w, h, r):
    return (f"M{x + r:.2f},{y:.2f}h{w - 2 * r:.2f}a{r:.2f},{r:.2f} 0,0 1,{r:.2f},{r:.2f}v{h - 2 * r:.2f}"
            f"a{r:.2f},{r:.2f} 0,0 1,{-r:.2f},{r:.2f}h{-(w - 2 * r):.2f}a{r:.2f},{r:.2f} 0,0 1,{-r:.2f},{-r:.2f}"
            f"v{-(h - 2 * r):.2f}a{r:.2f},{r:.2f} 0,0 1,{r:.2f},{-r:.2f}z")

def _elipse_path(cx, cy, rx, ry):
    return f"M{cx - rx:.2f},{cy:.2f}a{rx:.2f},{ry:.2f} 0,1 0,{2 * rx:.2f},0a{rx:.2f},{ry:.2f} 0,1 0,{-2 * rx:.2f},0z"

def _vd(fig, sangria="        "):
    k = fig[0]
    if k in ("elipse", "circulo"):
        if k == "elipse":
            _, cx, cy, rx, ry, c, o = fig
        else:
            _, cx, cy, rx, c, o = fig; ry = rx
        return f'{sangria}<path android:fillColor="{_color(c, o)}" android:pathData="{_elipse_path(cx, cy, rx, ry)}"/>'
    if k == "rrect":
        _, x, y, w, hh, r, c, o = fig
        return f'{sangria}<path android:fillColor="{_color(c, o)}" android:pathData="{_rrect_path(x, y, w, hh, r)}"/>'
    if k == "trazo":
        _, d, c, w = fig
        return (f'{sangria}<path android:strokeColor="{_color(c)}" android:strokeWidth="{w}" android:strokeLineCap="round" '
                f'android:pathData="{d}"/>')
    if k == "grupo":
        g = fig[1]
        px, py = g["pivote"]
        hijos = "\n".join(_vd(h, sangria + "    ") for h in g["hijos"])
        return f'{sangria}<group android:rotation="{g["rot"]}" android:pivotX="{px}" android:pivotY="{py}">\n{hijos}\n{sangria}</group>'

def primer_plano_android(escala=0.32):
    """Capa delantera del icono adaptable (108×108; la zona segura es el círculo de 66 dp)."""
    d = 54 - 100 * escala
    cuerpo = "\n".join(_vd(x) for x in figuras())
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Xochi, el ajolote de PhysioGestion (generado con marca.py). -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <group android:translateX="{d:.2f}" android:translateY="{d + 1.5:.2f}" android:scaleX="{escala}" android:scaleY="{escala}">
{cuerpo}
    </group>
</vector>
'''

FONDO_ANDROID = f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Fondo violeta del icono (degradado de la marca). -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient android:type="linear" android:startX="0" android:startY="0" android:endX="108" android:endY="108">
                <item android:offset="0" android:color="{_color(FONDO_A)}" />
                <item android:offset="1" android:color="{_color(FONDO_B)}" />
            </gradient>
        </aapt:attr>
    </path>
</vector>
'''

if __name__ == "__main__":
    open("logo.svg", "w").write(svg())
    open("cara.svg", "w").write(svg(fondo=False, escala=1))
    open("ic_launcher_foreground.xml", "w").write(primer_plano_android())
    open("ic_launcher_background.xml", "w").write(FONDO_ANDROID)
    # Vista previa del icono adaptable con la máscara circular de Android.
    esc = 0.32; d = 54 - 100 * esc
    prev = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108"><defs><clipPath id="m"><circle cx="54" cy="54" r="54"/></clipPath>'
            f'<linearGradient id="fondo-xochi" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="{FONDO_A}"/><stop offset="1" stop-color="{FONDO_B}"/></linearGradient></defs>'
            f'<g clip-path="url(#m)"><rect width="108" height="108" fill="url(#fondo-xochi)"/>'
            f'<g transform="translate({d:.2f} {d + 1.5:.2f}) scale({esc})">' + "".join(_svg(x) for x in figuras()) + '</g></g>'
            f'<circle cx="54" cy="54" r="33" fill="none" stroke="#fff" stroke-opacity="0.5" stroke-dasharray="2 2" stroke-width="0.5"/></svg>')
    open("adaptable.svg", "w").write(prev)
