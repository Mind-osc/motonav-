# Components

Θέμα: σκούρο από προεπιλογή, accent μωβ (`#A78BFA`). Όλα τα χρώματα είναι ρόλοι από `tokens.json`. Κανένα component δεν έχει δικό του hex.
Εικονίδια: Lucide (ISC/MIT). Γραμματοσειρά: Inter (OFL), αριθμοί tabular για να μη «χορεύουν» ταχύτητα και αποστάσεις.
Όλα τα εικονίδια, τα χρώματα και τα κείμενα είναι δικά μας.

## Θέματα και προσαρμογή χρωμάτων (S11 → «Εμφάνιση»)

```
Φόντο      Σκούρο (προεπιλογή) | Μαύρο AMOLED | Ανοιχτό | Αυτόματο (μέρα ανοιχτό, νύχτα σκούρο, με βάση ηλιοβασίλεμα)
Χρώμα      Μωβ (προεπιλογή), Κόκκινο, Πορτοκαλί, Κίτρινο, Πράσινο, Κυανό, Μπλε, Ροζ, Λευκό/Γκρι
           + «Δικό μου»: τροχός χρωμάτων / hex
Πού φαίνεται το χρώμα   κουμπιά, γραμμή διαδρομής, βέλος θέσης, ταχύτητα, δείκτης κλίσης, εικονίδιο επόμενης κίνησης
Χάρτης     ακολουθεί το φόντο (σκούρος χάρτης σε σκούρο/AMOLED)
Προεπισκόπηση   ζωντανή μικρογραφία της οθόνης πλοήγησης πάνω από τις επιλογές
```

Κανόνας για «Δικό μου» χρώμα: κρατάμε την απόχρωση που διάλεξες και, αν χρειαστεί, τη φωτίζουμε ή τη σκουραίνουμε ώσπου να διαβάζεται (≥ 3:1 πάνω στο φόντο). Το κείμενο πάνω στο χρώμα γίνεται αυτόματα μαύρο ή λευκό (≥ 4.5:1). Αν το χρώμα άλλαξε για να διαβάζεται, δείχνουμε μικρό σημείωμα «προσαρμόστηκε για να φαίνεται στον ήλιο».

Το κόκκινο (`danger`) μένει πάντα κόκκινο για υπέρβαση ορίου και κινδύνους, ανεξάρτητα από το accent. Αν διαλέξεις Κόκκινο accent, η υπέρβαση ορίου δείχνεται και με αναβόσβημα + εικονίδιο, όχι μόνο με χρώμα.

Αποθήκευση: DataStore (`theme_mode`, `accent_hex`, `accent_preset`).

## Κουμπί

```
Button
  variants  primary (accent), secondary (surface-2 + border), ghost, danger
  sizes     md 56dp, ride 72dp (οθόνη πλοήγησης), icon-ride 72×72dp
  states    default, pressed (−8% φωτεινότητα + scale .97), focus (2dp ring focus), disabled (40% opacity), loading (spinner, ίδιο πλάτος)
  tokens    bg accent, text on-accent, radius md, type label (ride: title)
  a11y      contentDescription σε κάθε κουμπί εικονιδίου, απόσταση ≥ 12dp μεταξύ τους, haptic στο πάτημα
  used on   S01, S03, S04, S05, S07, S10, S13, S14, S15
```

## Χάρτης

```
MapView (MapLibre)
  variants  home (βόρεια πάνω), planning (εναλλακτικές), navigation (ακολουθεί + περιστρέφεται, κλίση 45°), overview
  states    loading, no-data (περιοχή χωρίς offline χάρτη: μήνυμα + «Κατέβασε»), dark, light
  tokens    στυλ χάρτη από bg/surface· διαδρομή accent 9dp με περίγραμμα bg 3dp· εναλλακτικές text-muted 6dp
  a11y      κουμπί «κέντραρε σε μένα» πάντα ορατό
  used on   S01, S03, S04, S05, S06, S08, S09, S13
```

## Βέλος θέσης

```
PositionArrow
  variants  normal (accent), no-signal (text-muted, διακεκομμένος κύκλος ακρίβειας)
  tokens    accent, περίγραμμα bg 2dp
  used on   S01, S05
```

## Πάνελ επόμενης κίνησης

