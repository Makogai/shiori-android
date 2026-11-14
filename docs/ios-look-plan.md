# iOS look: screen map and plan

Goal: the Shiori Android build (`app.mihon.friends`) looks and feels like the Shiori iOS app
(`tachiyomi-ios`, branch `friends`) on every screen, using native Compose / Material 3 components styled
like iOS. No data model, preference key, DB schema or feature changes.

## Screen map

| iOS (App/…) | Android counterpart | Plan |
| --- | --- | --- |
| Shell/RootView: tab bar Library · Recents · Browse · Search · Settings (SF `books.vertical.fill`, `clock.fill`, `safari.fill`, `magnifyingglass`, `gearshape.fill`) | `ui/home/HomeScreen` (Library · Updates · History · Browse · More, Material nav bar / rail) | Same 5 tabs, labels and order; filled Material Symbols (`collections_bookmark`, `schedule`, `explore`, `search`, `settings`); floating capsule bar on phones, rail kept on tablets. Badges: Browse = extension updates + failing sources, Recents = new updates, Settings = download queue. |
| Shell/Routes, DeepLinks | `MainActivity` shortcuts → `HomeScreen.Tab` | Keep every `HomeScreen.Tab`; Updates/History open Recents on the matching segment, More opens Settings. |
| Theme/AppTheme, ThemedComponents, SettingsIcon | `presentation/theme/*`, `presentation-core` | Grouped background + card colour (iOS `card(dark:)` formula) applied to every theme; default theme uses the iOS Tachiyomi palette; iOS type scale; rounder shapes; inset-grouped section helpers; coloured rounded-square settings icons. |
| Library/LibraryView (+Components, FilterBar, OptionsSheet, Sections), Common/MangaCells | `ui/library/LibraryTab`, `presentation/library/*`, `presentation/library/components/*`, `mihon/feature/friends/ui/LibraryArena`, cards strip | Large title, capsule category tabs, 2:3 covers radius 8 with hairline + shadow, capsule badges, list rows in one rounded card, iOS empty state. Arena card and Cards strip stay above the grid. Options sheet keeps its Filter / Sort / Display tabs. |
| Recents/RecentsView (+OptionsSheet, ReadingStatsCard), Recaps/* | `ui/updates/UpdatesTab` + `ui/history/HistoryTab`, `ReadingStreakCard`, `friends/ui/RecapScreens` | New Recents tab: large title and segmented picker History · Updates that hosts the existing History and Updates screens (both data sources and all actions kept). Reading streak card stays on top of History. |
| Browse/BrowseView, SourceListsView, ExtensionsView, SourceHealthView, MigrationViews | `ui/browse/BrowseTab` (Sources · Extensions · Migrate pager), `presentation/browse/*`, `mihon/feature/sourcehealth` | Large title, segmented control instead of the Material tab row, inset-grouped source rows with rounded-square icons and a "Latest" pill, capsule Get / Update buttons for extensions. |
| Search/SearchTabView, Browse/GlobalSearchView | `ui/browse/source/globalsearch/GlobalSearchScreen` (pushed from Browse) | New Search tab hosting global search without an up button; filter chips and per-source carousels with 2:3 covers. Still pushable from Browse and deep links. |
| Settings/SettingsView (+ subpages) | `ui/more/MoreTab` + `presentation/more/MoreScreen`, `SettingsMainScreen`, `presentation/more/settings/*` | More becomes Settings: large title, iOS grouping (Incognito / Tools / Appearance / Library, Reader, Downloads, Browse / Tracking, Data / Security, Advanced / Help, About), every Mihon entry kept (Downloaded only, Categories, Support us). Settings subpages render as inset-grouped cards. |
| Manga/MangaDetailsView, MangaHeader, ChapterListComponents | `ui/manga/MangaScreen`, `presentation/manga/*` | Blurred cover backdrop, iOS header, equal-width action tiles, chips, chapter rows in a card, iOS chapter header, capsule Continue button. |
| Reader/ReaderViewControls, QuickSettings, Sheets | `ui/reader/*`, `presentation/reader/*` | Floating dark-glass top / bottom bars with round controls. Image pipeline untouched. |
| Settings/StatsView | `ui/stats/StatsScreen` | Inset-grouped cards. |
| Downloads/DownloadsView | `ui/download/DownloadQueueScreen` | Theme only. |
| Friends/ArenaView, Cards/* | `mihon/feature/friends/ui/*`, `mihon/feature/friends/cards/*` | Already ported from iOS; theme only. |
| Onboarding/* | `ui/more/OnboardingScreen` | Theme only. |

## Order of work

1. Shell (tabs, floating bar, Recents, Search tab, Settings tab) and theme.
2. Library, Recents, manga details, Browse, Search, Settings subpages, reader chrome.

## Deliberately not matched

- No backdrop blur behind the tab bar or reader bars: Compose cannot blur what is behind a view, so they
  use a translucent fill instead.
- No SF Pro: the system font (Roboto) with the iOS sizes and weights.
- System back, edge-to-edge, Android permissions and the tablet navigation rail stay.
- iOS-only features (Sync, App icon picker, Smart categories, "Grouped" Recents view) are not added; this is
  a visual overhaul.

## Status

Done: theme (palette, grouped surfaces, type scale, shapes, bar tinting, large titles), shell (floating tab
bar, Recents, Search tab, Settings tab), Settings pages, Library, Recents rows and entry points, manga
details, Browse (sources, extensions), global search, reader bars, options sheets.

Left as Mihon, themed only: Arena and Cards (already ported from iOS), Statistics, download queue, migration,
source health, Library list mode, the reader settings sheet, onboarding.

The default theme changed from Material You to the iOS blue theme. Only installs that never picked a theme
are affected; the preference key and stored values are unchanged.
