# Item Converter

Convert items directly from your inventory/container/me system using recipe types.

## Features

### Inventory Conversion

Hold the conversion key (default: Left Alt) while hovering over an item to open a conversion popup. The popup shows all available conversion targets based on configured recipe types.

- **Left-click**: Convert to inventory
- **Right-click**: Drop converted item
- **Release key**: Convert all and replace in slot
- **Shift + click**: Convert entire stack
- **Number keys (1-9)**: Quick select and convert to numbered slot

### Middle-Click Auto Conversion

Target a block in the world and middle-click to automatically search your inventory for items that can be converted to that block and perform the conversion.

- In creative mode, hold shift to use this feature (normal middle-click does pick block)
- Prefers items in main inventory over hotbar
- Toggle with `/itemconverter middleclick`

### Special Tags

Configure special item tags that receive priority treatment:
- Appear first in conversion results
- Highlighted with colored border
- Preferred source for middle-click conversions

## Configuration

### Client Config (`config/item_converter.client.json`)

```json
{
  "pressTicks": 0,
  "highlightColor": -2130706433,
  "showTooltips": true,
  "allowScroll": true,
  "middleClickConvert": true,
  "specialTags": [],
  "specialTagBorderColor": -2763307
}
```

| Option | Description |
|--------|-------------|
| `pressTicks` | Ticks to hold key before popup opens (0 = instant) |
| `highlightColor` | Hover highlight color (ARGB hex) |
| `showTooltips` | Show item tooltips on hover |
| `allowScroll` | Allow scrolling to change hotbar slot while popup open |
| `middleClickConvert` | Enable middle-click block conversion |
| `specialTags` | List of tags for priority treatment (e.g. `["minecraft:logs"]`) |
| `specialTagBorderColor` | Border color for special items (ARGB hex, default: gold) |

### Common Config (`config/item_converter.common.json`)

```json
{
  "recipeTypes": ["minecraft:stonecutting"]
}
```

| Option | Description |
|--------|-------------|
| `recipeTypes` | Recipe types to use for conversions |

## Commands

| Command | Description |
|---------|-------------|
| `/itemconverter middleclick` | Toggle middle-click conversion on/off |

## Compatibility

- **Applied Energistics 2**: Works with ME terminals for converting items in ME storage