```
ManeuverPanel
  variants  turn, roundabout (με αριθμό εξόδου), continue, arrive, then (μικρή δεύτερη κίνηση αν είναι < 200 m μετά)
  states    normal, near (< 200 m: μεγαλώνει, εικονίδιο accent), reroute («Νέα διαδρομή…»)
  tokens    bg surface, απόσταση type maneuver, δρόμος type street, εικονίδιο 64dp accent
  a11y      ανακοινώνεται και φωνητικά· κείμενο ≥ 24sp
  used on   S05
```

## Ταχύτητα και όριο

```
Speedometer
  states    normal (text), over-limit (danger + αναβόσβημα 1 Hz + δόνηση μία φορά), unknown-limit (χωρίς κύκλο ορίου)
  tokens    αριθμός type speed, μονάδα type label text-muted, κύκλος ορίου λευκός με κόκκινο περίγραμμα (σήμα ΚΟΚ)
  used on   S01, S05
```

## Δείκτης κλίσης

```
LeanGauge
  variants  ημικύκλιο με βελόνα, μέγιστη αριστερά/δεξιά ως μικρά σημάδια
  states    normal, uncalibrated (text-muted + «Βαθμονόμησε»)
  tokens    βελόνα accent, κλίμακα border, αριθμός type title
  used on   S05, S08
```

## Μπάρα πληροφοριών

```
TripBar
  content   ETA | χλμ που μένουν | χρόνος που μένει | (προαιρετικά) υψόμετρο, απόσταση ως βενζίνη
  tokens    bg surface, αριθμοί type title, ετικέτες label text-muted
  used on   S05, S06
```

## Καιρός

```
WeatherChip
  variants  arrival, along-route
  states    loading (skeleton), offline («Χωρίς δεδομένα»), rain (info), storm/wind (warning)
  tokens    bg surface-2, radius pill
  used on   S04, S05
```

## Γράφημα υψομέτρου

```
ElevationChart
  variants  mini (48dp), full (160dp, με δείκτη θέσης)
  tokens    γραμμή accent, γέμισμα accent 20%, άξονες text-muted
  used on   S04, S06, S09
```

## Κάρτα επιλογής διαδρομής

```
RouteOptionCard
  variants  fastest, twisty, alternative
  states    selected (περίγραμμα accent 2dp), computing (skeleton), unavailable
  content   τύπος, χρόνος, χλμ, «+12 λεπτά, πολλές στροφές» για Twisty
  used on   S04
```

## Διακόπτης / επιλογή

```
Toggle / SegmentedControl
  states    on (accent), off (surface-3), disabled
  sizes     ύψος 56dp
  used on   S04, S11, S13
```

## Γραμμή λίστας

```
ListRow
  variants  place, fuel (με παράκαμψη σε λεπτά), trip, region (με μέγεθος και πρόοδο), saved
  states    default, pressed, empty-list (μήνυμα + ενέργεια)
  tokens    ύψος ≥ 64dp, τίτλος body, δεύτερη γραμμή label text-muted
  used on   S02, S09, S10, S12, S15
```

## Μπάρα προόδου

```
ProgressBar
  states    determinate (accent), paused, error (danger + «Ξανά»)
  used on   S10
```

## Ειδοποιήσεις

```
Toast        info / warning, κάτω από το πάνελ κίνησης, 3 s, χωρίς κουμπιά κατά την οδήγηση
EdgeAlert    παλλόμενο περίγραμμα 12dp στις άκρες: danger (κίνδυνος, όριο), warning (καιρός)· σέβεται «μειωμένη κίνηση»
used on      παντού / S05
```

## Πλακίδιο αναφοράς

```
ReportTile
  variants  καιρός, αντικείμενο, ολισθηρό, έργα, κλειστός δρόμος
  sizes     πλέγμα 2×3, κάθε πλακίδιο ≥ 120dp
  states    default, pressed, sent (✓ και κλείσιμο σε 1,5 s, «Αναίρεση» 5 s)
  used on   S07
```

## Κανόνες για όλες τις οθόνες οδήγησης (S05, S06, S07)

- Τίποτα κάτω από 14sp, τίποτα κάτω από 72dp για πάτημα.
- Καμία πληκτρολόγηση πάνω από 10 km/h.
- Λειτουργεί όρθιο και πλάγιο.
- Η φωτεινότητα της οθόνης δεν πέφτει κατά την πλοήγηση.
