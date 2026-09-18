# DESIGN.md: DinDim login brand panel

## Source

- URL: https://dribbble.com/shots/24517314-Desktop-web-app-login-screen-design
- Capture date: 2026-09-18
- Evidence: Dribbble page metadata, supplied layout reference, and the existing DinDim dashboard UI

## Reference Screenshot

![Supplied login composition reference](./.firecrawl/login-layout-reference.webp)

Use the screenshot as the visual source of truth for the split-screen hierarchy and overlapping product-preview composition. Do not reproduce its blue palette, branding, or copy.

## Design Summary

The login keeps a functional form panel beside an expressive product panel. The product panel combines concise value copy, an animated abstract background, and a layered dashboard preview. The DinDim version uses its existing green palette, soft rounded surfaces, and financial-dashboard vocabulary.

## Design Tokens

### Colors

- Light brand background: vivid emerald gradient, approximately `#087457` to `#1da678`.
- Dark brand background: deep forest gradient, approximately `#062b22` to `#0d5441`.
- Preview surfaces: `#ffffff` / `#17261e` according to the active theme.
- Preview text: `#20382c` / `#edf8f1`.
- Accent: the existing DinDim green, with warm expense highlights.

### Typography

- Keep the existing Inter/system stack.
- Use a compact uppercase eyebrow, a high-impact heading, and restrained supporting copy.
- Product-preview labels are intentionally miniature and subordinate to the main message.

### Spacing And Layout

- Desktop: two-column authentication layout with the brand panel taking slightly more than half the width.
- The collage uses one main dashboard surface plus two floating cards with staggered depth.
- Rounded corners stay between `0.75rem` and `1.5rem`; shadows are soft and green-tinted.
- Mobile/tablet: simplify or hide the collage before it competes with the form.

## Components

- Animated wave field: several translucent curved lines moving at different speeds.
- Dashboard miniature: header, summary cards, chart, and recent transactions.
- Floating balance card: primary financial total.
- Floating category card: ring visualization and short legend.

## Page Patterns

- Brand copy remains readable above the visual preview.
- The collage is decorative and excluded from the accessibility tree.
- Theme changes restyle the collage through CSS variables rather than loading duplicate images.
- Motion pauses under `prefers-reduced-motion`.

## Content Style

- Calm, reassuring, and concise.
- Focus on clarity, organization, and financial confidence.

## Agent Build Instructions

- Build the preview with semantic-free decorative HTML and CSS so it stays sharp at every resolution.
- Reuse the visual vocabulary already present in the dashboard.
- Keep the animation slow and ambient; it must not distract from the login form.
- Preserve all authentication behavior and validation.

## Rerun Inputs

workflow: firecrawl-website-design-clone  
source_url: https://dribbble.com/shots/24517314-Desktop-web-app-login-screen-design  
target_stack: Angular + TypeScript + CSS  
output: DESIGN.md
