# Integrasi ConverseCraft, AcidIsland, dan UltraCosmetics

## ConverseCraft -> AcidIsland

AcidIsland otomatis hook ke event `ConversationEndEvent` ConverseCraft jika plugin `ConverseCraft` aktif.
Jika conversation selesai dengan reason `COMPLETE`, AcidIsland membaca mapping:

```yaml
integrations:
  conversecraft:
    story-stage-conversations:
      acidisland_chapter_1: 1
      acidisland_chapter_2: 2
      acidisland_chapter_3: 3
```

Artinya conversation `acidisland_chapter_1` akan menaikkan story-stage player ke `1`.

## AcidIsland -> ConverseCraft

Saat story-stage player berubah, AcidIsland menyinkronkan flag ConverseCraft:

```text
acidisland.story.stage.1
acidisland.story.stage.2
acidisland.story.stage.3
```

Flag ini bisa dipakai di option ConverseCraft:

```yaml
options:
  chapter_2:
    text: "&a[Lanjut Chapter 2]"
    flag: "acidisland.story.stage.1"
    goto: chapter_2_intro
```

## Command

ConverseCraft action fallback:

```yaml
actions:
  - "console: ai admin story set %player% 1"
```

Player/Admin bisa mulai conversation dari AcidIsland:

```text
/ai story start acidisland_chapter_1
```

## Generator Gate

Generator AcidIsland membaca `story-stage`:

```yaml
upgrades:
  generator:
    2:
      cost: 2000
      story-stage: 1
```

## UltraCosmetics

AcidIsland sekarang softdepend ke `UltraCosmetics`, sehingga plugin cosmetic bisa dipasang berdampingan tanpa load-order issue.
Kosmetik tetap dikelola oleh UltraCosmetics; AcidIsland hanya menyediakan story/progression gate dan soft dependency.
