<<<<<<<< HEAD:docs/es/ui/mobile-upgrade.md
# Mejora móvil
========
# Mobile improvement
>>>>>>>> b60f5adbac7165f510c63f156fe57b324b2e59b0:docs/en/ui/mobile-upgrade.md

## Foundations

Design variables (spaced, colors, radios and shadows), a fluid typographic scale for mobile, a responsive container with `padding-inline 'insurance and global rules that avoid horizontal overflows were added.

## List

Comparisons before and after for lists in multiple screen sizes.


## Cards and talk detail

Cards and mobile detail view were improved: consistent padding,
Responsible chips, accessible buttons and containers that avoid design leaps.

### Cards
Comparisons before and then are found in external files due to the restrictions of repository binaries.

### Talk detail
The catches before/then are also stored externally.

## Summary by iteration

1. Design foundations and fluid typography.
2. Navigation settings and accessible controls.
3. Review of lists and responsive tables.
4. Cards and detail view with fluid components.
5. Administrative metric panel.
6. QA Final mobile and documentation.

## Mobile test matrix

| Viewport (px) | Chrome/Android | Safari/ios |
| ------------ | ------------ | --------- |
| 360 × 640 | ✅ | ✅ |
| 390 × 844 | ✅ | ✅ |
| 414 × 896 | ✅ | ✅ |
| 768 × 1024 | ✅ | ✅ |

No horizontal displacement in key views and lighthouse ≥ 90 in good practices and accessibility.

## Key captures

| View | Before | After |
| --------------------------- | -------------------------------------- | -----------------------------------------------------------------------------------------------------
| List of talks | * (External: Talk-list-before.png)* | * (External: Talk-list-after.png)* |
| Talk detail | * (External: Talk-dotail-before.png)* | * (external: talk-dotail-after.png)* |
| Admin metric panel | ! [Before] (admin-metrics-before.png) | ! [After] (admin-metrics-after.png) |
| Login screen | * (External: login-before.png)* | * (external: login-affter.png)* |

## Accessibility Checklist

- [x] Verified AA contrast
- [x] keyboard navigation
- [x] Aryan labels in critical buttons
- [] lazy-load in long lists *(all future iteration) *

## Checklist of performance

- [x] `font-display: swap` in web sources *(no external sources are currently used) * *
- [X] CLS <0.1 on lists thanks to `Aspect-Ratio 'reserved
- [] lazy-images on lists *(all future iteration) *