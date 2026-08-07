# Słowniki gry — źródła, licencje, odtwarzanie

Gra generuje plansze w dwóch językach. Każdy ma własny alfabet, własny słownik i własną listę
wykluczeń — żadna z tych trzech rzeczy nie przenosi się między językami.

| | polski | angielski |
|---|---|---|
| dopuszczalność słowa | Słownik SJP.PL do gier | ENABLE |
| licencja | **CC BY 4.0** (do wyboru z GPL 2, LGPL 2.1, MPL 1.1, Apache 2.0) | **domena publiczna** |
| frekwencja | KWJP100, IPI PAN | OANC (Open American National Corpus) |
| licencja | **CC BY 4.0** | **bez ograniczeń**, komercyjne dozwolone |
| ARF | gotowy w źródle | liczony przez `buildDictionaryEn` |
| słów po przecięciu | 247 012 | 61 234 |

## Obowiązek atrybucji — do umieszczenia w aplikacji

Wymagają jej tylko źródła polskie. Ekran „O aplikacji" musi zawierać (treść można przeredagować,
elementy nie mogą zniknąć):

> Słownik polski oparty na **Słowniku SJP.PL** (https://sjp.pl) oraz na listach frekwencyjnych
> **KWJP100** — Korpus Współczesnego Języka Polskiego, Instytut Podstaw Informatyki PAN
> (https://kwjp.pl). Oba zasoby udostępnione na licencji
> **Creative Commons Uznanie autorstwa 4.0 Międzynarodowe** (https://creativecommons.org/licenses/by/4.0/).
> Materiał został zmodyfikowany: wybrano podzbiór słów i uszeregowano je według frekwencji.

Ostatnie zdanie nie jest ozdobnikiem — CC BY 4.0 wymaga oznaczenia, że materiał zmodyfikowano.

Źródła angielskie atrybucji nie wymagają. ENABLE jest w domenie publicznej („The ENABLE master word
list, WORD.LST, is herewith formally released into the Public Domain"), OANC nie nakłada ograniczeń.
Wymienienie ich mimo to jest uczciwe, ale nieobowiązkowe.

## Dlaczego takie źródła

**Polski, CC BY zamiast GPL.** Listy SJP.PL są udostępniane **do wyboru** na GPL 2, LGPL 2.1,
CC BY 4.0, MPL 1.1 lub Apache 2.0. Licencje są alternatywne, nie łączne, więc wybór CC BY 4.0
oznacza, że copyleft nie ma zastosowania i aplikacja może pozostać zamknięta.

**Odrzucone z powodu share-alike** (zaraziłoby wynik): `hermitdave/FrequencyWords` (CC BY-SA 4.0),
`rspeer/wordfreq` — dane na CC BY-SA 4.0, listy BNC/Leech (CC BY-SA 2.0 UK).

**Odrzucone z powodu braku licencji:** `count_1w.txt` Norviga. Strona licencjonuje kod na MIT,
o danych nie mówi nic, a pochodzą z korpusu LDC. Brak licencji to nie wolna licencja.

**Rozważana alternatywa dla polskiego**, gdyby atrybucja kiedyś przeszkadzała: dane fleksyjne
SGJP / PoliMorf na 2-clause BSD (https://morfeusz.sgjp.pl/doc/license/).

## Uwaga o pobieraniu OANC

Certyfikat TLS serwera `anc.org` wygasł. Pobranie wymaga wyłączenia weryfikacji certyfikatu
(`curl -k`), co oznacza, że nie da się potwierdzić pochodzenia pliku. Ryzyko jest niskie — to
korpus tekstowy, nie kod, a treść i tak przechodzi przez filtr alfabetu i przecięcie z ENABLE —
ale nie jest zerowe.

Suma kontrolna pobrania z 2026-08-05, do porównania przy ponownym pobraniu:

```
SHA-256  5a26559a1becba41a527cb674fff4fb9c4fb70b276f60422c4f07a0ef23fd867
plik     OANC_GrAF.zip, 655 230 430 bajtów
```

anc.org nie publikuje własnej sumy kontrolnej, więc powyższa nie jest dowodem autentyczności —
służy tylko do sprawdzenia, czy kolejne pobranie dało ten sam plik.

## Odtworzenie słowników

```bash
mkdir -p generator/data

# --- polski ---
# Nazwa pliku zmienia się z datą wydania, aktualną sprawdzić na https://sjp.pl/sl/growy/
curl -L -o /tmp/sjp.zip https://sjp.pl/slownik/growy/sjp-20260803.zip
unzip -p /tmp/sjp.zip slowa.txt > generator/data/sjp-slowa.txt

curl -L https://raw.githubusercontent.com/ipipan/kwjp100-varia/main/freqlists/kwjp100-slowa-orth_lc-all.csv.gz \
  | gunzip > generator/data/kwjp100-orth_lc-all.csv

# --- angielski ---
curl -L -o generator/data/enable1.txt \
  https://raw.githubusercontent.com/dolph/dictionary/master/enable1.txt

# 625 MB, wygasły certyfikat — patrz uwaga wyżej
curl -kL -o /tmp/oanc.zip http://www.anc.org/OANC/OANC_GrAF.zip
# rozpakować wyłącznie pliki .txt do generator/data/oanc/ (8824 pliki, 115 MB)

./gradlew :generator:buildDictionaries
```

## Co robi pipeline

1. Ze źródła dopuszczalności bierze słowa długości 3–12 złożone wyłącznie z liter alfabetu języka.
2. Przecina je z danymi frekwencyjnymi. Słowo nieobecne w korpusie wypada — po polsku tak znika
   1,82 mln form typu `niewynuceń`, po angielsku 91 tys. form z ENABLE.
3. Sortuje malejąco po **ARF** (average reduced frequency). ARF karze słowa częste tylko dlatego,
   że skupiają się w jednym dokumencie; surowa częstość takie nagradza. Dla polskiego ARF jest
   gotowy w KWJP100, dla angielskiego liczony z pozycji tokenów w OANC (wzór Savický–Hlaváčová,
   odstępy cykliczne).
4. Usuwa słowa z `blocklist-<język>.txt`. Każda usunięta forma trafia do `build/removed-<język>.txt`.
5. Zapisuje same słowa, po jednym w wierszu. **Numer wiersza jest rangą trudności.**

Nazwy własne odpadają same: obie listy dopuszczalności ich nie zawierają, więc przecięcie je usuwa,
mimo że listy frekwencyjne są sprowadzone do małych liter.

## Progi trudności

`Difficulty.EASY` = ranga 10 000, `Difficulty.HARD` = ranga 25 000.

**Ta sama ranga znaczy w obu językach co innego** i trzeba o tym pamiętać przy strojeniu:

| ranga | polski, min ARF | angielski, min ARF |
|---|---|---|
| 10 000 | 394,0 | 25,5 |
| 25 000 | 139,9 | 4,7 |

Różnica bierze się z rozmiaru korpusów (100 mln tokenów wobec 14,7 mln) i z fleksji — polski ma
2,07 mln dopuszczalnych form wobec 152 tys. angielskich. Mimo to ranga działa jako wspólne pokrętło,
bo o trudności decyduje pozycja w rankingu, nie bezwzględna wartość ARF. Kontrola na słowach z
okolic progu: polski 25 000 to `dachach, brygada, ruszam`, angielski 25 000 to `rehearsed, gaggle,
tabulate` — porównywalny poziom.

## Rozkład długości

polski:

| ranga | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 | 12 |
|---|---|---|---|---|---|---|---|---|---|---|
| 10 000 | 205 | 649 | 1283 | 1726 | 1763 | 1527 | 1173 | 831 | 548 | 295 |
| 25 000 | 324 | 1117 | 2449 | 3579 | 4306 | 4104 | 3503 | 2618 | 1855 | 1145 |
| 247 012 | 1256 | 4711 | 11869 | 20895 | 31488 | 39171 | 42157 | 39095 | 32449 | 23921 |

angielski:

| ranga | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 | 12 |
|---|---|---|---|---|---|---|---|---|---|---|
| 10 000 | 324 | 947 | 1300 | 1507 | 1567 | 1447 | 1189 | 868 | 566 | 285 |
| 25 000 | 611 | 1704 | 2695 | 3562 | 3958 | 3813 | 3311 | 2611 | 1674 | 1061 |
| 61 234 | 901 | 2895 | 5052 | 7559 | 9456 | 9941 | 8936 | 7394 | 5446 | 3654 |

Alfabety wynikają z danych: polski to 32 litery polskie plus `q`, `v`, `x` (zapożyczenia
dopuszczone w grach słownych), angielski to 26 liter. Wyłącznie małe litery, bez myślników i spacji.

## Listy wykluczeń

Dopasowanie od **początku** słowa, nie po podciągu. Pierwsza wersja dopasowywała podciąg i wycięła
`cechuje`, `wymachuje`, `dowcipach` — wszystkie zawierają wulgarny rdzeń przypadkiem. To wariant
problemu Scunthorpe.

Świadome kompromisy, o których warto wiedzieć:

- **polski**, 279 usuniętych form, zero fałszywych trafień.
- **angielski**, 65 usuniętych form, w tym kilka niewinnych: `niggard`, `niggardly`, `niggle`,
  `niggling` (zwykłe słowa złapane przez rdzeń `nigg`), `rapeseed` (rzepak), `pussycat`,
  `pussyfoot`, `shittim` (drewno biblijne). Zostawione, bo sama forma powierzchniowa jest
  zapalna — ale to decyzja redakcyjna, nie techniczna, i można ją cofnąć.
